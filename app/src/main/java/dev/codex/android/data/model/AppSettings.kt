package dev.codex.android.data.model

data class AppSettings(
    val baseUrl: String = "https://api.openai.com",
    val apiKey: String = "",
    val imageBaseUrl: String = "",
    val imageApiKey: String = "",
    val modelAlias: String = "gpt-5.4",
    val reasoningEffort: String = "high",
    val systemPrompt: String = "",
    val languageTag: String = "system",
)
