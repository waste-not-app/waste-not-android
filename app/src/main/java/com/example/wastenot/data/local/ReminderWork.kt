package com.example.wastenot.data.local

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.Operation
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import androidx.work.workDataOf
import com.example.wastenot.MainActivity
import com.example.wastenot.R
import com.example.wastenot.WasteNotApplication
import java.io.IOException
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first

interface ReminderScheduler {
    fun schedule(ownerId: String, hour: Int, minute: Int, zoneId: ZoneId): Operation
    fun cancel(ownerId: String): Operation
}

enum class ReminderSaveResult {
    SAVED,
    PERSISTENCE_FAILED,
    SCHEDULING_FAILED,
}

internal suspend fun saveReminderSetting(
    ownerId: String,
    setting: ReminderSetting,
    zoneId: ZoneId,
    store: ReminderStore?,
    scheduler: ReminderScheduler?,
): ReminderSaveResult {
    if (store == null) return ReminderSaveResult.PERSISTENCE_FAILED
    try {
        store.save(ownerId, setting)
    } catch (_: Exception) {
        currentCoroutineContext().ensureActive()
        return ReminderSaveResult.PERSISTENCE_FAILED
    }
    if (scheduler == null) return ReminderSaveResult.SCHEDULING_FAILED
    currentCoroutineContext().ensureActive()
    try {
        if (setting.enabled) scheduler.schedule(ownerId, setting.hour, setting.minute, zoneId).await()
        else scheduler.cancel(ownerId).await()
    } catch (_: Exception) {
        currentCoroutineContext().ensureActive()
        return ReminderSaveResult.SCHEDULING_FAILED
    }
    return ReminderSaveResult.SAVED
}

internal fun computeInitialDelayMillis(
    now: ZonedDateTime,
    hour: Int,
    minute: Int,
): Long {
    var next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
    if (!next.isAfter(now)) next = next.plusDays(1)
    return Duration.between(now, next).toMillis().coerceAtLeast(60_000L)
}

class WorkManagerReminderScheduler(context: Context) : ReminderScheduler {
    private val work = WorkManager.getInstance(context.applicationContext)

    override fun schedule(ownerId: String, hour: Int, minute: Int, zoneId: ZoneId): Operation {
        val delayMs = computeInitialDelayMillis(ZonedDateTime.now(zoneId), hour, minute)
        val request = PeriodicWorkRequestBuilder<DailyReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("owner" to ownerId, "zone" to zoneId.id))
            .build()
        // Updating existing periodic work keeps its cadence instead of applying the new initial delay.
        return work.enqueueUniquePeriodicWork(
            uniqueName(ownerId),
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            request,
        )
    }

    override fun cancel(ownerId: String): Operation = work.cancelUniqueWork(uniqueName(ownerId))

    private fun uniqueName(ownerId: String) = "daily-reminder-$ownerId"
}

class DailyReminderWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val owner = inputData.getString("owner") ?: return Result.success()
        val zoneId = runCatching { ZoneId.of(inputData.getString("zone") ?: "Asia/Jakarta") }
            .getOrDefault(ZoneId.of("Asia/Jakarta"))
        val container = (applicationContext as WasteNotApplication).container
        if (!container.isReminderEligible(owner, zoneId)) return Result.success()
        // A failed cancellation can leave a previously enqueued worker behind.
        val enabled = try {
            container.reminders.observe(owner).first().enabled
        } catch (_: IOException) {
            return Result.retry()
        }
        if (!enabled) return Result.success()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        ensureChannel(applicationContext)
        val launch = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            applicationContext,
            owner.hashCode(),
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_clock)
            .setContentTitle(applicationContext.getString(R.string.reminder_notif_title))
            .setContentText(applicationContext.getString(R.string.reminder_notif_body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(owner.hashCode(), notification)
        return Result.success()
    }

    companion object {
        private const val CHANNEL_ID = "wastenot_daily_reminder"

        private fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.reminder_channel_name),
                        NotificationManager.IMPORTANCE_DEFAULT,
                    ),
                )
            }
        }
    }
}
