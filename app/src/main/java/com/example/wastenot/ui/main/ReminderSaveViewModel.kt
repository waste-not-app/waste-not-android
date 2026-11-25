package com.example.wastenot.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wastenot.data.local.ReminderSaveResult
import com.example.wastenot.data.local.ReminderScheduler
import com.example.wastenot.data.local.ReminderSetting
import com.example.wastenot.data.local.ReminderStore
import com.example.wastenot.data.local.saveReminderSetting
import java.time.ZoneId
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A submitted setting and its outcome; a null result means the operation is still running. */
data class ReminderSaveProgress(val setting: ReminderSetting, val result: ReminderSaveResult? = null)

internal class ReminderSaveViewModel : ViewModel() {
    private val mutex = Mutex()
    private val mutableProgress = MutableStateFlow<ReminderSaveProgress?>(null)
    val progress = mutableProgress.asStateFlow()

    suspend fun save(
        ownerId: String,
        setting: ReminderSetting,
        zoneId: ZoneId,
        store: ReminderStore?,
        scheduler: ReminderScheduler?,
    ): ReminderSaveResult = viewModelScope.async {
        mutex.withLock {
            val attempt = ReminderSaveProgress(setting)
            mutableProgress.value = attempt
            val result = saveReminderSetting(ownerId, setting, zoneId, store, scheduler)
            mutableProgress.value = attempt.copy(result = result)
            result
        }
    }.await()
}
