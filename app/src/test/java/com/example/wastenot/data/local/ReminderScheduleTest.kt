package com.example.wastenot.data.local

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ReminderScheduleTest {
    @Test
    fun computeInitialDelaySchedulesLaterTodayOrNextDayWithFloor() {
        val zone = ZoneId.of("Asia/Jakarta")
        val morning = ZonedDateTime.of(2026, 11, 13, 8, 0, 0, 0, zone)
        assertEquals(12 * 3600 * 1000L, computeInitialDelayMillis(morning, 20, 0))

        val evening = ZonedDateTime.of(2026, 11, 13, 21, 0, 0, 0, zone)
        assertEquals(23 * 3600 * 1000L, computeInitialDelayMillis(evening, 20, 0))

        val exactMinute = ZonedDateTime.of(2026, 11, 13, 20, 0, 0, 0, zone)
        assertEquals(24 * 3600 * 1000L, computeInitialDelayMillis(exactMinute, 20, 0))

        val secondsBefore = ZonedDateTime.of(2026, 11, 13, 19, 59, 45, 0, zone)
        assertEquals(60_000L, computeInitialDelayMillis(secondsBefore, 20, 0))
    }

    @Test
    fun reminderSettingValidatesTimeBounds() {
        assertThrows(IllegalArgumentException::class.java) { ReminderSetting(true, 24, 0) }
        assertThrows(IllegalArgumentException::class.java) { ReminderSetting(true, 20, 60) }
    }
}
