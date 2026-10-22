package com.example.wastenot.data.dummy

import com.example.wastenot.model.UserProgress
import com.example.wastenot.model.WasteLog
import java.time.LocalDate

fun dummyProgress(records: List<WasteLog>, today: LocalDate): UserProgress {
    val xp = Math.multiplyExact(records.size, 10)
    // Streak remains absent while the optional capability is disabled.
    return UserProgress(records.size, xp, xp / 100 + 1, xp % 100, 100 - xp % 100, null, null, records.any { it.activityDate == today })
}
