package com.openhands.remote.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class MenuGlyphTest {
    @Test
    fun gitMenuItems_useOfficialCanvasIcons() {
        assertEquals("git_commit", menuGlyph("提交记录"))
        assertEquals("arrow_down", menuGlyph("拉取"))
        assertEquals("push", menuGlyph("推送"))
        assertEquals("pr", menuGlyph("创建 PR"))
        assertEquals("code_branch", menuGlyph("创建新分支"))
    }
}
