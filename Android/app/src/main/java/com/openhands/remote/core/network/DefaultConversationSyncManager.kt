package com.openhands.remote.core.network

import com.openhands.remote.core.model.BackendConnection
import com.openhands.remote.core.model.ConnectionState
import com.openhands.remote.core.model.ConversationSyncState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.random.Random

class DefaultConversationSyncManager(
    private val socket: ConversationSocket = OkHttpConversationSocket(),
    private val eventStore: EventStore = InMemoryEventStore(),
    private val historyClient: AgentServerHttpClient = AgentServerHttpClient(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : ConversationSyncManager {
    private val _state = MutableStateFlow(ConversationSyncState())
    override val state: StateFlow<ConversationSyncState> = _state.asStateFlow()
    private val _events = MutableStateFlow<List<com.openhands.remote.core.model.AgentEventEnvelope>>(emptyList())
    override val events: StateFlow<List<com.openhands.remote.core.model.AgentEventEnvelope>> = _events.asStateFlow()

    private var connection: BackendConnection? = null
    private var conversationId: String? = null
    private var reconnectJob: Job? = null
    private var historyJob: Job? = null
    private var manuallyStopped = false
    private var retryAttempt = 0
    private var connectionGeneration = 0L

    override fun start(backend: BackendConnection, conversationId: String) {
        stop()
        manuallyStopped = false
        this.connection = backend
        this.conversationId = conversationId
        eventStore.prune(backend.profile.id, conversationId, MAX_STORED_EVENTS)
        _events.value = eventStore.events(backend.profile.id, conversationId)
        retryAttempt = 0
        // Connect immediately so cached events and live updates are available
        // while the REST history backfill runs independently.
        connect()
        historyJob = scope.launch {
            historyClient.searchConversationEvents(backend, conversationId, INITIAL_HISTORY_PAGE_SIZE)
                .onSuccess { page ->
                    if (isCurrent(backend, conversationId)) {
                        page.events.forEach { eventStore.append(backend.profile.id, conversationId, it) }
                        eventStore.prune(backend.profile.id, conversationId, MAX_STORED_EVENTS)
                        _events.value = eventStore.events(backend.profile.id, conversationId)
                    }
                }
        }
    }

    override fun stop() {
        manuallyStopped = true
        reconnectJob?.cancel()
        reconnectJob = null
        historyJob?.cancel()
        historyJob = null
        connectionGeneration += 1
        socket.close()
        connection = null
        conversationId = null
        _events.value = emptyList()
        _state.value = ConversationSyncState(ConnectionState.IDLE)
    }

    override fun reconnect() {
        if (manuallyStopped) manuallyStopped = false
        reconnectJob?.cancel()
        retryAttempt = 0
        socket.close()
        connect()
    }

    override fun networkLost() {
        if (connection != null && conversationId != null && !manuallyStopped) {
            reconnectJob?.cancel()
            reconnectJob = null
            updateState(ConnectionState.WAITING_NETWORK, "网络不可用，等待恢复…")
        }
    }

    override fun networkAvailable() {
        if (!manuallyStopped && connection != null && conversationId != null) reconnect()
    }

    fun sendMessage(messageJson: String): Boolean = socket.send(messageJson)

    private fun isCurrent(backend: BackendConnection, id: String): Boolean =
        !manuallyStopped && connection?.profile?.id == backend.profile.id && conversationId == id

    private fun connect() {
        val currentConnection = connection ?: return
        val currentConversationId = conversationId ?: return
        val afterTimestamp = eventStore.lastTimestamp(
            currentConnection.profile.id,
            currentConversationId,
        )
        val generation = ++connectionGeneration
        socket.connect(
            currentConnection,
            currentConversationId,
            afterTimestamp,
            object : ConversationSocket.Listener {
                override fun onConnecting() {
                    if (generation == connectionGeneration) updateState(ConnectionState.CONNECTING)
                }

                override fun onConnected() {
                    if (generation != connectionGeneration) return
                    retryAttempt = 0
                    updateState(ConnectionState.SYNCING)
                }

                override fun onEvent(event: com.openhands.remote.core.model.AgentEventEnvelope) {
                    if (generation != connectionGeneration) return
                    val inserted = eventStore.append(
                        currentConnection.profile.id,
                        currentConversationId,
                        event,
                    )
                    if (inserted) {
                        _events.value = eventStore.events(
                            currentConnection.profile.id,
                            currentConversationId,
                        )
                        _state.value = _state.value.copy(
                            connectionState = ConnectionState.CONNECTED,
                            lastEventTimestamp = event.timestamp,
                            receivedEventCount = _state.value.receivedEventCount + 1,
                            errorMessage = null,
                        )
                        if (_state.value.receivedEventCount % PRUNE_INTERVAL == 0L) {
                            eventStore.prune(
                                currentConnection.profile.id,
                                currentConversationId,
                                MAX_STORED_EVENTS,
                            )
                            _events.value = eventStore.events(
                                currentConnection.profile.id,
                                currentConversationId,
                            )
                        }
                    }
                }

                override fun onFailure(message: String) {
                    if (generation != connectionGeneration) return
                    updateState(ConnectionState.DISCONNECTED, message)
                    scheduleReconnect()
                }

                override fun onClosed(code: Int, reason: String) {
                    if (generation == connectionGeneration && !manuallyStopped) {
                        updateState(ConnectionState.DISCONNECTED, "连接已关闭（$code）：$reason")
                        scheduleReconnect()
                    }
                }
            },
        )
    }

    private fun scheduleReconnect() {
        if (manuallyStopped || reconnectJob?.isActive == true) return
        val delayMillis = min(60_000L, 1_000L shl min(retryAttempt, 6))
        retryAttempt += 1
        reconnectJob = scope.launch {
            delay(delayMillis + Random.nextLong(0, maxOf(1, delayMillis / 5)))
            if (!manuallyStopped) connect()
        }
    }

    private fun updateState(state: ConnectionState, errorMessage: String? = null) {
        _state.value = _state.value.copy(
            connectionState = state,
            errorMessage = errorMessage,
        )
    }

    private companion object {
        const val MAX_STORED_EVENTS = 5_000
        const val INITIAL_HISTORY_PAGE_SIZE = 100
        const val PRUNE_INTERVAL = 100L
    }
}
