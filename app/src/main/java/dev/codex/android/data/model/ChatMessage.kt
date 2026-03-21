package dev.codex.android.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ChatActivity(
    val label: String,
    val status: String,
    val count: Int = 1,
)

data class ChatMessage(
    val id: Long,
    val conversationId: Long? = null,
    val role: MessageRole,
    val content: String,
    val imagePaths: List<String> = emptyList(),
    val reasoningSummary: String = "",
    val activityLog: List<ChatActivity> = emptyList(),
    val webSearchState: String = "",
    val createdAt: Long,
    val isError: Boolean = false,
)

enum class MessageRole {
    SYSTEM,
    USER,
    ASSISTANT;

    companion object {
        fun fromStorage(value: String): MessageRole = when (value) {
            "system" -> SYSTEM
            "assistant" -> ASSISTANT
            else -> USER
        }
    }

    fun toStorage(): String = when (this) {
        SYSTEM -> "system"
        USER -> "user"
        ASSISTANT -> "assistant"
    }
}
