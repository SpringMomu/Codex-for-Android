package dev.codex.android.data.local

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
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
            destination.absolutePath
        }.getOrNull()
    }
}
