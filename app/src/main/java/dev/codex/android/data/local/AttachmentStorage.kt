package dev.codex.android.data.local

import android.content.Context
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import dev.codex.android.core.media.ImageProcessing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Base64
import java.util.UUID

class AttachmentStorage(
    private val context: Context,
) {
    suspend fun importImages(uris: List<Uri>): List<String> = withContext(Dispatchers.IO) {
        uris.mapNotNull(::copyImageToAppStorage)
    }

    suspend fun deleteAttachments(paths: List<String>) = withContext(Dispatchers.IO) {
        paths.forEach { path ->
            runCatching { File(path).delete() }
        }
    }

    suspend fun saveGeneratedImage(base64: String): String = withContext(Dispatchers.IO) {
        val directory = File(context.filesDir, "generated-images").apply { mkdirs() }
        val destination = File(directory, "${UUID.randomUUID()}.png")
        destination.writeBytes(Base64.getDecoder().decode(base64))
        destination.absolutePath
    }

    suspend fun saveImageToGallery(path: String): Uri? = withContext(Dispatchers.IO) {
        val source = File(path)
        if (!source.exists() || source.length() == 0L) return@withContext null

        val resolver = context.contentResolver
        val mimeType = when (source.extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            else -> "image/png"
        }
        val extension = when (mimeType) {
            "image/jpeg" -> "jpg"
            "image/webp" -> "webp"
            else -> "png"
        }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "codex-image-${System.currentTimeMillis()}.$extension")
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Codex")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return@withContext null
        runCatching {
            resolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { input ->
                    input.copyTo(output)
                }
            } ?: error("Unable to open gallery destination")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val completedValues = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                resolver.update(uri, completedValues, null, null)
            }
            uri
        }.getOrElse {
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }

    private fun copyImageToAppStorage(uri: Uri): String? {
        val directory = File(context.filesDir, "attachments").apply { mkdirs() }
        val mimeType = context.contentResolver.getType(uri).orEmpty()
        val extension = when {
            mimeType.endsWith("png") -> ".png"
            mimeType.endsWith("webp") -> ".webp"
            mimeType.endsWith("gif") -> ".gif"
            else -> ".jpg"
        }
        val destination = File(directory, "${UUID.randomUUID()}$extension")

        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                destination.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            ImageProcessing.normalizeImportedImage(
                file = destination,
                mimeType = mimeType,
            )
            destination.absolutePath
        }.getOrNull()
    }
}
