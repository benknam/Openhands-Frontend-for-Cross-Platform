package com.openhands.remote.core

import com.openhands.remote.core.model.AgentEventEnvelope
import com.openhands.remote.core.model.AuthMode
import com.openhands.remote.core.model.AgentProfileResponse
import com.openhands.remote.core.model.BackendProfile
import com.openhands.remote.core.network.AgentServerHttpClient
import com.openhands.remote.core.network.InMemoryEventStore
import com.openhands.remote.core.network.formatNetworkError
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkModelsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun backendProfile_roundTripsThroughJson() {
        val profile = BackendProfile(
            id = "https://example.test",
            name = "Test backend",
            baseUrl = "https://example.test",
            authMode = AuthMode.SESSION_API_KEY,
        )

        val restored = json.decodeFromString<BackendProfile>(json.encodeToString(profile))

        assertEquals(profile, restored)
    }

    @Test
    fun eventStore_deduplicatesStableEventIds() {
        val store = InMemoryEventStore()
        val event = AgentEventEnvelope(
            id = "event-1",
            timestamp = "2026-09-07T01:00:00Z",
            type = "MessageEvent",
            payload = buildJsonObject { put("content", "hello") },
        )

        assertTrue(store.append("backend", "conversation", event))
        assertFalse(store.append("backend", "conversation", event))
        assertEquals(listOf(event), store.events("backend", "conversation"))
        assertEquals(event.timestamp, store.lastTimestamp("backend", "conversation"))
    }

    @Test
    fun bashOutputSchema_containsCommandAndIncrementalOutputFields() {
        val command = buildJsonObject {
            put("kind", "BashCommand")
            put("id", "command-1")
            put("command", "pwd")
        }
        val output = buildJsonObject {
            put("kind", "BashOutput")
            put("command_id", "command-1")
            put("stdout", "/workspace")
            put("stderr", "")
            put("exit_code", 0)
        }

        assertEquals("command-1", command["id"]?.jsonPrimitive?.content)
        assertEquals("command-1", output["command_id"]?.jsonPrimitive?.content)
        assertEquals(0, output["exit_code"]?.jsonPrimitive?.intOrNull)
    }

    @Test
    fun agentProfileResponse_supportsStableUuidAndActivePointer() {
        val response = json.decodeFromString<AgentProfileResponse>("""
            {"profiles":[{"id":"uuid-1","name":"default"}],"active_agent_profile_id":"uuid-1"}
        """.trimIndent())

        assertEquals("uuid-1", response.profiles.single().id)
        assertEquals("uuid-1", response.activeProfileId)
    }

    @Test
    fun llmProfileResponse_readsAllSavedProfilesAndActiveName() {
        val response = AgentServerHttpClient().parseLlmProfiles("""
            {"profiles":[
              {"name":"fast","model":"gpt-4o-mini"},
              {"name":"quality","model":"gpt-4o"}
            ],"active_profile":"quality"}
        """.trimIndent())

        assertEquals(listOf("fast", "quality"), response.profiles.map { it.name })
        assertEquals(listOf("gpt-4o-mini", "gpt-4o"), response.profiles.map { it.model })
        assertEquals("quality", response.activeProfile)
    }

    @Test
    fun llmProfileResponse_supportsObjectMapAndStringEntries() {
        val response = AgentServerHttpClient().parseLlmProfiles("""
            {"llm_profiles":{
              "fast":{"model":"gpt-4o-mini"},
              "quality":{"name":"quality","model":"gpt-4o"},
              "empty":null
            },"active_profile_name":"fast"}
        """.trimIndent())

        assertEquals(listOf("fast", "quality"), response.profiles.map { it.name })
        assertEquals(listOf("gpt-4o-mini", "gpt-4o"), response.profiles.map { it.model })
        assertEquals("fast", response.activeProfile)
    }

    @Test
    fun llmProfileResponse_mergesProfilesArrayWithNamedMap() {
        val response = AgentServerHttpClient().parseLlmProfiles("""
            {"profiles":[{"name":"CUDA","model":"openai/cuda"}],
             "llm_profiles":{
               "fast":{"model":"gpt-4o-mini"},
               "quality":{"name":"quality","model":"gpt-4o"}
             },
             "active_profile":"CUDA"}
        """.trimIndent())

        assertEquals(listOf("CUDA", "fast", "quality"), response.profiles.map { it.name })
        assertEquals("CUDA", response.activeProfile)
    }

    @Test
    fun llmProfileResponse_readsLiveAgentServerPayload() {
        val response = AgentServerHttpClient().parseLlmProfiles(
            """
            {"profiles":[
              {"name":"CUDA","model":"openai/cuda","base_url":"http://192.168.2.5:8080/v1","api_key_set":true},
              {"name":"GPT-5.6-Terra","model":"openai/gpt-5.6-terra","base_url":"http://mysubapi.com/v1","api_key_set":true},
              {"name":"GPT-5.6_SOL","model":"openai/gpt-5.6-sol","base_url":"http://mysubapi.com/v1","api_key_set":true}
            ]}
            """.trimIndent(),
        )

        assertEquals(listOf("CUDA", "GPT-5.6-Terra", "GPT-5.6_SOL"), response.profiles.map { it.name })
        assertEquals(3, response.profiles.size)
    }

    @Test
    fun llmProfileResponse_readsNestedLlmModelAndDataArray() {
        val response = AgentServerHttpClient().parseLlmProfiles("""
            {"data":[
              {"name":"fast","llm":{"model":"gpt-4o-mini"}},
              {"name":"quality","llm":{"model":"gpt-4o"}}
            ],"active_profile":"quality"}
        """.trimIndent())

        assertEquals(listOf("fast", "quality"), response.profiles.map { it.name })
        assertEquals(listOf("gpt-4o-mini", "gpt-4o"), response.profiles.map { it.model })
        assertEquals("quality", response.activeProfile)
    }

    @Test
    fun networkError_usesActionableAuthenticationMessageForUnauthorized() {
        val message = formatNetworkError(
            IllegalStateException("HTTP 401: {\"detail\":\"Unauthorized\"}"),
            "加载失败",
        )
        assertTrue(message.contains("Session API Key"))
        assertFalse(message.contains("Unauthorized"))
    }

    @Test
    fun networkError_keepsNonAuthenticationMessage() {
        assertEquals("连接超时", formatNetworkError(IllegalStateException("连接超时"), "加载失败"))
    }

    @Test
    fun networkError_doesNotSurfaceCancellationAsFailure() {
        val cancelled = kotlinx.coroutines.CancellationException("StandaloneCoroutine was cancelled")
        assertEquals("加载失败", formatNetworkError(cancelled, "加载失败"))
    }

    @Test
    fun conversationWorkingDir_readsNestedWorkspacePath() {
        val path = AgentServerHttpClient().extractWorkingDir(
            """{"id":"c1","workspace":{"working_dir":"/workspace/Openhands_FA"}}""",
        )
        assertEquals("/workspace/Openhands_FA", path)
    }

    @Test
    fun conversationWorkingDir_readsSelectedWorkspace() {
        val path = AgentServerHttpClient().extractWorkingDir(
            """{"id":"c1","selected_workspace":"/home/avenue/project"}""",
        )
        assertEquals("/home/avenue/project", path)
    }
}
