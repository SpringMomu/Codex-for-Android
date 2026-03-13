package dev.codex.android.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ConversationScrollPosition(
    val anchorMessageId: Long? = null,
    val firstVisibleItemIndex: Int = 0,
    val firstVisibleItemScrollOffset: Int = 0,
)
