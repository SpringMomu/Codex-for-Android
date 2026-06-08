package dev.codex.android.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.codex.android.core.i18n.AppLanguage
import dev.codex.android.data.model.AppSettings
import dev.codex.android.data.model.ChatProvider
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
        val chatProvider = stringPreferencesKey("chat_provider")
        val baseUrl = stringPreferencesKey("base_url")
        val apiKey = stringPreferencesKey("api_key")
        val codexBaseUrl = stringPreferencesKey("codex_base_url")
        val codexApiKey = stringPreferencesKey("codex_api_key")
        val claudeBaseUrl = stringPreferencesKey("claude_base_url")
        val claudeApiKey = stringPreferencesKey("claude_api_key")
        val imageBaseUrl = stringPreferencesKey("image_base_url")
        val imageApiKey = stringPreferencesKey("image_api_key")
        val modelAlias = stringPreferencesKey("model_alias")
        val selectedCodexModel = stringPreferencesKey("selected_codex_model")
        val selectedClaudeModel = stringPreferencesKey("selected_claude_model")
        val codexModels = stringPreferencesKey("codex_models")
        val claudeModels = stringPreferencesKey("claude_models")
        val maxContextTokens = intPreferencesKey("max_context_tokens")
        val reasoningEffort = stringPreferencesKey("reasoning_effort")
        val systemPrompt = stringPreferencesKey("system_prompt")
        val languageTag = stringPreferencesKey("language_tag")
        val conversationScrollPositions = stringPreferencesKey("conversation_scroll_positions")
    }

    val settings: Flow<AppSettings> = context.appSettingsDataStore.data.map { preferences ->
        val languageTag = preferences[Keys.languageTag] ?: AppLanguage.SYSTEM.storageValue
        val chatProvider = ChatProvider.fromStorage(preferences[Keys.chatProvider])
        val legacyBaseUrl = preferences[Keys.baseUrl]?.trim().orEmpty()
        val legacyApiKey = preferences[Keys.apiKey]?.trim().orEmpty()
        val legacyModelAlias = preferences[Keys.modelAlias]?.trim().orEmpty()
        val selectedCodexModel = preferences[Keys.selectedCodexModel]
            ?: legacyModelAlias.takeIf { chatProvider == ChatProvider.CODEX }
            ?: ""
        val selectedClaudeModel = preferences[Keys.selectedClaudeModel]
            ?: legacyModelAlias.takeIf { chatProvider == ChatProvider.CLAUDE }
            ?: ""
        AppSettings(
            chatProvider = chatProvider,
            codexBaseUrl = preferences[Keys.codexBaseUrl]
                ?: legacyBaseUrl.takeIf { chatProvider == ChatProvider.CODEX }
                ?: AppSettings.DEFAULT_CODEX_BASE_URL,
            codexApiKey = preferences[Keys.codexApiKey]
                ?: legacyApiKey.takeIf { chatProvider == ChatProvider.CODEX }
                ?: "",
            claudeBaseUrl = preferences[Keys.claudeBaseUrl]
                ?: legacyBaseUrl.takeIf { chatProvider == ChatProvider.CLAUDE }
                ?: AppSettings.DEFAULT_CLAUDE_BASE_URL,
            claudeApiKey = preferences[Keys.claudeApiKey]
                ?: legacyApiKey.takeIf { chatProvider == ChatProvider.CLAUDE }
                ?: "",
            imageBaseUrl = preferences[Keys.imageBaseUrl] ?: "",
            imageApiKey = preferences[Keys.imageApiKey] ?: "",
            selectedCodexModel = selectedCodexModel.trim(),
            selectedClaudeModel = selectedClaudeModel.trim(),
            codexModels = AppSettings.normalizeModels(
                models = decodeChatModels(preferences[Keys.codexModels]),
                selectedModel = selectedCodexModel,
            ),
            claudeModels = AppSettings.normalizeModels(
                models = decodeChatModels(preferences[Keys.claudeModels]),
                selectedModel = selectedClaudeModel,
            ),
            maxContextTokens = preferences[Keys.maxContextTokens]
                ?.takeIf { it > 0 }
                ?: AppSettings.DEFAULT_MAX_CONTEXT_TOKENS,
            reasoningEffort = preferences[Keys.reasoningEffort] ?: AppSettings().reasoningEffort,
            systemPrompt = preferences[Keys.systemPrompt] ?: "",
            languageTag = languageTag,
        )
    }

    suspend fun currentSettings(): AppSettings = settings.first()

    suspend fun currentLanguageTag(): String = currentSettings().languageTag

    suspend fun save(settings: AppSettings) {
        context.appSettingsDataStore.edit { preferences ->
            val codexModels = AppSettings.normalizeModels(
                models = settings.codexModels,
                selectedModel = settings.selectedModelFor(ChatProvider.CODEX),
            )
            val claudeModels = AppSettings.normalizeModels(
                models = settings.claudeModels,
                selectedModel = settings.selectedModelFor(ChatProvider.CLAUDE),
            )
            preferences[Keys.chatProvider] = settings.chatProvider.storageValue
            preferences[Keys.baseUrl] = settings.baseUrl.trim()
            preferences[Keys.apiKey] = settings.apiKey.trim()
            preferences[Keys.codexBaseUrl] = settings.codexBaseUrl.trim()
            preferences[Keys.codexApiKey] = settings.codexApiKey.trim()
            preferences[Keys.claudeBaseUrl] = settings.claudeBaseUrl.trim()
            preferences[Keys.claudeApiKey] = settings.claudeApiKey.trim()
            preferences[Keys.imageBaseUrl] = settings.imageBaseUrl.trim()
            preferences[Keys.imageApiKey] = settings.imageApiKey.trim()
            preferences[Keys.modelAlias] = settings.modelAlias.trim()
            preferences[Keys.selectedCodexModel] = settings.selectedModelFor(ChatProvider.CODEX).trim()
            preferences[Keys.selectedClaudeModel] = settings.selectedModelFor(ChatProvider.CLAUDE).trim()
            preferences[Keys.codexModels] = json.encodeToString(codexModels)
            preferences[Keys.claudeModels] = json.encodeToString(claudeModels)
            preferences[Keys.maxContextTokens] = settings.maxContextTokens.coerceAtLeast(1)
            preferences[Keys.reasoningEffort] = settings.reasoningEffort.trim()
            preferences[Keys.systemPrompt] = settings.systemPrompt.trim()
            preferences[Keys.languageTag] = settings.languageTag.trim().ifBlank { AppLanguage.SYSTEM.storageValue }
        }
    }

    suspend fun selectChatModel(
        provider: ChatProvider,
        modelAlias: String,
    ) {
        val normalizedModelAlias = modelAlias.trim()
        if (normalizedModelAlias.isBlank()) return

        context.appSettingsDataStore.edit { preferences ->
            val currentProvider = ChatProvider.fromStorage(preferences[Keys.chatProvider])
            when (provider) {
                ChatProvider.CODEX -> {
                    val models = AppSettings.normalizeModels(
                        models = decodeChatModels(preferences[Keys.codexModels]),
                        selectedModel = normalizedModelAlias,
                    )
                    preferences[Keys.selectedCodexModel] = normalizedModelAlias
                    preferences[Keys.codexModels] = json.encodeToString(models)
                }
                ChatProvider.CLAUDE -> {
                    val models = AppSettings.normalizeModels(
                        models = decodeChatModels(preferences[Keys.claudeModels]),
                        selectedModel = normalizedModelAlias,
                    )
                    preferences[Keys.selectedClaudeModel] = normalizedModelAlias
                    preferences[Keys.claudeModels] = json.encodeToString(models)
                }
            }
            if (currentProvider == provider) {
                preferences[Keys.modelAlias] = normalizedModelAlias
            }
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

    private fun decodeChatModels(raw: String?): List<String> = runCatching {
        if (raw.isNullOrBlank()) {
            emptyList()
        } else {
            json.decodeFromString<List<String>>(raw)
        }
    }.getOrElse { emptyList() }
}
