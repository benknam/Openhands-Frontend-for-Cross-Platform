package com.openhands.remote.core.network

import com.openhands.remote.core.model.AgentEventEnvelope

interface EventStore {
    fun append(backendId: String, conversationId: String, event: AgentEventEnvelope): Boolean
    fun events(backendId: String, conversationId: String): List<AgentEventEnvelope>
    fun lastTimestamp(backendId: String, conversationId: String): String?
    fun prune(backendId: String, conversationId: String, maxEvents: Int)
}

internal object EventStoreKey {
    fun stable(conversationId: String, event: AgentEventEnvelope): String {
        val source = "$conversationId|${event.timestamp}|${event.type}|${event.payload}"
        return java.security.MessageDigest.getInstance("SHA-256")
            .digest(source.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}

class InMemoryEventStore : EventStore {
    private val records = mutableMapOf<String, LinkedHashMap<String, AgentEventEnvelope>>()

    @Synchronized
    override fun append(
        backendId: String,
        conversationId: String,
        event: AgentEventEnvelope,
    ): Boolean {
        val conversationKey = "$backendId/$conversationId"
        val eventKey = event.id ?: stableEventKey(conversationId, event)
        val bucket = records.getOrPut(conversationKey) { LinkedHashMap() }
        if (bucket.containsKey(eventKey)) return false
        bucket[eventKey] = event
        return true
    }

    @Synchronized
    override fun events(backendId: String, conversationId: String): List<AgentEventEnvelope> =
        records["$backendId/$conversationId"]?.values
            ?.sortedWith(compareBy({ it.timestamp.orEmpty() }, { it.id.orEmpty() }))
            .orEmpty()

    @Synchronized
    override fun lastTimestamp(backendId: String, conversationId: String): String? =
        events(backendId, conversationId).lastOrNull()?.timestamp

    @Synchronized
    override fun prune(backendId: String, conversationId: String, maxEvents: Int) {
        val bucket = records["$backendId/$conversationId"] ?: return
        val keep = maxEvents.coerceAtLeast(1)
        while (bucket.size > keep) bucket.remove(bucket.entries.first().key)
    }

    private fun stableEventKey(conversationId: String, event: AgentEventEnvelope): String {
        return EventStoreKey.stable(conversationId, event)
    }
}
