package com.openhands.remote.core.network

import com.openhands.remote.core.model.AgentEventEnvelope
import com.openhands.remote.core.model.BackendProfile
import com.openhands.remote.core.model.ConversationSummary
import kotlinx.coroutines.flow.Flow

interface AgentServerTransport {
    suspend fun getServerInfo(backend: BackendProfile): Result<String>

    suspend fun listConversations(
        backend: BackendProfile,
        pageId: String? = null,
    ): Result<List<ConversationSummary>>

    suspend fun searchEvents(
        backend: BackendProfile,
        conversationId: String,
        afterTimestamp: String? = null,
    ): Result<List<AgentEventEnvelope>>

    fun observeConversationEvents(
        backend: BackendProfile,
        conversationId: String,
        afterTimestamp: String? = null,
    ): Flow<AgentEventEnvelope>

    suspend fun sendMessage(
        backend: BackendProfile,
        conversationId: String,
        messageJson: String,
    ): Result<Unit>
}
