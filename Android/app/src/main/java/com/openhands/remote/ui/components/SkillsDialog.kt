package com.openhands.remote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.openhands.remote.core.model.BackendConnection
import com.openhands.remote.core.network.AgentServerHttpClient
import com.openhands.remote.core.network.formatNetworkError
import com.openhands.remote.ui.theme.OpenHandsColors
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

@Composable
fun SkillsDialog(
    connection: BackendConnection,
    httpClient: AgentServerHttpClient,
    projectDir: String?,
    onDismiss: () -> Unit,
) {
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var skills by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var expanded by remember { mutableStateOf<Set<String>>(emptySet()) }
    val coroutineScope = rememberCoroutineScope()

    fun loadSkills(isRefresh: Boolean = false) {
        if (isRefresh) refreshing = true else loading = true
        error = false
        coroutineScope.launch {
            httpClient.getAvailableSkills(connection, projectDir)
                .onSuccess {
                    skills = it
                    notice = if (it.isEmpty()) "当前后端没有返回技能。" else null
                    error = false
                }
                .onFailure {
                    error = true
                    notice = formatNetworkError(it, "技能加载失败")
                }
            loading = false
            refreshing = false
        }
    }

    LaunchedEffect(connection.profile.id, projectDir) { loadSkills() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            color = OpenHandsColors.surface,
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 640.dp),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("可用技能", color = OpenHandsColors.text, fontSize = 20.sp)
                        Text(
                            "如果您更新技能，需要先停止对话，然后点击刷新按钮以查看更改。",
                            color = OpenHandsColors.textMuted,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    OhIconButton(
                        "refresh",
                        "刷新",
                        { loadSkills(isRefresh = true) },
                        enabled = !loading && !refreshing,
                    )
                    OhIconButton("close", "关闭", onDismiss)
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp, max = 460.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, OpenHandsColors.border, RoundedCornerShape(10.dp))
                        .background(OpenHandsColors.surfaceVariant)
                        .verticalScroll(rememberScrollState()),
                ) {
                    when {
                        loading -> Text(
                            "正在加载技能…",
                            color = OpenHandsColors.textMuted,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(20.dp),
                        )
                        error || skills.isEmpty() -> Text(
                            notice ?: "当前没有可用技能。",
                            color = OpenHandsColors.textMuted,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(20.dp),
                        )
                        else -> {
                            val grouped = ComposerMenuCatalog.groupSkills(skills, projectDir)
                            SkillScope.entries.forEach { skillScope ->
                                val scoped = grouped[skillScope].orEmpty()
                                if (scoped.isEmpty()) return@forEach
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(OpenHandsColors.surfaceRaised)
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(skillScope.title, color = OpenHandsColors.textMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                    Text(
                                        scoped.size.toString(),
                                        color = OpenHandsColors.textMuted,
                                        fontSize = 11.sp,
                                        modifier = Modifier
                                            .border(1.dp, OpenHandsColors.border, RoundedCornerShape(10.dp))
                                            .padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                                scoped.forEach { skill ->
                                    val name = ComposerMenuCatalog.skillName(skill)
                                    val key = "${skillScope.name}-$name"
                                    val isExpanded = expanded.contains(key)
                                    Column(modifier = Modifier.fillMaxWidth().border(1.dp, OpenHandsColors.border.copy(alpha = 0.4f))) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    expanded = if (isExpanded) expanded - key else expanded + key
                                                }
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(name, color = OpenHandsColors.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                            Text(
                                                ComposerMenuCatalog.skillTypeLabel(skill),
                                                color = OpenHandsColors.textMuted,
                                                fontSize = 11.sp,
                                                modifier = Modifier
                                                    .padding(end = 8.dp)
                                                    .border(1.dp, OpenHandsColors.border, RoundedCornerShape(10.dp))
                                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                            )
                                            OhInlineIcon(if (isExpanded) "chevron_down" else "chevron_right", if (isExpanded) "收起" else "展开")
                                        }
                                        if (isExpanded) {
                                            Column(
                                                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                val triggers = ComposerMenuCatalog.skillTriggers(skill)
                                                if (triggers.isNotEmpty()) {
                                                    Text("触发器", color = OpenHandsColors.textMuted, fontSize = 12.sp)
                                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        triggers.forEach { trigger ->
                                                            Text(
                                                                trigger,
                                                                color = OpenHandsColors.textSecondary,
                                                                fontSize = 11.sp,
                                                                modifier = Modifier
                                                                    .border(1.dp, OpenHandsColors.border, RoundedCornerShape(10.dp))
                                                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                                            )
                                                        }
                                                    }
                                                }
                                                Text("内容", color = OpenHandsColors.textMuted, fontSize = 12.sp)
                                                Text(
                                                    ComposerMenuCatalog.skillContent(skill).ifBlank { "技能没有内容" },
                                                    color = OpenHandsColors.textSecondary,
                                                    fontSize = 12.sp,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(OpenHandsColors.surface, RoundedCornerShape(8.dp))
                                                        .padding(10.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
