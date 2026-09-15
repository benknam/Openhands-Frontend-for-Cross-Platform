package com.openhands.remote.ui.conversation

import com.openhands.remote.core.model.AgentEventEnvelope
import com.openhands.remote.ui.components.InspectorTab
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class CanvasUiRequest(
    val command: String,
    val tab: InspectorTab,
    val path: String? = null,
) {
    val label: String
        get() = when (command) {
            "navigate_to_file", "show_preview" ->
                path?.substringAfterLast('/')?.takeIf { it.isNotBlank() }?.let { "打开文件：$it" }
                    ?: "打开文件面板"
            else -> "打开${tab.label}面板"
        }
}

fun AgentEventEnvelope.extractCanvasUiRequest(): CanvasUiRequest? =
    payload.extractCanvasUiRequest()

fun JsonObject.extractCanvasUiRequest(): CanvasUiRequest? {
    val toolName = (
        stringField("tool_name")
            ?: stringField("name")
            ?: (this["action"] as? JsonObject)?.stringField("kind")
            ?: ""
        ).lowercase()
    val action = this["action"] as? JsonObject
    val args = this["args"] as? JsonObject ?: action?.get("args") as? JsonObject
    val command = action?.stringField("command")
        ?: args?.stringField("command")
        ?: stringField("command")
    val tabName = action?.stringField("tab")
        ?: args?.stringField("tab")
        ?: stringField("tab")
    val path = action?.stringField("path")
        ?: args?.stringField("path")
        ?: action?.stringField("file_path")
        ?: args?.stringField("file_path")
        ?: stringField("path")
        ?: stringField("file_path")
    val isCanvas = toolName.contains("canvas_ui") ||
        toolName.contains("canvasuiaction") ||
        action?.stringField("kind").orEmpty().lowercase().contains("canvasui")
    if (!isCanvas) return null
    val tab = inspectorTabFor(command, tabName)
    return CanvasUiRequest(
        command = command ?: "open_tab",
        tab = tab,
        path = path?.takeIf { it.isNotBlank() },
    )
}

fun inspectorTabFor(command: String?, tabName: String?): InspectorTab {
    if (command == "navigate_to_file" || command == "show_preview") return InspectorTab.FILES
    return when (tabName?.lowercase()) {
        "browser" -> InspectorTab.BROWSER
        "terminal" -> InspectorTab.TERMINAL
        "planner", "tasklist" -> InspectorTab.PLANNER
        "usage" -> InspectorTab.USAGE
        "commits", "changes", "diff" -> InspectorTab.COMMITS
        "files", "vscode", "file" -> InspectorTab.FILES
        else -> InspectorTab.FILES
    }
}

fun inspectorTabForToolCategory(category: String): InspectorTab? = when (category) {
    "terminal" -> InspectorTab.TERMINAL
    "code", "canvas" -> InspectorTab.FILES
    "browser" -> InspectorTab.BROWSER
    else -> null
}

private fun JsonObject.stringField(key: String): String? =
    (this[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }
