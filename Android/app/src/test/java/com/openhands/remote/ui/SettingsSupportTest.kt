package com.openhands.remote.ui

import com.openhands.remote.core.model.AgentProfileSummary
import com.openhands.remote.core.model.LlmProfileSummary
import com.openhands.remote.ui.components.SettingsCatalog
import com.openhands.remote.ui.components.SettingsJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSupportTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun settingsNav_matchesOriginalWebLabels() {
        assertEquals(
            listOf("代理", "LLM", "压缩器", "代理上下文", "验证", "应用程序", "使用优化", "机密"),
            SettingsCatalog.navItems.map { it.key },
        )
    }

    @Test
    fun parseSchema_filtersCondenserFieldsAndTranslatesLabels() {
        val schema = json.parseToJsonElement(
            """
            {"sections":[
              {"key":"condenser","label":"Condenser","fields":[
                {"key":"condenser.enabled","label":"Enable","value_type":"boolean","prominence":"critical","depends_on":[]},
                {"key":"condenser.condenser_kind","label":"Kind","value_type":"string","choices":[{"value":"llm_summarizing","label":"llm_summarizing"},{"value":"no_op","label":"no_op"}]}
              ]},
              {"key":"llm","label":"LLM","fields":[{"key":"llm.model","label":"Model","value_type":"string"}]}
            ]}
            """.trimIndent(),
        ) as JsonObject

        val sections = SettingsJson.parseSchema(schema, setOf("condenser"))
        assertEquals(1, sections.size)
        assertEquals(listOf("condenser.enabled", "condenser.condenser_kind"), sections.single().fields.map { it.key })
        assertEquals("启用内存压缩", sections.single().fields.first().label)
        assertEquals("LLM 摘要", sections.single().fields[1].choices.first().second)
    }

    @Test
    fun deepMerge_keepsUntouchedLlmFieldsWhenSavingEdits() {
        val base = json.parseToJsonElement(
            """{"model":"openai/cuda","base_url":"http://192.168.2.5:8080/v1","temperature":0.0,"native_tool_calling":true}""",
        ) as JsonObject
        val overlay = buildJsonObject {
            put("model", "openai/cuda")
            put("temperature", 0.2)
        }
        val merged = SettingsJson.deepMerge(base, overlay)
        assertEquals("openai/cuda", SettingsJson.stringValue(merged, "model"))
        assertEquals("http://192.168.2.5:8080/v1", SettingsJson.stringValue(merged, "base_url"))
        assertEquals("0.2", SettingsJson.stringValue(merged, "temperature"))
        assertTrue(SettingsJson.booleanValue(merged, "native_tool_calling", false))
    }

    @Test
    fun putPath_writesNestedAgentSettingsDiff() {
        val nested = SettingsJson.putPath("condenser.enabled", JsonPrimitive(true))
        assertTrue(SettingsJson.booleanValue(nested, "condenser.enabled"))
    }

    @Test
    fun parseLlmDetail_readsConfigAndApiKeyFlag() {
        val detail = SettingsJson.parseLlmDetail(
            json.parseToJsonElement(
                """{"name":"CUDA","api_key_set":true,"config":{"model":"openai/cuda","base_url":"http://192.168.2.5:8080/v1"}}""",
            ) as JsonObject,
        )
        assertEquals("CUDA", detail.name)
        assertTrue(detail.apiKeySet)
        assertEquals("openai/cuda", SettingsJson.stringValue(detail.config, "model"))
    }

    @Test
    fun uniqueCopyName_avoidsExistingProfiles() {
        assertEquals("CUDA-copy", SettingsJson.uniqueCopyName("CUDA", setOf("CUDA")))
        assertEquals("CUDA-copy-1", SettingsJson.uniqueCopyName("CUDA", setOf("CUDA", "CUDA-copy")))
    }

    @Test
    fun profileName_validationMatchesWebRules() {
        assertTrue(SettingsJson.isProfileNameValid("GPT-5.6-Terra"))
        assertFalse(SettingsJson.isProfileNameValid("  bad name  "))
        assertEquals("gpt-5.6-sol", SettingsJson.deriveProfileName("openai/gpt-5.6-sol"))
    }

    @Test
    fun displayHelpers_joinModelAndAgentKind() {
        assertEquals(
            "CUDA  ·  openai/cuda",
            SettingsJson.llmDisplayName(LlmProfileSummary(name = "CUDA", model = "openai/cuda")),
        )
        assertEquals(
            "GPT-5.6-Terra",
            SettingsJson.agentSecondary(AgentProfileSummary(id = "1", name = "Boxin", llmProfileRef = "GPT-5.6-Terra")),
        )
    }
}
