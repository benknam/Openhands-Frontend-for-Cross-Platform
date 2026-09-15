package com.openhands.remote.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.openhands.remote.core.model.AgentEventEnvelope
import com.openhands.remote.core.network.SqliteEventStore
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SqliteEventStoreInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    @After
    fun resetDatabase() {
        context.deleteDatabase("openhands_events.db")
    }

    @Test
    fun eventsAndCursor_surviveStoreRecreation() {
        val first = SqliteEventStore(context)
        val event = event("event-1", "2026-09-09T08:10:00Z")

        assertTrue(first.append("backend", "conversation", event))
        assertFalse(first.append("backend", "conversation", event))
        first.close()

        val reopened = SqliteEventStore(context)
        assertEquals(listOf(event), reopened.events("backend", "conversation"))
        assertEquals(event.timestamp, reopened.lastTimestamp("backend", "conversation"))
        reopened.close()
    }

    private fun event(id: String, timestamp: String) = AgentEventEnvelope(
        id = id,
        timestamp = timestamp,
        type = "MessageEvent",
        payload = buildJsonObject { put("id", id) },
    )
}
