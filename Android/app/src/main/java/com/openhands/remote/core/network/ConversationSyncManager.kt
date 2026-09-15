package com.openhands.remote.core.network

import com.openhands.remote.core.model.AgentEventEnvelope
import com.openhands.remote.core.model.BackendConnection
import com.openhands.remote.core.model.ConversationSyncState
import kotlinx.coroutines.flow.StateFlow

interface ConversationSyncManager {
    val state: StateFlow<ConversationSyncState>
    val events: StateFlow<List<AgentEventEnvelope>>

    fun start(
        backend: BackendConnection,
        conversationId: String,
    )

    fun stop()

    fun reconnect()

    fun networkLost()

    fun networkAvailable()
}
