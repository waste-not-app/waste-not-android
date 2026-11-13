package com.example.wastenot.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PreparedAttachment(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
)

sealed interface PrepareAttachmentResult {
    data class Success(val photo: PreparedAttachment) : PrepareAttachmentResult
    data object UnsupportedFormat : PrepareAttachmentResult
    data object TooLarge : PrepareAttachmentResult
    data object Unreadable : PrepareAttachmentResult
}

internal object AttachmentImagePreparer {
    private const val MAX_BYTES = 5 * 1024 * 1024
    private const val MAX_DIMENSION = 4096

    suspend fun prepare(context: Context, uri: Uri): PrepareAttachmentResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val rawMime = resolver.getType(uri)
        val raw = try {
            resolver.openInputStream(uri)?.use { stream ->
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(16_384)
                var total = 0
                while (true) {
                    val read = stream.read(chunk)
                    if (read <= 0) break
                    total += read
                    if (total > 20 * 1024 * 1024) return@withContext PrepareAttachmentResult.TooLarge
                    buffer.write(chunk, 0, read)
                }
                buffer.toByteArray()
            } ?: return@withContext PrepareAttachmentResult.Unreadable
        } catch (_: Exception) {
            return@withContext PrepareAttachmentResult.Unreadable
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        val detectedMime = bounds.outMimeType ?: rawMime
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return@withContext PrepareAttachmentResult.Unreadable
        }
        if (detectedMime !in setOf("image/jpeg", "image/png", "image/webp")) {
            return@withContext PrepareAttachmentResult.UnsupportedFormat
        }

        val fileName = resolveFileName(context, uri)
        val withinLimits = raw.size <= MAX_BYTES &&
            bounds.outWidth <= MAX_DIMENSION &&
            bounds.outHeight <= MAX_DIMENSION &&
            bounds.outWidth.toLong() * bounds.outHeight <= 12_000_000L

        if (withinLimits) {
            return@withContext PrepareAttachmentResult.Success(
                PreparedAttachment(fileName, detectedMime!!, raw),
            )
        }

        // Downscale & compress to JPEG within backend limits (<=5 MiB, <=12 MP, <=4096px).
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size, opts)
            ?: return@withContext PrepareAttachmentResult.Unreadable
        try {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            val jpegBytes = out.toByteArray()
            if (jpegBytes.isEmpty() || jpegBytes.size > MAX_BYTES) {
                return@withContext PrepareAttachmentResult.TooLarge
            }
            val jpegName = fileName.substringBeforeLast('.') + ".jpg"
            PrepareAttachmentResult.Success(PreparedAttachment(jpegName, "image/jpeg", jpegBytes))
        } finally {
            bitmap.recycle()
        }
    }

    private fun resolveFileName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) {
                    val name = cursor.getString(idx)?.trim()
                    if (!name.isNullOrEmpty()) return name
                }
            }
        }
        return "lampiran.jpg"
    }
}
