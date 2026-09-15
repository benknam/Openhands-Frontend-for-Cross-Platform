package com.openhands.remote.core.network

import com.openhands.remote.core.model.AuthMode
import com.openhands.remote.core.model.BackendCapabilities
import com.openhands.remote.core.model.BackendConnection
import com.openhands.remote.core.model.AgentProfileResponse
import com.openhands.remote.core.model.AgentEventEnvelope
import com.openhands.remote.core.model.ConversationEventHistoryPage
import com.openhands.remote.core.model.AgentProfileSummary
import com.openhands.remote.core.model.LlmProfileResponse
import com.openhands.remote.core.model.LlmProfileSummary
import com.openhands.remote.core.model.PluginSpec
import com.openhands.remote.core.model.BashCommandResult
import com.openhands.remote.core.model.ConversationSearchResponse
import com.openhands.remote.core.model.MetricsSnapshot
import com.openhands.remote.core.model.TokenUsage
import com.openhands.remote.core.model.SendMessageRequest
import com.openhands.remote.core.model.MessageRole
import com.openhands.remote.core.model.MessageContent
import com.openhands.remote.core.model.RuntimeConversationInfo
import com.openhands.remote.core.model.RuntimeMetrics
import com.openhands.remote.core.model.RuntimeConversationStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.add
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AgentServerHttpClient(
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun getServerInfo(connection: BackendConnection): Result<String> =
        get(connection, "/server_info")

    suspend fun getHealth(connection: BackendConnection): Result<String> =
        get(connection, "/health")

    suspend fun getReady(connection: BackendConnection): Result<String> =
        get(connection, "/ready")

    suspend fun probeCapabilities(
        connection: BackendConnection,
        conversationId: String? = null,
    ): Result<BackendCapabilities> = withContext(Dispatchers.IO) {
        runCatching {
            val serverInfoBody = get(connection, "/server_info").getOrNull().orEmpty()
            val profileStatus = probeStatus(connection, "/api/agent-profiles", "GET")
            val fileStatus = probeStatus(
                connection,
                "/api/file/download?path=${java.net.URLEncoder.encode(".__openhands_probe__", Charsets.UTF_8.name())}",
                "GET",
            )
            val bashStatus = probeStatus(connection, "/api/bash/execute_bash_command", "POST")
            val socketStatus = probeWebSocket(connection, "/sockets/bash-events")
            val workingDir = conversationId?.let { getConversationWorkingDir(connection, it).getOrNull() }
            BackendCapabilities(
                serverVersion = extractString(serverInfoBody, setOf("version", "agent_server_version")),
                profiles = isRouteAvailable(profileStatus),
                fileDownload = isRouteAvailable(fileStatus),
                bashHttp = isRouteAvailable(bashStatus),
                bashWebSocket = socketStatus == 101,
                gitWorkspace = false,
                workingDir = workingDir,
            )
        }
    }

    suspend fun getConversationWorkingDir(
        connection: BackendConnection,
        conversationId: String,
    ): Result<String?> = getConversation(connection, conversationId).map { body ->
        extractWorkingDir(body)
    }

    internal fun extractWorkingDir(body: String): String? {
        val element = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return null
        return findWorkingDir(element)
    }

    private fun findWorkingDir(element: kotlinx.serialization.json.JsonElement): String? {
        if (element !is JsonObject) return null
        listOf("working_dir", "working_directory", "cwd", "selected_workspace").forEach { key ->
            (element[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.startsWith("/") }?.let { return it }
        }
        when (val workspace = element["workspace"]) {
            is JsonPrimitive -> workspace.contentOrNull?.takeIf { it.startsWith("/") }?.let { return it }
            is JsonObject -> findWorkingDir(workspace)?.let { return it }
            else -> Unit
        }
        listOf("conversation", "info").forEach { key ->
            element[key]?.let { findWorkingDir(it)?.let { path -> return path } }
        }
        return null
    }


    suspend fun getAgentProfiles(connection: BackendConnection): Result<AgentProfileResponse> =
        get(connection, "/api/agent-profiles").map(::parseAgentProfiles)

    suspend fun getLlmProfiles(connection: BackendConnection): Result<LlmProfileResponse> =
        get(connection, "/api/profiles").map(::parseLlmProfiles)

    suspend fun activateLlmProfile(
        connection: BackendConnection,
        profileName: String,
    ): Result<String> = post(
        connection,
        "/api/profiles/${profileName.encodePathSegment()}/activate",
    )

    suspend fun getLlmProfile(
        connection: BackendConnection,
        profileName: String,
        exposeSecrets: String? = "encrypted",
    ): Result<JsonObject> = get(
        connection,
        "/api/profiles/${profileName.encodePathSegment()}",
        extraHeaders = exposeSecrets?.let { mapOf("X-Expose-Secrets" to it) }.orEmpty(),
    ).map { body ->
        json.parseToJsonElement(body) as? JsonObject ?: error("LLM 配置文件响应格式错误")
    }

    suspend fun saveLlmProfile(
        connection: BackendConnection,
        profileName: String,
        llm: JsonObject,
        includeSecrets: Boolean = true,
    ): Result<String> = post(
        connection,
        "/api/profiles/${profileName.encodePathSegment()}",
        buildJsonObject {
            put("llm", llm)
            put("include_secrets", includeSecrets)
        },
    )

    suspend fun renameLlmProfile(
        connection: BackendConnection,
        profileName: String,
        newName: String,
    ): Result<String> = post(
        connection,
        "/api/profiles/${profileName.encodePathSegment()}/rename",
        buildJsonObject { put("new_name", newName) },
    )

    suspend fun deleteLlmProfile(
        connection: BackendConnection,
        profileName: String,
    ): Result<String> = delete(
        connection,
        "/api/profiles/${profileName.encodePathSegment()}",
    )

    suspend fun getAgentProfile(
        connection: BackendConnection,
        profileName: String,
    ): Result<JsonObject> = get(
        connection,
        "/api/agent-profiles/${profileName.encodePathSegment()}",
    ).map { body ->
        json.parseToJsonElement(body) as? JsonObject ?: error("代理配置文件响应格式错误")
    }

    suspend fun saveAgentProfile(
        connection: BackendConnection,
        profileName: String,
        profile: JsonObject,
    ): Result<String> = post(
        connection,
        "/api/agent-profiles/${profileName.encodePathSegment()}",
        profile,
    )

    suspend fun renameAgentProfile(
        connection: BackendConnection,
        profileName: String,
        newName: String,
    ): Result<String> = post(
        connection,
        "/api/agent-profiles/${profileName.encodePathSegment()}/rename",
        buildJsonObject { put("new_name", newName) },
    )

    suspend fun deleteAgentProfile(
        connection: BackendConnection,
        profileName: String,
    ): Result<String> = delete(
        connection,
        "/api/agent-profiles/${profileName.encodePathSegment()}",
    )

    suspend fun getAgentSettingsSchema(connection: BackendConnection): Result<JsonObject> =
        get(connection, "/api/settings/agent-schema").map { body ->
            json.parseToJsonElement(body) as? JsonObject ?: error("Agent schema 响应格式错误")
        }

    suspend fun getConversationSettingsSchema(connection: BackendConnection): Result<JsonObject> =
        get(connection, "/api/settings/conversation-schema").map { body ->
            json.parseToJsonElement(body) as? JsonObject ?: error("Conversation schema 响应格式错误")
        }

    suspend fun switchConversationLlmProfile(
        connection: BackendConnection,
        conversationId: String,
        profileName: String,
    ): Result<String> = post(
        connection,
        "/api/conversations/${conversationId.encodePathSegment()}/switch_profile",
        buildJsonObject { put("profile_name", profileName) },
    )

    internal fun parseAgentProfiles(body: String): AgentProfileResponse {
        val root = json.parseToJsonElement(body) as? JsonObject ?: return AgentProfileResponse()
        val profilesElement = root["profiles"] ?: root["agent_profiles"] ?: root["models"]
        val profiles = when (profilesElement) {
            is JsonArray -> profilesElement.mapNotNull { item ->
                when (item) {
                    is JsonObject -> parseAgentProfile(item)
                    is JsonPrimitive -> item.contentOrNull?.let { AgentProfileSummary(it, it) }
                    else -> null
                }
            }
            is JsonObject -> profilesElement.entries.mapNotNull { (key, value) ->
                parseAgentProfile(value as? JsonObject, key)
            }
            else -> emptyList()
        }
        val active = root["active_agent_profile_id"]?.jsonPrimitive?.contentOrNull
            ?: root["active_profile_id"]?.jsonPrimitive?.contentOrNull
        return AgentProfileResponse(profiles.distinctBy { it.id ?: it.name }, active)
    }

    private fun parseAgentProfile(
        profile: JsonObject?,
        fallbackName: String? = null,
    ): AgentProfileSummary? {
        if (profile == null) {
            return fallbackName?.let { AgentProfileSummary(it, it) }
        }
        val name = profile["name"]?.jsonPrimitive?.contentOrNull
            ?: profile["id"]?.jsonPrimitive?.contentOrNull
            ?: fallbackName
            ?: return null
        val id = profile["id"]?.jsonPrimitive?.contentOrNull ?: fallbackName
        return AgentProfileSummary(
            id = id,
            name = name,
            agentKind = profile["agent_kind"]?.jsonPrimitive?.contentOrNull,
            llmProfileRef = profile["llm_profile_ref"]?.jsonPrimitive?.contentOrNull,
        )
    }

    internal fun parseLlmProfiles(body: String): LlmProfileResponse {
        val root = json.parseToJsonElement(body) as? JsonObject ?: run {
            return LlmProfileResponse()
        }
        val collected = linkedMapOf<String, LlmProfileSummary>()
        sequenceOf(root["profiles"], root["llm_profiles"], root["items"], root["data"]).forEach { element ->
            collectLlmProfiles(element).forEach { profile ->
                collected.putIfAbsent(profile.name, profile)
            }
        }
        val profiles = collected.values.toList()
        val active = sequenceOf("active_profile", "active_profile_name", "active_llm_profile")
            .firstNotNullOfOrNull { root[it]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank) }
        return LlmProfileResponse(profiles = profiles, activeProfile = active)
    }

    private fun collectLlmProfiles(element: kotlinx.serialization.json.JsonElement?): List<LlmProfileSummary> =
        when (element) {
            is JsonArray -> element.mapNotNull { item ->
                when (item) {
                    is JsonObject -> parseLlmProfile(item)
                    is JsonPrimitive -> item.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
                        ?.let { LlmProfileSummary(name = it) }
                    else -> null
                }
            }
            is JsonObject -> element.entries.mapNotNull { (key, value) ->
                when (value) {
                    is JsonObject -> parseLlmProfile(value, key)
                    is JsonPrimitive -> value.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
                        ?.let { LlmProfileSummary(name = key.ifBlank { it }, model = it) }
                    else -> null
                }
            }
            else -> emptyList()
        }

    private fun parseLlmProfile(
        profile: JsonObject,
        fallbackName: String? = null,
    ): LlmProfileSummary? {
        val nestedLlm = profile["llm"] as? JsonObject
        val name = profile["name"]?.jsonPrimitive?.contentOrNull?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: profile["id"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
            ?: fallbackName?.trim()?.takeIf { it.isNotEmpty() }
            ?: return null
        val model = profile["model"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
            ?: nestedLlm?.get("model")?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        val baseUrl = profile["base_url"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
            ?: nestedLlm?.get("base_url")?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        val apiKeySet = profile["api_key_set"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
            ?: nestedLlm?.get("api_key_set")?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
            ?: false
        return LlmProfileSummary(name = name, model = model, baseUrl = baseUrl, apiKeySet = apiKeySet)
    }

    suspend fun activateAgentProfile(
        connection: BackendConnection,
        profileId: String,
    ): Result<String> = post(
        connection,
        "/api/agent-profiles/${profileId.encodePathSegment()}/activate",
    )

    suspend fun executeCommand(
        connection: BackendConnection,
        command: String,
        cwd: String? = null,
        timeoutSeconds: Int = 30,
    ): Result<BashCommandResult> {
        val body = buildJsonObject {
            put("command", command)
            cwd?.trim()?.takeIf { it.isNotEmpty() }?.let { put("cwd", it) }
            put("timeout", timeoutSeconds.coerceIn(1, 120))
        }
        return post(connection, "/api/bash/execute_bash_command", body).map { response ->
            val value = json.parseToJsonElement(response).jsonObject
            BashCommandResult(
                command = command,
                exitCode = value["exit_code"]?.jsonPrimitive?.intOrNull ?: -1,
                stdout = value["stdout"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                stderr = value["stderr"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
        }
    }

    suspend fun listWorkspaceFiles(
        connection: BackendConnection,
        workingDir: String,
        maxFiles: Int = 2_000,
    ): Result<List<String>> {
        val command = "find . \\( -name .git -prune -o -name node_modules -prune -o -name .venv -prune -o -name venv -prune -o -name __pycache__ -prune -o -name dist -prune -o -name build -prune -o -name .next -prune -o -name .cache -prune \\) -o -type f -print 2>/dev/null | sort | head -n ${maxFiles.coerceIn(1, 2_000)}"
        return executeCommand(connection, command, workingDir).map { result ->
            result.stdout.lineSequence()
                .map { it.trim().removePrefix("./") }
                .filter { it.isNotEmpty() }
                .distinct()
                .take(maxFiles.coerceIn(1, 2_000))
                .toList()
        }
    }

    suspend fun searchConversations(connection: BackendConnection): Result<String> =
        get(connection, "/api/conversations/search?limit=100")

    suspend fun searchConversationSummaries(
        connection: BackendConnection,
        pageId: String? = null,
        limit: Int = 30,
    ): Result<ConversationSearchResponse> {
        val query = buildString {
            append("/api/conversations/search?limit=")
            append(limit.coerceIn(1, 100))
            pageId?.takeIf { it.isNotBlank() }?.let {
                append("&page_id=")
                append(java.net.URLEncoder.encode(it, Charsets.UTF_8.name()))
            }
        }
        return get(connection, query).map { body ->
            json.decodeFromString<ConversationSearchResponse>(body)
        }
    }

    suspend fun getConversation(
        connection: BackendConnection,
        conversationId: String,
    ): Result<String> = get(connection, "/api/conversations/${conversationId.encodePathSegment()}")

    suspend fun searchConversationEvents(
        connection: BackendConnection,
        conversationId: String,
        limit: Int = 50,
        pageId: String? = null,
    ): Result<ConversationEventHistoryPage> {
        val encodedId = conversationId.encodePathSegment()
        val query = buildString {
            append("limit=").append(limit.coerceIn(1, 100))
            append("&sort_order=TIMESTAMP_DESC")
            pageId?.takeIf { it.isNotBlank() }?.let {
                append("&page_id=").append(java.net.URLEncoder.encode(it, Charsets.UTF_8.name()))
            }
        }
        val local = get(connection, "/api/conversations/$encodedId/events/search?$query")
        if (local.isSuccess) return local.map(::parseConversationEventHistory)
        return get(connection, "/api/v1/conversation/$encodedId/events/search?$query")
            .map(::parseConversationEventHistory)
    }

    private fun parseConversationEventHistory(body: String): ConversationEventHistoryPage {
        val root = json.parseToJsonElement(body)
        val items = when (root) {
            is JsonObject -> (root["items"] ?: root["events"]) as? JsonArray ?: JsonArray(emptyList())
            is JsonArray -> root
            else -> JsonArray(emptyList())
        }
        val events = items.mapNotNull { item ->
            val event = item as? JsonObject ?: return@mapNotNull null
            AgentEventEnvelope(
                id = event["id"]?.jsonPrimitive?.contentOrNull,
                timestamp = event["timestamp"]?.jsonPrimitive?.contentOrNull,
                type = event["kind"]?.jsonPrimitive?.contentOrNull
                    ?: event["type"]?.jsonPrimitive?.contentOrNull,
                payload = (event["payload"] as? JsonObject) ?: event,
            )
        }.mapIndexed { index, event -> index to event }
            .sortedWith(compareBy({ it.second.timestamp.orEmpty() }, { it.first }))
            .map { it.second }
        val nextPageId = (root as? JsonObject)?.get("next_page_id")?.jsonPrimitive?.contentOrNull
        return ConversationEventHistoryPage(events, nextPageId)
    }

    suspend fun createConversation(
        connection: BackendConnection,
        initialMessage: String? = null,
        agentProfileId: String? = null,
        workingDir: String? = null,
        plugins: List<PluginSpec> = emptyList(),
    ): Result<String> {
        val body = buildJsonObject {
            workingDir?.trim()?.takeIf { it.isNotEmpty() }?.let { root ->
                putJsonObject("workspace") {
                    put("working_dir", root)
                    put("kind", "LocalWorkspace")
                }
            }
            initialMessage?.takeIf { it.isNotBlank() }?.let { message ->
                putJsonObject("initial_message") {
                    put("role", "user")
                    putJsonArray("content") {
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", message)
                        })
                    }
                    put("run", false)
                }
            }
            agentProfileId?.takeIf { it.isNotBlank() }?.let { put("agent_profile_id", it) }
            if (plugins.isNotEmpty()) {
                putJsonArray("plugins") {
                    plugins.forEach { plugin ->
                        add(buildJsonObject {
                            put("source", plugin.source)
                            plugin.ref?.takeIf { it.isNotBlank() }?.let { put("ref", it) }
                            plugin.repoPath?.takeIf { it.isNotBlank() }?.let { put("repo_path", it) }
                        })
                    }
                }
            }
        }
        return post(connection, "/api/conversations", body)
    }

    suspend fun createConversationId(
        connection: BackendConnection,
        workingDir: String?,
        agentProfileId: String? = null,
        plugins: List<PluginSpec> = emptyList(),
    ): Result<String> = runCatching {
        val settings = get(connection, "/api/settings").getOrThrow()
        val profileId = agentProfileId?.takeIf { it.isNotBlank() }
            ?: json.parseToJsonElement(settings)
                .jsonObject["active_agent_profile_id"]
                ?.jsonPrimitive
                ?.content
                ?.takeIf { it.isNotBlank() }
            ?: error("服务端未配置 active_agent_profile_id")
        val body = createConversation(
            connection,
            agentProfileId = profileId,
            workingDir = workingDir,
            plugins = plugins,
        ).getOrThrow()
        json.parseToJsonElement(body).jsonObject["id"]?.jsonPrimitive?.content
            ?: error("创建会话响应缺少 id")
    }

    suspend fun sendMessage(
        connection: BackendConnection,
        conversationId: String,
        message: String,
    ): Result<String> {
        val body = buildJsonObject {
            put("role", "user")
            putJsonArray("content") {
                add(buildJsonObject {
                    put("type", "text")
                    put("text", message)
                })
            }
            put("run", false)
        }
        return post(connection, "/api/conversations/${conversationId.encodePathSegment()}/events", body)
    }

    suspend fun runConversation(connection: BackendConnection, conversationId: String): Result<String> =
        post(connection, "/api/conversations/${conversationId.encodePathSegment()}/run")

    suspend fun pauseConversation(connection: BackendConnection, conversationId: String): Result<String> =
        post(connection, "/api/conversations/${conversationId.encodePathSegment()}/pause")

    suspend fun interruptConversation(connection: BackendConnection, conversationId: String): Result<String> =
        post(connection, "/api/conversations/${conversationId.encodePathSegment()}/interrupt")

    suspend fun deleteConversation(connection: BackendConnection, conversationId: String): Result<String> =
        delete(connection, "/api/conversations/${conversationId.encodePathSegment()}")

    suspend fun downloadFile(
        connection: BackendConnection,
        path: String,
    ): Result<ByteArray> = getBytes(
        connection,
        "/api/file/download?path=${java.net.URLEncoder.encode(path, Charsets.UTF_8.name())}",
    )

    private suspend fun getBytes(
        connection: BackendConnection,
        path: String,
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val requestBuilder = Request.Builder()
                .url(buildUrl(connection.profile.baseUrl, path))
                .get()
            applyAuthentication(requestBuilder, connection)
            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    error("HTTP ${response.code}: ${(response.body?.string().orEmpty()).take(MAX_ERROR_BODY_LENGTH)}")
                }
                response.body?.bytes() ?: error("文件响应为空")
            }
        }
    }

    private fun probeStatus(
        connection: BackendConnection,
        path: String,
        method: String,
    ): Int? = runCatching {
        val requestBuilder = Request.Builder()
            .url(buildUrl(connection.profile.baseUrl, path))
        if (method == "GET") requestBuilder.get()
        else requestBuilder.post("{}".toRequestBody(JSON_MEDIA_TYPE))
        applyAuthentication(requestBuilder, connection)
        httpClient.newCall(requestBuilder.build()).execute().use { it.code }
    }.getOrNull()

    private fun extractString(body: String, keys: Set<String>): String? = runCatching {
        findString(json.parseToJsonElement(body), keys)
    }.getOrNull()

    private fun findString(
        element: kotlinx.serialization.json.JsonElement,
        keys: Set<String>,
    ): String? {
        if (element is kotlinx.serialization.json.JsonObject) {
            element.entries.firstNotNullOfOrNull { (key, value) ->
                if (key.lowercase() in keys && value is kotlinx.serialization.json.JsonPrimitive) {
                    value.contentOrNull
                } else null
            }?.let { return it }
            element.values.forEach { child -> findString(child, keys)?.let { return it } }
        } else if (element is kotlinx.serialization.json.JsonArray) {
            element.forEach { child -> findString(child, keys)?.let { return it } }
        }
        return null
    }

    private fun probeWebSocket(
        connection: BackendConnection,
        path: String,
        timeoutSeconds: Long = 5,
    ): Int? {
        val result = CountDownLatch(1)
        var status: Int? = null
        val requestBuilder = Request.Builder().url(buildWebSocketUrl(connection.profile.baseUrl, path))
        applyAuthentication(requestBuilder, connection)
        val socket = httpClient.newWebSocket(requestBuilder.build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                status = response.code
                webSocket.close(1000, "capability probe")
                result.countDown()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                status = response?.code
                result.countDown()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                result.countDown()
            }
        })
        if (!result.await(timeoutSeconds, TimeUnit.SECONDS)) {
            socket.cancel()
            return null
        }
        return status
    }

    private fun isRouteAvailable(status: Int?): Boolean =
        status != null && status != 404 && status != 405

    private fun buildWebSocketUrl(baseUrl: String, path: String): String {
        val scheme = when {
            baseUrl.startsWith("https://", ignoreCase = true) -> "wss://"
            baseUrl.startsWith("http://", ignoreCase = true) -> "ws://"
            else -> error("Base URL 必须使用 http:// 或 https://")
        }
        return "$scheme${baseUrl.substringAfter("://").trimEnd('/')}/${path.trimStart('/')}"
    }

    suspend fun updateConversationTitle(
        connection: BackendConnection,
        conversationId: String,
        title: String,
    ): Result<String> = requestWithBody(
        connection,
        "/api/conversations/${conversationId.encodePathSegment()}",
        "PATCH",
        buildJsonObject { put("title", title.trim()) },
    )

    // ============================================================
    // New API methods from upstream 1.18.0
    // ============================================================

    /**
     * Update conversation tags (upstream: updateConversationTags).
     * PATCH /api/conversations/{id} with tags map.
     */
    suspend fun updateConversationTags(
        connection: BackendConnection,
        conversationId: String,
        tags: Map<String, String>,
    ): Result<JsonObject> = requestWithBody(
        connection,
        "/api/conversations/${conversationId.encodePathSegment()}",
        "PATCH",
        buildJsonObject { putJsonObject("tags") { tags.forEach { (k, v) -> put(k, v) } } },
    ).map { json.parseToJsonElement(it) as? JsonObject ?: error("响应格式错误") }

    /**
     * Fork/branch a conversation (upstream: forkConversation).
     * POST /api/conversations/{id}/fork with from_event_id.
     */
    suspend fun forkConversation(
        connection: BackendConnection,
        sourceConversationId: String,
        fromEventId: String,
        title: String? = null,
    ): Result<JsonObject> = requestWithBody(
        connection,
        "/api/conversations/${sourceConversationId.encodePathSegment()}/fork",
        "POST",
        buildJsonObject {
            put("from_event_id", fromEventId)
            title?.let { put("title", it) }
        },
    ).map { json.parseToJsonElement(it) as? JsonObject ?: error("响应格式错误") }

    /**
     * Get event parent ID for branching (upstream: getEventParentId).
     * GET /api/events/{eventId} to retrieve parent_id.
     */
    suspend fun getEventParentId(
        connection: BackendConnection,
        eventId: String,
    ): Result<String?> = get(connection, "/api/events/${eventId.encodePathSegment()}").map { body ->
        val root = json.parseToJsonElement(body) as? JsonObject ?: return@map null
        (root["parent_id"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    }

    /**
     * Get runtime conversation info with stats and metrics (upstream: getRuntimeConversation).
     * GET /api/conversations/{id}/runtime
     */
    suspend fun getRuntimeConversation(
        connection: BackendConnection,
        conversationId: String,
    ): Result<RuntimeConversationInfo> = get(connection, "/api/conversations/${conversationId.encodePathSegment()}/runtime").map { body ->
        val root = json.parseToJsonElement(body) as? JsonObject ?: error("响应格式错误")
        RuntimeConversationInfo(
            id = root["id"]?.jsonPrimitive?.contentOrNull ?: conversationId,
            title = root["title"]?.jsonPrimitive?.contentOrNull,
            metrics = parseMetricsSnapshot(root["metrics"]),
            createdAt = root["created_at"]?.jsonPrimitive?.contentOrNull,
            updatedAt = root["updated_at"]?.jsonPrimitive?.contentOrNull,
            status = (root["status"]?.jsonPrimitive?.contentOrNull ?: "UNKNOWN").uppercase(),
            stats = RuntimeConversationStats(
                usageToMetrics = parseUsageToMetrics(root["usage_to_metrics"]),
            ),
        )
    }

    /**
     * Send structured message with content array (upstream: sendMessage).
     * POST /api/conversations/{id}/events with SendMessageRequest.
     */
    suspend fun sendStructuredMessage(
        connection: BackendConnection,
        conversationId: String,
        role: MessageRole,
        contents: List<MessageContent>,
        run: Boolean = true,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val contentArray = buildJsonArray {
                contents.forEach { content ->
                    when (content) {
                        is MessageContent.Text -> buildJsonObject {
                            put("type", "text")
                            put("text", content.text)
                        }
                        is MessageContent.Image -> buildJsonObject {
                            put("type", "image")
                            putJsonArray("image_urls") {
                                content.imageUrls.forEach { add(it) }
                            }
                        }
                    }
                }
            }
            val body = buildJsonObject {
                put("role", role.name.lowercase())
                put("content", contentArray)
                if (run) put("run", true)
            }
            val requestBuilder = Request.Builder()
                .url(buildUrl(connection.profile.baseUrl, "/api/conversations/${conversationId.encodePathSegment()}/events"))
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            applyAuthentication(requestBuilder, connection)
            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    error("HTTP ${response.code}: ${(response.body?.string().orEmpty()).take(MAX_ERROR_BODY_LENGTH)}")
                }
            }
        }
    }

    /**
     * Get hooks for a conversation (upstream: getHooks).
     * GET /api/conversations/{id}/hooks
     */
    suspend fun getConversationHooks(
        connection: BackendConnection,
        projectDir: String? = null,
    ): Result<JsonObject> = post(
        connection,
        "/api/hooks",
        buildJsonObject {
            projectDir?.takeIf { it.isNotBlank() }?.let { put("project_dir", it) }
        },
    ).map { body ->
        json.parseToJsonElement(body) as? JsonObject ?: error("响应格式错误")
    }

    /**
     * Load skills from Agent Server.
     * POST /api/skills  (GET is 405 Method Not Allowed)
     */
    suspend fun getAvailableSkills(
        connection: BackendConnection,
        projectDir: String? = null,
    ): Result<List<JsonObject>> = post(
        connection,
        "/api/skills",
        buildJsonObject {
            // One request avoids waiting on a second loader when the server has no public catalog.
            put("load_public", true)
            put("load_user", true)
            put("load_project", true)
            put("load_org", false)
            projectDir?.takeIf { it.isNotBlank() }?.let { put("project_dir", it) }
        },
    ).map { body -> parseNamedObjects(body, "skills") }

    private fun jsonName(item: JsonObject): String =
        item["name"]?.jsonPrimitive?.contentOrNull
            ?: item["id"]?.jsonPrimitive?.contentOrNull
            ?: "未命名"

    suspend fun getSecrets(
        connection: BackendConnection,
    ): Result<List<JsonObject>> = get(connection, "/api/settings/secrets").map { body ->
        parseNamedObjects(body, "secrets")
    }

    suspend fun upsertSecret(
        connection: BackendConnection,
        name: String,
        value: String,
        description: String? = null,
    ): Result<String> = requestWithBody(
        connection,
        "/api/settings/secrets",
        "PUT",
        buildJsonObject {
            put("name", name)
            put("value", value)
            description?.takeIf { it.isNotBlank() }?.let { put("description", it) }
        },
    )

    suspend fun deleteSecret(
        connection: BackendConnection,
        name: String,
    ): Result<String> = delete(connection, "/api/settings/secrets/${name.encodePathSegment()}")

    suspend fun getSettings(connection: BackendConnection): Result<JsonObject> =
        get(connection, "/api/settings").map { body ->
            json.parseToJsonElement(body) as? JsonObject ?: error("设置响应格式错误")
        }

    suspend fun patchSettings(
        connection: BackendConnection,
        body: JsonObject,
    ): Result<String> = requestWithBody(connection, "/api/settings", "PATCH", body)

    suspend fun createMcpServer(
        connection: BackendConnection,
        settingsKey: String,
        server: JsonObject,
    ): Result<String> = post(
        connection,
        "/api/settings/mcp/${settingsKey.encodePathSegment()}",
        server,
    )

    suspend fun patchMcpServer(
        connection: BackendConnection,
        settingsKey: String,
        patch: JsonObject,
    ): Result<String> = requestWithBody(
        connection,
        "/api/settings/mcp/${settingsKey.encodePathSegment()}",
        "PATCH",
        patch,
    )

    suspend fun deleteMcpServer(
        connection: BackendConnection,
        settingsKey: String,
    ): Result<String> = delete(
        connection,
        "/api/settings/mcp/${settingsKey.encodePathSegment()}",
    )

    suspend fun getInstalledPlugins(connection: BackendConnection): Result<List<JsonObject>> =
        get(connection, "/api/plugins/installed").map { body -> parseNamedObjects(body, "plugins") }

    suspend fun getLocalPlugins(connection: BackendConnection): Result<List<JsonObject>> =
        post(
            connection,
            "/api/plugins",
            buildJsonObject {
                put("load_user", true)
                put("load_project", false)
            },
        ).map { body -> parseNamedObjects(body, "plugins") }

    suspend fun getMarketplacePlugins(connection: BackendConnection): Result<List<JsonObject>> =
        get(connection, "/api/plugins/marketplace").map { body -> parseNamedObjects(body, "plugins") }

    suspend fun installPlugin(
        connection: BackendConnection,
        source: String,
        ref: String? = null,
        repoPath: String? = null,
    ): Result<String> = post(
        connection,
        "/api/plugins/install",
        buildJsonObject {
            put("source", source)
            ref?.takeIf { it.isNotBlank() }?.let { put("ref", it) }
            repoPath?.takeIf { it.isNotBlank() }?.let { put("repo_path", it) }
        },
    )

    suspend fun uninstallPlugin(
        connection: BackendConnection,
        name: String,
    ): Result<String> = delete(connection, "/api/plugins/installed/${name.encodePathSegment()}")

    suspend fun setPluginEnabled(
        connection: BackendConnection,
        name: String,
        enabled: Boolean,
    ): Result<String> = requestWithBody(
        connection,
        "/api/plugins/installed/${name.encodePathSegment()}",
        "PATCH",
        buildJsonObject { put("enabled", enabled) },
    )

    private fun parseNamedObjects(body: String, arrayKey: String): List<JsonObject> {
        val element = json.parseToJsonElement(body)
        val array = when (element) {
            is JsonArray -> element
            is JsonObject -> element[arrayKey] as? JsonArray
                ?: element["items"] as? JsonArray
            else -> null
        } ?: return emptyList()
        return array.mapNotNull { item ->
            when (item) {
                is JsonObject -> item
                is JsonPrimitive -> item.contentOrNull?.takeIf { it.isNotBlank() }?.let { name ->
                    buildJsonObject { put("name", name) }
                }
                else -> null
            }
        }
    }

    private fun parseMetricsSnapshot(element: kotlinx.serialization.json.JsonElement?): MetricsSnapshot? {
        if (element !is JsonObject) return null
        val tokenUsage = element["accumulated_token_usage"] as? JsonObject
        val costStr = element["accumulated_cost"]?.jsonPrimitive?.contentOrNull
        val budgetStr = element["max_budget_per_task"]?.jsonPrimitive?.contentOrNull
        return MetricsSnapshot(
            accumulatedCost = costStr?.toDoubleOrNull(),
            maxBudgetPerTask = budgetStr?.toDoubleOrNull(),
            accumulatedTokenUsage = parseTokenUsage(tokenUsage),
        )
    }

    private fun parseTokenUsage(element: JsonObject?): TokenUsage? {
        if (element == null) return null
        return TokenUsage(
            promptTokens = element["prompt_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
            completionTokens = element["completion_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
            cacheReadTokens = element["cache_read_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
            cacheWriteTokens = element["cache_write_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
            contextWindow = element["context_window"]?.jsonPrimitive?.intOrNull ?: 0,
            perTurnToken = element["per_turn_token"]?.jsonPrimitive?.intOrNull ?: 0,
        )
    }

    private fun parseUsageToMetrics(element: kotlinx.serialization.json.JsonElement?): Map<String, RuntimeMetrics> {
        if (element !is JsonObject) return emptyMap()
        val result = mutableMapOf<String, RuntimeMetrics?>()
        for ((key, value) in element.entries) {
            val metrics = when (value) {
                is JsonObject -> parseRuntimeMetrics(value)
                else -> null
            }
            result[key] = metrics
        }
        return result.filterValues { it != null }.mapValues { it.value!! }
    }

    private fun parseRuntimeMetrics(element: JsonObject): RuntimeMetrics? {
        val costStr = element["accumulated_cost"]?.jsonPrimitive?.contentOrNull
        val budgetStr = element["max_budget_per_task"]?.jsonPrimitive?.contentOrNull
        return RuntimeMetrics(
            modelName = element["model_name"]?.jsonPrimitive?.contentOrNull ?: "unknown",
            accumulatedCost = costStr?.toDoubleOrNull() ?: 0.0,
            maxBudgetPerTask = budgetStr?.toDoubleOrNull(),
            accumulatedTokenUsage = parseTokenUsage(element["accumulated_token_usage"] as? JsonObject),
        )
    }

    private suspend fun get(
        connection: BackendConnection,
        path: String,
        extraHeaders: Map<String, String> = emptyMap(),
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatchingNonCancellation {
            val requestBuilder = Request.Builder()
                .url(buildUrl(connection.profile.baseUrl, path))
                .get()
            applyAuthentication(requestBuilder, connection)
            extraHeaders.forEach { (key, value) -> requestBuilder.header(key, value) }
            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    error("HTTP ${response.code}: ${body.take(MAX_ERROR_BODY_LENGTH)}")
                }
                body
            }
        }
    }

    private suspend fun post(
        connection: BackendConnection,
        path: String,
        body: kotlinx.serialization.json.JsonObject = buildJsonObject { },
    ): Result<String> = requestWithBody(connection, path, "POST", body)

    private suspend fun delete(
        connection: BackendConnection,
        path: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatchingNonCancellation {
            val requestBuilder = Request.Builder()
                .url(buildUrl(connection.profile.baseUrl, path))
                .delete()
            applyAuthentication(requestBuilder, connection)
            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    error("HTTP ${response.code}: ${responseBody.take(MAX_ERROR_BODY_LENGTH)}")
                }
                responseBody
            }
        }
    }

    private suspend fun requestWithBody(
        connection: BackendConnection,
        path: String,
        method: String,
        body: kotlinx.serialization.json.JsonObject,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatchingNonCancellation {
            val requestBuilder = Request.Builder()
                .url(buildUrl(connection.profile.baseUrl, path))
                .method(
                    method,
                    body.toString().toRequestBody(JSON_MEDIA_TYPE),
                )
            applyAuthentication(requestBuilder, connection)
            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    error("HTTP ${response.code}: ${responseBody.take(MAX_ERROR_BODY_LENGTH)}")
                }
                responseBody
            }
        }
    }

    private fun applyAuthentication(
        builder: Request.Builder,
        connection: BackendConnection,
    ) {
        val apiKey = connection.apiKey?.trim().orEmpty()
        if (apiKey.isEmpty()) return
        when (connection.profile.authMode) {
            AuthMode.SESSION_API_KEY -> builder.header("X-Session-API-Key", apiKey)
            AuthMode.BEARER -> builder.header("Authorization", "Bearer $apiKey")
            AuthMode.COOKIE -> Unit
        }
    }

    private fun buildUrl(baseUrl: String, path: String): String =
        baseUrl.trimEnd('/') + "/" + path.trimStart('/')

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val MAX_ERROR_BODY_LENGTH = 500
    }
}

fun formatNetworkError(error: Throwable?, fallback: String): String {
    if (error is CancellationException) return fallback
    val message = error?.message.orEmpty()
    return when {
        message.contains("HTTP 401", ignoreCase = true) ||
            message.contains("unauthorized", ignoreCase = true) ->
            "认证失败：Session API Key 无效或未配置，请到后端管理重新保存后连接"
        message.contains("HTTP 403", ignoreCase = true) ||
            message.contains("forbidden", ignoreCase = true) ->
            "没有权限访问此 Agent Server 资源"
        message.contains("cancelled", ignoreCase = true) ||
            message.contains("canceled", ignoreCase = true) ||
            message.contains("StandaloneCoroutine", ignoreCase = true) ->
            fallback
        else -> message.ifBlank { fallback }
    }
}

private inline fun <T> runCatchingNonCancellation(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }

private fun String.encodePathSegment(): String =
    java.net.URLEncoder.encode(this, Charsets.UTF_8.name()).replace("+", "%20")

