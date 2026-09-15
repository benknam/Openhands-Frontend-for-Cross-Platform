package com.openhands.remote

import android.content.Context
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.os.Bundle
import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.unit.sp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openhands.remote.core.model.BackendCapabilities
import com.openhands.remote.core.model.BackendConnection
import com.openhands.remote.core.model.ConversationSummary
import com.openhands.remote.core.model.ConnectionState
import com.openhands.remote.core.network.AgentServerHttpClient
import com.openhands.remote.core.network.formatNetworkError
import com.openhands.remote.core.network.DefaultConversationSyncManager
import com.openhands.remote.core.network.SqliteEventStore
import com.openhands.remote.core.security.BackendRepository
import com.openhands.remote.core.security.UiOptimizationSettings
import com.openhands.remote.feature.backends.BackendSetupViewModel
import com.openhands.remote.ui.components.ConversationCenterPane
import com.openhands.remote.ui.components.ConversationList
import com.openhands.remote.ui.components.CollapsedOpenHandsSidebar
import com.openhands.remote.ui.components.BranchManagerDialog
import com.openhands.remote.ui.components.ConversationTopBar
import com.openhands.remote.ui.components.OpenHandsSidebar
import com.openhands.remote.ui.components.WorkspaceInspectorPanel
import com.openhands.remote.ui.components.InspectorTab
import com.openhands.remote.ui.theme.OpenHandsRemoteTheme
import com.openhands.remote.core.model.AgentProfileSummary
import com.openhands.remote.core.model.PluginSpec
import com.openhands.remote.ui.components.PluginsDialog
import com.openhands.remote.ui.components.SettingsDialog
import com.openhands.remote.ui.components.SkillsDialog
import com.openhands.remote.ui.components.MoreOptionsDialog

import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonPrimitive
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import com.openhands.remote.ui.components.OhIconButton
import com.openhands.remote.ui.components.OhStatusDot
import com.openhands.remote.ui.theme.OpenHandsColors

private const val MAX_CONVERSATION_PAGES = 5

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OpenHandsRemoteTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                ) {
                    OpenHandsRoot(PaddingValues())
                }
            }
        }
        window.decorView.post { hideSystemBars() }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        hideSystemBars()
    }

    @Suppress("DEPRECATION")
    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.decorView.windowInsetsController?.apply {
                hide(android.view.WindowInsets.Type.systemBars())
                systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }
}

private fun conversationWorkspacePath(summary: ConversationSummary): String? {
    val raw = summary.workingDir ?: summary.selectedWorkspace ?: when (val workspace = summary.workspace) {
        is kotlinx.serialization.json.JsonPrimitive -> workspace.contentOrNull
        is kotlinx.serialization.json.JsonObject -> listOf("working_dir", "working_directory", "cwd", "path")
            .firstNotNullOfOrNull { key -> workspace[key]?.jsonPrimitive?.contentOrNull }
        else -> null
    } ?: return null
    return raw.trim().takeIf { it.startsWith("/") }
}

private fun conversationWorkspaceLabel(summary: ConversationSummary): String? {
    val raw = conversationWorkspacePath(summary) ?: when (val workspace = summary.workspace) {
        is kotlinx.serialization.json.JsonPrimitive -> workspace.contentOrNull
        is kotlinx.serialization.json.JsonObject -> workspace["name"]?.jsonPrimitive?.contentOrNull
        else -> null
    } ?: return null
    return raw.trim().trimEnd('/').substringAfterLast('/').takeIf { it.isNotBlank() }
}

private fun workspaceDisplayName(path: String): String =
    path.trim().trimEnd('/').substringAfterLast('/').ifBlank { path.trim() }

@Composable
private fun OverviewRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = OpenHandsColors.textSecondary, fontSize = 12.sp)
        Text(value, color = OpenHandsColors.text, fontSize = 13.sp)
    }
}

@Composable
private fun OpenHandsRoot(
    paddingValues: PaddingValues,
    viewModel: BackendSetupViewModel = viewModel(),
) {
    val setupState by viewModel.state.collectAsStateWithLifecycle()
    val connection = setupState.connection
    if (connection != null && setupState.connectionState == ConnectionState.CONNECTED) {
        ConversationScreen(
            paddingValues = paddingValues,
            connection = connection,
            onDisconnect = viewModel::disconnect,
            startupPage = setupState.startupPage,
            onStartupPageChange = viewModel::setStartupPage,
            onConnectBackend = viewModel::checkAndSave,
            onUpdateBackend = viewModel::updateBackend,
            onDeleteBackend = viewModel::deleteBackend,
        )
    } else {
        BackendSetupScreen(paddingValues, viewModel)
    }
}

@Composable
private fun BackendSetupScreen(
    paddingValues: PaddingValues,
    viewModel: BackendSetupViewModel,
) {
    var baseUrl by rememberSaveable { mutableStateOf("") }
    var apiKey by rememberSaveable { mutableStateOf("") }
    val setupState by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Openhands Frontend", style = MaterialTheme.typography.headlineMedium)
        Text("移动端远程控制中心", style = MaterialTheme.typography.bodyLarge)
        Text(
            "连接可访问的 Agent Server。网络工具由部署环境决定，会话恢复由客户端同步层负责。",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (setupState.savedConnections.isNotEmpty()) {
            Text("已保存后端", style = MaterialTheme.typography.titleMedium)
            setupState.savedConnections.forEach { saved ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.connect(saved) },
                        enabled = setupState.connectionState != ConnectionState.CONNECTING,
                        modifier = Modifier.weight(1f),
                    ) {
                        Column {
                            Text(saved.profile.name, maxLines = 1)
                            Text(saved.profile.baseUrl, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                    Button(
                        onClick = { viewModel.deleteBackend(saved.profile.id) },
                        enabled = setupState.connectionState != ConnectionState.CONNECTING,
                    ) { Text("删除") }
                }
            }
        }
        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Agent Server 地址输入框" },
            label = { Text("Agent Server Base URL") },
            placeholder = { Text("https://backend.example.com") },
            singleLine = true,
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Session API Key 输入框，可选" },
            label = { Text("Session API Key（可选）") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
        )
        Button(
            onClick = { viewModel.checkAndSave(baseUrl, apiKey) },
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = if (setupState.connectionState == ConnectionState.CONNECTING) {
                        "正在连接 Agent Server"
                    } else "连接 Agent Server"
                },
            enabled = baseUrl.isNotBlank() && setupState.connectionState != ConnectionState.CONNECTING,
        ) {
            if (setupState.connectionState == ConnectionState.CONNECTING) {
                CircularProgressIndicator()
            } else {
                Text("连接后端")
            }
        }
        setupState.message?.let { message ->
            Text(
                text = message,
                color = if (setupState.connectionState == ConnectionState.CONNECTED) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ConversationScreen(
    paddingValues: PaddingValues,
    connection: BackendConnection,
    onDisconnect: () -> Unit,
    startupPage: String,
    onStartupPageChange: (String) -> Unit,
    onConnectBackend: (String, String) -> Unit,
    onUpdateBackend: (String, String, String, String, String?) -> Unit,
    onDeleteBackend: (String) -> Unit,
) {
    val context = LocalContext.current
    val backendRepository = remember(context.applicationContext) {
        BackendRepository(context.applicationContext)
    }
    val manager = remember(connection.profile.id) {
        DefaultConversationSyncManager(eventStore = SqliteEventStore(context.applicationContext))
    }
    val savedBackends by backendRepository.connections.collectAsStateWithLifecycle(initialValue = emptyList())
    val storedUiSettings by backendRepository.uiOptimizationSettings.collectAsStateWithLifecycle(
        initialValue = UiOptimizationSettings(),
    )
    val httpClient = remember { AgentServerHttpClient() }
    val syncState by manager.state.collectAsStateWithLifecycle()
    val events by manager.events.collectAsStateWithLifecycle()
    val eventWorkingDir = remember(events) {
        events.asReversed().firstNotNullOfOrNull { event ->
            listOf("working_dir", "cwd").firstNotNullOfOrNull { key ->
                (event.payload[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.startsWith("/") }
            }
        }
    }
    val isWideScreen = LocalConfiguration.current.screenWidthDp >= 650
    var workspaceWorkingDir by rememberSaveable { mutableStateOf<String?>(null) }
    var capabilities by remember { mutableStateOf<BackendCapabilities?>(null) }
    val scope = rememberCoroutineScope()

    var conversationId by rememberSaveable { mutableStateOf("") }
    var activeConversationId by rememberSaveable { mutableStateOf<String?>(null) }
    var actionMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var showInspector by rememberSaveable { mutableStateOf(false) }
    var rightPanelCollapsed by rememberSaveable { mutableStateOf(false) }
    var leftSidebarCollapsed by rememberSaveable { mutableStateOf(true) }
    var conversations by remember { mutableStateOf<List<ConversationSummary>>(emptyList()) }
    var conversationsLoading by remember { mutableStateOf(false) }
    var conversationListMessage by remember { mutableStateOf<String?>(null) }
    var conversationFilter by rememberSaveable { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedWorkspace by rememberSaveable { mutableStateOf<String?>(null) }
    var savedWorkspaces by rememberSaveable { mutableStateOf(listOf<String>()) }
    var pinnedConversationIds by rememberSaveable { mutableStateOf(setOf<String>()) }
    var showAddWorkspaceDialog by remember { mutableStateOf(false) }
    var showManageWorkspaceDialog by remember { mutableStateOf(false) }
    var showPluginsDialog by remember { mutableStateOf(false) }
    var pluginPickerMode by remember { mutableStateOf(false) }
    var selectedPlugins by remember { mutableStateOf<List<PluginSpec>>(emptyList()) }
    var showSkillsDialog by remember { mutableStateOf(false) }
    var agentProfiles by remember { mutableStateOf<List<AgentProfileSummary>>(emptyList()) }
    var selectedAgentProfileId by remember { mutableStateOf<String?>(null) }
    var workspaceInput by rememberSaveable { mutableStateOf("") }
    var conversationSort by rememberSaveable { mutableStateOf("recent") }
    var showMoreOptionsDialog by rememberSaveable { mutableStateOf(false) }
    var showArchivedConversations by rememberSaveable { mutableStateOf(false) }
    var hideOldConversations by rememberSaveable { mutableStateOf(false) }
    var oldConversationWeeks by rememberSaveable { mutableStateOf(1) }
    var automationVisibility by rememberSaveable { mutableStateOf("all") }
    var showMetadataRepository by rememberSaveable { mutableStateOf(false) }
    var showMetadataAgent by rememberSaveable { mutableStateOf(false) }
    var showMetadataLabels by rememberSaveable { mutableStateOf(false) }
    var showMetadataDetails by rememberSaveable { mutableStateOf(false) }

    var showEditBackendDialog by remember { mutableStateOf(false) }
    var editingBackend by remember { mutableStateOf<BackendConnection?>(null) }
    var editBackendName by rememberSaveable { mutableStateOf("") }
    var editBackendUrl by rememberSaveable { mutableStateOf("") }
    var editBackendNote by rememberSaveable { mutableStateOf("") }
    var editBackendKey by rememberSaveable { mutableStateOf("") }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameTitle by rememberSaveable { mutableStateOf("") }

    var showBranchDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showBackendManager by remember { mutableStateOf(false) }
    var showAddBackendDialog by remember { mutableStateOf(false) }
    var backendUrlInput by rememberSaveable { mutableStateOf("") }
    var backendApiKeyInput by rememberSaveable { mutableStateOf("") }

    var showOverviewCard by rememberSaveable { mutableStateOf(false) }
    var showResourceDialog by remember { mutableStateOf(false) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var inspectorRequestedTab by remember { mutableStateOf<InspectorTab?>(null) }
    var inspectorRequestedFile by remember { mutableStateOf<String?>(null) }
    var inspectorSelectedTab by remember { mutableStateOf(InspectorTab.FILES) }
    var resourceDialogTitle by remember { mutableStateOf("") }
    var resourceDialogBody by remember { mutableStateOf("加载中…") }

    var creatingConversation by remember { mutableStateOf(false) }
    var startupMenuOpen by remember { mutableStateOf(false) }
    var deletingConversation by remember { mutableStateOf(false) }
    var sendingMessage by remember { mutableStateOf(false) }
    var newConversationWorkspace by rememberSaveable { mutableStateOf("") }

    var uiSettings by remember { mutableStateOf(UiOptimizationSettings()) }
    var composerCollapsed by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(storedUiSettings) {
        uiSettings = storedUiSettings
    }

    fun updateUiSettings(transform: (UiOptimizationSettings) -> UiOptimizationSettings) {
        val updated = transform(uiSettings)
        uiSettings = updated
        scope.launch { backendRepository.saveUiOptimizationSettings(updated) }
    }

    fun beginNewConversation() {
        manager.stop()
        activeConversationId = null
        conversationId = ""
        newConversationWorkspace = selectedWorkspace.orEmpty()
        showCreateDialog = false
        actionMessage = null
        composerCollapsed = false
    }

    fun openPluginPicker() {
        pluginPickerMode = true
        showPluginsDialog = true
    }

    fun openPluginManager() {
        pluginPickerMode = false
        showPluginsDialog = true
    }

    LaunchedEffect(connection.profile.id) {
        httpClient.getAgentProfiles(connection)
            .onSuccess { response ->
                agentProfiles = response.profiles
                if (selectedAgentProfileId == null || response.profiles.none { it.id == selectedAgentProfileId || it.name == selectedAgentProfileId }) {
                    selectedAgentProfileId = response.activeProfileId ?: response.profiles.firstOrNull()?.id
                }
            }
            .onFailure { agentProfiles = emptyList() }
    }

    LaunchedEffect(connection.profile.id, activeConversationId, eventWorkingDir, conversations) {
        val summaryPath = conversations.firstOrNull { it.id == activeConversationId }?.let(::conversationWorkspacePath)
        workspaceWorkingDir = eventWorkingDir ?: summaryPath
        activeConversationId?.let { id ->
            httpClient.getConversationWorkingDir(connection, id)
                .onSuccess { path -> if (path != null) workspaceWorkingDir = path }
        }
        httpClient.probeCapabilities(connection, activeConversationId)
            .onSuccess { capabilities = it }
            .onFailure { capabilities = null }
    }

    suspend fun loadConversations() {
        conversationsLoading = true
        conversationListMessage = null
        val loaded = mutableListOf<ConversationSummary>()
        var nextPageId: String? = null
        var failure: Throwable? = null
        repeat(MAX_CONVERSATION_PAGES) {
            val result = httpClient.searchConversationSummaries(connection, pageId = nextPageId)
            val response = result.getOrNull()
            if (response == null) {
                failure = result.exceptionOrNull()
                return@repeat
            }
            loaded += response.items
            nextPageId = response.nextPageId?.takeIf { it.isNotBlank() }
            if (nextPageId == null) return@repeat
        }
        if (failure != null) {
            conversationListMessage = formatNetworkError(failure, "会话列表加载失败")
        } else {
            conversations = loaded.distinctBy { it.id }
            if (activeConversationId == null && startupPage == BackendRepository.STARTUP_LAST_CONVERSATION) {
                val restoredId = backendRepository.getSelectedConversation(connection.profile.id)
                if (restoredId != null && loaded.any { it.id == restoredId }) {
                    conversationId = restoredId
                    activeConversationId = restoredId
                    manager.start(connection, restoredId)
                }
            }
        }
        conversationsLoading = false
    }

    fun openInspectorTab(tab: InspectorTab, path: String? = null) {
        inspectorRequestedTab = tab
        inspectorSelectedTab = tab
        inspectorRequestedFile = path
        rightPanelCollapsed = false
        if (!isWideScreen) showInspector = true
    }

    fun closeInspector() {
        rightPanelCollapsed = true
        showInspector = false
    }

    fun showResource(title: String, body: String) {
        resourceDialogTitle = title
        resourceDialogBody = body
        showResourceDialog = true
    }

    fun loadSkills() {
        showSkillsDialog = true
    }

    fun loadHooks() {
        showResource("可用钩子", "正在加载钩子…")
        scope.launch {
            httpClient.getConversationHooks(connection, workspaceWorkingDir)
                .onSuccess { hooks ->
                    val config = hooks["hook_config"]
                    val body = when {
                        config == null || config.toString() in setOf("null", "{}") ->
                            "当前工作区没有钩子配置。"
                        else -> config.toString().take(4000)
                    }
                    showResource("可用钩子", body)
                }
                .onFailure { showResource("可用钩子", formatNetworkError(it, "钩子加载失败")) }
        }
    }

    fun runGitAction(action: String) {
        if (action == "创建新分支") {
            showBranchDialog = true
            return
        }
        val root = workspaceWorkingDir
        if (root.isNullOrBlank()) {
            actionMessage = "后端未提供 workspace 根目录"
            return
        }
        val command = when (action) {
            "提交记录" -> "git log --oneline -n 20"
            "拉取" -> "git pull --ff-only"
            "推送" -> "git push"
            "创建 PR" -> "git status -sb && git remote -v"
            else -> null
        } ?: return
        scope.launch {
            httpClient.executeCommand(connection, command, root)
                .onSuccess { result ->
                    val output = listOf(result.stdout, result.stderr)
                        .filter { it.isNotBlank() }
                        .joinToString("\n")
                        .ifBlank { "$action 完成（退出码 ${result.exitCode}）" }
                    if (action == "提交记录" || action == "创建 PR") {
                        showResource(action, output.take(4000))
                    }
                    actionMessage = if (result.exitCode == 0) {
                        output.lineSequence().firstOrNull().orEmpty().ifBlank { "$action 完成" }
                    } else {
                        "Git 失败：${output.take(180)}"
                    }
                }
                .onFailure { actionMessage = formatNetworkError(it, "$action 失败") }
        }
    }

    val visibleConversations = conversations
        .filter { summary ->
            when (selectedWorkspace) {
                null, "" -> true
                else -> conversationWorkspaceLabel(summary) == selectedWorkspace ||
                    conversationWorkspacePath(summary) == selectedWorkspace
            }
        }
        .filter { summary ->
            val filter = conversationFilter.trim()
            filter.isEmpty() || summary.id.contains(filter, ignoreCase = true) ||
                summary.title.orEmpty().contains(filter, ignoreCase = true)
        }
        .let { list ->
            when (conversationSort) {
                "workspace" -> list.sortedBy { conversationWorkspaceLabel(it).orEmpty() }
                "created" -> list.sortedBy { it.createdAt.orEmpty() }
                else -> list.sortedByDescending { it.updatedAt.orEmpty() }
            }
        }

    LaunchedEffect(connection.profile.id) {
        pinnedConversationIds = backendRepository.getPinnedConversationIds(connection.profile.id).toSet()
        if (activeConversationId != null) {
            manager.start(connection, activeConversationId.orEmpty())
        }
        loadConversations()
    }

    fun togglePinnedConversation(summary: ConversationSummary) {
        pinnedConversationIds = if (summary.id in pinnedConversationIds) {
            pinnedConversationIds - summary.id
        } else {
            setOf(summary.id) + pinnedConversationIds
        }
        scope.launch {
            backendRepository.savePinnedConversationIds(connection.profile.id, pinnedConversationIds.toList())
        }
    }

    DisposableEffect(manager, context) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        var activeNetwork: android.net.Network? = null
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: android.net.Network) {
                if (activeNetwork == network) {
                    activeNetwork = null
                    manager.networkLost()
                }
            }

            override fun onAvailable(network: android.net.Network) {
                activeNetwork = network
                manager.networkAvailable()
            }
        }
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
        onDispose {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            manager.stop()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .imePadding()
            .background(OpenHandsColors.background),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            val isWideLayout = maxWidth >= 650.dp
            val listPane: @Composable () -> Unit = {
                if (isWideLayout && leftSidebarCollapsed) {
                    CollapsedOpenHandsSidebar(
                        onExpand = { leftSidebarCollapsed = false },
                        onCreate = { beginNewConversation() },
                        onSettings = { showSettingsDialog = true },
                    )
                } else if (isWideLayout) {
                    OpenHandsSidebar(
                        conversations = visibleConversations,
                        workspaceConversations = conversations + savedWorkspaces.map { path -> ConversationSummary(id = "workspace-$path", workingDir = path) },
                        selectedId = activeConversationId,
                        filter = conversationFilter,
                        loading = conversationsLoading,
                        onFilterChange = { conversationFilter = it },
                        logoCollapseEnabled = uiSettings.logoCollapseEnabled,
                        onCreate = { beginNewConversation() },
                        onRefresh = { scope.launch { loadConversations() } },
                        onSelect = { summary ->
                            conversationId = summary.id
                            activeConversationId = summary.id
                            manager.start(connection, summary.id)
                            scope.launch {
                                backendRepository.saveSelectedConversation(connection.profile.id, summary.id)
                            }
                        },
                        onSettings = { showSettingsDialog = true },
                        onExtensions = { openPluginManager() },
                        onBackend = { showBackendManager = true },
                        onManageBackend = { showBackendManager = true },
                        onAddBackend = { showAddBackendDialog = true },
                        onAddWorkspace = { workspaceInput = ""; showAddWorkspaceDialog = true },
                        onManageWorkspace = { showManageWorkspaceDialog = true },
                        selectedWorkspace = selectedWorkspace,
                        onWorkspaceSelected = { selectedWorkspace = it },
                        pinnedConversationIds = pinnedConversationIds,
                        onTogglePin = { togglePinnedConversation(it) },
                        backendLabel = connection.profile.name,
                        onAutomate = { showResource("Automate", "自动化项目由 Agent Server 管理。当前会话可通过消息触发任务；后续版本会接入 Automation Server。") },
                        onSort = { sort ->
                            conversationSort = sort
                            actionMessage = null
                        },
                        onMoreOptions = { showMoreOptionsDialog = true },
                        connectionState = syncState.connectionState,
                        sortMode = conversationSort,
                        onDeleteAll = { showDeleteAllDialog = true },
                        onCollapseSidebar = { leftSidebarCollapsed = true },
                    )
                } else {
                    ConversationList(
                        conversations = visibleConversations,
                        selectedId = activeConversationId,
                        filter = conversationFilter,
                        loading = conversationsLoading,
                        modifier = Modifier.fillMaxWidth(),
                        onFilterChange = { conversationFilter = it },
                        onCreate = { beginNewConversation() },
                        onRefresh = { scope.launch { loadConversations() } },
                        onSelect = { summary ->
                            conversationId = summary.id
                            activeConversationId = summary.id
                            manager.start(connection, summary.id)
                            scope.launch {
                                backendRepository.saveSelectedConversation(connection.profile.id, summary.id)
                            }
                        },
                    )
                }
            }
            val centerPane: @Composable (Modifier) -> Unit = { paneModifier ->
                Column(modifier = paneModifier.fillMaxSize()) {
                    ConversationTopBar(
                        modifier = Modifier
                            .background(OpenHandsColors.surface)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        connection = connection,
                        connectionState = syncState.connectionState,
                        executionStatus = conversations.firstOrNull { it.id == activeConversationId }?.executionStatus,
                        conversationTitle = conversations.firstOrNull { it.id == activeConversationId }?.title,
                        onDisconnect = onDisconnect,
                        onLeftPanel = { leftSidebarCollapsed = !leftSidebarCollapsed },
                        leftPanelOpen = !leftSidebarCollapsed,
                        onWorkspace = {
                            rightPanelCollapsed = false
                            if (!isWideScreen) showInspector = true
                        },
                        showRightPanelButton = !isWideLayout || rightPanelCollapsed,
                        onOverview = { showOverviewCard = true },
                        onGit = { action -> runGitAction(action) },
                        onMenu = { action ->
                            val id = activeConversationId
                            when (action) {
                                "删除对话" -> if (id != null) showDeleteDialog = true
                                "重连" -> manager.reconnect()
                                "暂停" -> id?.let { selected -> scope.launch { httpClient.pauseConversation(connection, selected).onSuccess { actionMessage = "对话已暂停" }.onFailure { actionMessage = formatNetworkError(it, "暂停失败") } } }
                                "继续" -> id?.let { selected -> scope.launch { httpClient.runConversation(connection, selected).onSuccess { actionMessage = "对话已继续" }.onFailure { actionMessage = formatNetworkError(it, "继续失败") } } }
                                "中断" -> id?.let { selected -> scope.launch { httpClient.interruptConversation(connection, selected).onSuccess { actionMessage = "对话已中断" }.onFailure { actionMessage = formatNetworkError(it, "中断失败") } } }
                                "终端" -> openInspectorTab(InspectorTab.TERMINAL)
                                "导出..." -> {
                                    val text = events.joinToString("\n") { event ->
                                        "${event.timestamp.orEmpty()} ${event.type.orEmpty()} ${event.payload}"
                                    }.ifBlank { "当前会话没有可导出的事件。" }
                                    showResource("导出会话", text.take(8000))
                                }
                                "下载对话数据" -> {
                                    val summary = conversations.firstOrNull { it.id == id }
                                    showResource(
                                        "对话数据",
                                        "标题：${summary?.title ?: "未命名"}\n会话：${id ?: "无"}\n工作区：${workspaceWorkingDir ?: "未提供"}\n事件数：${events.size}",
                                    )
                                }
                                "显示用量和成本" -> openInspectorTab(InspectorTab.USAGE)
                                "显示可用技能" -> loadSkills()
                                "显示可用钩子" -> loadHooks()
                                "重命名" -> {
                                    renameTitle = conversations.firstOrNull { it.id == id }?.title.orEmpty()
                                    showRenameDialog = id != null
                                }
                                else -> actionMessage = action
                            }
                        },
                    )
                    ConversationCenterPane(
                    events = events,
                    connection = connection,
                    httpClient = httpClient,
                    connectionState = syncState.connectionState,
                    lastEventTimestamp = syncState.lastEventTimestamp,
                    conversations = conversations,
                    activeConversationId = activeConversationId,
                    actionMessage = actionMessage,
                    sendingMessage = sendingMessage,
                    deletingConversation = deletingConversation,
                    chatBubbleActionsEnabled = uiSettings.chatBubbleActionsEnabled,
                    adaptiveWidthEnabled = uiSettings.adaptiveWidthEnabled,
                    showRecommendedAutomations = uiSettings.showRecommendedAutomations,
                    composerHidingEnabled = uiSettings.composerHidingEnabled,
                    autoHideComposer = uiSettings.autoHideComposer,
                    autoHideDelaySeconds = uiSettings.autoHideDelaySeconds,
                    sendShortcut = uiSettings.sendShortcut,
                    onReconnect = { manager.reconnect() },
                    onDelete = { showDeleteDialog = true },
                    onPause = {
                        activeConversationId?.let { id -> scope.launch {
                            httpClient.pauseConversation(connection, id)
                                .onFailure { actionMessage = formatNetworkError(it, "操作失败") }
                        } }
                    },
                    onResume = {
                        activeConversationId?.let { id -> scope.launch {
                            httpClient.runConversation(connection, id)
                                .onFailure { actionMessage = formatNetworkError(it, "操作失败") }
                        } }
                    },
                    onInterrupt = {
                        activeConversationId?.let { id -> scope.launch {
                            httpClient.interruptConversation(connection, id)
                                .onFailure { actionMessage = formatNetworkError(it, "操作失败") }
                        } }
                    },
                    onOpenWorkspace = { workspaceInput = ""; showAddWorkspaceDialog = true },
                    onPlugins = { if (activeConversationId == null) openPluginPicker() else openPluginManager() },
                    pluginCount = selectedPlugins.size,
                    onShowSkills = { loadSkills() },
                    onShowHooks = { loadHooks() },
                    onAddFiles = { actionMessage = "当前版本请在消息中粘贴文件路径；图片和文件上传将在后续版本提供。" },
                    onManageAgentProfiles = { showSettingsDialog = true },
                    agentProfileOptions = agentProfiles,
                    selectedAgentProfileId = selectedAgentProfileId,
                    onAgentProfileSelected = { id ->
                        selectedAgentProfileId = id
                        scope.launch {
                            httpClient.activateAgentProfile(connection, id)
                                .onSuccess { actionMessage = "已选择代理配置文件" }
                                .onFailure { actionMessage = formatNetworkError(it, "切换代理配置失败") }
                        }
                    },
                    onSend = { text ->
                        if (sendingMessage) return@ConversationCenterPane
                        sendingMessage = true
                        scope.launch {
                            val id = activeConversationId ?: httpClient
                                .createConversationId(
                                    connection,
                                    newConversationWorkspace.takeIf { it.isNotBlank() } ?: selectedWorkspace,
                                    selectedAgentProfileId,
                                    selectedPlugins,
                                )
                                .getOrElse {
                                    actionMessage = formatNetworkError(it, "创建会话失败")
                                    sendingMessage = false
                                    return@launch
                                }
                            if (activeConversationId == null) {
                                conversationId = id
                                activeConversationId = id
                                backendRepository.saveSelectedConversation(connection.profile.id, id)
                                manager.start(connection, id)
                                loadConversations()
                            }
                            httpClient.sendMessage(connection, id, text)
                                .onSuccess { httpClient.runConversation(connection, id) }
                                .onFailure { actionMessage = formatNetworkError(it, "发送失败") }
                            sendingMessage = false
                        }
                    },
                    onBranch = { showBranchDialog = true },
                    inspectorOpen = if (isWideLayout) !rightPanelCollapsed else showInspector,
                    inspectorTab = inspectorSelectedTab,
                    onOpenInspector = { tab, path -> openInspectorTab(tab, path) },
                    onCloseInspector = { closeInspector() },

                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                }
            }
            if (isWideLayout) {
                Row(
                    modifier = Modifier.fillMaxSize().pointerInput(leftSidebarCollapsed, rightPanelCollapsed) {
                        var distance = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { distance = 0f },
                            onHorizontalDrag = { _, dragAmount -> distance += dragAmount },
                            onDragEnd = {
                                if (abs(distance) < 80f) return@detectHorizontalDragGestures
                                if (distance > 0f) {
                                    if (leftSidebarCollapsed) leftSidebarCollapsed = false else rightPanelCollapsed = true
                                } else {
                                    if (!leftSidebarCollapsed) leftSidebarCollapsed = true else rightPanelCollapsed = false
                                }
                            },
                        )
                    },
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    listPane()
                    centerPane(Modifier.weight(1f))
                    if (!rightPanelCollapsed) {
                        WorkspaceInspectorPanel(
                            connection = connection,
                            connectionState = syncState.connectionState,
                            eventCount = events.size,
                            selectedConversationId = activeConversationId,
                            workingDir = workspaceWorkingDir,
                            capabilities = capabilities,
                            events = events,
                            requestedTab = inspectorRequestedTab,
                            requestedFile = inspectorRequestedFile,
                            onRequestedTabConsumed = { inspectorRequestedTab = null },
                            onSelectedTabChange = { inspectorSelectedTab = it },
                            onCollapse = { closeInspector() },
                            modifier = Modifier.width(420.dp),
                        )
                    }
                }
            } else {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.fillMaxSize().pointerInput(leftSidebarCollapsed, showInspector) {
                        var distance = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { distance = 0f },
                            onHorizontalDrag = { _, dragAmount -> distance += dragAmount },
                            onDragEnd = {
                                if (abs(distance) < 80f) return@detectHorizontalDragGestures
                                if (distance > 0f) {
                                    if (leftSidebarCollapsed) leftSidebarCollapsed = false else showInspector = false
                                } else {
                                    if (showInspector) showInspector = false else if (leftSidebarCollapsed) showInspector = true else leftSidebarCollapsed = true
                                }
                            },
                        )
                    },
                ) {
                    centerPane(Modifier.fillMaxSize())
                    if (!leftSidebarCollapsed) {
                        Box(modifier = Modifier.fillMaxSize().clickable { leftSidebarCollapsed = true })
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !leftSidebarCollapsed,
                        modifier = Modifier.align(androidx.compose.ui.Alignment.CenterStart),
                        enter = slideInHorizontally(initialOffsetX = { -it }),
                        exit = slideOutHorizontally(targetOffsetX = { -it }),
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxHeight().fillMaxWidth(0.88f),
                            color = OpenHandsColors.background,
                            shadowElevation = 12.dp,
                        ) {
                            OpenHandsSidebar(
                                conversations = visibleConversations,
                                workspaceConversations = conversations + savedWorkspaces.map { path -> ConversationSummary(id = "workspace-$path", workingDir = path) },
                                selectedId = activeConversationId,
                                filter = conversationFilter,
                                loading = conversationsLoading,
                                onFilterChange = { conversationFilter = it },
                                logoCollapseEnabled = uiSettings.logoCollapseEnabled,
                                onSelect = { summary ->
                                    conversationId = summary.id
                                    activeConversationId = summary.id
                                    leftSidebarCollapsed = true
                                    manager.start(connection, summary.id)
                                    scope.launch {
                                        backendRepository.saveSelectedConversation(connection.profile.id, summary.id)
                                    }
                                },
                                onCreate = { beginNewConversation() },
                                onRefresh = { scope.launch { loadConversations() } },
                                onSettings = { showSettingsDialog = true },
                                onExtensions = { openPluginManager() },
                                onBackend = { showBackendManager = true },
                                onManageBackend = { showBackendManager = true },
                                onAddBackend = { showAddBackendDialog = true },
                                onAddWorkspace = { workspaceInput = ""; showAddWorkspaceDialog = true },
                                onManageWorkspace = { showManageWorkspaceDialog = true },
                                onSort = { sort ->
                                    conversationSort = sort
                                    actionMessage = null
                                },
                                onMoreOptions = { showMoreOptionsDialog = true },
                                selectedWorkspace = selectedWorkspace,
                                onWorkspaceSelected = { selectedWorkspace = it },
                                pinnedConversationIds = pinnedConversationIds,
                                onTogglePin = { togglePinnedConversation(it) },
                                backendLabel = connection.profile.name,
                                onAutomate = { showResource("Automate", "自动化项目由 Agent Server 管理。当前会话可通过消息触发任务；后续版本会接入 Automation Server。") },
                                connectionState = syncState.connectionState,
                                sortMode = conversationSort,
                                onDeleteAll = { showDeleteAllDialog = true },
                                onCollapseSidebar = { leftSidebarCollapsed = true },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showOverviewCard) {
        val overviewSummary = conversations.firstOrNull { it.id == activeConversationId }
        Box(modifier = Modifier.fillMaxSize().clickable { showOverviewCard = false }) {
            Surface(
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.TopCenter)
                    .padding(top = 58.dp, start = if (isWideScreen) 180.dp else 12.dp, end = 12.dp)
                    .width(320.dp),
                color = OpenHandsColors.surface,
                shape = RoundedCornerShape(10.dp),
                tonalElevation = 8.dp,
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("会话详情", color = OpenHandsColors.text, modifier = Modifier.weight(1f))
                        OhIconButton("close", "关闭概览", { showOverviewCard = false }, modifier = Modifier.size(28.dp))
                    }
                    HorizontalDivider(color = OpenHandsColors.border)
                    OverviewRow("标题", overviewSummary?.title?.takeIf { it.isNotBlank() } ?: "新建对话")
                    OverviewRow("状态", overviewSummary?.executionStatus ?: if (activeConversationId == null) "未选择会话" else "未知状态")
                    OverviewRow("后端", connection.profile.baseUrl)
                    OverviewRow("工作区", workspaceWorkingDir ?: "未提供")
                    OverviewRow("事件", events.size.toString())
                    OverviewRow("连接", syncState.connectionState.name)
                    if (!actionMessage.isNullOrBlank()) {
                        OverviewRow("提示", actionMessage.orEmpty())
                    }
                }
            }
        }
    }


    if (showInspector && !isWideScreen) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.CenterEnd,
        ) {
            Box(modifier = Modifier.fillMaxSize().clickable { showInspector = false })
            androidx.compose.animation.AnimatedVisibility(
                visible = showInspector,
                enter = slideInHorizontally(initialOffsetX = { it }),
                exit = slideOutHorizontally(targetOffsetX = { it }),
            ) {
                Surface(
                    modifier = Modifier.fillMaxHeight().fillMaxWidth(0.92f),
                    color = OpenHandsColors.background,
                    shadowElevation = 12.dp,
                ) {
                    WorkspaceInspectorPanel(
                        connection = connection,
                        connectionState = syncState.connectionState,
                        eventCount = events.size,
                        selectedConversationId = activeConversationId,
                        workingDir = workspaceWorkingDir,
                        capabilities = capabilities,
                        events = events,
                        requestedTab = inspectorRequestedTab,
                        requestedFile = inspectorRequestedFile,
                        onRequestedTabConsumed = { inspectorRequestedTab = null },
                        onSelectedTabChange = { inspectorSelectedTab = it },
                        onCollapse = { closeInspector() },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    if (showAddWorkspaceDialog) {
        AlertDialog(
            onDismissRequest = { showAddWorkspaceDialog = false },
            title = { Text("添加工作区") },
            text = {
                OutlinedTextField(
                    value = workspaceInput,
                    onValueChange = { workspaceInput = it },
                    label = { Text("后端绝对路径") },
                    placeholder = { Text("/workspace/project") },
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val path = workspaceInput.trim()
                        if (path.startsWith("/")) {
                            savedWorkspaces = (savedWorkspaces + path).distinct()
                            selectedWorkspace = workspaceDisplayName(path)
                            showAddWorkspaceDialog = false
                        }
                    },
                    enabled = workspaceInput.trim().startsWith("/"),
                ) { Text("添加") }
            },
            dismissButton = { TextButton(onClick = { showAddWorkspaceDialog = false }) { Text("取消") } },
        )
    }

    if (showManageWorkspaceDialog) {
        AlertDialog(
            onDismissRequest = { showManageWorkspaceDialog = false },
            title = { Text("管理工作区") },
            text = {
                if (savedWorkspaces.isEmpty()) {
                    Text("暂无已保存的工作区路径。")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        savedWorkspaces.forEach { path ->
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(workspaceDisplayName(path), color = OpenHandsColors.text)
                                    Text(path, color = OpenHandsColors.textMuted, fontSize = 12.sp)
                                }
                                TextButton(onClick = {
                                    savedWorkspaces = savedWorkspaces - path
                                    if (selectedWorkspace == workspaceDisplayName(path)) selectedWorkspace = null
                                }) { Text("删除") }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showManageWorkspaceDialog = false }) { Text("完成") } },
        )
    }


    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { if (!creatingConversation) showCreateDialog = false },
            title = { Text("新建会话") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (creatingConversation) "正在创建…" else "创建一个新的空闲会话")
                    actionMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                    OutlinedTextField(
                        value = newConversationWorkspace,
                        onValueChange = { newConversationWorkspace = it },
                        label = { Text("工作目录") },
                        singleLine = true,
                        enabled = !creatingConversation,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (creatingConversation) return@Button
                        if (!newConversationWorkspace.trim().startsWith("/")) {
                            actionMessage = "请输入后端上的绝对 workspace 路径"
                            return@Button
                        }
                        creatingConversation = true
                        scope.launch {
                            val result = httpClient.createConversationId(connection, newConversationWorkspace)
                            val id = result.getOrNull()
                            if (id == null) {
                                actionMessage = result.exceptionOrNull()?.message ?: "创建会话失败"
                            } else {
                                conversationId = id
                                activeConversationId = id
                                backendRepository.saveSelectedConversation(connection.profile.id, id)
                                showCreateDialog = false
                                manager.start(connection, id)
                                loadConversations()
                            }
                            creatingConversation = false
                        }
                    },
                    enabled = !creatingConversation && newConversationWorkspace.trim().startsWith("/"),
                ) { Text("创建") }
            },
            dismissButton = {
                Button(
                    onClick = { showCreateDialog = false },
                    enabled = !creatingConversation,
                ) { Text("取消") }
            },
        )
    }

    if (showRenameDialog && activeConversationId != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("重命名会话") },
            text = { OutlinedTextField(renameTitle, { renameTitle = it }, label = { Text("标题") }, singleLine = true) },
            confirmButton = {
                Button(onClick = {
                    val id = activeConversationId ?: return@Button
                    scope.launch {
                        httpClient.updateConversationTitle(connection, id, renameTitle)
                            .onSuccess {
                                conversations = conversations.map { item -> if (item.id == id) item.copy(title = renameTitle) else item }
                                actionMessage = "会话标题已更新"
                            }
                            .onFailure { actionMessage = formatNetworkError(it, "重命名失败") }
                    }
                    showRenameDialog = false
                }, enabled = renameTitle.isNotBlank()) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("取消") } },
        )
    }

    if (showDeleteDialog && activeConversationId != null) {
        AlertDialog(
            onDismissRequest = { if (!deletingConversation) showDeleteDialog = false },
            title = { Text("删除会话？") },
            text = {
                Text("此操作会从 Agent Server 删除当前会话，且无法撤销。")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val id = activeConversationId ?: return@Button
                        if (deletingConversation) return@Button
                        deletingConversation = true
                        scope.launch {
                            httpClient.deleteConversation(connection, id)
                                .onSuccess {
                                    manager.stop()
                                    activeConversationId = null
                                    conversationId = ""
                                    backendRepository.saveSelectedConversation(
                                        connection.profile.id,
                                        "",
                                    )
                                    showDeleteDialog = false
                                    loadConversations()
                                }
                                .onFailure { actionMessage = formatNetworkError(it, "删除会话失败") }
                            deletingConversation = false
                        }
                    },
                    enabled = !deletingConversation,
                ) { Text(if (deletingConversation) "删除中" else "确认删除") }
            },
            dismissButton = {
                Button(
                    onClick = { showDeleteDialog = false },
                    enabled = !deletingConversation,
                ) { Text("取消") }
            },
        )
    }

    if (showResourceDialog) {
        AlertDialog(
            onDismissRequest = { showResourceDialog = false },
            title = { Text(resourceDialogTitle) },
            text = {
                Text(
                    resourceDialogBody,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = { TextButton(onClick = { showResourceDialog = false }) { Text("关闭") } },
        )
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { if (!deletingConversation) showDeleteAllDialog = false },
            title = { Text("删除全部会话？") },
            text = { Text("此操作会从 Agent Server 删除当前列表中的全部会话，且无法撤销。") },
            confirmButton = {
                Button(
                    onClick = {
                        if (deletingConversation) return@Button
                        deletingConversation = true
                        scope.launch {
                            val ids = conversations.map { it.id }
                            var failed = 0
                            ids.forEach { id ->
                                httpClient.deleteConversation(connection, id).onFailure { failed++ }
                            }
                            manager.stop()
                            activeConversationId = null
                            conversationId = ""
                            backendRepository.saveSelectedConversation(connection.profile.id, "")
                            showDeleteAllDialog = false
                            loadConversations()
                            actionMessage = if (failed == 0) "已删除 ${ids.size} 个会话" else "已尝试删除，失败 $failed 个"
                            deletingConversation = false
                        }
                    },
                    enabled = !deletingConversation && conversations.isNotEmpty(),
                ) { Text(if (deletingConversation) "删除中" else "确认全部删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }, enabled = !deletingConversation) { Text("取消") }
            },
        )
    }

    if (showBranchDialog && activeConversationId != null) {
        BranchManagerDialog(
            connection = connection,
            httpClient = httpClient,
            workingDir = workspaceWorkingDir,
            onDismiss = { showBranchDialog = false },
            onChanged = { actionMessage = it },
        )
    }

    if (showBackendManager) {
        Dialog(onDismissRequest = { showBackendManager = false }) {
            Surface(color = OpenHandsColors.surface, shape = RoundedCornerShape(14.dp), tonalElevation = 8.dp, modifier = Modifier.fillMaxWidth(0.96f)) {
                Column(modifier = Modifier.padding(18.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("管理后端", color = OpenHandsColors.text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = { showBackendManager = false }) { Text("完成") }
                    }
                    savedBackends.forEach { saved ->
                        Surface(color = OpenHandsColors.surfaceVariant, shape = RoundedCornerShape(10.dp), border = androidx.compose.foundation.BorderStroke(1.dp, OpenHandsColors.border)) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    OhStatusDot(if (saved.profile.id == connection.profile.id) OpenHandsColors.success else OpenHandsColors.textMuted)
                                    Text(saved.profile.name, color = OpenHandsColors.text, modifier = Modifier.weight(1f).padding(start = 8.dp))
                                    Text(if (saved.profile.id == connection.profile.id) "已连接" else "可用", color = if (saved.profile.id == connection.profile.id) OpenHandsColors.success else OpenHandsColors.textMuted, fontSize = 12.sp)
                                }
                                Text(saved.profile.baseUrl, color = OpenHandsColors.textSecondary, fontSize = 12.sp)
                                if (saved.profile.note.isNotBlank()) Text(saved.profile.note, color = OpenHandsColors.textMuted, fontSize = 12.sp)
                                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                    TextButton(onClick = { onConnectBackend(saved.profile.baseUrl, saved.apiKey.orEmpty()); showBackendManager = false }) { Text("切换") }
                                    TextButton(onClick = { editingBackend = saved; editBackendName = saved.profile.name; editBackendUrl = saved.profile.baseUrl; editBackendNote = saved.profile.note; editBackendKey = saved.apiKey.orEmpty(); showEditBackendDialog = true }) { Text("编辑") }
                                    TextButton(onClick = { onDeleteBackend(saved.profile.id) }) { Text("删除") }
                                }
                            }
                        }
                    }
                    if (savedBackends.isEmpty()) Text("暂无保存的后端", color = OpenHandsColors.textMuted)
                    Button(onClick = { showAddBackendDialog = true; showBackendManager = false }, modifier = Modifier.fillMaxWidth()) { Text("添加后端") }
                }
            }
        }
    }

    if (showEditBackendDialog && editingBackend != null) {
        AlertDialog(
            onDismissRequest = { showEditBackendDialog = false },
            title = { Text("编辑后端") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(editBackendName, { editBackendName = it }, label = { Text("名称") }, singleLine = true)
                    OutlinedTextField(editBackendUrl, { editBackendUrl = it }, label = { Text("Agent Server 地址") }, singleLine = true)
                    OutlinedTextField(editBackendNote, { editBackendNote = it }, label = { Text("备注") }, minLines = 2)
                    OutlinedTextField(editBackendKey, { editBackendKey = it }, label = { Text("Session API Key（留空保持不变）") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val existing = editingBackend ?: return@Button
                    onUpdateBackend(existing.profile.id, editBackendName, editBackendUrl, editBackendNote, editBackendKey.ifBlank { existing.apiKey })
                    showEditBackendDialog = false
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showEditBackendDialog = false }) { Text("取消") } },
        )
    }

    if (showAddBackendDialog) {
        AlertDialog(
            onDismissRequest = { showAddBackendDialog = false },
            title = { Text("添加后端") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = backendUrlInput, onValueChange = { backendUrlInput = it }, label = { Text("Agent Server 地址") }, placeholder = { Text("http://192.168.1.10:8000") }, singleLine = true)
                    OutlinedTextField(value = backendApiKeyInput, onValueChange = { backendApiKeyInput = it }, label = { Text("Session API Key（可选）") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = { onConnectBackend(backendUrlInput, backendApiKeyInput); backendUrlInput = ""; backendApiKeyInput = ""; showAddBackendDialog = false }) { Text("连接并保存") }
            },
            dismissButton = { TextButton(onClick = { showAddBackendDialog = false }) { Text("取消") } },
        )
    }


    if (showPluginsDialog) {
        PluginsDialog(
            connection = connection,
            httpClient = httpClient,
            onDismiss = { showPluginsDialog = false },
            onOpenSettings = {
                showPluginsDialog = false
                showSettingsDialog = true
            },
            pickerMode = pluginPickerMode,
            selectedPlugins = selectedPlugins,
            onSelectionChange = { selectedPlugins = it },
        )
    }

    if (showMoreOptionsDialog) {
        MoreOptionsDialog(
            showArchived = showArchivedConversations,
            onShowArchivedChange = { showArchivedConversations = it },
            hideOld = hideOldConversations,
            onHideOldChange = { hideOldConversations = it },
            oldWeeks = oldConversationWeeks,
            onOldWeeksChange = { oldConversationWeeks = it },
            automationVisibility = automationVisibility,
            onAutomationVisibilityChange = { automationVisibility = it },
            showRepository = showMetadataRepository,
            onShowRepositoryChange = { showMetadataRepository = it },
            showAgent = showMetadataAgent,
            onShowAgentChange = { showMetadataAgent = it },
            showLabels = showMetadataLabels,
            onShowLabelsChange = { showMetadataLabels = it },
            showDetails = showMetadataDetails,
            onShowDetailsChange = { showMetadataDetails = it },
            onDismiss = { showMoreOptionsDialog = false },
        )
    }

    if (showSkillsDialog) {
        SkillsDialog(
            connection = connection,
            httpClient = httpClient,
            projectDir = workspaceWorkingDir ?: selectedWorkspace,
            onDismiss = { showSkillsDialog = false },
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            connection = connection,
            httpClient = httpClient,
            startupPage = startupPage,
            onStartupPageChange = onStartupPageChange,
            logoCollapseEnabled = uiSettings.logoCollapseEnabled,
            onLogoCollapseEnabledChange = { value -> updateUiSettings { it.copy(logoCollapseEnabled = value) } },
            adaptiveWidthEnabled = uiSettings.adaptiveWidthEnabled,
            onAdaptiveWidthEnabledChange = { value -> updateUiSettings { it.copy(adaptiveWidthEnabled = value) } },
            sendShortcut = uiSettings.sendShortcut,
            onSendShortcutChange = { value -> updateUiSettings { it.copy(sendShortcut = value) } },
            showRecommendedAutomations = uiSettings.showRecommendedAutomations,
            onShowRecommendedAutomationsChange = { value -> updateUiSettings { it.copy(showRecommendedAutomations = value) } },
            composerHidingEnabled = uiSettings.composerHidingEnabled,
            onComposerHidingEnabledChange = { value -> updateUiSettings { it.copy(composerHidingEnabled = value) } },
            autoHideComposer = uiSettings.autoHideComposer,
            onAutoHideComposerChange = { value -> updateUiSettings { it.copy(autoHideComposer = value) } },
            autoHideDelaySeconds = uiSettings.autoHideDelaySeconds,
            onAutoHideDelaySecondsChange = { value -> updateUiSettings { it.copy(autoHideDelaySeconds = value) } },
            chatBubbleActionsEnabled = uiSettings.chatBubbleActionsEnabled,
            onChatBubbleActionsEnabledChange = { value -> updateUiSettings { it.copy(chatBubbleActionsEnabled = value) } },
            onManageBackends = { showSettingsDialog = false; showBackendManager = true },
            onDisconnect = { showSettingsDialog = false; onDisconnect() },
            onDismiss = { showSettingsDialog = false },
        )
    }
}
