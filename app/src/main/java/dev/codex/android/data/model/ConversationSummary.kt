package dev.codex.android.data.model

data class ConversationSummary(
    val id: Long,
    val title: String,
    val preview: String,
    val createdAt: Long,
    val updatedAt: Long,
)
