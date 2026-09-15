package com.openhands.remote.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ============================================================
// Token Usage & Metrics (upstream 1.18.0: TokenUsage, MetricsSnapshot)
// ============================================================

@Serializable
data class TokenUsage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("cache_read_tokens") val cacheReadTokens: Int = 0,
    @SerialName("cache_write_tokens") val cacheWriteTokens: Int = 0,
    @SerialName("context_window") val contextWindow: Int = 0,
    @SerialName("per_turn_token") val perTurnToken: Int = 0,
)

@Serializable
data class MetricsSnapshot(
    @SerialName("accumulated_cost") val accumulatedCost: Double? = null,
    @SerialName("max_budget_per_task") val maxBudgetPerTask: Double? = null,
    @SerialName("accumulated_token_usage") val accumulatedTokenUsage: TokenUsage? = null,
)

// ============================================================
// Message Content Types (upstream 1.18.0: MessageTextContent, MessageImageContent, SendMessageRequest)
// ============================================================

@Serializable
sealed interface MessageContent {
    @Serializable
    @SerialName("text")
    data class Text(
        val text: String,
    ) : MessageContent

    @Serializable
    @SerialName("image")
    data class Image(
        @SerialName("image_urls") val imageUrls: List<String> = emptyList(),
    ) : MessageContent
}

@Serializable
enum class MessageRole {
    USER, SYSTEM, ASSISTANT, TOOL
}

@Serializable
data class SendMessageRequest(
    val role: MessageRole,
    val content: List<MessageContent>,
)

// ============================================================
// Sandbox Status (upstream 1.18.0: SandboxStatus)
// ============================================================

@Serializable
enum class SandboxStatus {
    @SerialName("PAUSED") PAUSED,
    @SerialName("RUNNING") RUNNING,
    @SerialName("STARTING") STARTING,
    @SerialName("MISSING") MISSING,
    @SerialName("ERROR") ERROR,
}

// ============================================================
// Conversation Search Response (upstream 1.18.0: AppConversationPage)
// ============================================================

@Serializable
data class ConversationSearchResponse(
    val items: List<ConversationSummary> = emptyList(),
    @SerialName("next_page_id") val nextPageId: String? = null,
)

// ============================================================
// Conversation Summary (upstream 1.18.0: AppConversation)
// Extended with all new fields from upstream 1.18.0
// ============================================================

@Serializable
data class ConversationSummary(
    val id: String,
    @SerialName("created_by_user_id") val createdByUserId: String? = null,
    @SerialName("selected_repository") val selectedRepository: String? = null,
    @SerialName("selected_branch") val selectedBranch: String? = null,
    @SerialName("git_provider") val gitProvider: String? = null,
    val title: String? = null,
    @SerialName("trigger") val trigger: String? = null,
    @SerialName("pr_number") val prNumber: List<Int> = emptyList(),
    /**
     * High-level kind of the conversation's agent — "openhands" for an LLM-driven Agent,
     * "acp" for an ACPAgent that delegates to an external ACP CLI subprocess.
     */
    @SerialName("agent_kind") val agentKind: String? = null,
    /**
     * For ACP conversations, the registry key of the ACP CLI server (e.g. "claude-code", "codex").
     */
    @SerialName("acp_server") val acpServer: String? = null,
    /**
     * Server-side key-value tags from the agent-server's ConversationInfo.tags.
     */
    val tags: Map<String, String>? = null,
    @SerialName("llm_model") val llmModel: String? = null,
    val metrics: MetricsSnapshot? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("execution_status") val executionStatus: String? = null,
    /**
     * Cloud-only sandbox lifecycle status. Absent/null for local agent-server conversations.
     */
    @SerialName("sandbox_status") val sandboxStatus: SandboxStatus? = null,
    @SerialName("conversation_url") val conversationUrl: String? = null,
    @SerialName("session_api_key") val sessionApiKey: String? = null,
    @SerialName("sandbox_id") val sandboxId: String? = null,
    val workspace: kotlinx.serialization.json.JsonElement? = null,
    /**
     * The local workspace the user explicitly attached when creating this conversation.
     */
    @SerialName("selected_workspace") val selectedWorkspace: String? = null,
    /**
     * The LLM profile this conversation was created with / last switched to.
     */
    @SerialName("active_profile") val activeProfile: String? = null,
    val public: Boolean? = null,
    @SerialName("sub_conversation_ids") val subConversationIds: List<String> = emptyList(),
    // Legacy fields for backward compatibility
    @SerialName("last_event_timestamp") val lastEventTimestamp: String? = null,
    @SerialName("working_dir") val workingDir: String? = null,
    @SerialName("branch") val branch: String? = null,
)

// ============================================================
// Runtime Conversation Info (upstream 1.18.0: RuntimeConversationInfo, RuntimeMetrics)
// ============================================================

@Serializable
data class RuntimeConversationStats(
    @SerialName("usage_to_metrics") val usageToMetrics: Map<String, RuntimeMetrics> = emptyMap(),
)

@Serializable
data class RuntimeMetrics(
    @SerialName("model_name") val modelName: String = "unknown",
    @SerialName("accumulated_cost") val accumulatedCost: Double = 0.0,
    @SerialName("max_budget_per_task") val maxBudgetPerTask: Double? = null,
    @SerialName("accumulated_token_usage") val accumulatedTokenUsage: TokenUsage? = null,
)

@Serializable
data class RuntimeConversationInfo(
    val id: String,
    val title: String? = null,
    val metrics: MetricsSnapshot? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val status: String = "UNKNOWN",
    val stats: RuntimeConversationStats = RuntimeConversationStats(),
)

// ============================================================
// Agent Event Envelope (existing - unchanged)
// ============================================================

@Serializable
data class AgentEventEnvelope(
    val id: String? = null,
    val timestamp: String? = null,
    val type: String? = null,
    val payload: kotlinx.serialization.json.JsonObject = kotlinx.serialization.json.buildJsonObject { },
)

data class ConversationEventHistoryPage(
    val events: List<AgentEventEnvelope> = emptyList(),
    val nextPageId: String? = null,
)

// ============================================================
// Connection State (existing - unchanged)
// ============================================================

enum class ConnectionState {
    IDLE,
    CONNECTING,
    AUTHENTICATING,
    SYNCING,
    CONNECTED,
    DISCONNECTED,
    WAITING_NETWORK,
    AUTH_FAILED,
    NOT_FOUND,
    FAILED,
}

data class ConversationSyncState(
    val connectionState: ConnectionState = ConnectionState.IDLE,
    val lastEventTimestamp: String? = null,
    val receivedEventCount: Long = 0,
    val errorMessage: String? = null,
)
