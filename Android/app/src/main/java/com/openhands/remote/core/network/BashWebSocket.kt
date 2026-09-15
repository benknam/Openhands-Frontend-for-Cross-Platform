package com.openhands.remote.core.network

import com.openhands.remote.core.model.AuthMode
import com.openhands.remote.core.model.BackendConnection
import com.openhands.remote.core.model.BashCommandResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit

interface BashCommandRunner {
    fun connect(connection: BackendConnection, listener: Listener)
    fun execute(command: String, cwd: String, timeoutSeconds: Int = 30): Boolean
    fun close()

    interface Listener {
        fun onConnecting()
        fun onConnected()
        fun onOutput(command: String, stdout: String, stderr: String)
        fun onCompleted(result: BashCommandResult)
        fun onFailure(message: String)
        fun onClosed(code: Int, reason: String)
    }
}

/**
 * Implements the agent-server /sockets/bash-events protocol. Commands are sent
 * as JSON after authentication; BashCommand echoes provide IDs used to join
 * subsequent incremental BashOutput frames.
 */
class OkHttpBashCommandRunner(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : BashCommandRunner {
    private data class Pending(
        val command: String,
        val cwd: String,
        val timeoutSeconds: Int,
    )

    private data class Active(
        val request: Pending,
        val stdout: StringBuilder = StringBuilder(),
        val stderr: StringBuilder = StringBuilder(),
    )

    private var webSocket: WebSocket? = null
    private var ready = false
    private var listener: BashCommandRunner.Listener? = null
    private var connection: BackendConnection? = null
    private var reconnectJob: Job? = null
    private var retryAttempt = 0
    private var manuallyClosed = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val waiting = ArrayDeque<Pending>()
    private val pendingEcho = ArrayDeque<Pending>()
    private val active = mutableMapOf<String, Active>()

    @Synchronized
    override fun connect(connection: BackendConnection, listener: BashCommandRunner.Listener) {
        close()
        manuallyClosed = false
        this.connection = connection
        this.listener = listener
        listener.onConnecting()
        openSocket(connection)
    }

    @Synchronized
    private fun openSocket(connection: BackendConnection) {
        val requestBuilder = Request.Builder().url(buildBashWebSocketUrl(connection.profile.baseUrl))
        connection.apiKey?.trim()?.takeIf {
            it.isNotEmpty() && connection.profile.authMode == AuthMode.BEARER
        }?.let { requestBuilder.header("Authorization", "Bearer $it") }
        webSocket = client.newWebSocket(requestBuilder.build(), socketListener(connection))
    }

    @Synchronized
    override fun execute(command: String, cwd: String, timeoutSeconds: Int): Boolean {
        val request = Pending(command, cwd, timeoutSeconds.coerceIn(1, 120))
        val socket = webSocket
        if (!ready || socket == null) {
            waiting.addLast(request)
            return true
        }
        send(request, socket)
        return true
    }

    @Synchronized
    override fun close() {
        manuallyClosed = true
        reconnectJob?.cancel()
        reconnectJob = null
        ready = false
        webSocket?.close(NORMAL_CLOSE_CODE, "client closed")
        webSocket = null
        waiting.clear()
        pendingEcho.clear()
        active.clear()
    }

    @Synchronized
    private fun scheduleReconnect() {
        if (manuallyClosed || reconnectJob?.isActive == true || connection == null) return
        val delayMillis = minOf(60_000L, 1_000L shl retryAttempt.coerceAtMost(6))
        retryAttempt += 1
        reconnectJob = scope.launch {
            delay(delayMillis)
            synchronized(this@OkHttpBashCommandRunner) {
                reconnectJob = null
                if (!manuallyClosed) connection?.let(::openSocket)
            }
        }
    }

    private fun socketListener(connection: BackendConnection) = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            synchronized(this@OkHttpBashCommandRunner) {
                if (connection.apiKey?.isNotBlank() == true &&
                    connection.profile.authMode == AuthMode.SESSION_API_KEY
                ) {
                    webSocket.send(buildJsonObject {
                        put("type", "auth")
                        put("session_api_key", connection.apiKey!!.trim())
                    }.toString())
                }
                ready = true
                retryAttempt = 0
                listener?.onConnected()
                while (waiting.isNotEmpty()) send(waiting.removeFirst(), webSocket)
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            runCatching { json.parseToJsonElement(text).jsonObject }
                .onSuccess(::handleMessage)
                .onFailure { /* Ignore malformed frames like the web client. */ }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            synchronized(this@OkHttpBashCommandRunner) {
                ready = false
                failInFlight(t.message ?: "Bash WebSocket 连接失败，正在重连")
                scheduleReconnect()
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            synchronized(this@OkHttpBashCommandRunner) {
                ready = false
                failInFlight("Bash WebSocket 已关闭，正在重连…")
                scheduleReconnect()
            }
            listener?.onClosed(code, reason)
        }
    }

    private fun send(request: Pending, socket: WebSocket) {
        pendingEcho.addLast(request)
        socket.send(buildJsonObject {
            put("command", request.command)
            put("cwd", request.cwd)
            put("timeout", request.timeoutSeconds)
        }.toString())
    }

    @Synchronized
    private fun handleMessage(value: JsonObject) {
        val kind = value["kind"]?.jsonPrimitive?.contentOrNull.orEmpty()
        when {
            kind == "BashError" || kind.contains("BashError") -> {
                failAll(
                    "Bash 错误：${value["code"]?.jsonPrimitive?.contentOrNull ?: "unknown"}: " +
                        (value["detail"]?.jsonPrimitive?.contentOrNull ?: "unknown"),
                )
            }
            kind == "BashCommand" || kind.contains("BashCommand") -> {
                val id = value["id"]?.jsonPrimitive?.contentOrNull
                val request = if (pendingEcho.isEmpty()) null else pendingEcho.removeFirst()
                if (id != null && request != null) active[id] = Active(request)
            }
            kind == "BashOutput" || kind.contains("BashOutput") -> {
                val id = value["command_id"]?.jsonPrimitive?.contentOrNull ?: return
                val command = active[id] ?: return
                val stdout = value["stdout"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val stderr = value["stderr"]?.jsonPrimitive?.contentOrNull.orEmpty()
                command.stdout.append(stdout)
                command.stderr.append(stderr)
                listener?.onOutput(command.request.command, stdout, stderr)
                val exitCode = value["exit_code"]?.jsonPrimitive?.intOrNull
                if (exitCode != null) {
                    active.remove(id)
                    listener?.onCompleted(
                        BashCommandResult(
                            command = command.request.command,
                            exitCode = exitCode,
                            stdout = command.stdout.toString(),
                            stderr = command.stderr.toString(),
                        ),
                    )
                }
            }
        }
    }
    @Synchronized
    private fun failInFlight(message: String) {
        val hadInFlight = pendingEcho.isNotEmpty() || active.isNotEmpty()
        pendingEcho.clear()
        active.clear()
        if (hadInFlight) listener?.onFailure(message)
    }

    @Synchronized
    private fun failAll(message: String) {
        waiting.clear()
        pendingEcho.clear()
        active.clear()
        listener?.onFailure(message)
    }

    private fun buildBashWebSocketUrl(baseUrl: String): String {
        val scheme = when {
            baseUrl.startsWith("https://", ignoreCase = true) -> "wss://"
            baseUrl.startsWith("http://", ignoreCase = true) -> "ws://"
            else -> error("Base URL 必须使用 http:// 或 https://")
        }
        return "$scheme${baseUrl.substringAfter("://").trimEnd('/')}/sockets/bash-events"
    }

    private companion object {
        const val NORMAL_CLOSE_CODE = 1000
    }
}
