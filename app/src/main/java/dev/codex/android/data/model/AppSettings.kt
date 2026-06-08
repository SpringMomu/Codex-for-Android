package dev.codex.android.data.model

data class AppSettings(
    val chatProvider: ChatProvider = ChatProvider.CODEX,
    val codexBaseUrl: String = DEFAULT_CODEX_BASE_URL,
    val codexApiKey: String = "",
    val claudeBaseUrl: String = DEFAULT_CLAUDE_BASE_URL,
    val claudeApiKey: String = "",
    val imageBaseUrl: String = "",
    val imageApiKey: String = "",
    val selectedCodexModel: String = "",
    val selectedClaudeModel: String = "",
    val codexModels: List<String> = emptyList(),
    val claudeModels: List<String> = emptyList(),
    val maxContextTokens: Int = DEFAULT_MAX_CONTEXT_TOKENS,
    val reasoningEffort: String = "high",
    val systemPrompt: String = "",
    val languageTag: String = "system",
) {
    val baseUrl: String
        get() = when (chatProvider) {
            ChatProvider.CODEX -> codexBaseUrl
            ChatProvider.CLAUDE -> claudeBaseUrl
        }

    val apiKey: String
        get() = when (chatProvider) {
            ChatProvider.CODEX -> codexApiKey
            ChatProvider.CLAUDE -> claudeApiKey
        }

    val modelAlias: String
        get() = selectedModelFor(chatProvider)

    fun modelsFor(provider: ChatProvider): List<String> = when (provider) {
        ChatProvider.CODEX -> normalizeModels(codexModels, selectedCodexModel)
        ChatProvider.CLAUDE -> normalizeModels(claudeModels, selectedClaudeModel)
    }

    fun selectedModelFor(provider: ChatProvider): String {
        val selected = when (provider) {
            ChatProvider.CODEX -> selectedCodexModel
            ChatProvider.CLAUDE -> selectedClaudeModel
        }.trim()
        return selected.ifBlank { modelsFor(provider).firstOrNull().orEmpty() }
    }

    companion object {
        const val DEFAULT_CODEX_BASE_URL = "https://api.openai.com"
        const val DEFAULT_CLAUDE_BASE_URL = "https://ai.furrist.com"
        const val DEFAULT_MAX_CONTEXT_TOKENS = 200_000

        fun normalizeModels(
            models: List<String>,
            selectedModel: String? = null,
        ): List<String> = buildList {
            addAll(models)
            if (!selectedModel.isNullOrBlank()) {
                add(selectedModel)
            }
        }
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
    }
}

enum class ChatProvider(
    val storageValue: String,
) {
    CODEX("codex"),
    CLAUDE("claude");

    companion object {
        fun fromStorage(value: String?): ChatProvider = when (value?.lowercase()) {
            CLAUDE.storageValue -> CLAUDE
            else -> CODEX
        }
    }
}
