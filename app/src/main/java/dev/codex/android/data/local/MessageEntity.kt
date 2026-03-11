package dev.codex.android.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["conversationId"])],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val role: String,
    val content: String,
    @ColumnInfo(defaultValue = "'[]'") val imagePaths: String = "[]",
    @ColumnInfo(defaultValue = "''") val reasoningSummary: String = "",
    @ColumnInfo(defaultValue = "'[]'") val activityLog: String = "[]",
    @ColumnInfo(defaultValue = "''") val webSearchState: String = "",
    val createdAt: Long,
    val isError: Boolean,
)
