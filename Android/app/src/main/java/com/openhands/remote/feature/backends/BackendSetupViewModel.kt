package com.openhands.remote.feature.backends

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.openhands.remote.core.model.AuthMode
import com.openhands.remote.core.model.BackendConnection
import com.openhands.remote.core.model.BackendProfile
import com.openhands.remote.core.network.AgentServerHttpClient
import com.openhands.remote.core.network.formatNetworkError
import com.openhands.remote.core.model.ConnectionState
import com.openhands.remote.core.security.BackendRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URI

class BackendSetupViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = BackendRepository(application)
    private val client = AgentServerHttpClient()
    private val _state = MutableStateFlow(BackendSetupState())
    val state: StateFlow<BackendSetupState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.update { it.copy(startupPage = repository.getStartupPage()) }
        }
        viewModelScope.launch {
            repository.connections.collect { savedConnections ->
                _state.update { it.copy(savedConnections = savedConnections) }
                val current = _state.value
                if (current.connection == null &&
                    current.connectionState == ConnectionState.IDLE &&
                    savedConnections.isNotEmpty()
                ) {
                    connect(savedConnections.first(), persist = false, restored = true)
                }
            }
        }
    }

    fun disconnect() {
        _state.update {
            it.copy(
                connectionState = ConnectionState.IDLE,
                connection = null,
                message = null,
            )
        }
    }

    fun connect(connection: BackendConnection) {
        connect(connection, persist = false, restored = false)
    }

    fun deleteBackend(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun updateBackend(
        existingId: String,
        name: String,
        baseUrl: String,
        note: String,
        apiKey: String?,
    ) {
        val normalizedUrl = normalizeUrl(baseUrl) ?: return
        viewModelScope.launch {
            repository.save(
                BackendConnection(
                    profile = BackendProfile(
                        id = existingId,
                        name = name.trim().ifBlank { URI(normalizedUrl).host ?: normalizedUrl },
                        baseUrl = normalizedUrl,
                        note = note.trim(),
                        authMode = AuthMode.SESSION_API_KEY,
                    ),
                    apiKey = apiKey?.trim()?.ifBlank { null },
                ),
            )
            if (_state.value.connection?.profile?.id == existingId) {
                _state.update { it.copy(connection = null, connectionState = ConnectionState.IDLE, message = "后端已更新，请重新连接") }
            }
        }
    }

    fun setStartupPage(page: String) {
        viewModelScope.launch {
            repository.saveStartupPage(page)
            _state.update { it.copy(startupPage = page) }
        }
    }

    fun checkAndSave(baseUrl: String, apiKey: String) {
        val normalizedUrl = normalizeUrl(baseUrl)
        if (normalizedUrl == null) {
            _state.value = BackendSetupState(
                connectionState = ConnectionState.FAILED,
                message = "请输入有效的 http:// 或 https:// 地址",
            )
            return
        }

        val profile = BackendProfile(
            id = normalizedUrl,
            name = URI(normalizedUrl).host ?: normalizedUrl,
            baseUrl = normalizedUrl,
            authMode = AuthMode.SESSION_API_KEY,
        )
        val connection = BackendConnection(profile, apiKey.trim().ifEmpty { null })

        connect(connection, persist = true, restored = false)
    }

    private fun connect(
        connection: BackendConnection,
        persist: Boolean,
        restored: Boolean,
    ) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    connectionState = ConnectionState.CONNECTING,
                    connection = null,
                    message = if (restored) "正在恢复已保存后端…" else "正在连接后端…",
                )
            }
            client.getServerInfo(connection).fold(
                onSuccess = { body ->
                    // /server_info is public on many deployments. Verify a protected
                    // resource before accepting the connection or persisting its key.
                    client.getLlmProfiles(connection).fold(
                        onSuccess = {
                            if (persist) repository.save(connection)
                            _state.update {
                                it.copy(
                                    connectionState = ConnectionState.CONNECTED,
                                    message = if (restored) {
                                        "已恢复连接：${body.take(240)}"
                                    } else {
                                        "连接成功：${body.take(240)}"
                                    },
                                    connection = connection,
                                )
                            }
                        },
                        onFailure = { error ->
                            _state.update {
                                it.copy(
                                    connectionState = ConnectionState.FAILED,
                                    message = formatNetworkError(error, "无法验证 Agent Server 访问权限"),
                                    connection = null,
                                )
                            }
                        },
                    )
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            connectionState = ConnectionState.FAILED,
                            message = error.message ?: "无法连接到 Agent Server",
                            connection = null,
                        )
                    }
                },
            )
        }
    }

    private fun normalizeUrl(value: String): String? {
        val candidate = value.trim().trimEnd('/')
        if (candidate.isEmpty()) return null
        return runCatching {
            val uri = URI(candidate)
            require(uri.scheme == "http" || uri.scheme == "https")
            require(!uri.host.isNullOrBlank())
            candidate
        }.getOrNull()
    }
}

data class BackendSetupState(
    val connectionState: ConnectionState = ConnectionState.IDLE,
    val message: String? = null,
    val startupPage: String = BackendRepository.STARTUP_NEW_CONVERSATION,
    val connection: BackendConnection? = null,
    val savedConnections: List<BackendConnection> = emptyList(),
)
