package com.openhands.remote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.res.painterResource
import com.openhands.remote.R
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openhands.remote.core.model.ConnectionState
import com.openhands.remote.ui.theme.OpenHandsColors

fun backendStatusColor(fallback: Color, state: ConnectionState): Color = when (state) {
    ConnectionState.CONNECTED -> OpenHandsColors.success
    ConnectionState.DISCONNECTED,
    ConnectionState.AUTH_FAILED,
    ConnectionState.NOT_FOUND,
    ConnectionState.FAILED -> OpenHandsColors.error
    ConnectionState.WAITING_NETWORK -> OpenHandsColors.warning
    ConnectionState.CONNECTING,
    ConnectionState.AUTHENTICATING,
    ConnectionState.SYNCING,
    ConnectionState.IDLE -> fallback
}

@Composable
fun OhIconButton(
    icon: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) OpenHandsColors.surfaceRaised else Color.Transparent)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        val tint = if (enabled) OpenHandsColors.textSecondary else OpenHandsColors.textDisabled
        val drawableId = ohDrawableFor(icon, label)
        if (drawableId != null) {
            Icon(
                painter = painterResource(drawableId),
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Text(icon, color = tint, fontSize = 18.sp)
        }
    }
}

private fun ohDrawableFor(icon: String, label: String): Int? = when {
    icon == "logo" -> R.drawable.oh_logo
    icon == "more" || icon == "⋮" -> R.drawable.oh_more
    icon == "info" || icon == "ⓘ" -> R.drawable.oh_info
    icon == "exclamation" || icon == "!" || label.contains("概览") -> R.drawable.oh_exclamation
    icon == "drawer" || icon == "▣" || icon == "☰" -> R.drawable.oh_drawer
    icon == "plus" || icon == "＋" -> R.drawable.oh_plus
    icon == "folder" || icon == "⊞" || label.contains("工作区") -> R.drawable.oh_folder_plus
    icon == "changes" || icon == "diff" || icon == "file_diff" || label.contains("变更") -> R.drawable.oh_changes
    icon == "plan" || icon == "planner" || icon == "todo" || label.contains("规划") -> R.drawable.oh_plan
    icon == "browse" || icon == "globe" || icon == "browser" || label.contains("浏览") -> R.drawable.oh_browse
    icon == "send" || icon == "↑" -> R.drawable.oh_arrow_up
    icon == "terminal" || icon == "›_" -> R.drawable.oh_terminal
    icon == "usage" || icon == "◔" -> R.drawable.oh_usage
    icon == "settings" || icon == "⚙" || (label.contains("设置") && !label.contains("后端")) -> R.drawable.oh_settings
    icon == "search" || icon == "⌕" || label.contains("搜索") -> R.drawable.oh_search
    icon == "pin" || icon == "📌" || label.contains("固定") -> R.drawable.oh_pin
    icon == "custom" || icon == "◇" || label == "自定义" -> R.drawable.oh_custom
    icon == "automations" || icon == "◈" || label.contains("Automate") -> R.drawable.oh_automations
    icon == "git" || icon == "git_commit" -> R.drawable.oh_git_commit
    icon == "code_branch" -> R.drawable.oh_code_branch
    icon == "pr" -> R.drawable.oh_pr
    icon == "arrow_down" || icon == "pull" -> R.drawable.oh_arrow_down
    icon == "push" -> R.drawable.oh_git_push
    icon == "repo_forked" -> R.drawable.oh_repo_forked
    icon == "edit" -> R.drawable.oh_edit
    icon == "skills" -> R.drawable.oh_skills
    icon == "hook" -> R.drawable.oh_hook
    icon == "tools" -> R.drawable.oh_tools
    icon == "robot" -> R.drawable.oh_robot
    icon == "paperclip" -> R.drawable.oh_paperclip
    icon == "document" -> R.drawable.oh_document
    icon == "tachometer" -> R.drawable.oh_tachometer
    icon == "water" -> R.drawable.oh_water
    icon == "chevron_right" -> R.drawable.oh_chevron_right
    icon == "download" -> R.drawable.oh_download
    icon == "trash" -> R.drawable.oh_trash
    icon == "collapse" || icon == "—" || icon == "−" || label.contains("收拢") -> R.drawable.oh_collapse
    icon == "refresh" || icon == "↻" -> R.drawable.oh_refresh
    icon == "back" || icon == "‹" -> R.drawable.oh_back
    icon == "close" || icon == "×" -> R.drawable.oh_close
    icon == "filter" || icon == "≡" -> R.drawable.oh_filter
    icon == "check" || icon == "✓" -> R.drawable.oh_check
    icon == "chevron_down" || label.contains("后端菜单") -> R.drawable.oh_chevron_down
    label.contains("分支") || icon == "branch" -> R.drawable.oh_code_branch
    else -> null
}

@Composable
fun OhPill(
    text: String,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    leading: String? = null,
    trailing: String? = null,
    selected: Boolean = false,
) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(if (selected) OpenHandsColors.surfaceRaised else Color.Transparent)
            .border(1.dp, OpenHandsColors.border, shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.let { OhInlineIcon(it, "", Modifier.padding(end = 7.dp)) }
        Text(text, color = OpenHandsColors.textSecondary, fontSize = 13.sp, maxLines = 1)
        trailing?.let { OhInlineIcon(it, "", Modifier.padding(start = 7.dp)) }
    }
}

@Composable
fun OhInlineIcon(icon: String, label: String, modifier: Modifier = Modifier) {
    ohDrawableFor(icon, label)?.let { drawableId ->
        Icon(
            painter = painterResource(drawableId),
            contentDescription = null,
            tint = OpenHandsColors.textSecondary,
            modifier = modifier.size(16.dp),
        )
    }
}

@Composable
fun OhMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    items: List<String>,
    onItemClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier
            .background(OpenHandsColors.surface)
            .border(1.dp, OpenHandsColors.border, RoundedCornerShape(10.dp)),
    ) {
        items.forEach { item ->
            if (item == "---") HorizontalDivider(color = OpenHandsColors.border)
            else DropdownMenuItem(
                text = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OhInlineIcon(menuGlyph(item), item, Modifier.padding(end = 10.dp))
                        Text(item, color = OpenHandsColors.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        if (hasNestedMenu(item)) OhInlineIcon("chevron_right", "展开")
                    }
                },
                onClick = {
                    onItemClick(item)
                    if (!hasNestedMenu(item)) onDismissRequest()
                },
            )
        }
    }
}

data class OhRichMenuEntry(
    val label: String = "",
    val icon: String? = null,
    val checked: Boolean = false,
    val heading: Boolean = false,
    val divider: Boolean = false,
)

@Composable
fun OhRichMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    entries: List<OhRichMenuEntry>,
    onItemClick: (OhRichMenuEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier
            .background(OpenHandsColors.surfaceVariant)
            .border(1.dp, OpenHandsColors.border, RoundedCornerShape(8.dp)),
    ) {
        entries.forEach { entry ->
            when {
                entry.divider -> HorizontalDivider(color = OpenHandsColors.border)
                entry.heading -> Text(entry.label, color = OpenHandsColors.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
                else -> DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            entry.icon?.let { OhInlineIcon(it, entry.label, Modifier.padding(end = 10.dp)) }
                            Text(entry.label, color = OpenHandsColors.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            if (entry.checked) OhInlineIcon("check", "已选择")
                        }
                    },
                    onClick = {
                        onItemClick(entry)
                        if (entry.label != "‹ 返回" && !entry.heading && !entry.divider) onDismissRequest()
                    },
                )
            }
        }
    }
}

internal fun menuGlyph(label: String): String = when {
    label.contains("重命名") -> "edit"
    label.contains("切换代理配置文件") || label.contains("管理代理配置文件") -> "robot"
    label.contains("技能") -> "skills"
    label.contains("钩子") -> "hook"
    label.contains("添加文件") || label.contains("图片") -> "paperclip"
    label.contains("提高测试覆盖率") -> "tachometer"
    label.contains("README") -> "document"
    label.contains("自动合并") -> "pr"
    label.contains("清理依赖") -> "water"
    label.contains("宏") -> "settings"
    label.contains("工具") -> "tools"
    label.contains("导出") || label.contains("下载") -> "download"
    label.contains("用量") -> "usage"
    label.contains("删除") -> "trash"
    label.contains("停止") || label.contains("中断") -> "close"
    label.contains("继续") || label.contains("重连") -> "refresh"
    label.contains("终端") -> "terminal"
    label.contains("返回") -> "back"
    label.contains("提交记录") || label.contains("提交") -> "git_commit"
    label.contains("拉取") -> "arrow_down"
    label.contains("推送") -> "push"
    label.contains("创建 PR") || label.contains("PR") -> "pr"
    label.contains("分支") -> "code_branch"
    else -> "info"
}

internal fun hasNestedMenu(label: String): Boolean =
    label == ComposerMenuCatalog.SWITCH_AGENT_PROFILE ||
        label == ComposerMenuCatalog.MACROS ||
        label == "‹ 返回"

@Composable
fun OhSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        color = OpenHandsColors.textMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.6.sp,
    )
}

@Composable
fun OhStatusDot(color: Color = OpenHandsColors.success, modifier: Modifier = Modifier) {
    Text("●", modifier = modifier, color = color, fontSize = 12.sp)
}
