package com.openhands.remote.core.security

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.openhands.remote.core.model.BackendConnection
import com.openhands.remote.core.model.BackendProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.backendDataStore by preferencesDataStore(name = "backend_connections")

class BackendRepository(
    private val context: Context,
    private val protector: ApiKeyProtector = ApiKeyProtector(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    val connections: Flow<List<BackendConnection>> = context.backendDataStore.data.map { preferences ->
        val stored = preferences[BACKENDS_KEY].orEmpty()
        if (stored.isEmpty()) return@map emptyList()
        json.decodeFromString<List<StoredBackend>>(stored).mapNotNull { item ->
            runCatching {
                BackendConnection(item.profile, protector.decrypt(item.encryptedApiKey))
            }.getOrNull()
        }
    }

    suspend fun save(connection: BackendConnection) {
        context.backendDataStore.edit { preferences ->
            val current = decodeStored(preferences[BACKENDS_KEY])
                .filterNot { it.profile.id == connection.profile.id }
            val next = current + StoredBackend(
                profile = connection.profile,
                encryptedApiKey = protector.encrypt(connection.apiKey.orEmpty()),
            )
            preferences[BACKENDS_KEY] = json.encodeToString<List<StoredBackend>>(next)
        }
    }

    suspend fun delete(id: String) {
        context.backendDataStore.edit { preferences ->
            val next = decodeStored(preferences[BACKENDS_KEY]).filterNot { it.profile.id == id }
            preferences[BACKENDS_KEY] = json.encodeToString<List<StoredBackend>>(next)
        }
    }

    suspend fun saveSelectedConversation(profileId: String, conversationId: String) {
        context.backendDataStore.edit { preferences ->
            preferences[selectedConversationKey(profileId)] = conversationId
        }
    }

    suspend fun getSelectedConversation(profileId: String): String? =
        context.backendDataStore.data.map { preferences ->
            preferences[selectedConversationKey(profileId)]
        }.first()

    suspend fun getStartupPage(): String =
        context.backendDataStore.data.map { preferences ->
            preferences[STARTUP_PAGE_KEY] ?: STARTUP_NEW_CONVERSATION
        }.first()

    suspend fun saveStartupPage(page: String) {
        context.backendDataStore.edit { preferences ->
            preferences[STARTUP_PAGE_KEY] = page
        }
    }

    suspend fun getPinnedConversationIds(profileId: String): List<String> =
        context.backendDataStore.data.map { preferences ->
            decodeIdList(preferences[pinnedConversationsKey(profileId)])
        }.first()

    suspend fun savePinnedConversationIds(profileId: String, ids: List<String>) {
        context.backendDataStore.edit { preferences ->
            preferences[pinnedConversationsKey(profileId)] = json.encodeToString(ids.distinct())
        }
    }


    val uiOptimizationSettings: Flow<UiOptimizationSettings> = context.backendDataStore.data.map { preferences ->
        UiOptimizationSettings(
            logoCollapseEnabled = preferences[UI_LOGO_COLLAPSE_ENABLED] ?: true,
            adaptiveWidthEnabled = preferences[UI_ADAPTIVE_WIDTH_ENABLED] ?: false,
            sendShortcut = preferences[UI_SEND_SHORTCUT] ?: "Ctrl + Enter",
            showRecommendedAutomations = preferences[UI_RECOMMENDED_AUTOMATIONS] ?: true,
            chatBubbleActionsEnabled = preferences[UI_CHAT_BUBBLE_ACTIONS] ?: false,
            composerHidingEnabled = preferences[UI_COMPOSER_HIDING] ?: false,
            autoHideComposer = preferences[UI_AUTO_HIDE_COMPOSER] ?: false,
            autoHideDelaySeconds = (preferences[UI_AUTO_HIDE_DELAY] ?: 5).coerceIn(1, 3600),
        )
    }

    suspend fun saveUiOptimizationSettings(settings: UiOptimizationSettings) {
        context.backendDataStore.edit { preferences ->
            preferences[UI_LOGO_COLLAPSE_ENABLED] = settings.logoCollapseEnabled
            preferences[UI_ADAPTIVE_WIDTH_ENABLED] = settings.adaptiveWidthEnabled
            preferences[UI_SEND_SHORTCUT] = settings.sendShortcut
            preferences[UI_RECOMMENDED_AUTOMATIONS] = settings.showRecommendedAutomations
            preferences[UI_CHAT_BUBBLE_ACTIONS] = settings.chatBubbleActionsEnabled
            preferences[UI_COMPOSER_HIDING] = settings.composerHidingEnabled
            preferences[UI_AUTO_HIDE_COMPOSER] = settings.autoHideComposer
            preferences[UI_AUTO_HIDE_DELAY] = settings.autoHideDelaySeconds.coerceIn(1, 3600)
        }
    }

    private fun selectedConversationKey(profileId: String) =
        stringPreferencesKey("selected_conversation_$profileId")

    private fun pinnedConversationsKey(profileId: String) =
        stringPreferencesKey("pinned_conversations_$profileId")

    private fun decodeIdList(value: String?): List<String> {
        if (value.isNullOrEmpty()) return emptyList()
        return runCatching { json.decodeFromString<List<String>>(value) }.getOrDefault(emptyList())
    }

    private fun decodeStored(value: String?): List<StoredBackend> {
        if (value.isNullOrEmpty()) return emptyList()
        return runCatching { json.decodeFromString<List<StoredBackend>>(value) }
            .getOrDefault(emptyList())
    }

    @Serializable
    private data class StoredBackend(
        val profile: BackendProfile,
        val encryptedApiKey: String,
    )

    companion object {
        const val STARTUP_NEW_CONVERSATION = "new_conversation"
        const val STARTUP_LAST_CONVERSATION = "last_conversation"
        val BACKENDS_KEY = stringPreferencesKey("backends_json")
        val STARTUP_PAGE_KEY = stringPreferencesKey("startup_page")
        private val UI_LOGO_COLLAPSE_ENABLED = booleanPreferencesKey("ui_logo_collapse_enabled")
        private val UI_ADAPTIVE_WIDTH_ENABLED = booleanPreferencesKey("ui_adaptive_width_enabled")
        private val UI_SEND_SHORTCUT = stringPreferencesKey("ui_send_shortcut")
        private val UI_RECOMMENDED_AUTOMATIONS = booleanPreferencesKey("ui_recommended_automations")
        private val UI_CHAT_BUBBLE_ACTIONS = booleanPreferencesKey("ui_chat_bubble_actions")
        private val UI_COMPOSER_HIDING = booleanPreferencesKey("ui_composer_hiding")
        private val UI_AUTO_HIDE_COMPOSER = booleanPreferencesKey("ui_auto_hide_composer")
        private val UI_AUTO_HIDE_DELAY = intPreferencesKey("ui_auto_hide_delay")
    }
}

data class UiOptimizationSettings(
    val logoCollapseEnabled: Boolean = true,
    val adaptiveWidthEnabled: Boolean = false,
    val sendShortcut: String = "Ctrl + Enter",
    val showRecommendedAutomations: Boolean = true,
    val chatBubbleActionsEnabled: Boolean = false,
    val composerHidingEnabled: Boolean = false,
    val autoHideComposer: Boolean = false,
    val autoHideDelaySeconds: Int = 5,
)
