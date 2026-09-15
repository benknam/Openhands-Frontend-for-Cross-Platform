package com.openhands.remote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.openhands.remote.core.model.AgentProfileSummary
import com.openhands.remote.core.model.BackendConnection
import com.openhands.remote.core.model.LlmProfileSummary
import com.openhands.remote.core.network.AgentServerHttpClient
import com.openhands.remote.core.network.formatNetworkError
import com.openhands.remote.core.security.BackendRepository
import com.openhands.remote.ui.theme.OpenHandsColors
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@Composable
fun SettingsDialog(
    connection: BackendConnection,
    httpClient: AgentServerHttpClient,
    startupPage: String,
    onStartupPageChange: (String) -> Unit,
    logoCollapseEnabled: Boolean,
    onLogoCollapseEnabledChange: (Boolean) -> Unit,
    adaptiveWidthEnabled: Boolean,
    onAdaptiveWidthEnabledChange: (Boolean) -> Unit,
    sendShortcut: String,
    onSendShortcutChange: (String) -> Unit,
    showRecommendedAutomations: Boolean,
    onShowRecommendedAutomationsChange: (Boolean) -> Unit,
    composerHidingEnabled: Boolean,
    onComposerHidingEnabledChange: (Boolean) -> Unit,
    autoHideComposer: Boolean,
    onAutoHideComposerChange: (Boolean) -> Unit,
    autoHideDelaySeconds: Int,
    onAutoHideDelaySecondsChange: (Int) -> Unit,
    chatBubbleActionsEnabled: Boolean,
    onChatBubbleActionsEnabledChange: (Boolean) -> Unit,
    onManageBackends: () -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(SettingsCatalog.navItems.first().key) }
    var notice by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var llmProfiles by remember { mutableStateOf<List<LlmProfileSummary>>(emptyList()) }
    var activeLlm by remember { mutableStateOf<String?>(null) }
    var agentProfiles by remember { mutableStateOf<List<AgentProfileSummary>>(emptyList()) }
    var activeAgent by remember { mutableStateOf<String?>(null) }
    var secrets by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var settings by remember { mutableStateOf<JsonObject?>(null) }
    var agentSchema by remember { mutableStateOf<JsonObject?>(null) }
    var conversationSchema by remember { mutableStateOf<JsonObject?>(null) }
    var language by remember { mutableStateOf("zh-CN") }
    var gitUserName by remember { mutableStateOf("") }
    var gitUserEmail by remember { mutableStateOf("") }
    var soundNotifications by remember { mutableStateOf(false) }
    var analyticsConsent by remember { mutableStateOf(false) }
    var titleLlmProfile by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val selectedItem = SettingsCatalog.navItems.first { it.key == selected }

    fun jsonName(item: JsonObject): String =
        item["name"]?.jsonPrimitive?.contentOrNull
            ?: item["id"]?.jsonPrimitive?.contentOrNull
            ?: "未命名"

    fun applySettings(value: JsonObject) {
        settings = value
        val prefs = BackendSettingsParser.appPreferences(value)
        language = BackendSettingsParser.stringSetting(prefs, value, "language") ?: "zh-CN"
        gitUserName = BackendSettingsParser.stringSetting(prefs, value, "git_user_name").orEmpty()
        gitUserEmail = BackendSettingsParser.stringSetting(prefs, value, "git_user_email").orEmpty()
        titleLlmProfile = BackendSettingsParser.stringSetting(prefs, value, "title_llm_profile").orEmpty()
        soundNotifications = BackendSettingsParser.booleanSetting(prefs, value, "enable_sound_notifications")
        analyticsConsent = BackendSettingsParser.booleanSetting(prefs, value, "user_consents_to_analytics")
    }

    fun loadRemoteSettings() {
        loading = true
        scope.launch {
            httpClient.getLlmProfiles(connection)
                .onSuccess {
                    llmProfiles = it.profiles
                    activeLlm = it.activeProfile
                }
                .onFailure { notice = formatNetworkError(it, "LLM 档案加载失败") }
            httpClient.getAgentProfiles(connection)
                .onSuccess { response ->
                    agentProfiles = response.profiles
                    activeAgent = response.activeProfileId
                }
                .onFailure { notice = formatNetworkError(it, "代理档案加载失败") }
            httpClient.getSecrets(connection)
                .onSuccess { items -> secrets = items }
                .onFailure { secrets = emptyList() }
            httpClient.getAgentSettingsSchema(connection)
                .onSuccess { agentSchema = it }
                .onFailure { agentSchema = null }
            httpClient.getConversationSettingsSchema(connection)
                .onSuccess { conversationSchema = it }
                .onFailure { conversationSchema = null }
            httpClient.getSettings(connection)
                .onSuccess { applySettings(it) }
                .onFailure { notice = formatNetworkError(it, "后端设置加载失败") }
            loading = false
        }
    }

    fun patchAppPreferences(vararg pairs: Pair<String, JsonElement>) {
        scope.launch {
            val body = buildJsonObject {
                put(
                    "misc_settings_diff",
                    buildJsonObject {
                        put(
                            "app_preferences",
                            buildJsonObject { pairs.forEach { (key, value) -> put(key, value) } },
                        )
                    },
                )
            }
            httpClient.patchSettings(connection, body)
                .onSuccess {
                    notice = "已保存"
                    httpClient.getSettings(connection).onSuccess { applySettings(it) }
                }
                .onFailure { notice = formatNetworkError(it, "保存失败") }
        }
    }

    fun patchNested(diffKey: String, nested: JsonObject) {
        scope.launch {
            httpClient.patchSettings(connection, buildJsonObject { put(diffKey, nested) })
                .onSuccess {
                    notice = "已保存"
                    httpClient.getSettings(connection).onSuccess { applySettings(it) }
                }
                .onFailure { notice = formatNetworkError(it, "保存失败") }
        }
    }

    LaunchedEffect(connection.profile.id) { loadRemoteSettings() }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(color = OpenHandsColors.surface, shape = RoundedCornerShape(0.dp), tonalElevation = 10.dp, modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .width(IntrinsicSize.Max)
                        .widthIn(min = 168.dp, max = 260.dp)
                        .fillMaxHeight()
                        .background(OpenHandsColors.surfaceVariant)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text("设置", color = OpenHandsColors.text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(8.dp))
                    SettingsCatalog.navItems.forEach { item ->
                        TextButton(onClick = { selected = item.key; notice = null }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(7.dp)) {
                            Text(item.title, color = if (selected == item.key) OpenHandsColors.text else OpenHandsColors.textMuted, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(selectedItem.title, color = OpenHandsColors.text, style = MaterialTheme.typography.headlineSmall)
                            Text(selectedItem.subtitle, color = OpenHandsColors.textMuted, fontSize = 13.sp)
                        }
                        TextButton(onClick = { loadRemoteSettings() }, enabled = !loading) { Text("刷新") }
                        TextButton(onClick = onDismiss) { Text("关闭") }
                    }
                    when (selected) {
                        "代理" -> AgentProfilesSection(
                            connection, httpClient, loading, agentProfiles, activeAgent, llmProfiles,
                            { notice = it }, { loadRemoteSettings() }, onManageBackends, onDisconnect,
                        )
                        "LLM" -> LlmProfilesSection(
                            connection, httpClient, loading, llmProfiles, activeLlm,
                            { notice = it }, { loadRemoteSettings() },
                        )
                        "压缩器" -> SchemaSettingsSection(
                            loading,
                            SettingsJson.parseSchema(agentSchema, setOf("condenser")),
                            SettingsJson.nestedObject(settings, "agent_settings"),
                            "后端没有返回压缩器 schema。",
                        ) { patchNested("agent_settings_diff", it) }
                        "代理上下文" -> SchemaSettingsSection(
                            loading,
                            SettingsJson.parseSchema(agentSchema, setOf("agent_context")),
                            SettingsJson.nestedObject(settings, "agent_settings"),
                            "后端没有返回代理上下文 schema。",
                        ) { patchNested("agent_settings_diff", it) }
                        "验证" -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            SchemaSettingsSection(
                                loading,
                                SettingsJson.parseSchema(conversationSchema, setOf("verification", "general")),
                                SettingsJson.nestedObject(settings, "conversation_settings"),
                                "后端没有返回会话验证 schema。",
                            ) { patchNested("conversation_settings_diff", it) }
                            SchemaSettingsSection(
                                loading,
                                SettingsJson.parseSchema(agentSchema, setOf("verification")),
                                SettingsJson.nestedObject(settings, "agent_settings"),
                                "后端没有返回代理验证 schema。",
                            ) { patchNested("agent_settings_diff", it) }
                        }
                        "应用程序" -> ApplicationSettingsSection(
                            startupPage, onStartupPageChange, language, { language = it },
                            gitUserName, { gitUserName = it }, gitUserEmail, { gitUserEmail = it },
                            soundNotifications, { soundNotifications = it }, analyticsConsent, { analyticsConsent = it },
                            titleLlmProfile, { titleLlmProfile = it }, llmProfiles, { selected = "LLM" },
                        ) {
                            patchAppPreferences(
                                "language" to JsonPrimitive(language),
                                "git_user_name" to JsonPrimitive(gitUserName),
                                "git_user_email" to JsonPrimitive(gitUserEmail),
                                "enable_sound_notifications" to JsonPrimitive(soundNotifications),
                                "user_consents_to_analytics" to JsonPrimitive(analyticsConsent),
                                "title_llm_profile" to if (titleLlmProfile.isBlank()) JsonNull else JsonPrimitive(titleLlmProfile),
                            )
                        }
                        "使用优化" -> UiOptimizationsSettingsSection(
                            logoCollapseEnabled, onLogoCollapseEnabledChange,
                            adaptiveWidthEnabled, onAdaptiveWidthEnabledChange,
                            sendShortcut, onSendShortcutChange,
                            showRecommendedAutomations, onShowRecommendedAutomationsChange,
                            chatBubbleActionsEnabled, onChatBubbleActionsEnabledChange,
                            composerHidingEnabled, onComposerHidingEnabledChange,
                            autoHideComposer, onAutoHideComposerChange,
                            autoHideDelaySeconds, onAutoHideDelaySecondsChange,
                        )
                        "机密" -> SecretsSection(loading, secrets, ::jsonName, { notice = it }, { name, value, description ->
                            scope.launch {
                                httpClient.upsertSecret(connection, name, value, description)
                                    .onSuccess {
                                        notice = "已保存 $name"
                                        httpClient.getSecrets(connection).onSuccess { secrets = it }
                                    }
                                    .onFailure { notice = formatNetworkError(it, "保存机密失败") }
                            }
                        }, { name ->
                            scope.launch {
                                httpClient.deleteSecret(connection, name)
                                    .onSuccess {
                                        secrets = secrets.filterNot { jsonName(it) == name }
                                        notice = "已删除 $name"
                                    }
                                    .onFailure { notice = formatNetworkError(it, "删除机密失败") }
                            }
                        })
                    }
                    notice?.let {
                        Text(it, color = if (it.contains("失败") || it.contains("无效")) OpenHandsColors.error else OpenHandsColors.success, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentProfilesSection(
    connection: BackendConnection,
    httpClient: AgentServerHttpClient,
    loading: Boolean,
    profiles: List<AgentProfileSummary>,
    activeId: String?,
    llmProfiles: List<LlmProfileSummary>,
    onNotice: (String) -> Unit,
    onReload: () -> Unit,
    onManageBackends: () -> Unit,
    onDisconnect: () -> Unit,
) {
    var mode by remember { mutableStateOf("list") }
    var editingName by remember { mutableStateOf<String?>(null) }
    var storedProfile by remember { mutableStateOf<JsonObject?>(null) }
    var profileName by remember { mutableStateOf("") }
    var llmRef by remember { mutableStateOf(llmProfiles.firstOrNull()?.name.orEmpty()) }
    var enableSubAgents by remember { mutableStateOf(false) }
    var enableSwitchLlm by remember { mutableStateOf(true) }
    var concurrency by remember { mutableStateOf("1") }
    val scope = rememberCoroutineScope()
    val existingNames = profiles.map { it.name }.toSet()

    fun openCreate() {
        mode = "create"
        editingName = null
        storedProfile = null
        profileName = ""
        llmRef = llmProfiles.firstOrNull()?.name.orEmpty()
        enableSubAgents = false
        enableSwitchLlm = true
        concurrency = "1"
    }

    if (mode == "list") {
        SettingHeading("当前 Agent Server", connection.profile.baseUrl)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("可用档案", color = OpenHandsColors.text, modifier = Modifier.weight(1f))
            Button(onClick = { openCreate() }) { Text("添加代理配置") }
        }
        if (profiles.isEmpty()) {
            Text(if (loading) "正在读取代理档案…" else "后端没有返回代理档案。", color = OpenHandsColors.textMuted, fontSize = 13.sp)
        } else {
            profiles.forEach { profile ->
                ProfileActionRow(
                    title = profile.name,
                    subtitle = SettingsJson.agentSecondary(profile),
                    isDefault = profile.id == activeId,
                    onActivate = {
                        val id = profile.id ?: return@ProfileActionRow
                        scope.launch {
                            httpClient.activateAgentProfile(connection, id)
                                .onSuccess { onNotice("已将「${profile.name}」设为默认"); onReload() }
                                .onFailure { onNotice(formatNetworkError(it, "激活失败")) }
                        }
                    },
                    onEdit = {
                        scope.launch {
                            httpClient.getAgentProfile(connection, profile.name)
                                .onSuccess { body ->
                                    val detail = SettingsJson.parseAgentDetail(body)
                                    storedProfile = detail.profile
                                    editingName = detail.name
                                    profileName = detail.name
                                    llmRef = SettingsJson.stringValue(detail.profile, "llm_profile_ref", llmProfiles.firstOrNull()?.name.orEmpty())
                                    enableSubAgents = SettingsJson.booleanValue(detail.profile, "enable_sub_agents")
                                    enableSwitchLlm = SettingsJson.booleanValue(detail.profile, "enable_switch_llm_tool", true)
                                    concurrency = SettingsJson.stringValue(detail.profile, "tool_concurrency_limit", "1")
                                    mode = "edit"
                                }
                                .onFailure { onNotice(formatNetworkError(it, "加载代理档案失败")) }
                        }
                    },
                    onDuplicate = {
                        scope.launch {
                            httpClient.getAgentProfile(connection, profile.name)
                                .onSuccess { body ->
                                    val detail = SettingsJson.parseAgentDetail(body)
                                    val copyName = SettingsJson.uniqueCopyName(profile.name, existingNames)
                                    val payload = SettingsJson.withoutKeys(detail.profile, "id", "name", "revision")
                                    httpClient.saveAgentProfile(connection, copyName, payload)
                                        .onSuccess { onNotice("配置文件已复制为「$copyName」"); onReload() }
                                        .onFailure { onNotice(formatNetworkError(it, "复制失败")) }
                                }
                                .onFailure { onNotice(formatNetworkError(it, "复制失败")) }
                        }
                    },
                    onDelete = {
                        scope.launch {
                            httpClient.deleteAgentProfile(connection, profile.name)
                                .onSuccess { onNotice("已删除 ${profile.name}"); onReload() }
                                .onFailure { onNotice(formatNetworkError(it, "删除失败")) }
                        }
                    },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onManageBackends) { Text("管理后端") }
            TextButton(onClick = onDisconnect) { Text("切换后端") }
        }
        return
    }

    Text(if (mode == "edit") "编辑代理配置" else "添加代理配置", color = OpenHandsColors.text, style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(profileName, { profileName = it }, label = { Text("档案名称") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    DropdownField("LLM 配置", llmRef, llmProfiles.map { it.name to SettingsJson.llmDisplayName(it) }) { llmRef = it }
    SettingToggle("启用子智能体", enableSubAgents) { enableSubAgents = it }
    SettingToggle("允许智能体切换 LLM 配置", enableSwitchLlm) { enableSwitchLlm = it }
    OutlinedTextField(concurrency, { concurrency = it }, label = { Text("工具并发限制") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { mode = "list" }) { Text("返回") }
        Button(onClick = {
            val name = profileName.trim()
            if (!SettingsJson.isProfileNameValid(name)) {
                onNotice("档案名称无效：需以字母或数字开头，仅含字母、数字、. _ -")
                return@Button
            }
            if (llmRef.isBlank()) {
                onNotice("请选择 LLM 配置")
                return@Button
            }
            val edited = buildJsonObject {
                put("agent_kind", "openhands")
                put("llm_profile_ref", llmRef)
                put("enable_sub_agents", enableSubAgents)
                put("enable_switch_llm_tool", enableSwitchLlm)
                concurrency.toIntOrNull()?.let { put("tool_concurrency_limit", it) }
            }
            val payload = storedProfile?.let { SettingsJson.withoutKeys(SettingsJson.deepMerge(it, edited), "id", "name", "revision") } ?: edited
            scope.launch {
                val original = editingName
                if (mode == "edit" && original != null && original != name) {
                    val renamed = httpClient.renameAgentProfile(connection, original, name)
                    if (renamed.isFailure) {
                        onNotice(formatNetworkError(renamed.exceptionOrNull(), "重命名失败"))
                        return@launch
                    }
                }
                httpClient.saveAgentProfile(connection, name, payload)
                    .onSuccess {
                        onNotice(if (mode == "create") "已创建 $name" else "已更新 $name")
                        mode = "list"
                        onReload()
                    }
                    .onFailure { onNotice(formatNetworkError(it, "保存失败")) }
            }
        }) { Text("保存") }
    }
}

@Composable
private fun LlmProfilesSection(
    connection: BackendConnection,
    httpClient: AgentServerHttpClient,
    loading: Boolean,
    profiles: List<LlmProfileSummary>,
    activeName: String?,
    onNotice: (String) -> Unit,
    onReload: () -> Unit,
) {
    var mode by remember { mutableStateOf("list") }
    var editingName by remember { mutableStateOf<String?>(null) }
    var baseConfig by remember { mutableStateOf(buildJsonObject { }) }
    var profileName by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var temperature by remember { mutableStateOf("0.0") }
    var topP by remember { mutableStateOf("1.0") }
    var timeout by remember { mutableStateOf("120") }
    var stream by remember { mutableStateOf(false) }
    var nativeToolCalling by remember { mutableStateOf(true) }
    var renaming by remember { mutableStateOf<LlmProfileSummary?>(null) }
    var renameValue by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val existingNames = profiles.map { it.name }.toSet()

    fun openCreate() {
        mode = "create"
        editingName = null
        baseConfig = buildJsonObject { }
        profileName = ""
        model = ""
        apiKey = ""
        baseUrl = ""
        temperature = "0.0"
        topP = "1.0"
        timeout = "120"
        stream = false
        nativeToolCalling = true
    }

    if (mode == "list") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("可用档案", color = OpenHandsColors.text, modifier = Modifier.weight(1f))
            Button(onClick = { openCreate() }) { Text("添加 LLM 配置") }
        }
        if (profiles.isEmpty()) {
            Text(if (loading) "正在读取模型档案…" else "后端没有返回模型档案。", color = OpenHandsColors.textMuted, fontSize = 13.sp)
        } else {
            profiles.forEach { profile ->
                ProfileActionRow(
                    title = profile.name,
                    subtitle = profile.model,
                    isDefault = profile.name == activeName,
                    onActivate = {
                        scope.launch {
                            httpClient.activateLlmProfile(connection, profile.name)
                                .onSuccess { onNotice("已将「${profile.name}」设为默认"); onReload() }
                                .onFailure { onNotice(formatNetworkError(it, "激活失败")) }
                        }
                    },
                    onEdit = {
                        scope.launch {
                            httpClient.getLlmProfile(connection, profile.name)
                                .onSuccess { body ->
                                    val config = SettingsJson.parseLlmDetail(body).config
                                    baseConfig = config
                                    editingName = profile.name
                                    profileName = profile.name
                                    model = SettingsJson.stringValue(config, "model")
                                    apiKey = ""
                                    baseUrl = SettingsJson.stringValue(config, "base_url")
                                    temperature = SettingsJson.stringValue(config, "temperature", "0.0")
                                    topP = SettingsJson.stringValue(config, "top_p", "1.0")
                                    timeout = SettingsJson.stringValue(config, "timeout", "120")
                                    stream = SettingsJson.booleanValue(config, "stream")
                                    nativeToolCalling = SettingsJson.booleanValue(config, "native_tool_calling", true)
                                    mode = "edit"
                                }
                                .onFailure { onNotice(formatNetworkError(it, "加载 LLM 档案失败")) }
                        }
                    },
                    onRename = { renaming = profile; renameValue = profile.name },
                    onDuplicate = {
                        scope.launch {
                            httpClient.getLlmProfile(connection, profile.name)
                                .onSuccess { body ->
                                    val detail = SettingsJson.parseLlmDetail(body)
                                    val copyName = SettingsJson.uniqueCopyName(profile.name, existingNames)
                                    httpClient.saveLlmProfile(connection, copyName, detail.config)
                                        .onSuccess { onNotice("配置文件已复制为「$copyName」"); onReload() }
                                        .onFailure { onNotice(formatNetworkError(it, "复制失败")) }
                                }
                                .onFailure { onNotice(formatNetworkError(it, "复制失败")) }
                        }
                    },
                    onDelete = {
                        scope.launch {
                            httpClient.deleteLlmProfile(connection, profile.name)
                                .onSuccess { onNotice("已删除 ${profile.name}"); onReload() }
                                .onFailure { onNotice(formatNetworkError(it, "删除失败")) }
                        }
                    },
                )
            }
        }
        renaming?.let { profile ->
            OutlinedTextField(renameValue, { renameValue = it }, label = { Text("重命名 ${profile.name}") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { renaming = null }) { Text("取消") }
                Button(onClick = {
                    val next = renameValue.trim()
                    if (!SettingsJson.isProfileNameValid(next)) {
                        onNotice("档案名称无效")
                        return@Button
                    }
                    scope.launch {
                        httpClient.renameLlmProfile(connection, profile.name, next)
                            .onSuccess { onNotice("已重命名为 $next"); renaming = null; onReload() }
                            .onFailure { onNotice(formatNetworkError(it, "重命名失败")) }
                    }
                }) { Text("保存名称") }
            }
        }
        return
    }

    Text(if (mode == "edit") "编辑 LLM 配置" else "添加 LLM 配置", color = OpenHandsColors.text, style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(profileName, { profileName = it }, label = { Text("档案名称") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    OutlinedTextField(model, {
        model = it
        if (mode == "create" && profileName.isBlank()) profileName = SettingsJson.deriveProfileName(it)
    }, label = { Text("模型") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    OutlinedTextField(
        apiKey, { apiKey = it },
        label = { Text(if (mode == "edit") "API密钥（留空表示不更改）" else "API密钥") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
    )
    OutlinedTextField(baseUrl, { baseUrl = it }, label = { Text("基础URL") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    OutlinedTextField(temperature, { temperature = it }, label = { Text("温度") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    OutlinedTextField(topP, { topP = it }, label = { Text("Top P") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    OutlinedTextField(timeout, { timeout = it }, label = { Text("超时") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    SettingToggle("流式传输", stream) { stream = it }
    SettingToggle("原生工具调用", nativeToolCalling) { nativeToolCalling = it }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { mode = "list" }) { Text("返回") }
        Button(onClick = {
            val name = profileName.trim()
            if (!SettingsJson.isProfileNameValid(name)) {
                onNotice("档案名称无效：需以字母或数字开头，仅含字母、数字、. _ -")
                return@Button
            }
            if (model.isBlank()) {
                onNotice("模型为必填项")
                return@Button
            }
            val dirty = buildJsonObject {
                put("model", model.trim())
                if (apiKey.isNotBlank()) put("api_key", apiKey)
                if (baseUrl.isNotBlank()) put("base_url", baseUrl.trim()) else if (mode == "create") put("base_url", JsonNull)
                temperature.toDoubleOrNull()?.let { put("temperature", it) }
                topP.toDoubleOrNull()?.let { put("top_p", it) }
                timeout.toIntOrNull()?.let { put("timeout", it) }
                put("stream", stream)
                put("native_tool_calling", nativeToolCalling)
            }
            val llm = if (mode == "edit") SettingsJson.deepMerge(baseConfig, dirty) else dirty
            scope.launch {
                val original = editingName
                if (mode == "edit" && original != null && original != name) {
                    val renamed = httpClient.renameLlmProfile(connection, original, name)
                    if (renamed.isFailure) {
                        onNotice(formatNetworkError(renamed.exceptionOrNull(), "重命名失败"))
                        return@launch
                    }
                }
                httpClient.saveLlmProfile(connection, name, llm)
                    .onSuccess {
                        if (activeName == original || activeName == name) httpClient.activateLlmProfile(connection, name)
                        onNotice(if (mode == "create") "已创建 $name" else "已更新 $name")
                        mode = "list"
                        onReload()
                    }
                    .onFailure { onNotice(formatNetworkError(it, "保存失败")) }
            }
        }) { Text("保存") }
    }
}

@Composable
private fun SchemaSettingsSection(
    loading: Boolean,
    sections: List<SettingsSection>,
    values: JsonObject?,
    emptyText: String,
    onSave: (JsonObject) -> Unit,
) {
    val drafts = remember(sections, values) { mutableStateMapOf<String, String>() }
    val toggles = remember(sections, values) { mutableStateMapOf<String, Boolean>() }
    LaunchedEffect(sections, values) {
        sections.flatMap { it.fields }.forEach { field ->
            val current = SettingsJson.lookup(values, field.key) ?: field.defaultValue
            if (field.valueType == "boolean" && field.choices.isEmpty()) {
                toggles[field.key] = SettingsJson.textOf(current).toBooleanStrictOrNull() ?: false
            } else {
                drafts[field.key] = SettingsJson.textOf(current)
            }
        }
    }
    if (sections.isEmpty()) {
        Text(if (loading) "正在读取设置…" else emptyText, color = OpenHandsColors.textMuted, fontSize = 13.sp)
        return
    }
    sections.forEach { section ->
        Text(section.label, color = OpenHandsColors.text, style = MaterialTheme.typography.titleMedium)
        section.fields.filter { field ->
            field.dependsOn.all { dependency -> toggles[dependency] ?: SettingsJson.booleanValue(values, dependency) }
        }.forEach { field ->
            val help = field.description
            when {
                field.valueType == "boolean" && field.choices.isEmpty() -> {
                    SettingToggle(field.label, toggles[field.key] ?: false) { toggles[field.key] = it }
                    help?.let { HintText(it) }
                }
                field.choices.isNotEmpty() -> {
                    DropdownField(field.label, drafts[field.key].orEmpty(), field.choices) { drafts[field.key] = it }
                    help?.let { HintText(it) }
                }
                else -> {
                    OutlinedTextField(
                        value = drafts[field.key].orEmpty(),
                        onValueChange = { drafts[field.key] = it },
                        label = { Text(field.label) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = field.valueType != "array" && field.valueType != "object",
                        visualTransformation = if (field.secret) PasswordVisualTransformation() else VisualTransformation.None,
                    )
                    help?.let { HintText(it) }
                }
            }
        }
    }
    Button(onClick = {
        var payload = buildJsonObject { }
        sections.flatMap { it.fields }.forEach { field ->
            val original = SettingsJson.lookup(values, field.key) ?: field.defaultValue
            val value = if (field.valueType == "boolean" && field.choices.isEmpty()) {
                JsonPrimitive(toggles[field.key] ?: false)
            } else {
                SettingsJson.parseFieldValue(field, drafts[field.key].orEmpty(), original)
            }
            payload = SettingsJson.deepMerge(payload, SettingsJson.putPath(field.key, value))
        }
        onSave(payload)
    }) { Text("保存更改") }
}

@Composable
private fun ApplicationSettingsSection(
    startupPage: String,
    onStartupPageChange: (String) -> Unit,
    language: String,
    onLanguageChange: (String) -> Unit,
    gitUserName: String,
    onGitUserNameChange: (String) -> Unit,
    gitUserEmail: String,
    onGitUserEmailChange: (String) -> Unit,
    soundNotifications: Boolean,
    onSoundNotificationsChange: (Boolean) -> Unit,
    analyticsConsent: Boolean,
    onAnalyticsConsentChange: (Boolean) -> Unit,
    titleLlmProfile: String,
    onTitleLlmProfileChange: (String) -> Unit,
    llmProfiles: List<LlmProfileSummary>,
    onOpenLlm: () -> Unit,
    onSave: () -> Unit,
) {
    DropdownField("语言", language, SettingsCatalog.languages, onLanguageChange)
    SettingToggle("发送匿名使用数据", analyticsConsent, onAnalyticsConsentChange)
    SettingToggle("声音通知", soundNotifications, onSoundNotificationsChange)
    var startupOpen by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("启动页", color = OpenHandsColors.text, modifier = Modifier.weight(1f))
        TextButton(onClick = { startupOpen = true }) {
            Text(if (startupPage == BackendRepository.STARTUP_LAST_CONVERSATION) "上次会话" else "新建会话")
        }
        DropdownMenu(expanded = startupOpen, onDismissRequest = { startupOpen = false }) {
            DropdownMenuItem(text = { Text("新建会话") }, onClick = { startupOpen = false; onStartupPageChange(BackendRepository.STARTUP_NEW_CONVERSATION) })
            DropdownMenuItem(text = { Text("上次会话") }, onClick = { startupOpen = false; onStartupPageChange(BackendRepository.STARTUP_LAST_CONVERSATION) })
        }
    }
    Text("对话标题", color = OpenHandsColors.text, style = MaterialTheme.typography.titleMedium)
    HintText("选择用于生成会话标题的 LLM 配置。")
    DropdownField(
        "标题生成模型",
        titleLlmProfile.ifBlank { "__automatic__" },
        listOf("__automatic__" to "自动") + llmProfiles.map { it.name to SettingsJson.llmDisplayName(it) },
    ) { onTitleLlmProfileChange(if (it == "__automatic__") "" else it) }
    TextButton(onClick = onOpenLlm) { Text("管理 LLM 配置") }
    Text("Git 设置", color = OpenHandsColors.text, style = MaterialTheme.typography.titleMedium)
    HintText("用于提交更改的 Git 用户名和邮箱。")
    OutlinedTextField(gitUserName, onGitUserNameChange, label = { Text("Git 用户名") }, placeholder = { Text("Git提交的用户名") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    OutlinedTextField(gitUserEmail, onGitUserEmailChange, label = { Text("Git 邮箱") }, placeholder = { Text("Git提交的电子邮件") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    Button(onClick = onSave) { Text("保存更改") }
}

@Composable
private fun SecretsSection(
    loading: Boolean,
    secrets: List<JsonObject>,
    jsonName: (JsonObject) -> String,
    onNotice: (String) -> Unit,
    onCreate: (String, String, String?) -> Unit,
    onDelete: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<String?>(null) }
    var secretName by remember { mutableStateOf("") }
    var secretValue by remember { mutableStateOf("") }
    var secretDescription by remember { mutableStateOf("") }
    val filtered = secrets.filter {
        query.isBlank() || jsonName(it).contains(query, ignoreCase = true) ||
            it["description"]?.jsonPrimitive?.contentOrNull.orEmpty().contains(query, ignoreCase = true)
    }
    OutlinedTextField(query, { query = it }, label = { Text("搜索机密") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    if (filtered.isEmpty()) {
        Text(if (loading) "正在读取机密…" else "您还没有任何机密。", color = OpenHandsColors.textMuted, fontSize = 13.sp)
    } else {
        filtered.forEach { secret ->
            val name = jsonName(secret)
            val description = secret["description"]?.jsonPrimitive?.contentOrNull.orEmpty()
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(name, color = OpenHandsColors.text, fontSize = 14.sp)
                    if (description.isNotBlank()) Text(description, color = OpenHandsColors.textMuted, fontSize = 12.sp)
                }
                TextButton(onClick = { editing = name; secretName = name; secretValue = ""; secretDescription = description }) { Text("编辑") }
                TextButton(onClick = { onDelete(name) }) { Text("删除") }
            }
        }
    }
    Text(if (editing == null) "添加机密" else "编辑机密", color = OpenHandsColors.text, style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(secretName, { secretName = it }, label = { Text("名称") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    OutlinedTextField(
        secretValue, { secretValue = it },
        label = { Text(if (editing == null) "值" else "值（留空表示不更改）") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
    )
    OutlinedTextField(secretDescription, { secretDescription = it }, label = { Text("说明（可选）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (editing != null) TextButton(onClick = { editing = null; secretName = ""; secretValue = ""; secretDescription = "" }) { Text("取消") }
        Button(onClick = {
            val name = secretName.trim()
            if (name.isEmpty()) { onNotice("请填写机密名称"); return@Button }
            if (editing == null && secretValue.isBlank()) { onNotice("请填写机密值"); return@Button }
            if (secrets.any { jsonName(it) == name && name != editing }) { onNotice("机密名称已存在"); return@Button }
            onCreate(name, secretValue, secretDescription.takeIf { it.isNotBlank() })
            editing = null
            secretName = ""
            secretValue = ""
            secretDescription = ""
        }) { Text("保存机密") }
    }
}

@Composable
private fun ProfileActionRow(
    title: String,
    subtitle: String?,
    isDefault: Boolean,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onRename: (() -> Unit)? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().border(1.dp, OpenHandsColors.border, RoundedCornerShape(8.dp)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = OpenHandsColors.text, fontSize = 14.sp)
                if (isDefault) Text("默认", color = OpenHandsColors.success, fontSize = 12.sp)
            }
            if (!subtitle.isNullOrBlank()) Text(subtitle, color = OpenHandsColors.textMuted, fontSize = 12.sp)
        }
        TextButton(onClick = { menuOpen = true }) { Text("操作") }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text("编辑") }, onClick = { menuOpen = false; onEdit() })
            onRename?.let { DropdownMenuItem(text = { Text("重命名") }, onClick = { menuOpen = false; it() }) }
            DropdownMenuItem(text = { Text("复制") }, onClick = { menuOpen = false; onDuplicate() })
            DropdownMenuItem(text = { Text(if (isDefault) "已是默认" else "设为默认") }, onClick = { menuOpen = false; onActivate() }, enabled = !isDefault)
            DropdownMenuItem(text = { Text("删除") }, onClick = { menuOpen = false; onDelete() })
        }
    }
}

@Composable
private fun DropdownField(label: String, selected: String, items: List<Pair<String, String>>, onSelected: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val current = items.firstOrNull { it.first == selected }?.second ?: selected.ifBlank { "请选择" }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, color = OpenHandsColors.text, modifier = Modifier.weight(1f))
        TextButton(onClick = { open = true }) { Text(current) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { open = false; onSelected(value) })
            }
        }
    }
}

@Composable
private fun SettingHeading(title: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, color = OpenHandsColors.text, style = MaterialTheme.typography.titleSmall)
        Text(value, color = OpenHandsColors.textSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun UiOptimizationsSettingsSection(
    logoCollapseEnabled: Boolean,
    onLogoCollapseEnabledChange: (Boolean) -> Unit,
    adaptiveWidthEnabled: Boolean,
    onAdaptiveWidthEnabledChange: (Boolean) -> Unit,
    sendShortcut: String,
    onSendShortcutChange: (String) -> Unit,
    showRecommendedAutomations: Boolean,
    onShowRecommendedAutomationsChange: (Boolean) -> Unit,
    chatBubbleActionsEnabled: Boolean,
    onChatBubbleActionsEnabledChange: (Boolean) -> Unit,
    composerHidingEnabled: Boolean,
    onComposerHidingEnabledChange: (Boolean) -> Unit,
    autoHideComposer: Boolean,
    onAutoHideComposerChange: (Boolean) -> Unit,
    autoHideDelaySeconds: Int,
    onAutoHideDelaySecondsChange: (Int) -> Unit,
) {
    val sendShortcuts = listOf("Ctrl + Enter", "Alt + Enter", "⌘ + Enter", "Ctrl + Shift + Enter")
    var shortcutMenuOpen by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingToggle("Logo 图标收拢/展开控制", logoCollapseEnabled, onLogoCollapseEnabledChange)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("发送快捷键", color = OpenHandsColors.text, modifier = Modifier.weight(1f))
            TextButton(onClick = { shortcutMenuOpen = true }) { Text(sendShortcut) }
            DropdownMenu(expanded = shortcutMenuOpen, onDismissRequest = { shortcutMenuOpen = false }) {
                sendShortcuts.forEach { shortcut ->
                    DropdownMenuItem(text = { Text(shortcut) }, onClick = { onSendShortcutChange(shortcut); shortcutMenuOpen = false })
                }
            }
        }
        SettingToggle("自适应宽屏信息", adaptiveWidthEnabled, onAdaptiveWidthEnabledChange)
        HintText("开启后仅根据你的消息内容收窄气泡，助手回复、工具结果和其他信息保持全宽。")
        SettingToggle("新建对话显示推荐的自动化", showRecommendedAutomations, onShowRecommendedAutomationsChange)
        SettingToggle("优化用户消息操作", chatBubbleActionsEnabled, onChatBubbleActionsEnabledChange)
        HintText("在用户消息气泡旁显示快速操作按钮。")
        SettingToggle("隐藏输入框", composerHidingEnabled, onComposerHidingEnabledChange)
        HintText("发送消息后自动收起输入区域，减少界面干扰。")
        if (composerHidingEnabled) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 12.dp, top = 4.dp).alpha(0.9f)) {
                SettingToggle("发送后自动隐藏", autoHideComposer, onAutoHideComposerChange)
                if (autoHideComposer) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
                        Text("自动隐藏延迟：", color = OpenHandsColors.textSecondary, fontSize = 13.sp)
                        OutlinedTextField(
                            value = autoHideDelaySeconds.toString(),
                            onValueChange = { newDelay -> onAutoHideDelaySecondsChange(newDelay.toIntOrNull()?.coerceIn(1, 3600) ?: autoHideDelaySeconds) },
                            modifier = Modifier.width(80.dp),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(color = OpenHandsColors.text),
                        )
                        Text("秒", color = OpenHandsColors.textMuted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().border(1.dp, OpenHandsColors.border, RoundedCornerShape(8.dp)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = OpenHandsColors.text, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun HintText(value: String, color: Color = OpenHandsColors.textMuted) {
    Text(value, color = color, fontSize = 12.sp)
}
