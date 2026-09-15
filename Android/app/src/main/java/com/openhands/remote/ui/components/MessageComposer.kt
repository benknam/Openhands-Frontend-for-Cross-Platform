package com.openhands.remote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openhands.remote.ui.theme.OpenHandsColors

@Composable
fun MessageComposer(
    value: String,
    enabled: Boolean,
    sending: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    sendShortcut: String = "Ctrl + Enter",
    modelName: String = "GPT-Terra",
    modelOptions: List<String> = emptyList(),
    onModelSelected: (String) -> Unit = {},
    usageLabel: String? = null,
    usagePercent: Float? = null,
    onAdd: (String) -> Unit = {},
    agentProfileOptions: List<Pair<String, String>> = emptyList(),
    selectedAgentProfileId: String? = null,
    onAgentProfileSelected: (String) -> Unit = {},
    onManageAgentProfiles: () -> Unit = {},
    onMacroSelected: (String) -> Unit = {},
    onAddFiles: () -> Unit = {},
    showWelcomeActions: Boolean = false,
    onOpenWorkspace: () -> Unit = {},
    onPlugins: () -> Unit = {},
    pluginCount: Int = 0,
    onBranch: () -> Unit = {},
    onCollapse: () -> Unit = {},
    onExpand: () -> Unit = {},
    collapsed: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var modelMenuOpen by remember { mutableStateOf(false) }
    var addMenuOpen by remember { mutableStateOf(false) }
    var addMenuPage by remember { mutableStateOf("root") }
    if (collapsed) {
        Box(modifier = modifier) {
            CircleComposerButton("collapse", "展开消息输入区", onExpand, true, 46.dp)
        }
        return
    }
    Column(
        modifier = modifier.fillMaxWidth().background(OpenHandsColors.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(OpenHandsColors.surfaceRaised, RoundedCornerShape(15.dp))
                .border(1.dp, OpenHandsColors.border, RoundedCornerShape(15.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled && !sending,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 70.dp, max = 180.dp)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Enter && matchesSendShortcut(event, sendShortcut)) {
                            onSend()
                            true
                        } else {
                            false
                        }
                    }
                    .semantics { contentDescription = "消息输入框" },
                placeholder = { Text("你想要构建什么?", color = OpenHandsColors.textMuted.copy(alpha = 0.78f), fontSize = 15.sp) },
                minLines = 2,
                maxLines = 7,
                textStyle = TextStyle(color = OpenHandsColors.text, fontSize = 15.sp, lineHeight = 22.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                shape = RoundedCornerShape(10.dp),
                colors = TextFieldDefaults.colors(focusedContainerColor = OpenHandsColors.surfaceRaised, unfocusedContainerColor = OpenHandsColors.surfaceRaised, disabledContainerColor = OpenHandsColors.surfaceRaised, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent),
            )
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    OhIconButton(
                        "plus",
                        "添加宏、文件和图片",
                        { addMenuOpen = true; addMenuPage = "root" },
                        modifier = Modifier.size(40.dp),
                    )
                    when (addMenuPage) {
                        "macros" -> OhMenu(
                            expanded = addMenuOpen,
                            onDismissRequest = { addMenuOpen = false; addMenuPage = "root" },
                            items = listOf("‹ 返回") + ComposerMenuCatalog.macroItems(),
                            onItemClick = { item ->
                                if (item == "‹ 返回") {
                                    addMenuPage = "root"
                                } else {
                                    ComposerMenuCatalog.macroPrompt(item)?.let(onMacroSelected)
                                    onAdd(item)
                                    addMenuOpen = false
                                    addMenuPage = "root"
                                }
                            },
                            modifier = Modifier.width(250.dp),
                        )
                        "profiles" -> OhRichMenu(
                            expanded = addMenuOpen,
                            onDismissRequest = { addMenuOpen = false; addMenuPage = "root" },
                            entries = buildList {
                                add(OhRichMenuEntry("‹ 返回", icon = "back"))
                                add(OhRichMenuEntry(ComposerMenuCatalog.AVAILABLE_PROFILES, heading = true))
                                if (agentProfileOptions.isEmpty()) {
                                    add(OhRichMenuEntry("没有可用的代理配置文件"))
                                } else {
                                    agentProfileOptions.forEach { (id, label) ->
                                        add(OhRichMenuEntry(label, icon = "robot", checked = id == selectedAgentProfileId))
                                    }
                                }
                                add(OhRichMenuEntry(divider = true))
                                add(OhRichMenuEntry(ComposerMenuCatalog.MANAGE_AGENT_PROFILES, icon = "settings"))
                            },
                            onItemClick = { entry ->
                                when {
                                    entry.label == "‹ 返回" -> addMenuPage = "root"
                                    entry.label == ComposerMenuCatalog.MANAGE_AGENT_PROFILES -> {
                                        addMenuOpen = false
                                        addMenuPage = "root"
                                        onManageAgentProfiles()
                                    }
                                    entry.heading || entry.divider || entry.label == "没有可用的代理配置文件" -> Unit
                                    else -> {
                                        agentProfileOptions.firstOrNull { it.second == entry.label }?.first?.let(onAgentProfileSelected)
                                        addMenuOpen = false
                                        addMenuPage = "root"
                                    }
                                }
                            },
                            modifier = Modifier.width(280.dp),
                        )
                        else -> OhMenu(
                            expanded = addMenuOpen,
                            onDismissRequest = { addMenuOpen = false; addMenuPage = "root" },
                            items = ComposerMenuCatalog.mainItems(showAgentProfileSwitch = true),
                            onItemClick = { item ->
                                when (item) {
                                    ComposerMenuCatalog.SWITCH_AGENT_PROFILE -> addMenuPage = "profiles"
                                    ComposerMenuCatalog.MACROS -> addMenuPage = "macros"
                                    ComposerMenuCatalog.ADD_FILES -> {
                                        addMenuOpen = false
                                        onAddFiles()
                                        onAdd(item)
                                    }
                                    else -> {
                                        addMenuOpen = false
                                        onAdd(item)
                                    }
                                }
                            },
                            modifier = Modifier.width(250.dp),
                        )
                    }
                }
                Box {
                    OhPill(
                        modelName,
                        onClick = { modelMenuOpen = true },
                        trailing = "chevron_down",
                        modifier = Modifier
                            .heightIn(min = 42.dp)
                            .widthIn(max = 220.dp)
                            .semantics { contentDescription = "选择模型档案" },
                    )
                    DropdownMenu(
                        expanded = modelMenuOpen,
                        onDismissRequest = { modelMenuOpen = false },
                        modifier = Modifier
                            .background(OpenHandsColors.surfaceVariant)
                            .widthIn(min = 260.dp, max = 420.dp),
                    ) {
                        if (modelOptions.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("正在加载模型档案…", color = OpenHandsColors.textMuted) },
                                onClick = { modelMenuOpen = false },
                                enabled = false,
                            )
                        } else {
                            modelOptions.distinct().forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option, color = OpenHandsColors.text, maxLines = 2) },
                                    onClick = { modelMenuOpen = false; onModelSelected(option) },
                                )
                            }
                        }
                    }
                }
                Box(modifier = Modifier.weight(1f))
                CircleComposerButton("collapse", "收起对话输入框", onCollapse, true, 46.dp)
                if (sending) {
                    CircularProgressIndicator(modifier = Modifier.size(50.dp).testTag("send_progress").semantics { contentDescription = "消息发送中" }, color = OpenHandsColors.success, strokeWidth = 3.dp)
                } else {
                    CircleComposerButton("send", "发送消息", onSend, enabled && value.isNotBlank(), 50.dp, "send_button")
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(OpenHandsColors.background)
                .padding(horizontal = 12.dp, vertical = 2.dp)
                .heightIn(min = 36.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (showWelcomeActions) {
                OhPill("打开工作区", onClick = onOpenWorkspace, leading = "folder", modifier = Modifier.height(36.dp))
                OhPill(
                    if (pluginCount > 0) "插件 · $pluginCount" else "插件",
                    onClick = onPlugins,
                    leading = "custom",
                    modifier = Modifier.height(36.dp),
                )
            } else {
                OhPill("OpenHands_FA", onClick = onBranch, leading = "branch", modifier = Modifier.height(36.dp))
            }
            Box(modifier = Modifier.weight(1f))
            usagePercent?.let { TokenUsageBar(it, usageLabel) }
        }
    }
}


private fun matchesSendShortcut(event: androidx.compose.ui.input.key.KeyEvent, shortcut: String): Boolean =
    when {
        shortcut.startsWith("Ctrl") -> event.isCtrlPressed && !event.isAltPressed
        shortcut.startsWith("Alt") -> event.isAltPressed && !event.isCtrlPressed
        shortcut.startsWith("⌘") -> event.isMetaPressed
        else -> event.isCtrlPressed && event.isShiftPressed
    }

@Composable
private fun CircleComposerButton(icon: String, label: String, onClick: () -> Unit, enabled: Boolean, size: Dp, testTag: String? = null) {
    Box(
        modifier = Modifier.size(size).border(2.dp, if (enabled) OpenHandsColors.textSecondary else OpenHandsColors.textDisabled, CircleShape).clickable(enabled = enabled, onClick = onClick).semantics { contentDescription = label }.then(testTag?.let { Modifier.testTag(it) } ?: Modifier),
        contentAlignment = Alignment.Center,
    ) {
        OhInlineIcon(if (icon == "send") "send" else "collapse", label, Modifier.size(if (icon == "send") 27.dp else 25.dp))
    }
}

@Composable
private fun TokenUsageBar(percent: Float, label: String?) {
    val progress = percent.coerceIn(0f, 1f)
    val danger = progress > 0.8f
    val tone = if (danger) OpenHandsColors.error else OpenHandsColors.success
    Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(150.dp)) {
        Text(label ?: "Token ${(progress * 100).toInt()}%", color = if (danger) OpenHandsColors.error else OpenHandsColors.textMuted, fontSize = 11.sp)
        Box(modifier = Modifier.fillMaxWidth().height(5.dp).background(OpenHandsColors.border, RoundedCornerShape(3.dp))) {
            Box(modifier = Modifier.fillMaxWidth(progress).height(5.dp).background(tone, RoundedCornerShape(3.dp)))
        }
    }
}
