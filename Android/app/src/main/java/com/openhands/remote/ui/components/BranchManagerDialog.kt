package com.openhands.remote.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.openhands.remote.core.model.BackendConnection
import com.openhands.remote.core.network.AgentServerHttpClient
import kotlinx.coroutines.launch

@Composable
fun BranchManagerDialog(
    connection: BackendConnection,
    httpClient: AgentServerHttpClient,
    workingDir: String?,
    onDismiss: () -> Unit,
    onChanged: (String) -> Unit,
) {
    var branches by remember { mutableStateOf<List<String>>(emptyList()) }
    var currentBranch by remember { mutableStateOf<String?>(null) }
    var newBranch by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("正在读取分支…") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        scope.launch {
            val root = workingDir
            if (root == null) {
                status = "后端未提供 workspace 根目录"
                return@launch
            }
            busy = true
            val result = httpClient.executeCommand(
                connection,
                "git branch --format='%(refname:short)'",
                root,
            )
            result.onSuccess {
                branches = it.stdout.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
                status = "共 ${branches.size} 个本地分支"
            }.onFailure { status = "读取失败：${it.message ?: "未知错误"}" }
            httpClient.executeCommand(connection, "git branch --show-current", root)
                .onSuccess { currentBranch = it.stdout.trim().ifBlank { null } }
            busy = false
        }
    }

    LaunchedEffect(connection.profile.id, workingDir) { refresh() }

    fun runGit(command: String, success: String) {
        scope.launch {
            val root = workingDir
            if (root == null) {
                status = "后端未提供 workspace 根目录"
                return@launch
            }
            busy = true
            httpClient.executeCommand(connection, command, root)
                .onSuccess { result ->
                    if (result.exitCode == 0) {
                        status = success
                        refresh()
                        onChanged(success)
                    } else {
                        status = result.stderr.ifBlank { "Git 操作失败（退出码 ${result.exitCode}）" }
                    }
                }
                .onFailure { status = it.message ?: "Git 操作失败" }
            busy = false
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("分支管理") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("当前：${currentBranch ?: "无（detached/unborn）"}")
                Text(status, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                ) {
                    OutlinedTextField(
                        value = newBranch,
                        onValueChange = { newBranch = it },
                        modifier = Modifier.fillMaxWidth().padding(6.dp),
                        label = { Text("新分支名称") },
                        singleLine = true,
                        enabled = !busy,
                    )
                }
                Button(
                    onClick = {
                        val name = newBranch.trim()
                        if (isValidBranchName(name)) {
                            runGit("git switch -c -- ${shellQuote(name)}", "已创建并切换：$name")
                            newBranch = ""
                        } else status = "分支名无效或为空"
                    },
                    enabled = !busy && workingDir != null && newBranch.isNotBlank(),
                ) { Text("创建并切换") }
                HorizontalDivider()
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                    items(branches, key = { it }) { branch ->
                        TextButton(
                            onClick = {
                                if (branch != currentBranch) {
                                    runGit("git switch -- ${shellQuote(branch)}", "已切换：$branch")
                                }
                            },
                            enabled = !busy && workingDir != null,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (branch == currentBranch) OhInlineIcon("check", "当前分支")
                                Text(branch)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("关闭") } },
    )
}

private fun isValidBranchName(value: String): Boolean =
    value.length <= 200 && value.matches(Regex("[A-Za-z0-9._/-]+")) &&
        !value.startsWith("/") && !value.endsWith("/") &&
        !value.contains("..") && !value.contains("//")

private fun shellQuote(value: String): String = "'${value.replace("'", "'\\\''")}'"
