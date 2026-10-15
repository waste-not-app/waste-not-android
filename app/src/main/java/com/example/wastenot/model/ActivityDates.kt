package com.example.wastenot.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class ActivityDateRange(val from: LocalDate, val to: LocalDate) {
    init {
        require(!from.isAfter(to))
        require(ChronoUnit.DAYS.between(from, to) < 366)
    }

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(from) && !date.isAfter(to)

    companion object {
        fun lastDays(serverToday: LocalDate, days: Int): ActivityDateRange {
            require(days in 1..366)
            return ActivityDateRange(serverToday.minusDays(days - 1L), serverToday)
        }
    }
}

object ActivityDates {
    fun accountDate(serverInstant: Instant, accountZone: ZoneId): LocalDate =
        serverInstant.atZone(accountZone).toLocalDate()

    fun canSave(date: LocalDate, serverToday: LocalDate, originalDate: LocalDate? = null): Boolean =
        date == originalDate || date in ActivityDateRange(serverToday.minusDays(30), serverToday)
}
