package com.example.wastenot.ui.main

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountDateTest {
    @Test fun midnightUsesTheAccountZoneRatherThanUtc() = runTest {
        val start = Instant.parse("2026-11-09T16:59:59.750Z")
        val dates = mutableListOf<LocalDate>()
        val clock = ReadingClock { start.plusMillis(testScheduler.currentTime) }
        backgroundScope.launch { accountDateChanges(ZoneId.of("Asia/Jakarta"), clock).collect { dates.add(it) } }
        runCurrent()
        assertEquals(listOf(LocalDate.of(2026, 11, 9)), dates)
        advanceTimeBy(249); runCurrent()
        assertEquals(1, dates.size)
        advanceTimeBy(1); runCurrent()
        assertEquals(listOf(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 10)), dates)
    }

    @Test fun theNextDateArrivesAfterAShortDayAtTheDaylightSavingBoundary() = runTest {
        val start = Instant.parse("2026-03-08T04:59:59.750Z")
        val dates = mutableListOf<LocalDate>()
        val clock = ReadingClock { start.plusMillis(testScheduler.currentTime) }
        backgroundScope.launch { accountDateChanges(ZoneId.of("America/New_York"), clock).collect { dates.add(it) } }
        runCurrent()
        advanceTimeBy(250); runCurrent()
        assertEquals(listOf(LocalDate.of(2026, 3, 7), LocalDate.of(2026, 3, 8)), dates)
        advanceTimeBy(23 * 60 * 60 * 1_000L - 1); runCurrent()
        assertEquals(2, dates.size)
        advanceTimeBy(1); runCurrent()
        assertEquals(LocalDate.of(2026, 3, 9), dates.last())
        assertEquals(3, dates.size)
    }

    @Test fun clockCorrectionsAreObservedWithoutRepeatingUnchangedDates() = runTest {
        var wallTime = Instant.parse("2026-11-09T12:00:00Z")
        val dates = mutableListOf<LocalDate>()
        val clock = ReadingClock { wallTime }
        backgroundScope.launch { accountDateChanges(ZoneId.of("Asia/Jakarta"), clock).collect { dates.add(it) } }
        runCurrent()
        advanceTimeBy(60_000); runCurrent()
        assertEquals(listOf(LocalDate.of(2026, 11, 9)), dates)
        wallTime = wallTime.plusSeconds(2 * 24 * 60 * 60L)
        advanceTimeBy(60_000); runCurrent()
        assertEquals(listOf(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 11)), dates)
        wallTime = wallTime.minusSeconds(2 * 24 * 60 * 60L)
        advanceTimeBy(60_000); runCurrent()
        assertEquals(listOf(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 9)), dates)
    }

    private class ReadingClock(private val zone: ZoneId = ZoneOffset.UTC, private val read: () -> Instant) : Clock() {
        override fun getZone(): ZoneId = zone
        override fun withZone(zone: ZoneId): Clock = ReadingClock(zone, read)
        override fun instant(): Instant = read()
    }
}
