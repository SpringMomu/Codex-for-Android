package dev.codex.android.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.codex.android.core.i18n.AppLanguage
import dev.codex.android.data.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.appSettingsDataStore by preferencesDataStore(name = "codex_settings")

class SettingsRepository(
    private val context: Context,
) {
    private object Keys {
        val baseUrl = stringPreferencesKey("base_url")
        val apiKey = stringPreferencesKey("api_key")
        val modelAlias = stringPreferencesKey("model_alias")
        val reasoningEffort = stringPreferencesKey("reasoning_effort")
        val systemPrompt = stringPreferencesKey("system_prompt")
        val languageTag = stringPreferencesKey("language_tag")
    }

    val settings: Flow<AppSettings> = context.appSettingsDataStore.data.map { preferences ->
        val languageTag = preferences[Keys.languageTag] ?: AppLanguage.SYSTEM.storageValue
        AppSettings(
            baseUrl = preferences[Keys.baseUrl] ?: AppSettings().baseUrl,
            apiKey = preferences[Keys.apiKey] ?: "",
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
            preferences[Keys.modelAlias] = settings.modelAlias.trim()
            preferences[Keys.reasoningEffort] = settings.reasoningEffort.trim()
            preferences[Keys.systemPrompt] = settings.systemPrompt.trim()
            preferences[Keys.languageTag] = settings.languageTag.trim().ifBlank { AppLanguage.SYSTEM.storageValue }
        }
    }
}
