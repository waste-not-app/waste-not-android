package com.example.wastenot.model

import java.time.LocalDate
import java.time.ZoneId

data class DashboardSnapshot(
    val date: LocalDate,
    val timezone: ZoneId,
    val summary: WasteSummary,
    val recentLogs: List<WasteLog>,
    val progress: UserProgress,
)

data class StatisticsDayTotal(val date: LocalDate, val handled: QuantityTotals, val activityCount: Long)

data class StatisticsSnapshot(
    val range: ActivityDateRange,
    val timezone: ZoneId,
    val summary: WasteSummary,
    val daily: List<StatisticsDayTotal>,
    val categories: Map<WasteCategory, QuantityTotals>,
    val methods: Map<DisposalMethod, Long>,
)
