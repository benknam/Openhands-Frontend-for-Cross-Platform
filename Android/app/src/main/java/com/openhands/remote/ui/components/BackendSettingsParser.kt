package com.openhands.remote.ui.components

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

data class McpServerItem(
    val key: String,
    val name: String,
    val detail: String,
)

object BackendSettingsParser {
    fun appPreferences(settings: JsonObject): JsonObject? {
        val misc = settings["misc_settings"] as? JsonObject
        return (misc?.get("app_preferences") as? JsonObject)
            ?: (settings["app_preferences"] as? JsonObject)
    }

    fun stringSetting(prefs: JsonObject?, settings: JsonObject, key: String): String? =
        prefs?.get(key)?.jsonPrimitive?.contentOrNull
            ?: settings[key]?.jsonPrimitive?.contentOrNull

    fun booleanSetting(prefs: JsonObject?, settings: JsonObject, key: String): Boolean =
        (prefs?.get(key) as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()
            ?: (settings[key] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()
            ?: false

    fun stringSetSetting(prefs: JsonObject?, settings: JsonObject, key: String): Set<String> {
        val array = (prefs?.get(key) as? JsonArray) ?: (settings[key] as? JsonArray) ?: return emptySet()
        return array.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.toSet()
    }

    fun parseMcpServers(settings: JsonObject): List<McpServerItem> {
        val raw = settings["mcp_config"]
            ?: (settings["agent_settings"] as? JsonObject)?.get("mcp_config")
            ?: return emptyList()
        val map = when (raw) {
            is JsonObject -> raw["mcpServers"] as? JsonObject ?: raw
            else -> return emptyList()
        }
        return map.entries.map { (key, value) ->
            val obj = value as? JsonObject
            val name = obj?.get("name")?.jsonPrimitive?.contentOrNull ?: key
            val command = obj?.get("command")?.jsonPrimitive?.contentOrNull
            val url = obj?.get("url")?.jsonPrimitive?.contentOrNull
            McpServerItem(key, name, command ?: url ?: key)
        }
    }
}

fun unassignedWorkspaceConversations(
    conversations: List<Pair<String, String?>>,
): List<String> = conversations.filter { it.second.isNullOrBlank() }.map { it.first }
