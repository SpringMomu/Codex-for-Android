package dev.codex.android.data.repository

import android.net.Uri
import dev.codex.android.data.local.AttachmentStorage
import dev.codex.android.data.local.ImageGenerationDao
import dev.codex.android.data.local.ImageGenerationEntity
import dev.codex.android.data.model.ImageGeneration
import dev.codex.android.data.model.ImageGenerationStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ImageGenerationRepository(
    private val imageGenerationDao: ImageGenerationDao,
    private val attachmentStorage: AttachmentStorage,
) {
    private val json = Json

    fun observeGenerations(): Flow<List<ImageGeneration>> = imageGenerationDao.observeGenerations().map { items ->
        items.map(::toModel)
    }

    suspend fun importReferenceImages(uris: List<Uri>): List<String> = attachmentStorage.importImages(uris)

    suspend fun createGeneration(
        prompt: String,
        referenceImagePaths: List<String>,
    ): Long {
        val now = System.currentTimeMillis()
        return imageGenerationDao.insertGeneration(
            ImageGenerationEntity(
                prompt = prompt,
                referenceImagePath = referenceImagePaths.firstOrNull(),
                referenceImagePaths = encodeReferenceImagePaths(referenceImagePaths),
                status = ImageGenerationStatus.QUEUED.toStorage(),
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun getGeneration(id: Long): ImageGeneration? = imageGenerationDao.getGeneration(id)?.let(::toModel)

    suspend fun getPendingGenerations(): List<ImageGeneration> = imageGenerationDao.getPendingGenerations().map(::toModel)

    suspend fun markRunning(id: Long) {
        updateStatus(
            id = id,
            status = ImageGenerationStatus.RUNNING,
            generatedImagePath = null,
            errorMessage = "",
        )
    }

    suspend fun markSucceeded(
        id: Long,
        imageBase64: String,
    ) {
        val imagePath = attachmentStorage.saveGeneratedImage(imageBase64)
        updateStatus(
            id = id,
            status = ImageGenerationStatus.SUCCEEDED,
            generatedImagePath = imagePath,
            errorMessage = "",
        )
    }

    suspend fun markFailed(
        id: Long,
        errorMessage: String,
    ) {
        updateStatus(
            id = id,
            status = ImageGenerationStatus.FAILED,
            generatedImagePath = null,
            errorMessage = errorMessage,
        )
    }

    suspend fun retry(id: Long): Boolean {
        val generation = imageGenerationDao.getGeneration(id) ?: return false
        imageGenerationDao.updateGeneration(
            generation.copy(
                status = ImageGenerationStatus.QUEUED.toStorage(),
                errorMessage = "",
                generatedImagePath = null,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        return true
    }

    suspend fun deleteGeneration(id: Long) {
        val generation = imageGenerationDao.getGeneration(id) ?: return
        imageGenerationDao.deleteGeneration(id)
        attachmentStorage.deleteAttachments(
            decodeReferenceImagePaths(generation).plus(listOfNotNull(generation.generatedImagePath)),
        )
    }

    suspend fun deleteLocalAttachment(path: String) {
        attachmentStorage.deleteAttachments(listOf(path))
    }

    suspend fun saveImageToGallery(path: String): Uri? = attachmentStorage.saveImageToGallery(path)

    private suspend fun updateStatus(
        id: Long,
        status: ImageGenerationStatus,
        generatedImagePath: String?,
        errorMessage: String,
    ) {
        val generation = imageGenerationDao.getGeneration(id) ?: return
        imageGenerationDao.updateGeneration(
            generation.copy(
                status = status.toStorage(),
                generatedImagePath = generatedImagePath,
                errorMessage = errorMessage,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    private fun toModel(entity: ImageGenerationEntity): ImageGeneration = ImageGeneration(
        id = entity.id,
        prompt = entity.prompt,
        referenceImagePaths = decodeReferenceImagePaths(entity),
        generatedImagePath = entity.generatedImagePath,
        status = ImageGenerationStatus.fromStorage(entity.status),
        errorMessage = entity.errorMessage,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
    )

    private fun encodeReferenceImagePaths(paths: List<String>): String = json.encodeToString(paths)

    private fun decodeReferenceImagePaths(entity: ImageGenerationEntity): List<String> {
        val decoded = runCatching {
            json.decodeFromString<List<String>>(entity.referenceImagePaths)
        }.getOrElse { emptyList() }
        return (decoded + listOfNotNull(entity.referenceImagePath)).distinct()
    }
}
