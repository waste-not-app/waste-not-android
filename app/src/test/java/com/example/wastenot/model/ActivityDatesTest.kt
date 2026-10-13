package com.example.wastenot.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityDatesTest {
    private val today = LocalDate.parse("2026-09-21")

    @Test
    fun accountDateFollowsTheAccountZoneAroundMidnightAndDst() {
        val serverInstant = Instant.parse("2026-09-20T17:00:00Z")
        assertEquals(today, ActivityDates.accountDate(serverInstant, ZoneId.of("Asia/Jakarta")))
        assertEquals(today.minusDays(1), ActivityDates.accountDate(serverInstant, ZoneId.of("UTC")))
        assertEquals(LocalDate.parse("2026-03-29"), ActivityDates.accountDate(
            Instant.parse("2026-03-28T23:30:00Z"), ZoneId.of("Europe/Berlin")))
    }

    @Test
    fun sevenAndThirtyDayRangesAreInclusiveAndEndAtTheServerDate() {
        val seven = ActivityDateRange.lastDays(today, 7)
        assertEquals(LocalDate.parse("2026-09-15"), seven.from)
        assertEquals(today, seven.to)
        assertTrue(today in seven)
        assertTrue(seven.from in seven)
        assertFalse(seven.from.minusDays(1) in seven)
        assertFalse(today.plusDays(1) in seven)
        assertEquals(LocalDate.parse("2026-08-23"), ActivityDateRange.lastDays(today, 30).from)
        assertEquals(today.minusDays(365), ActivityDateRange.lastDays(today, 366).from)
        assertThrows(IllegalArgumentException::class.java) { ActivityDateRange.lastDays(today, 367) }
        assertThrows(IllegalArgumentException::class.java) { ActivityDateRange.lastDays(today, 0) }
        assertThrows(IllegalArgumentException::class.java) { ActivityDateRange(today, today.minusDays(1)) }
    }

    @Test
    fun createAllowsThirtyDaysBeforeTodayAndEditCanKeepItsOriginalOldDate() {
        assertTrue(ActivityDates.canSave(today, today))
        assertTrue(ActivityDates.canSave(today.minusDays(30), today))
        assertFalse(ActivityDates.canSave(today.minusDays(31), today))
        assertFalse(ActivityDates.canSave(today.plusDays(1), today))
        val oldDate = today.minusDays(100)
        assertTrue(ActivityDates.canSave(oldDate, today, originalDate = oldDate))
        assertFalse(ActivityDates.canSave(oldDate.plusDays(1), today, originalDate = oldDate))
        assertTrue(ActivityDates.canSave(today, today, originalDate = oldDate))
    }
}
