package com.example.wastenot.data.dummy

import com.example.wastenot.data.demo.demoSummary
import com.example.wastenot.model.*
import java.time.LocalDate

data class StatisticsDay(val date: LocalDate, val handled: QuantityTotals)

data class StatisticsPeriod(
    val range: ActivityDateRange,
    val summary: WasteSummary,
    val daily: List<StatisticsDay>,
    val categories: Map<WasteCategory, QuantityTotals>,
    val methods: Map<DisposalMethod, Int>,
)

// Only the complete local dummy dataset can supply these aggregates; remote pages cannot.
fun dummyStatistics(records: List<WasteLog>, today: LocalDate, days: Int): StatisticsPeriod {
    require(days == 7 || days == 30)
    val range = ActivityDateRange.lastDays(today, days)
    val period = records.filter { it.activityDate in range }
    val dates = period.groupBy { it.activityDate }
    val categories = period.groupBy { it.category }
    return StatisticsPeriod(
        range, demoSummary(period),
        List(days) { offset ->
            val date = range.from.plusDays(offset.toLong())
            StatisticsDay(date, demoSummary(dates[date].orEmpty()).handled)
        },
        WasteCategory.entries.associateWith { demoSummary(categories[it].orEmpty()).handled },
        DisposalMethod.entries.associateWith { method -> period.count { it.method == method } },
    )
}
