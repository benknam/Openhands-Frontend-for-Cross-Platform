package com.openhands.remote.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.openhands.remote.core.model.BackendConnection
import com.openhands.remote.core.model.ConnectionState
import com.openhands.remote.ui.theme.OpenHandsColors

@Composable
fun ConversationTopBar(
    connection: BackendConnection,
    connectionState: ConnectionState,
    executionStatus: String? = null,
    onDisconnect: () -> Unit,
    onWorkspace: (() -> Unit)? = null,
    conversationTitle: String? = null,
    onOverview: () -> Unit = {},
    onGit: (String) -> Unit = {},
    onMenu: (String) -> Unit = {},
    onLeftPanel: (() -> Unit)? = null,
    leftPanelOpen: Boolean = false,
    showRightPanelButton: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var gitMenuOpen by remember { mutableStateOf(false) }
    val title = conversationTitle?.takeIf { it.isNotBlank() } ?: "新建对话"
    val status = executionStatus.orEmpty().uppercase()
    val statusColor = when {
        status in setOf("ERROR", "STUCK") -> OpenHandsColors.error
        status in setOf("LOADING", "INIT", "STARTING", "PAUSING") -> OpenHandsColors.warning
        status == "PAUSED" -> OpenHandsColors.textMuted
        connectionState == ConnectionState.CONNECTED -> OpenHandsColors.success
        else -> OpenHandsColors.warning
    }
    val menuItems = listOf(
        "重命名",
        "---",
        "显示可用技能",
        "显示可用钩子",
        "导出...",
        "下载对话数据",
        "---",
        "显示用量和成本",
        "暂停",
        "继续",
        "中断",
        "删除对话",
    )
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        onLeftPanel?.let { OhIconButton("drawer", if (leftPanelOpen) "收起左侧面板" else "展开左侧面板", it) }
        OhStatusDot(color = statusColor, modifier = Modifier.padding(horizontal = 7.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .widthIn(min = 96.dp)
                .heightIn(min = 36.dp)
                .clickable(onClick = onOverview)
                .semantics { contentDescription = "打开会话详情" }
                .padding(vertical = 6.dp, horizontal = 2.dp),
            contentAlignment = androidx.compose.ui.Alignment.CenterStart,
        ) {
            Text(
                title,
                color = OpenHandsColors.text,
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                maxLines = 1,
            )
        }
        Box {
            OhIconButton("more", "打开会话菜单", { menuOpen = true })
            OhMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, items = menuItems, onItemClick = onMenu)
        }
        Box {
            OhPill("Git 操作", onClick = { gitMenuOpen = true }, leading = "git_commit")
            OhMenu(expanded = gitMenuOpen, onDismissRequest = { gitMenuOpen = false }, items = listOf("提交记录", "拉取", "推送", "创建 PR", "创建新分支"), onItemClick = onGit)
        }
        if (showRightPanelButton) {
            onWorkspace?.let { OhIconButton("drawer", "打开右侧面板", it) }
        }
    }
}
