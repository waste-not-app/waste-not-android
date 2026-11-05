package com.example.wastenot.data.network.dto

import com.example.wastenot.model.ActivityDateRange
import com.example.wastenot.model.DashboardSnapshot
import com.example.wastenot.model.DisposalMethod
import com.example.wastenot.model.QuantityTotals
import com.example.wastenot.model.StatisticsDayTotal
import com.example.wastenot.model.StatisticsSnapshot
import com.example.wastenot.model.WasteCategory
import com.example.wastenot.model.WasteSummary
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class SummaryDto(
    @SerialName("total_handled_weight_g") val handledWeightG: Long,
    @SerialName("total_handled_count") val handledCount: Long,
    @SerialName("avoided_weight_g") val avoidedWeightG: Long,
    @SerialName("avoided_count") val avoidedCount: Long,
    @SerialName("activity_count") val activityCount: Long,
    @SerialName("handled_activity_count") val handledActivityCount: Long,
    @SerialName("recycled_activity_count") val recycledActivityCount: Long,
    @SerialName("recycling_rate_percent") val recyclingRatePercent: JsonPrimitive?,
    @SerialName("recycling_rate_basis") val recyclingRateBasis: String,
) {
    fun toModel(): WasteSummary {
        require(recyclingRateBasis == "HANDLED_ACTIVITY_COUNT")
        require(recyclingRatePercent?.isString != true)
        return WasteSummary(QuantityTotals(handledWeightG, handledCount),
            QuantityTotals(avoidedWeightG, avoidedCount), activityCount, handledActivityCount,
            recycledActivityCount, recyclingRatePercent?.content?.toBigDecimal())
    }
}

@Serializable
data class DashboardDto(
    val date: String,
    val timezone: String,
    val summary: SummaryDto,
    @SerialName("recent_logs") val recentLogs: List<WasteLogDto>,
    val progress: ProgressDto,
) {
    fun toModel(): DashboardSnapshot {
        val today = LocalDate.parse(date)
        val zone = accountZone(timezone)
        val logs = recentLogs.map { it.toModel() }
        require(logs.size <= 3 && logs.map { it.id.lowercase() }.distinct().size == logs.size)
        require(logs.all { !it.activityDate.isAfter(today) && it.activityTimezone == zone })
        return DashboardSnapshot(today, zone, summary.toModel(), logs, progress.toModel())
    }
}

@Serializable
data class StatisticsDayDto(
    val date: String,
    @SerialName("handled_weight_g") val handledWeightG: Long,
    @SerialName("handled_count") val handledCount: Long,
    @SerialName("activity_count") val activityCount: Long,
) {
    fun toModel(): StatisticsDayTotal {
        require(activityCount >= 0)
        return StatisticsDayTotal(LocalDate.parse(date), QuantityTotals(handledWeightG, handledCount), activityCount)
    }
}

@Serializable
data class CategoryTotalDto(
    val category: String,
    @SerialName("handled_weight_g") val handledWeightG: Long,
    @SerialName("handled_count") val handledCount: Long,
)

@Serializable
data class StatisticsDto(
    val from: String,
    val to: String,
    val timezone: String,
    val summary: SummaryDto,
    @SerialName("method_counts") val methodCounts: Map<String, Long>,
    val daily: List<StatisticsDayDto>,
    val categories: List<CategoryTotalDto>,
) {
    fun toModel(): StatisticsSnapshot {
        val range = ActivityDateRange(LocalDate.parse(from), LocalDate.parse(to))
        val days = daily.map { it.toModel() }
        require(days.size.toLong() == ChronoUnit.DAYS.between(range.from, range.to) + 1)
        require(days.withIndex().all { (index, day) -> day.date == range.from.plusDays(index.toLong()) })
        val categoryTotals = categories.map {
            WasteCategory.valueOf(it.category) to QuantityTotals(it.handledWeightG, it.handledCount)
        }
        require(categoryTotals.size == WasteCategory.entries.size)
        require(categoryTotals.map { it.first }.toSet() == WasteCategory.entries.toSet())
        require(methodCounts.keys == DisposalMethod.entries.map { it.name }.toSet())
        require(methodCounts.values.all { it >= 0 })
        return StatisticsSnapshot(range, accountZone(timezone), summary.toModel(), days,
            categoryTotals.toMap(), methodCounts.mapKeys { DisposalMethod.valueOf(it.key) })
    }
}

private fun accountZone(value: String): ZoneId {
    require(value in ZoneId.getAvailableZoneIds())
    return ZoneId.of(value)
}
