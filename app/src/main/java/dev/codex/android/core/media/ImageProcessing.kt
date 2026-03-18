package dev.codex.android.core.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

object ImageProcessing {
    private const val MAX_IMPORTED_LONG_EDGE = 3_072
    private const val MAX_UPLOAD_LONG_EDGE = 3_072
    private const val MAX_RAW_UPLOAD_BYTES = 8L * 1024L * 1024L
    private const val JPEG_QUALITY = 92

    data class PreparedImage(
        val mimeType: String,
        val bytes: ByteArray,
    )

    fun normalizeImportedImage(file: File, mimeType: String): Boolean {
        if (!file.exists() || file.length() == 0L || mimeType.endsWith("gif")) return false

        val bounds = readBounds(file) ?: return false
        val longestEdge = max(bounds.width, bounds.height)
        val shouldResize = longestEdge > MAX_IMPORTED_LONG_EDGE
        val shouldRewrite = shouldResize || !supportsDirectStorage(mimeType)
        if (!shouldRewrite) return false

        val targetSize = scaledSize(bounds.width, bounds.height, MAX_IMPORTED_LONG_EDGE)
        val bitmap = decodeBitmap(file, targetSize.width, targetSize.height) ?: return false
        return writeBitmapInPlace(
            file = file,
            bitmap = bitmap,
            format = preferredOutputFormat(sourceMimeType = mimeType, bitmap = bitmap),
        )
    }

    fun prepareImageForUpload(path: String): PreparedImage? {
        val file = File(path)
        if (!file.exists() || file.length() == 0L) return null

        val mimeType = mimeTypeFromFile(file)
        if (mimeType == "image/gif") {
            return PreparedImage(
                mimeType = mimeType,
                bytes = file.readBytes(),
            )
        }

        val bounds = readBounds(file)
        if (bounds == null) {
            return PreparedImage(
                mimeType = mimeType,
                bytes = file.readBytes(),
            )
        }

        val longestEdge = max(bounds.width, bounds.height)
        val shouldNormalize = longestEdge > MAX_UPLOAD_LONG_EDGE || file.length() > MAX_RAW_UPLOAD_BYTES
        if (!shouldNormalize) {
            return PreparedImage(
                mimeType = mimeType,
                bytes = file.readBytes(),
            )
        }

        val targetSize = scaledSize(bounds.width, bounds.height, MAX_UPLOAD_LONG_EDGE)
        val bitmap = decodeBitmap(file, targetSize.width, targetSize.height) ?: return PreparedImage(
            mimeType = mimeType,
            bytes = file.readBytes(),
        )
        val format = preferredOutputFormat(sourceMimeType = mimeType, bitmap = bitmap)
        val bytes = compressBitmap(bitmap, format)
        return PreparedImage(
            mimeType = mimeTypeFor(format),
            bytes = bytes,
        )
    }

    fun loadBitmapForDisplay(
        path: String,
        targetWidthPx: Int,
        targetHeightPx: Int,
    ): Bitmap? {
        val file = File(path)
        if (!file.exists() || file.length() == 0L) return null

        val safeWidth = targetWidthPx.coerceAtLeast(1)
        val safeHeight = targetHeightPx.coerceAtLeast(1)
        return decodeBitmap(file, safeWidth, safeHeight)
    }

    private fun decodeBitmap(
        file: File,
        targetWidth: Int,
        targetHeight: Int,
    ): Bitmap? {
        val bounds = readBounds(file) ?: return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(
                width = bounds.width,
                height = bounds.height,
                targetWidth = targetWidth,
                targetHeight = targetHeight,
            )
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
        val rotated = applyExifOrientation(file, decoded)
        return scaleDownIfNeeded(
            bitmap = rotated,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
        )
    }

    private fun readBounds(file: File): ImageBounds? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        return ImageBounds(
            width = options.outWidth,
            height = options.outHeight,
        )
    }

    private fun calculateInSampleSize(
        width: Int,
        height: Int,
        targetWidth: Int,
        targetHeight: Int,
    ): Int {
        var sampleSize = 1
        var sampledWidth = width
        var sampledHeight = height

        while (sampledWidth / 2 >= targetWidth && sampledHeight / 2 >= targetHeight) {
            sampledWidth /= 2
            sampledHeight /= 2
            sampleSize *= 2
        }

        return sampleSize
    }

    private fun applyExifOrientation(
        file: File,
        bitmap: Bitmap,
    ): Bitmap {
        val orientation = runCatching {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix().applyOrientation(orientation)
        if (matrix.isIdentity) return bitmap

        val transformed = Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true,
        )
        if (transformed != bitmap) {
            bitmap.recycle()
        }
        return transformed
    }

    private fun Matrix.applyOrientation(orientation: Int): Matrix = apply {
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                postRotate(90f)
                postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                postRotate(-90f)
                postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
        }
    }

    private fun scaleDownIfNeeded(
        bitmap: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
    ): Bitmap {
        if (bitmap.width <= targetWidth && bitmap.height <= targetHeight) {
            return bitmap
        }

        val scale = minOf(
            targetWidth.toFloat() / bitmap.width.toFloat(),
            targetHeight.toFloat() / bitmap.height.toFloat(),
        )
        val scaledWidth = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val scaledHeight = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
        if (scaled != bitmap) {
            bitmap.recycle()
        }
        return scaled
    }

    private fun scaledSize(
        width: Int,
        height: Int,
        maxLongEdge: Int,
    ): ImageBounds {
        val longestEdge = max(width, height)
        if (longestEdge <= maxLongEdge) {
            return ImageBounds(width, height)
        }
        val scale = maxLongEdge.toFloat() / longestEdge.toFloat()
        return ImageBounds(
            width = (width * scale).roundToInt().coerceAtLeast(1),
            height = (height * scale).roundToInt().coerceAtLeast(1),
        )
    }

    private fun compressBitmap(
        bitmap: Bitmap,
        format: Bitmap.CompressFormat,
    ): ByteArray {
        val output = ByteArrayOutputStream()
        bitmap.useAndRecycle {
            compress(format, compressionQuality(format), output)
        }
        return output.toByteArray()
    }

    private fun writeBitmapInPlace(
        file: File,
        bitmap: Bitmap,
        format: Bitmap.CompressFormat,
    ): Boolean {
        val tempFile = File(file.parentFile, "${file.name}.tmp")
        val wrote = runCatching {
            tempFile.outputStream().use { output ->
                bitmap.useAndRecycle {
                    compress(format, compressionQuality(format), output)
                }
            }
            true
        }.getOrDefault(false)
        if (!wrote) {
            tempFile.delete()
            return false
        }

        return runCatching {
            if (file.exists()) {
                file.delete()
            }
            tempFile.renameTo(file)
        }.getOrElse {
            tempFile.delete()
            false
        }
    }

    private inline fun Bitmap.useAndRecycle(block: Bitmap.() -> Unit) {
        try {
            block()
        } finally {
            recycle()
        }
    }

    private fun preferredOutputFormat(
        sourceMimeType: String,
        bitmap: Bitmap,
    ): Bitmap.CompressFormat {
        return when {
            sourceMimeType.endsWith("png") -> Bitmap.CompressFormat.PNG
            bitmap.hasAlpha() -> Bitmap.CompressFormat.PNG
            else -> Bitmap.CompressFormat.JPEG
        }
    }

    private fun compressionQuality(format: Bitmap.CompressFormat): Int = when (format) {
        Bitmap.CompressFormat.PNG -> 100
        else -> JPEG_QUALITY
    }

    private fun supportsDirectStorage(mimeType: String): Boolean = mimeType.endsWith("jpeg") ||
        mimeType.endsWith("jpg") ||
        mimeType.endsWith("png") ||
        mimeType.endsWith("webp")

    private fun mimeTypeFromFile(file: File): String = when (file.extension.lowercase()) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        else -> "image/jpeg"
    }

    private fun mimeTypeFor(format: Bitmap.CompressFormat): String = when (format) {
        Bitmap.CompressFormat.PNG -> "image/png"
        else -> "image/jpeg"
    }

    private data class ImageBounds(
        val width: Int,
        val height: Int,
    )
}
