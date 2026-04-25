package dev.codex.android.data.model

data class ImageGeneration(
    val id: Long,
    val prompt: String,
    val referenceImagePath: String? = null,
    val generatedImagePath: String? = null,
    val status: ImageGenerationStatus,
    val errorMessage: String = "",
    val createdAt: Long,
    val updatedAt: Long,
)

enum class ImageGenerationStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED;

    companion object {
        fun fromStorage(value: String): ImageGenerationStatus = when (value) {
            "queued" -> QUEUED
            "running" -> RUNNING
            "succeeded" -> SUCCEEDED
            "failed" -> FAILED
            else -> FAILED
        }
    }

    fun toStorage(): String = when (this) {
        QUEUED -> "queued"
        RUNNING -> "running"
        SUCCEEDED -> "succeeded"
        FAILED -> "failed"
    }
}
