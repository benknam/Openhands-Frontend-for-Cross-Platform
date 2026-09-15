package com.openhands.remote.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openhands.remote.ui.theme.OpenHandsColors

@Composable
fun MoreOptionsDialog(
    showArchived: Boolean,
    onShowArchivedChange: (Boolean) -> Unit,
    hideOld: Boolean,
    onHideOldChange: (Boolean) -> Unit,
    oldWeeks: Int,
    onOldWeeksChange: (Int) -> Unit,
    automationVisibility: String,
    onAutomationVisibilityChange: (String) -> Unit,
    showRepository: Boolean,
    onShowRepositoryChange: (Boolean) -> Unit,
    showAgent: Boolean,
    onShowAgentChange: (Boolean) -> Unit,
    showLabels: Boolean,
    onShowLabelsChange: (Boolean) -> Unit,
    showDetails: Boolean,
    onShowDetailsChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("更多选项", color = OpenHandsColors.text)
                Text(
                    "选择对话如何分组以及每行显示什么。更改会自动保存。",
                    color = OpenHandsColors.textMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OptionSection("整理")
                OptionRow("按工作区", null, false, {})
                OptionRow("按日期", null, true, {})
                HorizontalDivider(modifier = Modifier.padding(vertical = 7.dp))

                OptionSection("排序方式")
                OptionRow("创建时间", null, false, {})
                OptionRow("更新时间", null, true, {})
                HorizontalDivider(modifier = Modifier.padding(vertical = 7.dp))

                OptionSection("会话")
                OptionRow("所有会话", null, true, {})
                OptionRow("仅活动", null, false, {})
                HorizontalDivider(modifier = Modifier.padding(vertical = 7.dp))

                OptionSection("其他")
                OptionSwitchRow("显示已归档", showArchived, onShowArchivedChange)
                OptionSwitchRowWithMenu(
                    label = "隐藏旧对话",
                    checked = hideOld,
                    onCheckedChange = onHideOldChange,
                    weeks = oldWeeks,
                    onWeeksChange = onOldWeeksChange,
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 7.dp))

                OptionSection("自动化")
                OptionRow("与全部一起显示", null, automationVisibility == "all") {
                    onAutomationVisibilityChange("all")
                }
                OptionRow("隐藏自动化运行", null, automationVisibility == "none") {
                    onAutomationVisibilityChange("none")
                }
                OptionRow("仅自动化运行", null, automationVisibility == "only") {
                    onAutomationVisibilityChange("only")
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 7.dp))

                OptionSection("元数据")
                OptionSwitchRow("仓库与分支", showRepository, onShowRepositoryChange)
                OptionSwitchRow("代理 / 模型", showAgent, onShowAgentChange)
                OptionSwitchRow("显示标签", showLabels, onShowLabelsChange)
                OptionSwitchRow("悬停时显示详情", showDetails, onShowDetailsChange)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun OptionSection(title: String) {
    Text(
        title,
        color = OpenHandsColors.textMuted,
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 5.dp, bottom = 2.dp),
    )
}

@Composable
private fun OptionRow(label: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OhInlineIcon(if (selected) "check" else "circle", label, Modifier.padding(end = 9.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = OpenHandsColors.text, fontSize = 14.sp)
            subtitle?.let { Text(it, color = OpenHandsColors.textMuted, fontSize = 11.sp) }
        }
        TextButton(onClick = onClick) { Text(if (selected) "✓" else "") }
    }
}

@Composable
private fun OptionSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OhInlineIcon("settings", label, Modifier.padding(end = 9.dp))
        Text(label, color = OpenHandsColors.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun OptionSwitchRowWithMenu(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    weeks: Int,
    onWeeksChange: (Int) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OhInlineIcon("visibility_off", label, Modifier.padding(end = 9.dp))
        Text(label, color = OpenHandsColors.text, fontSize = 14.sp)
        TextButton(onClick = { menuOpen = true }) { Text("超过 $weeks 周⌄", color = OpenHandsColors.textSecondary) }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            listOf(1, 2, 4, 8).forEach { value ->
                DropdownMenuItem(
                    text = { Text("超过 $value 周") },
                    onClick = { onWeeksChange(value); menuOpen = false },
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
