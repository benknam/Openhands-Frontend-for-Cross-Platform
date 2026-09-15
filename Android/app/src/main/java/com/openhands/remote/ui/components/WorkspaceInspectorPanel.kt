package com.openhands.remote.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openhands.remote.R
import com.openhands.remote.ui.theme.OpenHandsColors
import com.openhands.remote.core.model.AgentEventEnvelope
import com.openhands.remote.core.model.BackendCapabilities
import com.openhands.remote.core.model.BackendConnection
import com.openhands.remote.core.model.ConnectionState
import com.openhands.remote.core.network.AgentServerHttpClient
import com.openhands.remote.core.network.BashCommandRunner
import com.openhands.remote.core.network.OkHttpBashCommandRunner
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

enum class InspectorTab(val label: String, val icon: String) {
    FILES("文件", "folder"),
    COMMITS("变更", "changes"),
    PLANNER("规划", "plan"),
    TERMINAL("终端", "terminal"),
    BROWSER("浏览", "browse"),
    USAGE("用量", "usage"),
    OVERVIEW("概览", "info"),
}

@Composable
fun WorkspaceInspectorPanel(
    connection: BackendConnection,
    connectionState: ConnectionState,
    eventCount: Int,
    selectedConversationId: String?,
    workingDir: String? = null,
    capabilities: BackendCapabilities? = null,
    events: List<AgentEventEnvelope> = emptyList(),
    requestedTab: InspectorTab? = null,
    requestedFile: String? = null,
    onRequestedTabConsumed: () -> Unit = {},
    onSelectedTabChange: (InspectorTab) -> Unit = {},
    onCollapse: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableStateOf(InspectorTab.FILES) }
    LaunchedEffect(requestedTab) {
        requestedTab?.let {
            selectedTab = it
            onSelectedTabChange(it)
            onRequestedTabConsumed()
        }
    }
    val filePaths = remember(events) {
        events.flatMap { it.payload.collectStrings(setOf("path", "file_path", "filename")) }
            .filter { it.contains("/") || it.contains("\\") }
            .distinct()
            .take(MAX_INSPECTOR_ITEMS)
    }
    val browserValues = remember(events) {
        events.flatMap { it.payload.collectStrings(setOf("url", "current_url", "screenshot", "screenshot_base64")) }
            .distinct().take(MAX_INSPECTOR_ITEMS)
    }
    val httpClient = remember { AgentServerHttpClient() }
    val bashRunner = remember { OkHttpBashCommandRunner() }
    var files by remember { mutableStateOf<List<String>>(emptyList()) }
    var filesLoading by remember { mutableStateOf(false) }
    var filesError by remember { mutableStateOf<String?>(null) }
    var selectedFile by remember { mutableStateOf<String?>(null) }
    var selectedFileText by remember { mutableStateOf<String?>(null) }
    var selectedFileImage by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var fileLoading by remember { mutableStateOf(false) }
    var refreshToken by remember { mutableStateOf(0) }
    LaunchedEffect(requestedFile) {
        requestedFile?.takeIf { it.isNotBlank() }?.let { selectedFile = it }
    }

    LaunchedEffect(connection.profile.id, selectedConversationId, workingDir, refreshToken) {
        val root = workingDir?.takeIf { it.isNotBlank() }
        if (selectedConversationId == null || root == null) {
            files = emptyList()
            filesError = if (selectedConversationId == null) null else "后端未提供 workspace 根目录"
            return@LaunchedEffect
        }
        filesLoading = true
        filesError = null
        httpClient.listWorkspaceFiles(connection, root)
            .onSuccess { files = it }
            .onFailure { filesError = it.message ?: "文件列表加载失败" }
        filesLoading = false
    }

    LaunchedEffect(connection.profile.id, selectedConversationId, selectedFile, workingDir) {
        val relativePath = selectedFile ?: return@LaunchedEffect
        val root = workingDir?.takeIf { it.isNotBlank() } ?: run {
            selectedFileText = "后端未提供 workspace 根目录"
            return@LaunchedEffect
        }
        fileLoading = true
        selectedFileText = null
        selectedFileImage = null
        val absolutePath = if (relativePath.startsWith("/")) {
            relativePath
        } else {
            "${root.trimEnd('/')}/${relativePath.trimStart('/')}"
        }
        httpClient.downloadFile(connection, absolutePath).onSuccess { bytes ->
            if (isImagePath(relativePath)) {
                selectedFileImage = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } else {
                selectedFileText = bytes.toString(Charsets.UTF_8).take(MAX_FILE_CONTENT_CHARS)
            }
        }.onFailure { selectedFileText = "读取失败：${it.message ?: "未知错误"}" }
        fileLoading = false
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(onCollapse) {
                var distance = 0f
                detectHorizontalDragGestures(
                    onDragStart = { distance = 0f },
                    onHorizontalDrag = { _, amount -> distance += amount },
                    onDragEnd = { if (distance > 80f) onCollapse?.invoke() },
                )
            },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            InspectorTab.entries.filterNot { it == InspectorTab.OVERVIEW }.forEach { tab ->
                TextButton(
                    onClick = {
                        selectedTab = tab
                        onSelectedTabChange(tab)
                    },
                    modifier = Modifier.weight(1f).semantics { contentDescription = "工作区页签：${tab.label}" },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp, vertical = 5.dp),
                ) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OhInlineIcon(tab.icon, tab.label)
                        Text(
                            text = tab.label,
                            color = if (selectedTab == tab) OpenHandsColors.text else OpenHandsColors.textMuted,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                        )
                    }
                }
            }
            onCollapse?.let { collapse ->
                OhIconButton("drawer", "收拢右侧面板", collapse, modifier = Modifier.size(38.dp))
            }
        }
        HorizontalDivider(color = OpenHandsColors.border)
        if (selectedTab == InspectorTab.TERMINAL) {
            TerminalContent(
                connection = connection,
                bashRunner = bashRunner,
                workingDir = workingDir,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
            )
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (selectedTab) {
                    InspectorTab.FILES -> FilesContent(
                        files = files.ifEmpty { filePaths },
                        loading = filesLoading,
                        error = filesError,
                        selectedFile = selectedFile,
                        selectedFileText = selectedFileText,
                        selectedFileImage = selectedFileImage,
                        fileLoading = fileLoading,
                        onRefresh = { refreshToken++ },
                        onSelect = { selectedFile = it },
                    )
                    InspectorTab.COMMITS -> CommitsContent(events)
                    InspectorTab.PLANNER -> PlannerContent(events)
                    InspectorTab.BROWSER -> BrowserContent(browserValues)
                    InspectorTab.USAGE -> UsageContent(events)
                    InspectorTab.OVERVIEW -> OverviewContent(
                        connection = connection,
                        connectionState = connectionState,
                        eventCount = eventCount,
                        selectedConversationId = selectedConversationId,
                        capabilities = capabilities,
                    )
                    InspectorTab.TERMINAL -> Unit
                }
            }
        }
    }
}

@Composable
private fun OverviewContent(
    connection: BackendConnection,
    connectionState: ConnectionState,
    eventCount: Int,
    selectedConversationId: String?,
    capabilities: BackendCapabilities?,
) {
    Text("会话概览", style = MaterialTheme.typography.titleSmall)
    InspectorRow("后端", connection.profile.name)
    InspectorRow("地址", connection.profile.baseUrl)
    InspectorRow("状态", connectionState.label())
    InspectorRow("事件", eventCount.toString())
    InspectorRow("会话", selectedConversationId ?: "未选择")
    capabilities?.let {
        InspectorRow("版本", it.serverVersion ?: "未知")
        InspectorRow("能力", "profiles=${it.profiles}, files=${it.fileDownload}, bash=${it.bashWebSocket}")
        InspectorRow("workspace", it.workingDir ?: "未提供")
    } ?: InspectorRow("能力", "未探测")
    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
    Text("原版 OpenHands 面板", style = MaterialTheme.typography.titleSmall)
    Text(
        "右侧面板对应原版 Overview、Files、Browser、Terminal 区域；当前可用数据优先来自 Agent Server 实时事件。",
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun CommitsContent(events: List<AgentEventEnvelope>) {
    Text("变更", color = OpenHandsColors.text, fontSize = 16.sp)
    val changes = events.flatMap { it.payload.collectStrings(setOf("diff", "patch", "changed_files")) }.distinct()
    if (changes.isEmpty()) Text("暂无变更记录", color = OpenHandsColors.textMuted, fontSize = 13.sp)
    changes.forEach { Text(it, color = OpenHandsColors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(vertical = 4.dp)) }
}

@Composable
private fun PlannerContent(events: List<AgentEventEnvelope>) {
    Text("规划", color = OpenHandsColors.text, fontSize = 16.sp)
    Text("计划内容会随着 Agent 的事件流实时更新。", color = OpenHandsColors.textMuted, fontSize = 13.sp)
    events.filter { it.type?.contains("plan", ignoreCase = true) == true }.takeLast(20).forEachIndexed { index, event ->
        Text("${index + 1}. ${event.payload.collectStrings(setOf("message", "content", "title")).firstOrNull() ?: event.type}", color = OpenHandsColors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(vertical = 4.dp))
    }
}

@Composable
private fun UsageContent(events: List<AgentEventEnvelope>) {
    Text("用量", color = OpenHandsColors.text, fontSize = 16.sp)
    InspectorRow("事件数", events.size.toString())
    val tokenValues = events.flatMap { it.payload.collectStrings(setOf("tokens", "token_count", "cost")) }
    InspectorRow("Token / 成本", tokenValues.lastOrNull() ?: "后端未提供")
    Text("具体用量取决于 Agent Server 的事件协议。", color = OpenHandsColors.textMuted, fontSize = 12.sp)
}

@Composable
private fun FilesContent(
    files: List<String>,
    loading: Boolean,
    error: String?,
    selectedFile: String?,
    selectedFileText: String?,
    selectedFileImage: android.graphics.Bitmap?,
    fileLoading: Boolean,
    onRefresh: () -> Unit,
    onSelect: (String) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth().heightIn(min = 420.dp), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
        Column(modifier = Modifier.weight(0.38f).padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("文件树", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                OhIconButton("refresh", "刷新文件树", onRefresh, modifier = Modifier.size(32.dp))
            }
            if (loading) Text("正在读取…", style = MaterialTheme.typography.bodySmall)
            error?.let { Text("文件列表：$it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            if (files.isEmpty() && !loading) Text("尚未发现工作区文件。", style = MaterialTheme.typography.bodyMedium)
            files.sorted().forEach { path ->
                val depth = path.count { it == '/' || it == '\\' }.coerceAtMost(8)
                TextButton(
                    onClick = { onSelect(path) },
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "打开文件：$path" },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    Text(text = "${"  ".repeat(depth)}${if (path == selectedFile) "▸ " else ""}${path.substringAfterLast('/')}", maxLines = 1, color = if (path == selectedFile) OpenHandsColors.text else OpenHandsColors.textSecondary)
                }
            }
        }
        Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(OpenHandsColors.border))
        Column(modifier = Modifier.weight(0.62f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(selectedFile ?: "预览", style = MaterialTheme.typography.titleSmall, color = OpenHandsColors.text)
            when {
                selectedFile == null -> Text("选择左侧文件查看预览。", color = OpenHandsColors.textMuted)
                fileLoading -> Text("正在读取文件…")
                selectedFileImage != null -> Image(bitmap = selectedFileImage.asImageBitmap(), contentDescription = "文件图片：$selectedFile", modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                selectedFileText != null -> Text(selectedFileText, style = MaterialTheme.typography.bodySmall, color = OpenHandsColors.textSecondary)
                else -> Text("文件内容为空。", color = OpenHandsColors.textMuted)
            }
        }
    }
}

@Composable
private fun BrowserContent(values: List<String>) {
    val snapshot = values.firstOrNull(::looksLikeBase64Image)?.let(::decodeBase64Image)
    Text("浏览", style = MaterialTheme.typography.titleSmall)
    val urls = values.filterNot(::looksLikeBase64Image)
    if (urls.isEmpty() && snapshot == null) {
        Text("尚未收到 Browser URL 或快照事件。")
    } else {
        urls.forEach { Text(it.take(MAX_INSPECTOR_VALUE_CHARS), style = MaterialTheme.typography.bodySmall) }
        snapshot?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "浏览器页面快照",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth,
            )
        }
    }
}

@Composable
private fun TerminalContent(
    connection: BackendConnection,
    bashRunner: BashCommandRunner,
    workingDir: String?,
    modifier: Modifier = Modifier,
) {
    var command by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var connectionMessage by remember { mutableStateOf("正在连接 Bash WebSocket…") }
    val scope = rememberCoroutineScope()
    val logScroll = rememberScrollState()
    val logHorizontalScroll = rememberScrollState()

    DisposableEffect(connection.profile.id, bashRunner) {
        bashRunner.connect(connection, object : BashCommandRunner.Listener {
            override fun onConnecting() { connectionMessage = "正在连接 Bash WebSocket…" }
            override fun onConnected() { connectionMessage = "Bash WebSocket 已连接" }
            override fun onOutput(command: String, stdout: String, stderr: String) {
                val chunk = buildString {
                    append(stdout)
                    if (stderr.isNotEmpty()) {
                        if (isNotEmpty() && !endsWith("\n")) append('\n')
                        append(stderr)
                    }
                }
                if (chunk.isEmpty()) return
                scope.launch {
                    output = (output + chunk).takeLast(MAX_TERMINAL_OUTPUT_CHARS)
                }
            }
            override fun onCompleted(result: com.openhands.remote.core.model.BashCommandResult) {
                scope.launch {
                    running = false
                    if (output.isBlank()) {
                        output = "[命令执行完毕，没有输出]"
                    }
                }
            }
            override fun onFailure(message: String) {
                scope.launch {
                    connectionMessage = message
                    output = message
                    running = false
                }
            }
            override fun onClosed(code: Int, reason: String) {
                connectionMessage = "Bash WebSocket 已关闭，正在重连…"
            }
        })
        onDispose { bashRunner.close() }
    }

    LaunchedEffect(output) {
        if (output.isNotBlank()) {
            logScroll.animateScrollTo(logScroll.maxValue)
        }
    }

    fun runCommand() {
        val requested = command.trim()
        val root = workingDir
        if (requested.isEmpty() || running || root == null) return
        running = true
        output = ""
        bashRunner.execute(requested, root)
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("终端", style = MaterialTheme.typography.titleSmall)
        Text(connectionMessage, style = MaterialTheme.typography.bodySmall, color = OpenHandsColors.textMuted)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "终端命令输入" },
                label = { Text("执行命令") },
                singleLine = true,
                enabled = !running && workingDir != null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { runCommand() }),
            )
            Button(
                onClick = { runCommand() },
                enabled = command.isNotBlank() && !running && workingDir != null,
            ) { Text(if (running) "执行中…" else "执行") }
        }
        if (workingDir == null) {
            Text("后端未提供 workspace 根目录，终端已禁用。", color = OpenHandsColors.textMuted)
        }
        Text("终端日志", style = MaterialTheme.typography.labelMedium, color = OpenHandsColors.textSecondary)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(OpenHandsColors.surfaceCard)
                .border(1.dp, OpenHandsColors.border, RoundedCornerShape(10.dp))
                .semantics { contentDescription = "终端日志窗口" },
        ) {
            if (output.isBlank() && !running) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.oh_terminal),
                        contentDescription = null,
                        tint = OpenHandsColors.textMuted,
                        modifier = Modifier.size(36.dp),
                    )
                    Text(
                        TERMINAL_EMPTY_OUTPUT_MESSAGE,
                        color = OpenHandsColors.textMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            } else {
                SelectionContainer {
                    Text(
                        text = if (output.isBlank()) "正在执行…" else output,
                        color = OpenHandsColors.text,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(logScroll)
                            .horizontalScroll(logHorizontalScroll)
                            .padding(12.dp),
                    )
                }
            }
        }
    }
}

private fun looksLikeBase64Image(value: String): Boolean =
    value.startsWith("data:image/", ignoreCase = true) ||
        (value.length > 256 && value.matches(Regex("[A-Za-z0-9+/=\\\\r\\\\n]+")))

private fun decodeBase64Image(value: String): android.graphics.Bitmap? = runCatching {
    val encoded = value.substringAfter(",", value).replace(Regex("\\\\s"), "")
    Base64.decode(encoded, Base64.DEFAULT).let { bytes ->
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
}.getOrNull()

private fun isImagePath(path: String): Boolean =
    path.substringAfterLast('.', "").lowercase() in setOf("png", "jpg", "jpeg", "gif", "webp", "bmp")

private const val MAX_FILE_CONTENT_CHARS = 120_000
private const val MAX_TERMINAL_OUTPUT_CHARS = 24_000
internal const val TERMINAL_EMPTY_OUTPUT_MESSAGE = "尚无终端输出。智能体运行的命令将显示在此处。"


@Composable
private fun InspectorRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun InspectorValues(title: String, values: List<String>, empty: String) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    if (values.isEmpty()) {
        Text(empty, style = MaterialTheme.typography.bodyMedium)
    } else {
        values.forEach { value ->
            Text(
                text = value.take(MAX_INSPECTOR_VALUE_CHARS),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(8.dp),
            )
        }
    }
}

private fun JsonObject.collectStrings(keys: Set<String>): List<String> =
    entries.flatMap { (key, value) ->
        val own = if (key.lowercase() in keys) {
            (value as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }?.let(::listOf).orEmpty()
        } else emptyList()
        own + when (value) {
            is JsonObject -> value.collectStrings(keys)
            is JsonArray -> value.flatMap { child ->
                when (child) {
                    is JsonObject -> child.collectStrings(keys)
                    else -> emptyList()
                }
            }
            else -> emptyList()
        }
    }

private const val MAX_INSPECTOR_ITEMS = 100
private const val MAX_INSPECTOR_VALUE_CHARS = 4_000

private fun ConnectionState.label(): String = when (this) {
    ConnectionState.CONNECTED -> "已连接"
    ConnectionState.CONNECTING -> "连接中"
    ConnectionState.SYNCING -> "同步中"
    ConnectionState.DISCONNECTED -> "已断开"
    ConnectionState.FAILED -> "失败"
    ConnectionState.IDLE -> "空闲"
    else -> name
}
