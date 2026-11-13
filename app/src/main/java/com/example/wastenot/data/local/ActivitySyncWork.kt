package com.example.wastenot.data.local

import android.content.Context
import androidx.work.*
import com.example.wastenot.WasteNotApplication
import java.util.concurrent.TimeUnit

class ActivitySyncScheduler(context: Context) {
    private val work = WorkManager.getInstance(context.applicationContext)

    fun enqueue(owner: String) {
        val request = OneTimeWorkRequestBuilder<ActivitySyncWorker>()
            .setInputData(workDataOf("owner" to owner))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        // Appending preserves a write queued while the previous worker is finishing.
        work.enqueueUniqueWork("activity-sync-$owner", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }
}

class ActivitySyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val owner = inputData.getString("owner") ?: return Result.failure()
        val container = (applicationContext as WasteNotApplication).container
        return try {
            if (container.synchronizeActivities(owner)) Result.success() else Result.retry()
        } catch (_: android.database.sqlite.SQLiteException) {
            Result.retry()
        }
    }
}
