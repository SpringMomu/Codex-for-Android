package dev.codex.android.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "image_generations")
data class ImageGenerationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val prompt: String,
    val referenceImagePath: String? = null,
    @ColumnInfo(defaultValue = "'[]'") val referenceImagePaths: String = "[]",
    val generatedImagePath: String? = null,
    val status: String,
    val errorMessage: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "NULL") val startedAt: Long? = null,
    @ColumnInfo(defaultValue = "NULL") val completedAt: Long? = null,
)
