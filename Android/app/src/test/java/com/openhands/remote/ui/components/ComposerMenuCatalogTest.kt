package com.openhands.remote.ui.components

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposerMenuCatalogTest {
    @Test
    fun mainMenu_matchesNewConversationPlusMenu() {
        assertEquals(
            listOf(
                ComposerMenuCatalog.SWITCH_AGENT_PROFILE,
                ComposerMenuCatalog.MACROS,
                "---",
                ComposerMenuCatalog.SHOW_SKILLS,
                "---",
                ComposerMenuCatalog.ADD_FILES,
            ),
            ComposerMenuCatalog.mainItems(showAgentProfileSwitch = true),
        )
    }

    @Test
    fun pluginFromJson_readsMarketplaceCardFields() {
        val plugin = ComposerMenuCatalog.pluginFromJson(
            buildJsonObject {
                put("name", "browser")
                put("source", "github:OpenHands/browser")
                put("description", "Browse the web")
                put("ref", "main")
                put("repo_path", "skills/browser")
            },
        )

        requireNotNull(plugin)
        assertEquals("browser", plugin.name)
        assertEquals("github:OpenHands/browser", plugin.source)
        assertEquals("Browse the web", plugin.description)
        assertTrue(ComposerMenuCatalog.pluginMatchesSearch(plugin, "web"))
        assertFalse(ComposerMenuCatalog.pluginMatchesSearch(plugin, "terminal"))
    }

    @Test
    fun nestedMenuLabels_keepParentMenuOpen() {
        assertTrue(hasNestedMenu(ComposerMenuCatalog.SWITCH_AGENT_PROFILE))
        assertTrue(hasNestedMenu(ComposerMenuCatalog.MACROS))
        assertTrue(hasNestedMenu("‹ 返回"))
        assertFalse(hasNestedMenu(ComposerMenuCatalog.SHOW_SKILLS))
    }
}
