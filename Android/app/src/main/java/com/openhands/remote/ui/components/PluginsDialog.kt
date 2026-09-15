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
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openhands.remote.core.model.BackendConnection
import com.openhands.remote.core.model.PluginSpec
import com.openhands.remote.core.network.AgentServerHttpClient
import com.openhands.remote.core.network.formatNetworkError
import com.openhands.remote.ui.theme.OpenHandsColors
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun PluginsDialog(
    connection: BackendConnection,
    httpClient: AgentServerHttpClient,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    pickerMode: Boolean = false,
    selectedPlugins: List<PluginSpec> = emptyList(),
    onSelectionChange: (List<PluginSpec>) -> Unit = {},
) {
    if (pickerMode) {
        PluginPickerDialog(connection, httpClient, selectedPlugins, onSelectionChange, onDismiss)
        return
    }
    var loading by remember { mutableStateOf(true) }
    var notice by remember { mutableStateOf<String?>(null) }
    var installed by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var local by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var marketplace by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var source by remember { mutableStateOf("") }
    var repoPath by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun jsonName(item: JsonObject): String =
        item["name"]?.jsonPrimitive?.contentOrNull
            ?: item["id"]?.jsonPrimitive?.contentOrNull
            ?: "未命名插件"

    fun jsonEnabled(item: JsonObject): Boolean =
        item["enabled"]?.jsonPrimitive?.booleanOrNull ?: true

    fun jsonInstalled(item: JsonObject): Boolean =
        item["installed"]?.jsonPrimitive?.booleanOrNull ?: false

    fun loadPlugins() {
        loading = true
        scope.launch {
            val installedResult = httpClient.getInstalledPlugins(connection)
            val localResult = httpClient.getLocalPlugins(connection)
            val marketplaceResult = httpClient.getMarketplacePlugins(connection)
            installed = installedResult.getOrNull().orEmpty()
            local = localResult.getOrNull().orEmpty()
            marketplace = marketplaceResult.getOrNull().orEmpty()
            notice = when {
                installedResult.isFailure && localResult.isFailure && marketplaceResult.isFailure ->
                    formatNetworkError(
                        installedResult.exceptionOrNull()
                            ?: localResult.exceptionOrNull()
                            ?: marketplaceResult.exceptionOrNull()!!,
                        "插件加载失败",
                    )
                installed.isEmpty() && local.isEmpty() && marketplace.isEmpty() ->
                    "当前后端没有已安装、本地或市场插件。"
                else -> null
            }
            loading = false
        }
    }

    LaunchedEffect(connection.profile.id) { loadPlugins() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("插件") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "已安装插件的启用状态会写入 Agent Server，并在之后新建的会话中生效。市场插件可直接安装。",
                    color = OpenHandsColors.textMuted,
                    fontSize = 13.sp,
                )
                if (loading) {
                    Text("正在读取插件…", color = OpenHandsColors.textMuted, fontSize = 13.sp)
                }
                Text("已安装", color = OpenHandsColors.text, fontSize = 14.sp)
                if (installed.isEmpty()) {
                    Text("没有已安装插件。", color = OpenHandsColors.textMuted, fontSize = 12.sp)
                } else {
                    installed.forEach { plugin ->
                        val name = jsonName(plugin)
                        val description = plugin["description"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        var enabled by remember(name, jsonEnabled(plugin)) { mutableStateOf(jsonEnabled(plugin)) }
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(name, color = OpenHandsColors.text, fontSize = 14.sp)
                                if (description.isNotBlank()) Text(description, color = OpenHandsColors.textSecondary, fontSize = 12.sp)
                            }
                            Switch(
                                checked = enabled,
                                onCheckedChange = { checked ->
                                    enabled = checked
                                    scope.launch {
                                        httpClient.setPluginEnabled(connection, name, checked)
                                            .onSuccess { notice = if (checked) "已启用 $name" else "已停用 $name" }
                                            .onFailure {
                                                enabled = !checked
                                                notice = formatNetworkError(it, "插件状态更新失败")
                                            }
                                    }
                                },
                            )
                            TextButton(onClick = {
                                scope.launch {
                                    httpClient.uninstallPlugin(connection, name)
                                        .onSuccess {
                                            notice = "已卸载 $name"
                                            loadPlugins()
                                        }
                                        .onFailure { notice = formatNetworkError(it, "卸载失败") }
                                }
                            }) { Text("卸载") }
                        }
                    }
                }
                Text("本地发现", color = OpenHandsColors.text, fontSize = 14.sp)
                if (local.isEmpty()) {
                    Text("没有本地插件。", color = OpenHandsColors.textMuted, fontSize = 12.sp)
                } else {
                    local.forEach { plugin ->
                        val name = jsonName(plugin)
                        val description = plugin["description"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        Column {
                            Text(name, color = OpenHandsColors.text, fontSize = 14.sp)
                            if (description.isNotBlank()) Text(description, color = OpenHandsColors.textSecondary, fontSize = 12.sp)
                        }
                    }
                }
                Text("市场", color = OpenHandsColors.text, fontSize = 14.sp)
                if (marketplace.isEmpty()) {
                    Text("市场目录为空。", color = OpenHandsColors.textMuted, fontSize = 12.sp)
                } else {
                    marketplace.take(20).forEach { plugin ->
                        val name = jsonName(plugin)
                        val description = plugin["description"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        val pluginSource = plugin["source"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        val alreadyInstalled = jsonInstalled(plugin) || installed.any { jsonName(it) == name }
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(name, color = OpenHandsColors.text, fontSize = 14.sp)
                                if (description.isNotBlank()) Text(description, color = OpenHandsColors.textSecondary, fontSize = 12.sp)
                            }
                            TextButton(
                                enabled = pluginSource.isNotBlank() && !alreadyInstalled,
                                onClick = {
                                    scope.launch {
                                        httpClient.installPlugin(
                                            connection,
                                            pluginSource,
                                            plugin["ref"]?.jsonPrimitive?.contentOrNull,
                                            plugin["repo_path"]?.jsonPrimitive?.contentOrNull,
                                        )
                                            .onSuccess {
                                                notice = "已安装 $name"
                                                loadPlugins()
                                            }
                                            .onFailure { notice = formatNetworkError(it, "安装失败") }
                                    }
                                },
                            ) { Text(if (alreadyInstalled) "已安装" else "安装") }
                        }
                    }
                }
                OutlinedTextField(source, { source = it }, label = { Text("安装源，例如 github:OpenHands/extensions") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(repoPath, { repoPath = it }, label = { Text("仓库路径（可选）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Button(
                    onClick = {
                        val value = source.trim()
                        if (value.isEmpty()) {
                            notice = "请填写插件安装源"
                            return@Button
                        }
                        scope.launch {
                            httpClient.installPlugin(connection, value, repoPath = repoPath.trim().takeIf { it.isNotEmpty() })
                                .onSuccess {
                                    notice = "已安装 $value"
                                    source = ""
                                    repoPath = ""
                                    loadPlugins()
                                }
                                .onFailure { notice = formatNetworkError(it, "安装失败") }
                        }
                    },
                ) { Text("从源安装") }
                notice?.let { Text(it, color = OpenHandsColors.textMuted, fontSize = 12.sp) }
            }
        },


        confirmButton = {
            TextButton(onClick = onOpenSettings) { Text("打开设置") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@Composable
private fun PluginPickerDialog(
    connection: BackendConnection,
    httpClient: AgentServerHttpClient,
    selectedPlugins: List<PluginSpec>,
    onSelectionChange: (List<PluginSpec>) -> Unit,
    onDismiss: () -> Unit,
) {
    var loading by remember { mutableStateOf(true) }
    var plugins by remember { mutableStateOf<List<PluginSpec>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(connection.profile.id) {
        val results = listOf(
            httpClient.getMarketplacePlugins(connection),
            httpClient.getInstalledPlugins(connection),
            httpClient.getLocalPlugins(connection),
        )
        val merged = linkedMapOf<String, PluginSpec>()
        results.forEach { result ->
            result.getOrNull().orEmpty().forEach { item ->
                ComposerMenuCatalog.pluginFromJson(item)?.let { merged.putIfAbsent(it.identityKey(), it) }
            }
        }
        plugins = merged.values.toList()
        loading = false
    }
    val visible = plugins.filter { ComposerMenuCatalog.pluginMatchesSearch(it, query) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加插件") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("选择在新建会话中加载的插件。已选择 ${selectedPlugins.size} 个。", color = OpenHandsColors.textMuted, fontSize = 13.sp)
                OutlinedTextField(query, { query = it }, label = { Text("搜索插件") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                when {
                    loading -> Text("正在读取插件…", color = OpenHandsColors.textMuted)
                    visible.isEmpty() -> Text("没有可用插件。", color = OpenHandsColors.textMuted)
                    else -> visible.forEach { plugin ->
                        val selected = selectedPlugins.any { it.identityKey() == plugin.identityKey() }
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(plugin.name ?: plugin.source, color = OpenHandsColors.text)
                                Text(plugin.source, color = OpenHandsColors.textMuted, fontSize = 12.sp)
                                plugin.description?.takeIf { it.isNotBlank() }?.let { Text(it, color = OpenHandsColors.textSecondary, fontSize = 12.sp) }
                            }
                            TextButton(onClick = {
                                onSelectionChange(if (selected) selectedPlugins.filterNot { it.identityKey() == plugin.identityKey() } else selectedPlugins + plugin)
                            }) { Text(if (selected) "移除" else "添加") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
}
