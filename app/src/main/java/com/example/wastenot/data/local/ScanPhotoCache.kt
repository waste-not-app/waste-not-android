package com.example.wastenot.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.wastenot.model.ScanPhoto
import com.example.wastenot.model.ScanPhotoStore
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

class ScanPhotoCache(private val directory: File, owner: String, private val schedule: (String) -> Unit) : ScanPhotoStore {
    private val prefix = MessageDigest.getInstance("SHA-256").digest(owner.toByteArray()).joinToString("") { "%02x".format(it) }.take(24) + "-"

    fun save(jpeg: ByteArray, rotation: Int, now: Long = System.currentTimeMillis()): ScanPhoto {
        require(jpeg.size in 1..5 * 1024 * 1024 && rotation in listOf(0, 90, 180, 270))
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        require(bounds.outMimeType == "image/jpeg" && bounds.outWidth in 1..4096 && bounds.outHeight in 1..4096 &&
            bounds.outWidth.toLong() * bounds.outHeight <= 12_000_000)
        val options = BitmapFactory.Options().apply {
            while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize.coerceAtLeast(1) > 1600) inSampleSize = inSampleSize.coerceAtLeast(1) * 2
        }
        val decoded = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, options) ?: throw IOException("Invalid photo")
        var normalized: Bitmap? = null
        val file = File(directory, prefix + UUID.randomUUID() + ".jpg")
        val temporary = File(directory, file.name + ".tmp")
        try {
            normalized = if (rotation == 0) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height,
                Matrix().apply { postRotate(rotation.toFloat()) }, true)
            if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Photo directory unavailable")
            temporary.outputStream().use { if (!normalized.compress(Bitmap.CompressFormat.JPEG, 85, it)) throw IOException("Photo write failed") }
            if (temporary.length() > 5 * 1024 * 1024 || !temporary.renameTo(file)) throw IOException("Photo write failed")
            schedule(file.name)
            return ScanPhoto(file.absolutePath, normalized.width, normalized.height, now)
        } catch (error: Exception) {
            file.delete(); throw error
        } finally {
            temporary.delete(); if (normalized !== decoded) normalized?.recycle(); decoded.recycle()
        }
    }

    override fun exists(photo: ScanPhoto) = owned(photo)?.isFile == true
    override fun delete(photo: ScanPhoto) { owned(photo)?.delete() }
    override fun read(photo: ScanPhoto): ByteArray {
        val file = owned(photo) ?: throw IOException("Photo unavailable")
        require(file.length() in 1..5 * 1024 * 1024)
        return file.inputStream().use { input ->
            val buffer = ByteArray(5 * 1024 * 1024 + 1)
            var length = 0
            while (length < buffer.size) {
                val count = input.read(buffer, length, buffer.size - length)
                if (count < 0) break
                length += count
            }
            require(length in 1..5 * 1024 * 1024)
            buffer.copyOf(length)
        }
    }
    private fun owned(photo: ScanPhoto): File? = File(photo.path).takeIf {
        it.name.startsWith(prefix) && it.name.endsWith(".jpg") && it.canonicalFile.parentFile == directory.canonicalFile
    }

    fun removeExpired(now: Long = System.currentTimeMillis()) {
        directory.listFiles()?.filter { it.isFile && now - it.lastModified() >= TimeUnit.HOURS.toMillis(1) }?.forEach { it.delete() }
    }

    companion object {
        fun create(context: Context, owner: String): ScanPhotoCache {
            val app = context.applicationContext
            return ScanPhotoCache(File(app.cacheDir, "scan"), owner) { name ->
                WorkManager.getInstance(app).enqueue(OneTimeWorkRequestBuilder<ScanPhotoCleanupWorker>()
                    .setInitialDelay(1, TimeUnit.HOURS).setInputData(workDataOf("file" to name)).build())
            }
        }
    }
}

class ScanPhotoCleanupWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result {
        val name = inputData.getString("file") ?: return Result.failure()
        if (!name.matches(Regex("[0-9a-f]{24}-[0-9a-f-]{36}\\.jpg"))) return Result.failure()
        val directory = File(applicationContext.cacheDir, "scan")
        val file = File(directory, name)
        if (file.canonicalFile.parentFile != directory.canonicalFile) return Result.failure()
        return if (!file.exists() || file.delete()) Result.success() else Result.retry()
    }
}
