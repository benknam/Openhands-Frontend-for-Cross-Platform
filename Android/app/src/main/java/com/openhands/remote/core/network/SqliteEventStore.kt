package com.openhands.remote.core.network

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.openhands.remote.core.model.AgentEventEnvelope
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class SqliteEventStore(
    context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION), EventStore {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE events (
                backend_id TEXT NOT NULL,
                conversation_id TEXT NOT NULL,
                event_key TEXT NOT NULL,
                timestamp TEXT,
                event_type TEXT,
                payload_json TEXT NOT NULL,
                PRIMARY KEY (backend_id, conversation_id, event_key)
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX events_cursor ON events(backend_id, conversation_id, timestamp)",
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS events")
        onCreate(db)
    }

    override fun append(
        backendId: String,
        conversationId: String,
        event: AgentEventEnvelope,
    ): Boolean {
        val values = ContentValues().apply {
            put("backend_id", backendId)
            put("conversation_id", conversationId)
            put("event_key", event.id ?: stableEventKey(conversationId, event))
            put("timestamp", event.timestamp)
            put("event_type", event.type)
            put("payload_json", json.encodeToString(event.payload))
        }
        return writableDatabase.insertWithOnConflict(
            "events",
            null,
            values,
            SQLiteDatabase.CONFLICT_IGNORE,
        ) != -1L
    }

    override fun events(backendId: String, conversationId: String): List<AgentEventEnvelope> {
        val result = mutableListOf<AgentEventEnvelope>()
        readableDatabase.query(
            "events",
            arrayOf("event_key", "timestamp", "event_type", "payload_json"),
            "backend_id = ? AND conversation_id = ?",
            arrayOf(backendId, conversationId),
            null,
            null,
            "timestamp ASC, rowid ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += AgentEventEnvelope(
                    id = cursor.getString(0),
                    timestamp = cursor.getString(1),
                    type = cursor.getString(2),
                    payload = json.parseToJsonElement(cursor.getString(3)).jsonObject,
                )
            }
        }
        return result
    }

    override fun lastTimestamp(backendId: String, conversationId: String): String? =
        readableDatabase.query(
            "events",
            arrayOf("timestamp"),
            "backend_id = ? AND conversation_id = ?",
            arrayOf(backendId, conversationId),
            null,
            null,
            "timestamp DESC, rowid DESC",
            "1",
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    override fun prune(backendId: String, conversationId: String, maxEvents: Int) {
        val keep = maxEvents.coerceAtLeast(1)
        writableDatabase.delete(
            "events",
            "backend_id = ? AND conversation_id = ? AND rowid NOT IN " +
                "(SELECT rowid FROM events WHERE backend_id = ? AND conversation_id = ? " +
                "ORDER BY timestamp DESC, rowid DESC LIMIT ?)",
            arrayOf(backendId, conversationId, backendId, conversationId, keep.toString()),
        )
    }

    private fun stableEventKey(conversationId: String, event: AgentEventEnvelope): String =
        EventStoreKey.stable(conversationId, event)

    private companion object {
        const val DATABASE_NAME = "openhands_events.db"
        const val DATABASE_VERSION = 1
    }
}
