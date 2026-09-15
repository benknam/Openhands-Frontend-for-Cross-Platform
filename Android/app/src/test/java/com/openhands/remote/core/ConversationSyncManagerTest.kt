package com.openhands.remote.core

import com.openhands.remote.core.model.AgentEventEnvelope
import com.openhands.remote.core.model.BackendConnection
import com.openhands.remote.core.model.BackendProfile
import com.openhands.remote.core.model.ConnectionState
import com.openhands.remote.core.network.ConversationSocket
import com.openhands.remote.core.network.DefaultConversationSyncManager
import com.openhands.remote.core.network.InMemoryEventStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationSyncManagerTest {
    private val backend = BackendConnection(
        BackendProfile("backend-1", "Test", "http://localhost:8000"),
    )

    @Test
    fun start_restoresCachedEventsAndUsesLastTimestampOnConnect() {
        val store = InMemoryEventStore()
        val cached = event("cached", "2026-09-09T08:00:00Z")
        store.append(backend.profile.id, "conversation-1", cached)
        val socket = RecordingConversationSocket()
        val manager = DefaultConversationSyncManager(
            socket = socket,
            eventStore = store,
            scope = CoroutineScope(Dispatchers.Unconfined),
        )

        manager.start(backend, "conversation-1")

        assertEquals(listOf(cached), manager.events.value)
        assertEquals("2026-09-09T08:00:00Z", socket.lastAfterTimestamp)
        assertEquals(ConnectionState.CONNECTING, manager.state.value.connectionState)
    }

    @Test
    fun incomingEvents_arePersistedAndDuplicateEventsAreIgnored() {
        val store = InMemoryEventStore()
        val socket = RecordingConversationSocket()
        val manager = DefaultConversationSyncManager(
            socket = socket,
            eventStore = store,
            scope = CoroutineScope(Dispatchers.Unconfined),
        )
        manager.start(backend, "conversation-1")
        val incoming = event("event-1", "2026-09-09T08:01:00Z")

        socket.emit(incoming)
        socket.emit(incoming)

        assertEquals(listOf(incoming), manager.events.value)
        assertEquals(1L, manager.state.value.receivedEventCount)
        assertEquals("2026-09-09T08:01:00Z", manager.state.value.lastEventTimestamp)
        assertEquals(ConnectionState.CONNECTED, manager.state.value.connectionState)
    }

    @Test
    fun manualReconnect_reusesPersistedCursorAndIgnoresStaleSocketCallbacks() {
        val store = InMemoryEventStore()
        val socket = RecordingConversationSocket()
        val manager = DefaultConversationSyncManager(
            socket = socket,
            eventStore = store,
            scope = CoroutineScope(Dispatchers.Unconfined),
        )
        manager.start(backend, "conversation-1")
        val firstListener = socket.listeners.single()
        val incoming = event("event-1", "2026-09-09T08:02:00Z")
        firstListener.onEvent(incoming)

        manager.reconnect()
        assertEquals("2026-09-09T08:02:00Z", socket.lastAfterTimestamp)
        assertEquals(2, socket.connectCount)

        firstListener.onEvent(event("stale", "2026-09-09T08:03:00Z"))
        assertEquals(listOf(incoming), manager.events.value)
        assertEquals(1L, manager.state.value.receivedEventCount)
    }

    @Test
    fun networkLoss_waitsAndNetworkRecovery_reconnectsImmediately() {
        val socket = RecordingConversationSocket()
        val manager = DefaultConversationSyncManager(
            socket = socket,
            eventStore = InMemoryEventStore(),
            scope = CoroutineScope(Dispatchers.Unconfined),
        )
        manager.start(backend, "conversation-1")

        manager.networkLost()
        assertEquals(ConnectionState.WAITING_NETWORK, manager.state.value.connectionState)

        manager.networkAvailable()
        assertEquals(2, socket.connectCount)
        assertEquals(ConnectionState.CONNECTING, manager.state.value.connectionState)
    }

    @Test
    fun stop_clearsPresentationStateButKeepsStoreForNextSessionRecovery() {
        val store = InMemoryEventStore()
        val socket = RecordingConversationSocket()
        val manager = DefaultConversationSyncManager(
            socket = socket,
            eventStore = store,
            scope = CoroutineScope(Dispatchers.Unconfined),
        )
        manager.start(backend, "conversation-1")
        val incoming = event("event-1", "2026-09-09T08:04:00Z")
        socket.emit(incoming)

        manager.stop()
        assertEquals(ConnectionState.IDLE, manager.state.value.connectionState)
        assertTrue(manager.events.value.isEmpty())
        assertEquals(listOf(incoming), store.events(backend.profile.id, "conversation-1"))
    }

    private fun event(id: String, timestamp: String) = AgentEventEnvelope(
        id = id,
        timestamp = timestamp,
        type = "MessageEvent",
        payload = buildJsonObject { put("id", id) },
    )

    private class RecordingConversationSocket : ConversationSocket {
        val listeners = mutableListOf<ConversationSocket.Listener>()
        var lastAfterTimestamp: String? = null
        var connectCount = 0
        var lastListener: ConversationSocket.Listener? = null

        override fun connect(
            connection: BackendConnection,
            conversationId: String,
            afterTimestamp: String?,
            listener: ConversationSocket.Listener,
        ) {
            connectCount += 1
            lastAfterTimestamp = afterTimestamp
            lastListener = listener
            listeners += listener
            listener.onConnecting()
        }

        override fun send(messageJson: String): Boolean = true

        override fun close() = Unit

        fun emit(event: AgentEventEnvelope) {
            assertNotNull(lastListener)
            lastListener!!.onEvent(event)
        }
    }
}
