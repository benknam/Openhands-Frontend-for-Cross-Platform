package com.openhands.remote.ui.components

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendSettingsParserTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parseMcpServers_readsNestedAgentSettings() {
        val settings = json.parseToJsonElement(
            """
            {
              "agent_settings": {
                "mcp_config": {
                  "filesystem": {
                    "name": "filesystem",
                    "command": "npx"
                  }
                }
              }
            }
            """.trimIndent(),
        ).jsonObject

        val servers = BackendSettingsParser.parseMcpServers(settings)
        assertEquals(1, servers.size)
        assertEquals("filesystem", servers[0].key)
        assertEquals("npx", servers[0].detail)
    }

    @Test
    fun appPreferences_readsLanguageAndDisabledSkills() {
        val settings = json.parseToJsonElement(
            """
            {
              "misc_settings": {
                "app_preferences": {
                  "language": "zh-CN",
                  "enable_sound_notifications": true,
                  "disabled_skills": ["browser"]
                }
              }
            }
            """.trimIndent(),
        ).jsonObject
        val prefs = BackendSettingsParser.appPreferences(settings)
        assertEquals("zh-CN", BackendSettingsParser.stringSetting(prefs, settings, "language"))
        assertTrue(BackendSettingsParser.booleanSetting(prefs, settings, "enable_sound_notifications"))
        assertEquals(setOf("browser"), BackendSettingsParser.stringSetSetting(prefs, settings, "disabled_skills"))
        assertFalse(BackendSettingsParser.booleanSetting(prefs, settings, "missing"))
    }

    @Test
    fun unassignedWorkspaceConversations_keepBlankWorkingDirItems() {
        val conversations = listOf(
            "with-workspace" to "/workspace/app",
            "none" to null,
            "blank" to "",
        )
        assertEquals(listOf("none", "blank"), unassignedWorkspaceConversations(conversations))
    }
}
