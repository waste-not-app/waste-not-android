package com.example.wastenot.model

import com.example.wastenot.data.network.ApiFailureKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class SnapshotFreshness(
    val cached: Boolean = false,
    val confirmedAt: Instant? = null,
    val refreshError: ApiFailureKind? = null,
)

data class DashboardSnapshot(
    val date: LocalDate,
    val timezone: ZoneId,
    val summary: WasteSummary,
    val recentLogs: List<WasteLog>,
    val progress: UserProgress,
    val freshness: SnapshotFreshness = SnapshotFreshness(),
)

data class StatisticsDayTotal(val date: LocalDate, val handled: QuantityTotals, val activityCount: Long)

data class StatisticsSnapshot(
    val range: ActivityDateRange,
    val timezone: ZoneId,
    val summary: WasteSummary,
    val daily: List<StatisticsDayTotal>,
    val categories: Map<WasteCategory, QuantityTotals>,
    val methods: Map<DisposalMethod, Long>,
    val freshness: SnapshotFreshness = SnapshotFreshness(),
)
