package dev.codex.android.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val lastPreview: String,
    val createdAt: Long,
    val updatedAt: Long,
)
