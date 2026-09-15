package com.openhands.remote.ui.conversation

import com.openhands.remote.ui.components.InspectorTab
import org.junit.Assert.assertEquals
import org.junit.Test

class CanvasUiTest {
    @Test
    fun navigateToFile_opensFilesTab() {
        assertEquals(InspectorTab.FILES, inspectorTabFor("navigate_to_file", null))
        assertEquals(InspectorTab.FILES, inspectorTabFor("show_preview", "browser"))
    }

    @Test
    fun openTab_mapsKnownRightPanelTabs() {
        assertEquals(InspectorTab.TERMINAL, inspectorTabFor("open_tab", "terminal"))
        assertEquals(InspectorTab.BROWSER, inspectorTabFor("open_tab", "browser"))
        assertEquals(InspectorTab.PLANNER, inspectorTabFor("open_tab", "planner"))
        assertEquals(InspectorTab.PLANNER, inspectorTabFor("open_tab", "tasklist"))
        assertEquals(InspectorTab.FILES, inspectorTabFor("open_tab", "vscode"))
    }

    @Test
    fun toolCategory_mapsToMatchingPanel() {
        assertEquals(InspectorTab.TERMINAL, inspectorTabForToolCategory("terminal"))
        assertEquals(InspectorTab.FILES, inspectorTabForToolCategory("code"))
        assertEquals(InspectorTab.BROWSER, inspectorTabForToolCategory("browser"))
        assertEquals(null, inspectorTabForToolCategory("generic"))
    }
}
