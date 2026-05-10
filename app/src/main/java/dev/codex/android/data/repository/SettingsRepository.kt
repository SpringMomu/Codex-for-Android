package dev.codex.android.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.codex.android.core.i18n.AppLanguage
import dev.codex.android.data.model.AppSettings
import dev.codex.android.data.model.ConversationScrollPosition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.appSettingsDataStore by preferencesDataStore(name = "codex_settings")

class SettingsRepository(
    private val context: Context,
) {
    private val json = Json

    private object Keys {
        val baseUrl = stringPreferencesKey("base_url")
        val apiKey = stringPreferencesKey("api_key")
        val imageBaseUrl = stringPreferencesKey("image_base_url")
        val imageApiKey = stringPreferencesKey("image_api_key")
        val modelAlias = stringPreferencesKey("model_alias")
        val reasoningEffort = stringPreferencesKey("reasoning_effort")
        val systemPrompt = stringPreferencesKey("system_prompt")
        val languageTag = stringPreferencesKey("language_tag")
        val conversationScrollPositions = stringPreferencesKey("conversation_scroll_positions")
    }

    val settings: Flow<AppSettings> = context.appSettingsDataStore.data.map { preferences ->
        val languageTag = preferences[Keys.languageTag] ?: AppLanguage.SYSTEM.storageValue
        AppSettings(
            baseUrl = preferences[Keys.baseUrl] ?: AppSettings().baseUrl,
            apiKey = preferences[Keys.apiKey] ?: "",
            imageBaseUrl = preferences[Keys.imageBaseUrl] ?: "",
            imageApiKey = preferences[Keys.imageApiKey] ?: "",
            modelAlias = preferences[Keys.modelAlias] ?: AppSettings().modelAlias,
            reasoningEffort = preferences[Keys.reasoningEffort] ?: AppSettings().reasoningEffort,
            systemPrompt = preferences[Keys.systemPrompt] ?: "",
            languageTag = languageTag,
        )
    }

    suspend fun currentSettings(): AppSettings = settings.first()

    suspend fun currentLanguageTag(): String = currentSettings().languageTag

    suspend fun save(settings: AppSettings) {
        context.appSettingsDataStore.edit { preferences ->
            preferences[Keys.baseUrl] = settings.baseUrl.trim()
            preferences[Keys.apiKey] = settings.apiKey.trim()
            preferences[Keys.imageBaseUrl] = settings.imageBaseUrl.trim()
            preferences[Keys.imageApiKey] = settings.imageApiKey.trim()
            preferences[Keys.modelAlias] = settings.modelAlias.trim()
            preferences[Keys.reasoningEffort] = settings.reasoningEffort.trim()
            preferences[Keys.systemPrompt] = settings.systemPrompt.trim()
            preferences[Keys.languageTag] = settings.languageTag.trim().ifBlank { AppLanguage.SYSTEM.storageValue }
        }
    }

    suspend fun getConversationScrollPosition(conversationId: Long): ConversationScrollPosition? =
        context.appSettingsDataStore.data.first().let { preferences ->
            decodeConversationScrollPositions(
                raw = preferences[Keys.conversationScrollPositions],
            )[conversationId.toString()]
        }

    suspend fun saveConversationScrollPosition(
        conversationId: Long,
        position: ConversationScrollPosition,
    ) {
        context.appSettingsDataStore.edit { preferences ->
            val positions = decodeConversationScrollPositions(
                raw = preferences[Keys.conversationScrollPositions],
            ).toMutableMap()
            positions[conversationId.toString()] = position
            preferences[Keys.conversationScrollPositions] = json.encodeToString(positions)
        }
    }

    suspend fun clearConversationScrollPosition(conversationId: Long) {
        context.appSettingsDataStore.edit { preferences ->
            val positions = decodeConversationScrollPositions(
                raw = preferences[Keys.conversationScrollPositions],
            ).toMutableMap()
            positions.remove(conversationId.toString())
            if (positions.isEmpty()) {
                preferences.remove(Keys.conversationScrollPositions)
            } else {
                preferences[Keys.conversationScrollPositions] = json.encodeToString(positions)
            }
        }
    }

    private fun decodeConversationScrollPositions(raw: String?): Map<String, ConversationScrollPosition> = runCatching {
        if (raw.isNullOrBlank()) {
            emptyMap()
        } else {
            json.decodeFromString<Map<String, ConversationScrollPosition>>(raw)
        }
    }.getOrElse { emptyMap() }
}
