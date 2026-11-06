package com.example.wastenot.data.dummy

import com.example.wastenot.data.demo.demoSummary
import com.example.wastenot.model.*
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

// Only the complete local dummy dataset can supply these aggregates; remote pages cannot.
fun dummyStatistics(records: List<WasteLog>, range: ActivityDateRange, zone: ZoneId): StatisticsSnapshot {
    val period = records.filter { it.activityDate in range }
    val dates = period.groupBy { it.activityDate }
    val categories = period.groupBy { it.category }
    return StatisticsSnapshot(
        range, zone, demoSummary(period),
        List(ChronoUnit.DAYS.between(range.from, range.to).toInt() + 1) { offset ->
            val date = range.from.plusDays(offset.toLong())
            val summary = demoSummary(dates[date].orEmpty())
            StatisticsDayTotal(date, summary.handled, summary.activityCount)
        },
        WasteCategory.entries.associateWith { demoSummary(categories[it].orEmpty()).handled },
        DisposalMethod.entries.associateWith { method -> period.count { it.method == method }.toLong() },
    )
}

fun dummyDashboard(records: List<WasteLog>, today: LocalDate, zone: ZoneId) = DashboardSnapshot(
    today, zone, demoSummary(records.filter { it.activityDate == today }),
    records.sortedWith(compareByDescending<WasteLog> { it.activityDate }.thenByDescending { it.createdAt }).take(3),
    dummyProgress(records, today),
)
