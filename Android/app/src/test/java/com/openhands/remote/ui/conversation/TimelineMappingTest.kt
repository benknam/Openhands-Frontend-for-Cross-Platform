package com.openhands.remote.ui.conversation

import com.openhands.remote.core.model.AgentEventEnvelope
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineMappingTest {
    @Test
    fun userMessage_mapsToUserMessage() {
        val item = AgentEventEnvelope(
            id = "user-1",
            type = "MessageEvent",
            payload = buildJsonObject {
                put("role", "user")
                put("content", "hello")
            },
        ).toTimelineItem()

        assertTrue(item is TimelineItem.UserMessage)
        assertEquals("hello", (item as TimelineItem.UserMessage).text)
    }

    @Test
    fun actionWithNestedArguments_mapsWithoutCrashing() {
        val item = AgentEventEnvelope(
            id = "action-1",
            type = "ActionEvent",
            payload = buildJsonObject {
                put("tool_name", "terminal")
                putJsonObject("args") { put("command", "pwd") }
            },
        ).toTimelineItem()

        assertTrue(item is TimelineItem.ToolCall)
        assertEquals("terminal", (item as TimelineItem.ToolCall).name)
    }

    @Test
    fun stateEvent_isHiddenFromTimeline() {
        val items = AgentEventEnvelope(
            id = "state-1",
            type = "ConversationStateUpdateEvent",
            payload = buildJsonObject { put("state", "finished") },
        ).toTimelineItems()

        assertTrue(items.isEmpty())
    }

    @Test
    fun unknownEvent_preservesRawPayload() {
        val item = AgentEventEnvelope(
            id = "future-1",
            type = "FutureEvent",
            payload = buildJsonObject { put("value", 42) },
        ).toTimelineItem()

        assertTrue(item is TimelineItem.Unknown)
        assertEquals("42", (item as TimelineItem.Unknown).raw["value"].toString())
    }

    @Test
    fun officialAssistantMessage_readsLlmMessageContentBlocks() {
        val longText = "用户聊天气泡现在会限制为对话框宽度的最大 75%:\n\n```\nmax-w-[75%]\n```\n\n## 验证结果\n- 聊天气泡测试通过"
        val items = AgentEventEnvelope(
            id = "assistant-1",
            type = "MessageEvent",
            payload = buildJsonObject {
                put("source", "agent")
                putJsonObject("llm_message") {
                    put("role", "assistant")
                    putJsonArray("content") {
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", longText)
                        })
                    }
                }
            },
        ).toTimelineItems()

        assertEquals(1, items.size)
        assertTrue(items.single() is TimelineItem.AssistantMessage)
        assertEquals(longText, (items.single() as TimelineItem.AssistantMessage).text)
    }

    @Test
    fun conversationStateUpdate_isHiddenFromTimeline() {
        val items = AgentEventEnvelope(
            id = "state-2",
            type = "ConversationStateUpdateEvent",
            payload = buildJsonObject { put("key", "execution_status") },
        ).toTimelineItems()

        assertTrue(items.isEmpty())
    }

    @Test
    fun canvasUiAction_mapsToFilePanel() {
        val item = AgentEventEnvelope(
            id = "canvas-1",
            type = "ActionEvent",
            payload = buildJsonObject {
                put("tool_name", "canvas_ui_control")
                putJsonObject("action") {
                    put("kind", "CanvasUIAction")
                    put("command", "navigate_to_file")
                    put("path", "android/app/src/Main.kt")
                }
            },
        ).toTimelineItem()

        assertTrue(item is TimelineItem.ToolCall)
        val call = item as TimelineItem.ToolCall
        assertEquals("canvas", call.category)
        assertEquals("FILES", call.panelTab)
        assertEquals("android/app/src/Main.kt", call.panelPath)
    }

    @Test
    fun canvasUiOpenTab_mapsToTerminalPanel() {
        val request = AgentEventEnvelope(
            id = "canvas-2",
            type = "ActionEvent",
            payload = buildJsonObject {
                put("tool_name", "canvas_ui")
                putJsonObject("action") {
                    put("command", "open_tab")
                    put("tab", "terminal")
                }
            },
        ).extractCanvasUiRequest()

        assertEquals("open_tab", request?.command)
        assertEquals("TERMINAL", request?.tab?.name)
    }
}
