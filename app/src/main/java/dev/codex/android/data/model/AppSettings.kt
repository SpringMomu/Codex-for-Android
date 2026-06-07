package dev.codex.android.data.model

data class AppSettings(
    val chatProvider: ChatProvider = ChatProvider.CODEX,
    val baseUrl: String = "https://api.openai.com",
    val apiKey: String = "",
    val imageBaseUrl: String = "",
    val imageApiKey: String = "",
    val modelAlias: String = "gpt-5.4",
    val maxContextTokens: Int = DEFAULT_MAX_CONTEXT_TOKENS,
    val reasoningEffort: String = "high",
    val systemPrompt: String = "",
    val languageTag: String = "system",
) {
    companion object {
        const val DEFAULT_MAX_CONTEXT_TOKENS = 200_000
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
