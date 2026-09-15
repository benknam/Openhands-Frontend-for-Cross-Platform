package com.openhands.remote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openhands.remote.core.model.ConnectionState
import com.openhands.remote.core.model.ConversationSummary
import com.openhands.remote.ui.theme.OpenHandsColors

private val ClassicSelected = Color(0xFF29313D)

@Composable
fun CollapsedOpenHandsSidebar(
    onExpand: () -> Unit,
    onCreate: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxHeight().width(58.dp).background(OpenHandsColors.background).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ClassicLogo(onClick = onExpand, description = "展开左侧面板")
        ClassicTextButton("+", "新建会话", onCreate, compact = true)
        Spacer(Modifier.weight(1f))
        ClassicTextButton("设置", "设置", onSettings, compact = true)
    }
}

@Composable
fun OpenHandsSidebar(
    conversations: List<ConversationSummary>,
    workspaceConversations: List<ConversationSummary> = conversations,
    selectedId: String?,
    filter: String,
    loading: Boolean,
    onFilterChange: (String) -> Unit,
    logoCollapseEnabled: Boolean = true,
    onSelect: (ConversationSummary) -> Unit,
    onCreate: () -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
    onExtensions: () -> Unit,
    onBackend: () -> Unit,
    onManageBackend: () -> Unit,
    onAddBackend: () -> Unit,
    onAddWorkspace: () -> Unit,
    onManageWorkspace: () -> Unit,
    selectedWorkspace: String?,
    onWorkspaceSelected: (String) -> Unit,
    pinnedConversationIds: Set<String> = emptySet(),
    onTogglePin: (ConversationSummary) -> Unit = {},
    backendLabel: String,
    onAutomate: () -> Unit,
    onSort: (String) -> Unit,
    onMoreOptions: () -> Unit = {},
    connectionState: ConnectionState,
    sortMode: String,
    onDeleteAll: () -> Unit,
    onCollapseSidebar: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(true) }
    var sortOpen by remember { mutableStateOf(false) }
    val ordered = conversations.filter {
        filter.isBlank() || it.id.contains(filter, true) || it.title.orEmpty().contains(filter, true)
    }
    Column(modifier.fillMaxHeight().width(276.dp).background(OpenHandsColors.background)) {
        Row(
            Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ClassicLogo(
                onClick = if (logoCollapseEnabled) onCollapseSidebar else ({ }),
                description = "打开设置或收起左侧面板",
            )
            Spacer(Modifier.weight(1f))
            ClassicTextButton("设置", "打开设置", onSettings, compact = true)
        }
        HorizontalDivider(color = OpenHandsColors.border)
        ClassicTextButton("新建会话", "新建会话", onCreate)
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            ClassicTextButton(
                label = "会话管理 ${if (expanded) "⌃" else "⌄"}",
                description = "收拢或展开会话列表",
                onClick = { expanded = !expanded },
                modifier = Modifier.weight(1f),
            )
            ClassicTextButton("+", "新建会话", onCreate, compact = true)
            ClassicTextButton("≡", "排序菜单", { sortOpen = true }, compact = true)
            DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                DropdownMenuItem({ Text("最近") }, { onSort("recent"); sortOpen = false })
                DropdownMenuItem({ Text("按工作区") }, { onSort("workspace"); sortOpen = false })
                DropdownMenuItem({ Text("最早") }, { onSort("created"); sortOpen = false })
                DropdownMenuItem({ Text("更多选项") }, { onMoreOptions(); sortOpen = false })
                DropdownMenuItem({ Text("删除全部", color = OpenHandsColors.error) }, { onDeleteAll(); sortOpen = false })
            }
        }
        if (expanded) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 8.dp),
            ) {
                if (loading) item { Text("同步中…", color = OpenHandsColors.textMuted, fontSize = 12.sp, modifier = Modifier.padding(14.dp)) }
                if (ordered.isEmpty() && !loading) item { Text(if (filter.isBlank()) "暂无会话" else "没有匹配的会话", color = OpenHandsColors.textMuted, modifier = Modifier.padding(14.dp)) }
                items(ordered, key = { it.id }) { conversation ->
                    val title = conversation.title?.takeIf(String::isNotBlank) ?: conversation.id
                    ClassicConversationRow(
                        title = title,
                        selected = selectedId == conversation.id,
                        status = conversation.executionStatus,
                        pinned = conversation.id in pinnedConversationIds,
                        onClick = { onSelect(conversation) },
                        onPin = { onTogglePin(conversation) },
                    )
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
        HorizontalDivider(color = OpenHandsColors.border)
        Row(
            Modifier.fillMaxWidth().height(48.dp).clickable(role = Role.Button, onClick = onBackend).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("●", color = if (connectionState == ConnectionState.CONNECTED) OpenHandsColors.success else OpenHandsColors.warning, fontSize = 12.sp)
            Text(backendLabel.ifBlank { "未命名后端" }, color = OpenHandsColors.text, fontSize = 13.sp, modifier = Modifier.weight(1f).padding(start = 8.dp), maxLines = 1)
            Text("后端", color = OpenHandsColors.textMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ClassicLogo(onClick: () -> Unit, description: String) {
    BoxText("◈", description, onClick, modifier = Modifier.size(32.dp), logo = true)
}

@Composable
private fun ClassicTextButton(
    label: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    BoxText(label, description, onClick, modifier = modifier.fillMaxWidth().height(if (compact) 32.dp else 38.dp))
}

@Composable
private fun BoxText(label: String, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, logo: Boolean = false) {
    Row(
        modifier = modifier
            .background(if (logo) Color.Transparent else OpenHandsColors.surface, RoundedCornerShape(4.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description; role = Role.Button }
            .padding(horizontal = if (logo) 4.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(label, color = if (logo) OpenHandsColors.success else OpenHandsColors.textSecondary, fontSize = if (logo) 22.sp else 13.sp, fontWeight = if (logo) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun ClassicConversationRow(
    title: String,
    selected: Boolean,
    status: String?,
    pinned: Boolean,
    onClick: () -> Unit,
    onPin: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(38.dp)
            .background(if (selected) ClassicSelected else Color.Transparent, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("·", color = if (status.equals("ERROR", true)) OpenHandsColors.error else OpenHandsColors.success, fontSize = 18.sp)
        Text(if (pinned) "[置顶] $title" else title, color = OpenHandsColors.text, fontSize = 13.sp, maxLines = 1, modifier = Modifier.weight(1f).padding(start = 8.dp))
        Text("⋮", color = OpenHandsColors.textMuted, fontSize = 16.sp, modifier = Modifier.clickable(onClick = onPin).semantics { contentDescription = if (pinned) "取消置顶" else "置顶会话" })
    }
}
