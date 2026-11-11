package com.example.wastenot.ui.scan

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.ExifInterface
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import com.example.wastenot.data.local.ScanPhotoCache
import com.example.wastenot.data.local.ScanPhotoCleanupWorker
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class ScanPhotoCacheTest {
    @Test fun schedulesCleanupForAnInterruptedCapture() {
        val registry = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("verifyScheduledPhotoCleanup") == "true")
        val context = registry.targetContext
        val worker = WorkManager.getInstance(context)
        val tag = ScanPhotoCleanupWorker::class.java.name
        val before = worker.getWorkInfosByTag(tag).get(5, TimeUnit.SECONDS).map { it.id }.toSet()
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        val jpeg = ByteArrayOutputStream()
        jpeg.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        val cache = ScanPhotoCache.create(context, "interrupted-cleanup-qa")
        val start = System.currentTimeMillis()
        val photo = cache.save(jpeg.toByteArray(), 0)
        val scheduled = worker.getWorkInfosByTag(tag).get(5, TimeUnit.SECONDS).single { it.id !in before }
        val delayMs = scheduled.nextScheduleTimeMillis - start
        assertTrue(delayMs in 3_600_000..3_610_000)
        assertTrue(cache.exists(photo))
        context.cacheDir.resolve("photo-cleanup-probe").writeText("${File(photo.path).name}\n${scheduled.id}")
        println("PHOTO_CLEANUP scheduledDelayMs=$delayMs")
    }

    @Test fun normalizesRotationStripsExifSchedulesCleanupAndEnforcesOwner() {
        val root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "scan-test-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val input = File(root, "input.jpg")
            val bitmap = Bitmap.createBitmap(100, 60, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap); canvas.drawColor(Color.BLUE)
            canvas.drawRect(0f, 0f, 50f, 60f, Paint().apply { color = Color.RED })
            input.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }; bitmap.recycle()
            ExifInterface(input.path).apply {
                setAttribute(ExifInterface.TAG_MAKE, "PrivateCamera")
                setAttribute(ExifInterface.TAG_GPS_LATITUDE, "6/1,0/1,0/1")
                setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "S")
                saveAttributes()
            }
            val scheduled = mutableListOf<String>()
            val cache = ScanPhotoCache(root, "account-a", scheduled::add)
            val photo = cache.save(input.readBytes(), 90)
            assertEquals(60, photo.width); assertEquals(100, photo.height)
            assertEquals(listOf(File(photo.path).name), scheduled)
            val exif = ExifInterface(photo.path)
            assertNull(exif.getAttribute(ExifInterface.TAG_MAKE)); assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
            val normalized = android.graphics.BitmapFactory.decodeFile(photo.path)
            assertTrue(Color.red(normalized.getPixel(30, 20)) > 200)
            assertTrue(Color.blue(normalized.getPixel(30, 80)) > 200); normalized.recycle()
            val other = ScanPhotoCache(root, "account-b") {}
            assertArrayEquals(File(photo.path).readBytes(), cache.read(photo))
            assertThrows(java.io.IOException::class.java) { other.read(photo) }
            assertThrows(java.io.IOException::class.java) { cache.read(photo.copy(path = input.path)) }
            assertFalse(other.exists(photo)); other.delete(photo); assertTrue(cache.exists(photo))
            val otherPhoto = other.save(input.readBytes(), 0)
            cache.removeOwnerPhotos()
            assertFalse(cache.exists(photo))
            assertTrue(other.exists(otherPhoto))
            assertTrue(input.isFile)
        } finally { root.deleteRecursively() }
    }

    @Test fun rejectsOversizeAndMalformedPhotosAndSweepsOnlyExpiredPrivateFiles() {
        val root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "scan-test-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val cache = ScanPhotoCache(root, "account") {}
            assertThrows(IllegalArgumentException::class.java) { cache.save(ByteArray(5 * 1024 * 1024 + 1), 0) }
            assertThrows(IllegalArgumentException::class.java) { cache.save("not-jpeg".toByteArray(), 0) }
            val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
            val stream = ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream); bitmap.recycle()
            val photo = cache.save(stream.toByteArray(), 0)
            val file = File(photo.path); val now = System.currentTimeMillis()
            file.outputStream().use { it.write(ByteArray(5 * 1024 * 1024 + 1)) }
            assertThrows(IllegalArgumentException::class.java) { cache.read(photo) }
            assertTrue(file.setLastModified(now - 3_600_000))
            cache.removeExpired(now); assertFalse(cache.exists(photo))
            assertThrows(IllegalArgumentException::class.java) { cache.save(stream.toByteArray(), 45) }
        } finally { root.deleteRecursively() }
    }
}
