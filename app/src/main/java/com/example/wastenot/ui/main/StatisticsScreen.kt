package com.example.wastenot.ui.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.wastenot.R
import com.example.wastenot.data.dummy.StatisticsDay
import com.example.wastenot.data.dummy.dummyStatistics
import com.example.wastenot.model.*
import com.example.wastenot.ui.auth.MessageBanner
import com.example.wastenot.ui.components.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

@Composable
fun StatisticsScreen(
    data: WorkspaceUiState,
    demo: Boolean,
    date: LocalDate,
    padding: PaddingValues,
    onRetry: () -> Unit,
    onRecord: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var days by rememberSaveable { mutableIntStateOf(7) }
    var expanded by rememberSaveable(days) { mutableStateOf(false) }
    if (data.records.isEmpty() && (data.loading || data.error != null)) {
        WorkspaceLoading(data, onRetry, padding, modifier)
        return
    }
    val period = remember(data.records, date, days) { dummyStatistics(data.records, date, days) }
    ScrollPage(padding, modifier) {
        RangeSelector(days, { days = it })
        Text(stringResource(R.string.date_range, WasteFormat.activityDate(period.range.from), WasteFormat.activityDate(period.range.to)),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (data.error != null) {
            MessageBanner(stringResource(R.string.refresh_failed), isError = true)
            TextActionButton(stringResource(R.string.retry), onRetry)
        }
        if (data.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (!demo) {
            MessageBanner(stringResource(R.string.summary_pending))
        } else if (period.summary.activityCount == 0L) {
            Text(stringResource(R.string.statistics_empty), style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.statistics_empty_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
            ActionButton(stringResource(R.string.record_activity), onRecord, Modifier.fillMaxWidth())
        } else {
            SummaryBlock(period.summary, stringResource(R.string.handled_period), Modifier.testTag("statistics-summary"), beforeRate = {
                DailyChart(period.daily, count = false, Modifier.fillMaxWidth())
                if (period.summary.handled.itemCount > 0) DailyChart(period.daily, count = true, Modifier.fillMaxWidth())
                val expansionState = stringResource(if (expanded) R.string.daily_expanded else R.string.daily_collapsed)
                TextActionButton(stringResource(if (expanded) R.string.daily_hide else R.string.daily_show),
                    onClick = { expanded = !expanded }, modifier = Modifier.semantics { stateDescription = expansionState })
                if (expanded) {
                    Text(stringResource(R.string.daily_values), style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() })
                    period.daily.forEach { day ->
                        Text(stringResource(R.string.daily_row, WasteFormat.activityDate(day.date),
                            WasteFormat.integer(day.handled.weightGrams), WasteFormat.integer(day.handled.itemCount)),
                            modifier = Modifier.testTag("statistics-day-${day.date}"), style = MaterialTheme.typography.bodySmall)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            })
            StatisticsHeading(stringResource(R.string.methods_title))
            DisposalMethod.entries.forEach { method ->
                val count = period.methods.getValue(method)
                val fraction = count.toFloat() / period.summary.activityCount
                val percent = BigDecimal(count * 100L).divide(BigDecimal(period.summary.activityCount), 1, RoundingMode.HALF_UP)
                Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        Text(stringResource(method.labelResource), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.rate_value, checkNotNull(WasteFormat.percentage(percent))), style = MaterialTheme.typography.titleSmall)
                    }
                    LinearProgressIndicator(progress = { fraction }, Modifier.fillMaxWidth().height(7.dp).clearAndSetSemantics {},
                        trackColor = MaterialTheme.colorScheme.surfaceContainer, gapSize = 0.dp, drawStopIndicator = {})
                    Text(stringResource(R.string.method_fraction, count, period.summary.activityCount),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(stringResource(R.string.methods_basis), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            StatisticsHeading(stringResource(R.string.categories_title))
            Text(stringResource(R.string.category_totals_basis), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            WasteCategory.entries.forEach { category ->
                val totals = period.categories.getValue(category)
                Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(painterResource(category.iconResource), null, Modifier.size(24.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(category.labelResource), style = MaterialTheme.typography.titleSmall)
                        val weight = WasteFormat.weight(totals.weightGrams, summary = true)
                        Text(stringResource(R.string.category_totals, weight.amount + " " + stringResource(weight.unit.labelResource),
                            WasteFormat.integer(totals.itemCount)), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun StatisticsHeading(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = modifier.semantics { heading() })
}

@Composable
private fun DailyChart(daily: List<StatisticsDay>, count: Boolean, modifier: Modifier = Modifier) {
    val title = stringResource(if (count) R.string.daily_count_title else R.string.daily_weight_title)
    val unit = stringResource(if (count) R.string.unit_item else R.string.unit_gram)
    val values = daily.map { if (count) it.handled.itemCount else it.handled.weightGrams }
    val max = values.max().coerceAtLeast(1)
    val description = stringResource(R.string.daily_chart_description, title, unit,
        WasteFormat.activityDate(daily.first().date), WasteFormat.activityDate(daily.last().date))
    val primary = MaterialTheme.colorScheme.primary
    val divider = MaterialTheme.colorScheme.outlineVariant
    Column(modifier.testTag(if (count) "daily-count-chart" else "daily-weight-chart"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatisticsHeading(title)
        Text(stringResource(R.string.daily_scale, WasteFormat.integer(values.max()), unit),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Canvas(Modifier.fillMaxWidth().height(128.dp)
            .semantics { contentDescription = description; role = Role.Image }) {
            val slot = size.width / values.size
            val barWidth = (slot * 0.65f).coerceAtMost(27.dp.toPx())
            val bottom = size.height - 1.dp.toPx()
            drawLine(divider, Offset(0f, bottom), Offset(size.width, bottom), 1.dp.toPx())
            values.forEachIndexed { index, value ->
                if (value > 0) {
                    val height = (value.toDouble() / max * bottom).toFloat()
                    drawRoundRect(primary, Offset(index * slot + (slot - barWidth) / 2, bottom - height),
                        Size(barWidth, height), CornerRadius(3.dp.toPx()))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val ticks = if (daily.size == 7) daily else listOf(daily.first(), daily[daily.size / 2], daily.last())
            ticks.forEach { Text(it.date.dayOfMonth.toString(),
                modifier = if (daily.size == 7) Modifier.weight(1f) else Modifier,
                textAlign = if (daily.size == 7) TextAlign.Center else TextAlign.Start, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Text(stringResource(R.string.daily_axis), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
