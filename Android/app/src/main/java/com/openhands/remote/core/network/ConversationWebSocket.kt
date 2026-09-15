package com.openhands.remote.core.network

import com.openhands.remote.core.model.AgentEventEnvelope
import com.openhands.remote.core.model.AuthMode
import com.openhands.remote.core.model.BackendConnection
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

interface ConversationSocket {
    fun connect(
        connection: BackendConnection,
        conversationId: String,
        afterTimestamp: String?,
        listener: Listener,
    )

    fun send(messageJson: String): Boolean

    fun close()

    interface Listener {
        fun onConnecting()
        fun onConnected()
        fun onEvent(event: AgentEventEnvelope)
        fun onFailure(message: String)
        fun onClosed(code: Int, reason: String)
    }
}

class OkHttpConversationSocket(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : ConversationSocket {
    private var socket: WebSocket? = null
    private var listener: ConversationSocket.Listener? = null

    override fun connect(
        connection: BackendConnection,
        conversationId: String,
        afterTimestamp: String?,
        listener: ConversationSocket.Listener,
    ) {
        close()
        this.listener = listener
        listener.onConnecting()
        val url = buildWebSocketUrl(
            connection.profile.baseUrl,
            conversationId,
            afterTimestamp,
        )
        val requestBuilder = Request.Builder().url(url)
        connection.apiKey?.trim()?.takeIf {
            it.isNotEmpty() && connection.profile.authMode == AuthMode.BEARER
        }?.let { requestBuilder.header("Authorization", "Bearer $it") }
        socket = client.newWebSocket(requestBuilder.build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (connection.apiKey?.isNotBlank() == true &&
                    connection.profile.authMode == AuthMode.SESSION_API_KEY
                ) {
                    webSocket.send(buildJsonObject {
                        put("type", "auth")
                        put("session_api_key", connection.apiKey!!.trim())
                    }.toString())
                }
                listener.onConnected()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching {
                    val objectValue = json.parseToJsonElement(text).jsonObject
                    AgentEventEnvelope(
                        id = objectValue["id"]?.jsonPrimitive?.contentOrNull,
                        timestamp = objectValue["timestamp"]?.jsonPrimitive?.contentOrNull,
                        type = objectValue["kind"]?.jsonPrimitive?.contentOrNull
                            ?: objectValue["type"]?.jsonPrimitive?.contentOrNull,
                        payload = objectValue,
                    )
                }.onSuccess(listener::onEvent)
                    .onFailure { listener.onFailure("无法解析 Agent 事件: ${it.message}") }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                listener.onFailure(t.message ?: "WebSocket 连接失败")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                listener.onClosed(code, reason)
            }
        })
    }

    override fun send(messageJson: String): Boolean = socket?.send(messageJson) == true

    override fun close() {
        socket?.close(NORMAL_CLOSE_CODE, "client reconnect")
        socket = null
    }

    private fun buildWebSocketUrl(
        baseUrl: String,
        conversationId: String,
        afterTimestamp: String?,
    ): String {
        val scheme = when {
            baseUrl.startsWith("https://", ignoreCase = true) -> "wss://"
            baseUrl.startsWith("http://", ignoreCase = true) -> "ws://"
            else -> error("Base URL 必须使用 http:// 或 https://")
        }
        val host = baseUrl.substringAfter("://").trimEnd('/')
        val queryParameters = buildList {
            afterTimestamp?.takeIf { it.isNotBlank() }?.let {
                add("resend_mode=since")
                add("after_timestamp=${java.net.URLEncoder.encode(it, Charsets.UTF_8.name())}")
            }
        }
        val query = queryParameters.takeIf { it.isNotEmpty() }?.joinToString("&")?.let { "?$it" }.orEmpty()
        val encodedConversationId = java.net.URLEncoder.encode(conversationId, Charsets.UTF_8.name())
        return "$scheme$host/sockets/events/$encodedConversationId$query"
    }

    private companion object {
        const val NORMAL_CLOSE_CODE = 1000
    }
}
