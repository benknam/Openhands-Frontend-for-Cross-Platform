package com.openhands.remote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openhands.remote.ui.theme.OpenHandsColors
import com.openhands.remote.core.model.ConversationSummary

@Composable
fun ConversationList(
    conversations: List<ConversationSummary>,
    modifier: Modifier = Modifier,
    selectedId: String?,
    filter: String,
    loading: Boolean,
    onFilterChange: (String) -> Unit,
    onSelect: (ConversationSummary) -> Unit,
    onCreate: () -> Unit,
    onRefresh: () -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("会话", color = OpenHandsColors.textSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                OhIconButton("refresh", "刷新会话列表", onRefresh)
                OhIconButton("plus", "新建会话", onCreate)
            }
            if (loading) Text("同步中…", color = OpenHandsColors.textMuted, fontSize = 12.sp)
            if (conversations.isEmpty() && !loading) {
                Text(
                    if (filter.isBlank()) "暂无对话" else "没有匹配的对话",
                    color = OpenHandsColors.textMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(conversations, key = { it.id }) { summary ->
                        ConversationListItem(summary, summary.id == selectedId) { onSelect(summary) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationListItem(summary: ConversationSummary, selected: Boolean, onClick: () -> Unit) {
    val title = summary.title?.takeIf { it.isNotBlank() } ?: summary.id
    val status = summary.executionStatus.orEmpty().uppercase()
    val active = status in setOf("IDLE", "RUNNING", "WAITING_FOR_CONFIRMATION", "FINISHED", "AWAITING_USER_INPUT", "LOADING")
    val statusColor = when {
        status in setOf("RUNNING", "WAITING_FOR_CONFIRMATION", "AWAITING_USER_INPUT") -> OpenHandsColors.success
        status in setOf("LOADING", "INIT", "STARTING", "PAUSING") -> OpenHandsColors.warning
        status in setOf("ERROR", "STUCK") -> OpenHandsColors.error
        status == "PAUSED" -> OpenHandsColors.textMuted
        else -> OpenHandsColors.success
    }

    // Upstream 1.18.0: Display sandbox status badge if available
    val sandboxStatusText = summary.sandboxStatus?.name?.lowercase()?.replaceFirstChar { it.uppercase() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) OpenHandsColors.surfaceRaised else OpenHandsColors.background, RoundedCornerShape(7.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .semantics { contentDescription = "会话：$title，状态：${summary.executionStatus ?: "未知"}${if (selected) "，当前选中" else ""}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OhStatusDot(statusColor)
        Column(modifier = Modifier.weight(1f).padding(start = 9.dp)) {
            Text(title, color = if (selected) OpenHandsColors.text else OpenHandsColors.textSecondary, fontSize = 14.sp, maxLines = 1)
            // Upstream 1.18.0: Show agent_kind and sandbox status as secondary info
            val secondaryInfo = listOfNotNull(
                summary.agentKind?.takeIf { it.isNotBlank() }?.let { "[$it]" },
                sandboxStatusText?.let { "(Sandbox: $it)" },
                summary.metrics?.accumulatedCost?.let { "Cost: $it" },
            ).joinToString(" ")
            if (secondaryInfo.isNotBlank()) {
                Text(secondaryInfo, color = OpenHandsColors.textMuted, fontSize = 11.sp, maxLines = 1)
            }
        }
        summary.updatedAt?.let { Text(relativeTime(it), color = OpenHandsColors.textMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp)) }
    }
}

private fun relativeTime(value: String): String {
    val instant = runCatching { java.time.Instant.parse(value) }.getOrNull() ?: return value.take(5)
    val seconds = (java.time.Duration.between(instant, java.time.Instant.now()).seconds).coerceAtLeast(0)
    return when {
        seconds < 60 -> "刚刚"
        seconds < 3600 -> "${seconds / 60}m"
        seconds < 86_400 -> "${seconds / 3600}h"
        seconds < 604_800 -> "${seconds / 86_400}d"
        seconds < 2_592_000 -> "${seconds / 604_800}w"
        else -> "${seconds / 2_592_000}mo"
    }
}
