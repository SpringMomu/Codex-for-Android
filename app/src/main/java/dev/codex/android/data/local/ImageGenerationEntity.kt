package dev.codex.android.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "image_generations")
data class ImageGenerationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val prompt: String,
    val referenceImagePath: String? = null,
    val generatedImagePath: String? = null,
    val status: String,
    val errorMessage: String = "",
    val createdAt: Long,
    val updatedAt: Long,
)
