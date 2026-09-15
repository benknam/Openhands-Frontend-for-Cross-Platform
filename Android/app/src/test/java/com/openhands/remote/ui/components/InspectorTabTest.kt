package com.openhands.remote.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class InspectorTabTest {
    @Test
    fun inspectorTabs_keepOriginalWorkspaceOrder() {
        assertEquals(
            listOf("文件", "变更", "规划", "终端", "浏览", "用量", "概览"),
            InspectorTab.entries.map { it.label },
        )
    }

    @Test
    fun inspectorTabs_useMatchingPanelIcons() {
        assertEquals(
            listOf("folder", "changes", "plan", "terminal", "browse", "usage", "info"),
            InspectorTab.entries.map { it.icon },
        )
    }

    @Test
    fun terminalEmptyState_matchesOfficialCanvasCopy() {
        assertEquals(
            "尚无终端输出。智能体运行的命令将显示在此处。",
            TERMINAL_EMPTY_OUTPUT_MESSAGE,
        )
    }
}
