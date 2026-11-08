package com.example.wastenot.data.dummy

import com.example.wastenot.model.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class DummyProgressTest {
    private val log = WasteLog("id", WasteCategory.PLASTIC, DisposalMethod.RECYCLE, Quantity.Weight.of(250),
        LocalDate.of(2026, 10, 22), ZoneId.of("Asia/Jakarta"), "", 1, Instant.EPOCH, Instant.EPOCH)

    @Test fun nineTenAndElevenRecordsCrossTheLevelBoundaryWithoutCarryingOldXp() {
        listOf(9, 10, 11).forEach { count ->
            val progress = dummyProgress(List(count) { log.copy(id = it.toString()) }, log.activityDate)
            assertEquals(count * 10, progress.xp)
            assertEquals(if (count == 9) 1 else 2, progress.level)
            assertEquals(if (count == 9) 90 else if (count == 10) 0 else 10, progress.xpIntoLevel)
            assertEquals(if (count == 9) 10 else if (count == 10) 100 else 90, progress.xpToNextLevel)
            assertEquals(count, progress.activeLogCount)
        }
    }

    @Test fun editDoesNotAwardXpDeleteCanLowerLevelAndEmptyStaysLevelOne() {
        val records = List(10) { log.copy(id = it.toString()) }
        assertEquals(dummyProgress(records, log.activityDate), dummyProgress(records.map { it.copy(method = DisposalMethod.REDUCE, quantity = Quantity.Count.of(5)) }, log.activityDate))
        assertEquals(1, dummyProgress(records.dropLast(1), log.activityDate).level)
        assertTrue(dummyProgress(records, log.activityDate).hasLoggedToday)
        assertFalse(dummyProgress(records, log.activityDate.plusDays(1)).hasLoggedToday)
        val empty = dummyProgress(emptyList(), log.activityDate)
        assertEquals(0, empty.xp)
        assertEquals(1, empty.level)
        assertEquals(100, empty.xpToNextLevel)
        assertNull(empty.currentStreak)
        assertNull(empty.bestStreak)
    }
}
