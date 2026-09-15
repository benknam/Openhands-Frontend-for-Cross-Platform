package com.openhands.remote.ui.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openhands.remote.core.model.AgentEventEnvelope
import com.openhands.remote.ui.components.InspectorTab
import com.openhands.remote.ui.components.OhStatusDot
import com.openhands.remote.ui.theme.OpenHandsColors
import kotlinx.serialization.json.JsonObject

@Composable
fun TimelineItemCard(
    item: TimelineItem,
    chatBubbleActionsEnabled: Boolean = false,
    adaptiveWidthEnabled: Boolean = false,
    onOpenInspector: (InspectorTab, String?) -> Unit = { _, _ -> },
    onCloseInspector: () -> Unit = {},
    inspectorOpen: Boolean = false,
    inspectorTab: InspectorTab? = null,
) {
    when (item) {
        is TimelineItem.UserMessage -> MessageCard(
            title = "你",
            content = item.text,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            alignEnd = true,
            timestamp = item.timestamp,
            chatBubbleActionsEnabled = chatBubbleActionsEnabled,
            adaptiveWidthEnabled = adaptiveWidthEnabled,
        )
        is TimelineItem.AssistantMessage -> MessageCard(
            title = "OpenHands",
            content = item.text,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            timestamp = item.timestamp,
            chatBubbleActionsEnabled = chatBubbleActionsEnabled,
            adaptiveWidthEnabled = false,
        )
        // Upstream 1.18.0: Thought events (collapsible thinking)
        is TimelineItem.Thought -> ThoughtCard(
            title = "思考",
            content = item.content,
            raw = item.raw,
            timestamp = item.timestamp,
        )
        is TimelineItem.ToolCall -> ToolCard(
            title = toolTitle(item.category, item.name),
            summary = formatToolSummary(item.category, item.summary, item.raw),
            category = item.category,
            timestamp = item.timestamp,
            panelTab = item.panelTab,
            panelPath = item.panelPath,
            onOpenInspector = onOpenInspector,
            onCloseInspector = onCloseInspector,
            inspectorOpen = inspectorOpen,
            inspectorTab = inspectorTab,
        )
        is TimelineItem.ToolResult -> ToolCard(
            title = toolTitle(item.category, "工具结果"),
            summary = formatToolSummary(item.category, item.summary, item.raw),
            category = item.category,
            timestamp = item.timestamp,
            panelTab = item.panelTab,
            panelPath = item.panelPath,
            onOpenInspector = onOpenInspector,
            onCloseInspector = onCloseInspector,
            inspectorOpen = inspectorOpen,
            inspectorTab = inspectorTab,
        )
        // Upstream 1.18.0: Skill-ready events display
        is TimelineItem.SkillReady -> SkillReadyCard(
            skills = item.skills,
            raw = item.raw,
            timestamp = item.timestamp,
        )
        // Upstream 1.18.0: Hook execution events
        is TimelineItem.HookExecution -> HookExecutionCard(
            hookName = item.hookName,
            status = item.status,
            raw = item.raw,
            timestamp = item.timestamp,
        )
        // Upstream 1.18.0: Task tracking display
        is TimelineItem.TaskTracking -> TaskTrackingCard(
            taskTitle = item.taskTitle,
            status = item.status,
            subTasks = item.subTasks,
            raw = item.raw,
            timestamp = item.timestamp,
        )
        // Upstream 1.18.0: Critic result display
        is TimelineItem.CriticResult -> CriticResultCard(
            verdict = item.verdict,
            feedback = item.feedback,
            raw = item.raw,
            timestamp = item.timestamp,
        )
        is TimelineItem.Status -> StatusCard(item)
        is TimelineItem.Unknown -> StatusCard(
            TimelineItem.Status(item.id, item.timestamp, item.type, item.raw),
        )
    }
}

// ============================================================
// Upstream 1.18.0: New card types for enhanced event display
// ============================================================

@Composable
private fun ConstrainedCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val gutter = maxWidth / 20f
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = gutter),
        ) {
            content()
        }
    }
}

@Composable
private fun ThoughtCard(
    title: String,
    content: String,
    raw: JsonObject,
    timestamp: String?,
) {
    var expanded by remember { mutableStateOf(false) }
    ConstrainedCard {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.outline)
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "收起" else "展开")
                }
            }
            if (expanded) {
                androidx.compose.foundation.text.selection.SelectionContainer {
                    MarkdownContent(content.ifBlank { "（空思考）" })
                }
            } else {
                Text(
                    content.take(200).ifBlank { "（思考内容较长，点击展开）" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Timestamp(timestamp)
        }
    }
    }
}

@Composable
private fun SkillReadyCard(
    skills: List<String>,
    raw: JsonObject,
    timestamp: String?,
) {
    ConstrainedCard {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = OpenHandsColors.successBg,
            ),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("技能就绪", style = MaterialTheme.typography.labelLarge, color = OpenHandsColors.successText)
                skills.forEach { skill ->
                    Text("• $skill", style = MaterialTheme.typography.bodySmall, color = OpenHandsColors.textMuted)
                }
                Timestamp(timestamp)
            }
        }
    }
}

@Composable
private fun HookExecutionCard(
    hookName: String,
    status: String,
    raw: JsonObject,
    timestamp: String?,
) {
    val statusColor = when (status.lowercase()) {
        in setOf("success", "completed", "done") -> OpenHandsColors.success
        in setOf("error", "failed") -> OpenHandsColors.error
        else -> OpenHandsColors.warning
    }
    ConstrainedCard {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("钩子: $hookName", style = MaterialTheme.typography.titleSmall)
                    OhStatusDot(statusColor)
                }
                Text("状态: $status", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Timestamp(timestamp)
            }
        }
    }
}

@Composable
private fun TaskTrackingCard(
    taskTitle: String,
    status: String,
    subTasks: List<TimelineItem.TaskTracking.TaskItem>,
    raw: JsonObject,
    timestamp: String?,
) {
    val completedCount = subTasks.count { it.completed }
    ConstrainedCard {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
            ),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(taskTitle, style = MaterialTheme.typography.titleSmall)
                Text("状态: $status · 进度: $completedCount/${subTasks.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (subTasks.isNotEmpty()) {
                    subTasks.forEach { task ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OhStatusDot(if (task.completed) OpenHandsColors.success else OpenHandsColors.textMuted)
                            Text(task.title, style = MaterialTheme.typography.bodySmall, color = if (task.completed) OpenHandsColors.textMuted else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                Timestamp(timestamp)
            }
        }
    }
}

@Composable
private fun CriticResultCard(
    verdict: String,
    feedback: String,
    raw: JsonObject,
    timestamp: String?,
) {
    val isPositive = verdict.lowercase() in setOf("approve", "approved", "pass", "success")
    ConstrainedCard {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isPositive) OpenHandsColors.successBg else OpenHandsColors.failBg,
            ),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("AI 评估: $verdict", style = MaterialTheme.typography.titleSmall, color = if (isPositive) OpenHandsColors.successText else OpenHandsColors.failText)
                if (feedback.isNotBlank()) {
                    Text(feedback.take(300), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Timestamp(timestamp)
            }
        }
    }
}

@Composable
private fun MessageCard(
    title: String,
    content: String,
    containerColor: Color,
    timestamp: String?,
    alignEnd: Boolean = false,
    chatBubbleActionsEnabled: Boolean = false,
    adaptiveWidthEnabled: Boolean = false,
) {
    val wrapUserBubble = adaptiveWidthEnabled && alignEnd
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val gutter = maxWidth / 20f
        val maxBubbleWidth = if (wrapUserBubble) maxWidth * 0.75f else maxWidth - gutter * 2
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = gutter),
            horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
        ) {
            Card(
                modifier = if (wrapUserBubble) {
                    Modifier
                        .widthIn(max = maxBubbleWidth)
                        .wrapContentWidth()
                } else {
                    Modifier.fillMaxWidth()
                },
                colors = CardDefaults.cardColors(containerColor = containerColor),
            ) {
                Column(
                    modifier = Modifier
                        .then(if (wrapUserBubble) Modifier.wrapContentWidth() else Modifier.fillMaxWidth())
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = if (wrapUserBubble) Modifier.wrapContentWidth() else Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Text(title, style = MaterialTheme.typography.labelLarge)
                        if (chatBubbleActionsEnabled && alignEnd) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = {}) { Text("编辑") }
                                TextButton(onClick = {}) { Text("重试") }
                            }
                        }
                    }
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        MarkdownContent(content.ifBlank { "（空消息）" })
                    }
                    Timestamp(timestamp)
                }
            }
        }
    }
}

@Composable
private fun ToolCard(
    title: String,
    summary: String,
    category: String,
    timestamp: String?,
    panelTab: String? = null,
    panelPath: String? = null,
    onOpenInspector: (InspectorTab, String?) -> Unit = { _, _ -> },
    onCloseInspector: () -> Unit = {},
    inspectorOpen: Boolean = false,
    inspectorTab: InspectorTab? = null,
) {
    var detailsExpanded by remember { mutableStateOf(false) }
    val targetTab = panelTab?.let { runCatching { InspectorTab.valueOf(it) }.getOrNull() }
        ?: inspectorTabForToolCategory(category)
    val panelActive = inspectorOpen && targetTab != null && inspectorTab == targetTab
    ConstrainedCard {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    OhStatusDot(statusColor(summary))
                    if (targetTab != null) {
                        TextButton(
                            onClick = {
                                if (panelActive) {
                                    onCloseInspector()
                                } else {
                                    onOpenInspector(targetTab, panelPath)
                                }
                            },
                            modifier = Modifier.semantics {
                                contentDescription = if (panelActive) {
                                    "收起${targetTab.label}面板"
                                } else {
                                    "展开${targetTab.label}面板"
                                }
                            },
                        ) {
                            Text(if (panelActive) "收起" else "展开")
                        }
                    } else {
                        TextButton(onClick = { detailsExpanded = !detailsExpanded }) {
                            Text(if (detailsExpanded) "收起" else "展开")
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (summary.isNotBlank()) {
                        androidx.compose.foundation.text.selection.SelectionContainer {
                            MarkdownContent(summary)
                        }
                    } else {
                        Text("（无内容）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                    if (detailsExpanded && targetTab == null) {
                        Text(
                            "没有对应的右侧面板。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    Timestamp(timestamp)
                }
            }
        }
    }
}

@Composable
private fun statusColor(text: String): Color {
    val lowered = text.lowercase()
    return when {
        lowered.contains("error") || lowered.contains("fail") -> OpenHandsColors.error
        lowered.contains("success") || lowered.contains("done") -> OpenHandsColors.success
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
    }
}

private fun toolTitle(category: String, name: String): String {
    return when (category) {
        "terminal" -> "终端命令"
        "code" -> "文件编辑"
        "browser" -> "浏览器操作"
        "mcp" -> "MCP 工具调用"
        else -> if (name.isNotBlank()) name.uppercase() else "工具调用"
    }
}

private fun formatToolSummary(category: String, summary: String, raw: JsonObject): String {
    val observation = (raw["observation"] as? JsonObject) ?: raw
    val action = (raw["action"] as? JsonObject) ?: raw
    return when (category) {
        "terminal" -> {
            val command = listOfNotNull(
                observation.stringValue("command"),
                action.stringValue("command"),
            ).firstOrNull() ?: summary.ifBlank { "(未提供命令)" }
            val output = listOfNotNull(
                observation.stringValue("stdout"),
                observation.stringValue("output"),
            ).firstOrNull()?.trim().orEmpty()
            if (summary.isNotBlank()) "$command\n$summary" else "Command:\n`$command`\n\nOutput:\n```\n$output\n```".ifBlank { command }
        }

        "code" -> {
            val path = listOfNotNull(
                observation.stringValue("path"),
                action.stringValue("path"),
            ).firstOrNull()?.let { "`$it`" }.orEmpty()
            val fileText = listOfNotNull(
                observation.stringValue("file_text"),
                observation.stringValue("new_content"),
                observation.stringValue("content"),
            ).firstOrNull().orEmpty()
            if (summary.isNotBlank()) "$path\n$fileText".ifBlank { summary } else path.ifBlank { summary }
        }

        "browser" -> {
            val url = observation.stringValue("url")?.let { "Browsing $it" }.orEmpty()
            if (url.isNotBlank()) url else summary.ifBlank { "(浏览器操作)" }
        }

        else -> summary.ifBlank { "(无内容)" }
    }
}

private fun JsonObject.stringValue(key: String): String? =
    (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf(String::isNotBlank)

@Composable
private fun StatusCard(item: TimelineItem.Status) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "— ${item.label} —",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun Timestamp(timestamp: String?) {
    timestamp?.takeIf { it.isNotBlank() }?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            maxLines = 1,
        )
    }
}
