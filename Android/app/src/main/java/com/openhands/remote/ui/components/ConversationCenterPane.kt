package com.openhands.remote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.openhands.remote.core.model.AgentEventEnvelope
import com.openhands.remote.core.model.AgentProfileSummary
import com.openhands.remote.core.model.LlmProfileSummary
import kotlinx.serialization.json.contentOrNull

import com.openhands.remote.core.model.BackendConnection
import com.openhands.remote.core.model.ConnectionState
import com.openhands.remote.core.model.ConversationSummary
import com.openhands.remote.core.network.AgentServerHttpClient
import com.openhands.remote.core.network.formatNetworkError
import com.openhands.remote.ui.conversation.TimelineItem
import com.openhands.remote.ui.conversation.TimelineItemCard
import com.openhands.remote.ui.conversation.toTimelineItems
import com.openhands.remote.ui.theme.OpenHandsColors
import kotlinx.coroutines.launch

@Composable
fun ConversationCenterPane(
    events: List<AgentEventEnvelope>,
    connection: BackendConnection,
    httpClient: AgentServerHttpClient,
    connectionState: ConnectionState,
    lastEventTimestamp: String?,
    conversations: List<ConversationSummary>,
    activeConversationId: String?,
    actionMessage: String?,
    sendingMessage: Boolean,
    deletingConversation: Boolean,
    chatBubbleActionsEnabled: Boolean,
    adaptiveWidthEnabled: Boolean,
    composerHidingEnabled: Boolean,
    autoHideComposer: Boolean,
    autoHideDelaySeconds: Int,
    showRecommendedAutomations: Boolean = true,
    sendShortcut: String = "Ctrl + Enter",
    onReconnect: () -> Unit,
    onDelete: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onInterrupt: () -> Unit,
    onSend: (String) -> Unit,
    onOpenWorkspace: () -> Unit = {},
    onPlugins: () -> Unit = {},
    pluginCount: Int = 0,
    onShowSkills: () -> Unit = {},
    onShowHooks: () -> Unit = {},
    onAddFiles: () -> Unit = {},
    onManageAgentProfiles: () -> Unit = {},
    agentProfileOptions: List<AgentProfileSummary> = emptyList(),
    selectedAgentProfileId: String? = null,
    onAgentProfileSelected: (String) -> Unit = {},
    onBranch: () -> Unit = {},
    inspectorOpen: Boolean = false,
    inspectorTab: InspectorTab? = null,
    onOpenInspector: (InspectorTab, String?) -> Unit = { _, _ -> },
    onCloseInspector: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val timelineItems = remember(events) { events.flatMap { it.toTimelineItems() } }
    val timelineListState = rememberLazyListState()
    val timelineNearBottom by remember {
        derivedStateOf {
            val layoutInfo = timelineListState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            layoutInfo.totalItemsCount == 0 || lastVisible >= layoutInfo.totalItemsCount - 2
        }
    }
    val scope = rememberCoroutineScope()
    var message by rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    var composerCollapsed by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    var profiles by remember { androidx.compose.runtime.mutableStateOf<List<LlmProfileSummary>>(emptyList()) }
    var activeProfileName by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var profileMessage by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val usage = remember(events) { latestUsageSnapshot(events) }
    LaunchedEffect(activeConversationId) {
        if (activeConversationId == null) {
            composerCollapsed = false
        }
        if (timelineItems.isNotEmpty()) {
            timelineListState.scrollToItem(timelineItems.lastIndex)
        }
    }
    LaunchedEffect(activeConversationId, timelineItems.size) {
        if (timelineItems.isNotEmpty() && timelineNearBottom) {
            timelineListState.animateScrollToItem(timelineItems.lastIndex)
        }
    }

    LaunchedEffect(activeConversationId, conversations) {
        conversations.firstOrNull { it.id == activeConversationId }?.activeProfile?.let { name ->
            activeProfileName = name
        }
    }

    suspend fun refreshLlmProfiles() {
        repeat(3) { attempt ->
            val result = httpClient.getLlmProfiles(connection)
            val response = result.getOrNull()
            if (response != null) {
                profiles = response.profiles
                activeProfileName = conversations.firstOrNull { it.id == activeConversationId }?.activeProfile
                    ?: response.activeProfile
                    ?: activeProfileName
                    ?: response.profiles.firstOrNull()?.name
                profileMessage = profileMessage
                    ?.takeUnless {
                        it.contains("模型档案不可用") ||
                            it.contains("正在加载模型档案") ||
                            it.contains("认证失败")
                    }
                return
            }
            val error = result.exceptionOrNull() ?: return@repeat
            if (error is kotlinx.coroutines.CancellationException) throw error
            val errorText = error.message.orEmpty()
            if (
                errorText.contains("cancel", ignoreCase = true) ||
                    errorText.contains("StandaloneCoroutine", ignoreCase = true)
            ) {
                return
            }
            if (attempt < 2) {
                kotlinx.coroutines.delay(400L * (attempt + 1))
            } else if (profiles.isEmpty()) {
                profileMessage = formatNetworkError(error, "模型档案不可用")
            }
        }
    }

    LaunchedEffect(connection.profile.id) {
        if (profiles.isEmpty()) {
            profileMessage = "正在加载模型档案…"
        }
        refreshLlmProfiles()
    }

    val modelOptions = remember(profiles) {
        profiles.map(::profileDisplayName)
    }
    val composerHidden = composerCollapsed
    val executionStatus = executionStatusLabel(events)
    val showThinkingFloat = activeConversationId != null &&
        (executionStatus == "运行中" || timelineItems.lastOrNull() is TimelineItem.Thought)

    if (composerHidingEnabled && activeConversationId != null) {
        LaunchedEffect(sendingMessage, autoHideComposer, autoHideDelaySeconds, message, activeConversationId) {
            if (!sendingMessage && autoHideComposer && message.isEmpty()) {
                kotlinx.coroutines.delay(autoHideDelaySeconds * 1000L)
                if (message.isEmpty() && activeConversationId != null) {
                    composerCollapsed = true
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = timelineListState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 8.dp,
                end = 8.dp,
                top = 8.dp,
                bottom = if (composerHidden) 8.dp else 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (activeConversationId == null) {
                item(key = "welcome") {
                    Text(
                        text = "让我们开始开发！",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 8.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
                if (showRecommendedAutomations) {
                    item(key = "automations") {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("推荐自动化", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                TextButton(onClick = { }) { Text("+ 添加") }
                            }
                            Text(
                                "暂无推荐自动化。添加宏后，自动化会显示在这里。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                            )
                        }
                    }
                }
            }
            items(timelineItems, key = { it.id }) { item ->
                TimelineItemCard(
                    item = item,
                    chatBubbleActionsEnabled = chatBubbleActionsEnabled,
                    adaptiveWidthEnabled = adaptiveWidthEnabled,
                    onOpenInspector = onOpenInspector,
                    onCloseInspector = onCloseInspector,
                    inspectorOpen = inspectorOpen,
                    inspectorTab = inspectorTab,
                )
            }
        }
        if (!composerHidden) {
            MessageComposer(
                value = message,
                enabled = !sendingMessage,
                sending = sendingMessage,
                onValueChange = { message = it },
                sendShortcut = sendShortcut,
                onSend = {
                    val text = message.trim()
                    if (text.isNotEmpty() && !sendingMessage) {
                        message = ""
                        onSend(text)
                    }
                },
                modelName = profiles.firstOrNull { it.name == activeProfileName }?.let(::profileDisplayName)
                    ?: profiles.firstOrNull()?.let(::profileDisplayName)
                    ?: "未配置模型",
                modelOptions = modelOptions,
                onModelSelected = { selected ->
                    profiles.firstOrNull { profileDisplayName(it) == selected }?.let { profile ->
                        scope.launch {
                            val result = activeConversationId?.let { conversationId ->
                                httpClient.switchConversationLlmProfile(connection, conversationId, profile.name)
                            } ?: httpClient.activateLlmProfile(connection, profile.name)
                            result
                                .onSuccess {
                                    activeProfileName = profile.name
                                    profileMessage = if (activeConversationId == null) {
                                        "已设置默认模型档案：${profile.name}"
                                    } else {
                                        "已切换当前会话模型档案：${profile.name}"
                                    }
                                }
                                .onFailure { profileMessage = formatNetworkError(it, "模型切换失败") }
                        }
                    }
                },
                showWelcomeActions = activeConversationId == null,
                onOpenWorkspace = onOpenWorkspace,
                onPlugins = onPlugins,
                pluginCount = pluginCount,
                agentProfileOptions = agentProfileOptions.map { profile ->
                    (profile.id ?: profile.name) to listOfNotNull(profile.name, profile.llmProfileRef).joinToString(" · ")
                },
                selectedAgentProfileId = selectedAgentProfileId,
                onAgentProfileSelected = onAgentProfileSelected,
                onManageAgentProfiles = onManageAgentProfiles,
                onAddFiles = onAddFiles,
                onMacroSelected = { prompt ->
                    message = if (message.isBlank()) prompt else "$message\n\n$prompt"
                },
                onAdd = { action ->
                    when (action) {
                        ComposerMenuCatalog.SHOW_SKILLS -> onShowSkills()
                        ComposerMenuCatalog.SHOW_HOOKS -> onShowHooks()
                        ComposerMenuCatalog.ADD_FILES -> onAddFiles()
                    }
                },
                onBranch = onBranch,
                onCollapse = { composerCollapsed = true },
                onExpand = { composerCollapsed = false },
                collapsed = false,
                usageLabel = usage?.let { snapshot -> listOfNotNull(snapshot.usedTokens?.let { "Token $it" }, snapshot.contextWindow?.let { "/ $it" }).joinToString(" ") },
                usagePercent = usagePercent(usage),
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
            )
        }
        }

        val banner = when {
            profiles.isNotEmpty() && (
                profileMessage?.contains("模型档案不可用") == true ||
                    profileMessage?.contains("正在加载模型档案") == true ||
                    profileMessage?.contains("认证失败") == true
                ) -> actionMessage?.takeIf { it.isNotBlank() }
            profileMessage?.contains("正在加载模型档案") == true && profiles.isEmpty() ->
                actionMessage?.takeIf { it.isNotBlank() }
            else -> profileMessage?.takeIf { it.isNotBlank() } ?: actionMessage?.takeIf { it.isNotBlank() }
        }
        banner?.let { status ->
            val isError = status.contains("失败") || status.contains("不可用") || status.contains("认证") || status.contains("错误")
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp, start = 16.dp, end = 16.dp),
                color = if (isError) MaterialTheme.colorScheme.errorContainer else OpenHandsColors.surfaceRaised,
                shape = RoundedCornerShape(10.dp),
                tonalElevation = 2.dp,
            ) {
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isError) MaterialTheme.colorScheme.onErrorContainer else OpenHandsColors.success,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }

        if (!timelineNearBottom && timelineItems.isNotEmpty()) {
            TextButton(
                onClick = {
                    scope.launch { timelineListState.animateScrollToItem(timelineItems.lastIndex) }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (composerHidden) 72.dp else 196.dp),
            ) { Text("↓ 新消息，回到底部") }
        }

        if (showThinkingFloat) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 20.dp)
                    .semantics { contentDescription = "思考浮标" },
                color = OpenHandsColors.surfaceRaised,
                shape = RoundedCornerShape(20.dp),
                tonalElevation = 4.dp,
                shadowElevation = 4.dp,
            ) {
                Text(
                    "思考中 · $executionStatus",
                    color = OpenHandsColors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }

        if (composerHidden) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 20.dp)
                    .background(OpenHandsColors.background, CircleShape),
            ) {
                MessageComposer(
                    value = message,
                    enabled = !sendingMessage,
                    sending = sendingMessage,
                    onValueChange = {},
                    sendShortcut = sendShortcut,
                    onSend = {},
                    collapsed = true,
                    onCollapse = { composerCollapsed = true },
                    onExpand = { composerCollapsed = false },
                )
            }
        }
    }
}

private data class UsageSnapshot(
    val usedTokens: String?,
    val contextWindow: String?,
)

private fun latestUsageSnapshot(events: List<AgentEventEnvelope>): UsageSnapshot? =
    events.asReversed().firstNotNullOfOrNull { findUsageSnapshot(it.payload) }

private fun usagePercent(snapshot: UsageSnapshot?): Float? {
    val used = snapshot?.usedTokens?.toFloatOrNull() ?: return null
    val context = snapshot.contextWindow?.toFloatOrNull() ?: return null
    if (context <= 0f) return null
    return (used / context).coerceIn(0f, 1f)
}

private fun findUsageSnapshot(element: kotlinx.serialization.json.JsonElement): UsageSnapshot? {
    if (element is kotlinx.serialization.json.JsonObject) {
        val strings = element.entries.associate { (key, value) ->
            key.lowercase() to (value as? kotlinx.serialization.json.JsonPrimitive)?.content
        }
        val used = strings.firstValue(
            "total_tokens", "total_token", "used_tokens", "input_tokens", "prompt_tokens",
        )
        val context = strings.firstValue(
            "context_window", "context_window_size", "max_input_tokens", "max_tokens",
        )
        if (used != null || context != null) return UsageSnapshot(used, context)
        element.values.forEach { child -> findUsageSnapshot(child)?.let { return it } }
    } else if (element is kotlinx.serialization.json.JsonArray) {
        element.forEach { child -> findUsageSnapshot(child)?.let { return it } }
    }
    return null
}

private fun profileDisplayName(profile: LlmProfileSummary): String =
    profile.model?.let { "${profile.name}  ·  $it" } ?: profile.name

private fun Map<String, String?>.firstValue(vararg keys: String): String? =
    keys.firstNotNullOfOrNull { key -> get(key)?.takeIf { it.isNotBlank() } }


private fun executionStatusLabel(events: List<AgentEventEnvelope>): String {
    val status = events.asReversed().firstNotNullOfOrNull { event ->
        listOf("execution_status", "agent_state", "status").firstNotNullOfOrNull { key ->
            (event.payload[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
        }
    }?.uppercase() ?: "未知状态"
    return when (status) {
        "RUNNING" -> "运行中"
        "IDLE", "FINISHED" -> "等待下一条"
        "WAITING_FOR_CONFIRMATION" -> "等待确认"
        "PAUSED" -> "已暂停"
        "ERROR", "STUCK" -> "错误"
        else -> status
    }
}

