package com.openhands.remote.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AgentProfileSummary(
    val id: String? = null,
    val name: String,
    @SerialName("agent_kind") val agentKind: String? = null,
    @SerialName("llm_profile_ref") val llmProfileRef: String? = null,
)

@Serializable
data class AgentProfileResponse(
    val profiles: List<AgentProfileSummary> = emptyList(),
    @SerialName("active_agent_profile_id") val activeProfileId: String? = null,
)

/** A saved LLM configuration returned by the Agent Server /api/profiles route. */
@Serializable
data class LlmProfileSummary(
    val name: String,
    val model: String? = null,
    @SerialName("base_url") val baseUrl: String? = null,
    @SerialName("api_key_set") val apiKeySet: Boolean = false,
)

@Serializable
data class LlmProfileResponse(
    val profiles: List<LlmProfileSummary> = emptyList(),
    @SerialName("active_profile") val activeProfile: String? = null,
)

@Serializable
data class PluginSpec(
    val source: String,
    val ref: String? = null,
    @SerialName("repo_path") val repoPath: String? = null,
    val name: String? = null,
    val description: String? = null,
) {
    fun identityKey(): String = listOf(source, ref.orEmpty(), repoPath.orEmpty()).joinToString("\u0000")
}

data class BashCommandResult(
    val command: String,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

@Serializable
data class BackendCapabilities(
    val serverVersion: String? = null,
    val profiles: Boolean = false,
    val fileDownload: Boolean = false,
    val bashHttp: Boolean = false,
    val bashWebSocket: Boolean = false,
    val gitWorkspace: Boolean = false,
    val workingDir: String? = null,
)
