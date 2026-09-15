package com.openhands.remote.ui.conversation

import com.openhands.remote.core.model.AgentEventEnvelope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

sealed interface TimelineItem {
    val id: String
    val timestamp: String?

    data class UserMessage(
        override val id: String,
        override val timestamp: String?,
        val text: String,
        val raw: JsonObject,
    ) : TimelineItem

    data class AssistantMessage(
        override val id: String,
        override val timestamp: String?,
        val text: String,
        val raw: JsonObject,
    ) : TimelineItem

    data class ToolCall(
        override val id: String,
        override val timestamp: String?,
        val name: String,
        val summary: String,
        val category: String = "generic",
        val raw: JsonObject,
        val panelTab: String? = null,
        val panelPath: String? = null,
    ) : TimelineItem

    data class ToolResult(
        override val id: String,
        override val timestamp: String?,
        val summary: String,
        val category: String = "generic",
        val raw: JsonObject,
        val panelTab: String? = null,
        val panelPath: String? = null,
    ) : TimelineItem

    data class Status(
        override val id: String,
        override val timestamp: String?,
        val label: String,
        val raw: JsonObject,
    ) : TimelineItem

    // Upstream 1.18.0: New event types for skill-ready events
    data class SkillReady(
        override val id: String,
        override val timestamp: String?,
        val skills: List<String>,
        val raw: JsonObject,
    ) : TimelineItem

    // Upstream 1.18.0: Hook execution events
    data class HookExecution(
        override val id: String,
        override val timestamp: String?,
        val hookName: String,
        val status: String,
        val raw: JsonObject,
    ) : TimelineItem

    // Upstream 1.18.0: Task tracking events (planning/sub-tasks)
    data class TaskTracking(
        override val id: String,
        override val timestamp: String?,
        val taskTitle: String,
        val status: String,
        val subTasks: List<TaskItem>,
        val raw: JsonObject,
    ) : TimelineItem {
        data class TaskItem(
            val title: String,
            val status: String,
            val completed: Boolean,
        )
    }

    // Upstream 1.18.0: Critic result events (AI feedback/critique)
    data class CriticResult(
        override val id: String,
        override val timestamp: String?,
        val verdict: String,
        val feedback: String,
        val raw: JsonObject,
    ) : TimelineItem

    // Upstream 1.18.0: Thought events (collapsible thinking)
    data class Thought(
        override val id: String,
        override val timestamp: String?,
        val content: String,
        val raw: JsonObject,
    ) : TimelineItem

    data class Unknown(
        override val id: String,
        override val timestamp: String?,
        val type: String,
        val raw: JsonObject,
    ) : TimelineItem
}

fun AgentEventEnvelope.toTimelineItem(): TimelineItem =
    toTimelineItems().singleOrNull()
        ?: TimelineItem.Unknown(id ?: EventKey.from(this), timestamp, type.orEmpty().ifBlank { "Unknown event" }, payload)

fun AgentEventEnvelope.toTimelineItems(): List<TimelineItem> {
    val eventType = type.orEmpty()
    val normalizedType = eventType.lowercase()
    val eventId = id ?: EventKey.from(this)
    val llmMessage = payload["llm_message"] as? JsonObject
    val messageText = payload.messageText()
    val role = payload.messageRole()

    if (normalizedType.contains("conversationstateupdate") ||
        normalizedType.contains("systemprompt") ||
        normalizedType.contains("condensation") ||
        normalizedType.contains("streamingdelta")
    ) {
        return emptyList()
    }

    if (normalizedType.contains("skill_ready") ||
        (normalizedType.contains("observation") && payload.nestedKind("observation")?.contains("skills_ready") == true)
    ) {
        val skills = payload.extractSkillsFromPayload()
        return listOf(TimelineItem.SkillReady(eventId, timestamp, skills.ifEmpty { listOf("技能就绪") }, payload))
    }

    if (normalizedType.contains("hook_execution") || payload.stringValue("hook_name") != null) {
        return listOf(
            TimelineItem.HookExecution(
                eventId,
                timestamp,
                hookName = payload.stringValue("hook_name") ?: payload.stringValue("action") ?: eventType,
                status = payload.stringValue("status") ?: "执行中",
                raw = payload,
            ),
        )
    }

    if (normalizedType.contains("task_tracking") ||
        (normalizedType.contains("observation") && payload.nestedKind("observation")?.contains("task") == true)
    ) {
        return listOf(
            TimelineItem.TaskTracking(
                eventId,
                timestamp,
                payload.stringValue("title") ?: payload.stringValue("task_title") ?: "任务",
                payload.stringValue("status") ?: "进行中",
                payload.extractTaskItems(),
                payload,
            ),
        )
    }

    if (normalizedType.contains("critic_result") || payload["critic_result"] is JsonObject) {
        val critic = payload["critic_result"] as? JsonObject
        return listOf(
            TimelineItem.CriticResult(
                eventId,
                timestamp,
                verdict = critic?.stringValue("verdict")
                    ?: payload.stringValue("verdict")
                    ?: payload.stringValue("result")
                    ?: "待评估",
                feedback = critic?.stringValue("feedback") ?: payload.stringValue("feedback") ?: messageText,
                raw = payload,
            ),
        )
    }

    val isOfficialMessage = llmMessage != null ||
        normalizedType == "messageevent" ||
        (normalizedType.contains("message") && !normalizedType.contains("tool"))
    if (isOfficialMessage) {
        val items = mutableListOf<TimelineItem>()
        val (reasoning, visible) = splitInlineThink(messageText)
        if (reasoning.isNotBlank()) {
            items += TimelineItem.Thought("$eventId-think", timestamp, reasoning, payload)
        }
        val body = visible.ifBlank { messageText }
        items += if (role == "user") {
            TimelineItem.UserMessage(eventId, timestamp, body, payload)
        } else {
            TimelineItem.AssistantMessage(eventId, timestamp, body, payload)
        }
        return items
    }

    if (normalizedType.contains("thought") && !normalizedType.contains("action")) {
        return listOf(TimelineItem.Thought(eventId, timestamp, messageText, payload))
    }

    if (normalizedType.contains("action") || normalizedType.contains("tool_call") ||
        payload["action"] is JsonObject
    ) {
        val items = mutableListOf<TimelineItem>()
        val thought = payload.thoughtText()
        if (thought.isNotBlank()) {
            items += TimelineItem.Thought("$eventId-thought", timestamp, thought, payload)
        }
        val canvas = payload.extractCanvasUiRequest()
        val category = canvas?.let { "canvas" } ?: payload.toolCategory(eventType)
        items += TimelineItem.ToolCall(
            id = eventId,
            timestamp = timestamp,
            name = payload.stringValue("tool_name")
                ?: payload.nestedKind("action")
                ?: eventType,
            summary = canvas?.label ?: payload.actionSummary().ifBlank { messageText },
            category = category,
            raw = payload,
            panelTab = canvas?.tab?.name ?: inspectorTabForToolCategory(category)?.name,
            panelPath = canvas?.path ?: payload.panelPath(),
        )
        return items
    }

    if (normalizedType.contains("observation") || normalizedType.contains("tool_result") ||
        payload["observation"] is JsonObject
    ) {
        val canvas = payload.extractCanvasUiRequest()
        val category = canvas?.let { "canvas" } ?: payload.toolCategory(eventType)
        return listOf(
            TimelineItem.ToolResult(
                eventId,
                timestamp,
                canvas?.label ?: payload.observationSummary().ifBlank { messageText },
                category,
                payload,
                panelTab = canvas?.tab?.name ?: inspectorTabForToolCategory(category)?.name,
                panelPath = canvas?.path ?: payload.panelPath(),
            ),
        )
    }

    if (normalizedType.contains("error") || normalizedType.contains("finish")) {
        return listOf(TimelineItem.Status(eventId, timestamp, eventType.ifBlank { messageText }, payload))
    }

    return listOf(
        TimelineItem.Unknown(eventId, timestamp, eventType.ifBlank { "Unknown event" }, payload),
    )
}

private fun JsonObject.extractSkillsFromPayload(): List<String> =
    (this["skills"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { it as? JsonPrimitive }?.map { it.content }
        ?: emptyList()

private fun JsonObject.toolCategory(eventType: String): String {
    val kind = listOfNotNull(
        eventType,
        stringValue("tool_name"),
        stringValue("action"),
        (this["action"] as? JsonObject)?.stringValue("kind"),
        (this["observation"] as? JsonObject)?.stringValue("kind"),
    ).joinToString(" ").lowercase()
    return when {
        kind.contains("canvas_ui") || kind.contains("canvasui") -> "canvas"
        kind.contains("bash") || kind.contains("terminal") || kind.contains("command") -> "terminal"
        kind.contains("fileeditor") || kind.contains("strreplace") || kind.contains("file_editor") -> "code"
        kind.contains("browser") -> "browser"
        kind.contains("mcp") || kind.contains("tool") -> "tool"
        else -> "generic"
    }
}

private fun JsonObject.panelPath(): String? {
    val action = this["action"] as? JsonObject
    val observation = this["observation"] as? JsonObject
    val args = this["args"] as? JsonObject
    return listOfNotNull(
        action?.stringValue("path"),
        action?.stringValue("file_path"),
        args?.stringValue("path"),
        observation?.stringValue("path"),
        stringValue("path"),
        stringValue("file_path"),
    ).firstOrNull { it.isNotBlank() }
}

private fun JsonObject.extractTaskItems(): List<TimelineItem.TaskTracking.TaskItem> {
    val tasksArray = this["tasks"] as? kotlinx.serialization.json.JsonArray ?: return emptyList()
    return tasksArray.mapNotNull { task ->
        (task as? JsonObject)?.let { obj ->
            TimelineItem.TaskTracking.TaskItem(
                title = obj.stringValue("title") ?: "任务",
                status = obj.stringValue("status") ?: "未知",
                completed = stringValueOrNull(obj, "completed") == "true" || stringValueOrNull(obj, "done") == "true",
            )
        }
    }
}

private fun stringValueOrNull(obj: JsonObject, key: String): String? =
    (obj[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.messageRole(): String {
    val llm = this["llm_message"] as? JsonObject
    return (llm?.stringValue("role") ?: stringValue("role") ?: stringValue("source"))
        .orEmpty()
        .lowercase()
}

private fun JsonObject.messageText(): String {
    val llm = this["llm_message"] as? JsonObject
    extractTextBlocks(llm?.get("content")).takeIf { it.isNotBlank() }?.let { return it }
    extractTextBlocks(this["content"]).takeIf { it.isNotBlank() }?.let { return it }
    stringValue("text")?.let { return it }
    stringValue("message")?.let { return it }
    return ""
}

private fun JsonObject.thoughtText(): String {
    extractTextBlocks(this["thought"]).takeIf { it.isNotBlank() }?.let { return it }
    stringValue("reasoning_content")?.let { return it }
    val blocks = this["thinking_blocks"] as? JsonArray
    val thinking = blocks?.mapNotNull { block ->
        val obj = block as? JsonObject ?: return@mapNotNull null
        obj.stringValue("thinking") ?: obj.stringValue("text")
    }?.filter { it.isNotBlank() }?.joinToString("\n\n").orEmpty()
    return thinking
}

private fun JsonObject.actionSummary(): String {
    val action = this["action"] as? JsonObject ?: return ""
    return listOfNotNull(
        action.stringValue("command"),
        action.stringValue("path"),
        action.stringValue("url"),
        action.stringValue("file_text")?.take(400),
        extractTextBlocks(action["content"]),
    ).firstOrNull { it.isNotBlank() }.orEmpty()
}

private fun JsonObject.observationSummary(): String {
    val observation = this["observation"] as? JsonObject ?: return ""
    return listOfNotNull(
        observation.stringValue("output"),
        observation.stringValue("stdout"),
        observation.stringValue("content"),
        observation.stringValue("error"),
        observation.stringValue("path"),
        observation.stringValue("url"),
    ).firstOrNull { it.isNotBlank() }.orEmpty()
}

private fun JsonObject.nestedKind(key: String): String? =
    ((this[key] as? JsonObject)?.stringValue("kind") ?: stringValue(key))?.lowercase()

private fun extractTextBlocks(element: JsonElement?): String = when (element) {
    is JsonPrimitive -> element.content.takeIf { it.isNotBlank() && it != "null" }.orEmpty()
    is JsonArray -> element.mapNotNull { item ->
        when (item) {
            is JsonPrimitive -> item.content
            is JsonObject -> {
                val type = item.stringValue("type")?.lowercase()
                if (type == "image") null else item.stringValue("text") ?: item.stringValue("content")
            }
            else -> null
        }
    }.filter { it.isNotBlank() }.joinToString("\n")
    is JsonObject -> element.stringValue("text") ?: element.stringValue("content").orEmpty()
    else -> ""
}

internal fun splitInlineThink(content: String): Pair<String, String> {
    val leading = content.trimStart()
    if (!leading.startsWith("<think>")) return "" to content
    val afterOpen = leading.removePrefix("<think>")
    val close = afterOpen.indexOf("</think>")
    if (close < 0) return "" to content
    val reasoning = afterOpen.take(close).trim()
    val message = afterOpen.substring(close + "</think>".length).trim()
    return reasoning to message
}

private fun JsonObject.stringValue(key: String): String? =
    (this[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

private object EventKey {
    fun from(event: AgentEventEnvelope): String =
        "${event.timestamp.orEmpty()}|${event.type.orEmpty()}|${event.payload}".hashCode().toString()
}
