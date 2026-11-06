package com.example.wastenot.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.wastenot.R
import com.example.wastenot.model.*
import com.example.wastenot.ui.auth.MessageBanner
import com.example.wastenot.ui.components.*
import java.math.BigDecimal
import java.math.RoundingMode

@Composable
fun StatisticsScreen(
    data: SnapshotState<StatisticsSnapshot>,
    days: Int,
    padding: PaddingValues,
    onRetry: () -> Unit,
    onRecord: () -> Unit,
    onDays: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(days) { mutableStateOf(false) }
    ScrollPage(padding, modifier) {
        RangeSelector(days, onDays)
        SummaryStatus(data.loading, data.error, data.value != null, onRetry)
        val period = data.value ?: return@ScrollPage
        val previousPeriod = period.daily.size != days
        Text(if (previousPeriod) stringResource(R.string.summary_previous_period, period.daily.size,
                WasteFormat.activityDate(period.range.from), WasteFormat.activityDate(period.range.to))
            else stringResource(R.string.date_range, WasteFormat.activityDate(period.range.from), WasteFormat.activityDate(period.range.to)),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (period.summary.activityCount == 0L) {
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
            val methodLabels = DisposalMethod.entries.map { stringResource(it.labelResource) }
            val percentages = DisposalMethod.entries.map { method ->
                val count = period.methods.getValue(method)
                val percent = BigDecimal(count).multiply(BigDecimal(100)).divide(BigDecimal(period.summary.activityCount), 1, RoundingMode.HALF_UP)
                stringResource(R.string.rate_value, checkNotNull(WasteFormat.percentage(percent)))
            }
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val labelStyle = MaterialTheme.typography.bodySmall
            val percentStyle = MaterialTheme.typography.labelMedium
            val labelWidth = with(density) { methodLabels.maxOf { measurer.measure(it, labelStyle).size.width }.toDp() + 1.dp }.coerceAtLeast(66.dp)
            val percentWidth = with(density) { percentages.maxOf { measurer.measure(it, percentStyle).size.width }.toDp() + 1.dp }.coerceAtLeast(35.dp)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DisposalMethod.entries.forEachIndexed { index, method ->
                    val count = period.methods.getValue(method)
                    val fraction = count.toFloat() / period.summary.activityCount
                    val description = stringResource(R.string.method_fraction, count, period.summary.activityCount)
                    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { stateDescription = description },
                        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(methodLabels[index], Modifier.width(labelWidth), style = labelStyle)
                        LinearProgressIndicator(progress = { fraction }, Modifier.weight(1f).height(7.dp).clearAndSetSemantics {},
                            trackColor = MaterialTheme.colorScheme.surfaceContainer, gapSize = 0.dp, drawStopIndicator = {})
                        Text(percentages[index], Modifier.width(percentWidth), textAlign = TextAlign.End, style = percentStyle)
                    }
                }
            }
            Text(stringResource(R.string.methods_basis), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            val categoryBasis = stringResource(R.string.category_totals_basis)
            StatisticsHeading(stringResource(R.string.categories_title), Modifier.semantics { stateDescription = categoryBasis })
            Column(Modifier.fillMaxWidth()) {
                WasteCategory.entries.forEach { category ->
                    val totals = period.categories.getValue(category)
                    val weight = WasteFormat.weight(totals.weightGrams, summary = true)
                    val weightLabel = weight.amount + " " + stringResource(weight.unit.labelResource)
                    val totalLabel = if (totals.itemCount == 0L) weightLabel else stringResource(R.string.category_totals,
                        weightLabel, WasteFormat.integer(totals.itemCount))
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp).semantics(mergeDescendants = true) {},
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(stringResource(category.labelResource), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        Text(totalLabel, Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun StatisticsHeading(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = modifier.semantics { heading() })
}

@Composable
private fun DailyChart(daily: List<StatisticsDayTotal>, count: Boolean, modifier: Modifier = Modifier) {
    val title = stringResource(if (count) R.string.daily_count_title else R.string.daily_weight_title)
    val unit = stringResource(if (count) R.string.unit_item else R.string.unit_gram)
    val values = daily.map { if (count) it.handled.itemCount else it.handled.weightGrams }
    val max = values.max().coerceAtLeast(1)
    val description = stringResource(R.string.daily_chart_description, title, unit,
        WasteFormat.activityDate(daily.first().date), WasteFormat.activityDate(daily.last().date))
    val primary = MaterialTheme.colorScheme.primary
    val weekly = daily.size == 7
    val labelStyle = MaterialTheme.typography.bodySmall
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labels = values.map { if (it > 0) WasteFormat.integer(it) else "" }
    val scroll = rememberScrollState()
    Column(modifier.testTag(if (count) "daily-count-chart" else "daily-weight-chart"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatisticsHeading(title)
        if (!weekly) Text(stringResource(R.string.daily_scale, WasteFormat.integer(values.max()), unit),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val viewportWidth = maxWidth
            val labelSizes = (labels + daily.map { it.date.dayOfMonth.toString() }).map {
                textMeasurer.measure(it.ifEmpty { "0" }, labelStyle).size
            }
            val slot = if (weekly) with(density) { labelSizes.maxOf { it.width }.toDp() + 8.dp }.coerceAtLeast(32.dp) else 0.dp
            val plotWidth = viewportWidth.coerceAtLeast(slot * daily.size)
            val barWidth = (plotWidth / daily.size * 0.65f).coerceAtMost(27.dp)
            val maxBarHeight = if (weekly) 90.dp else 95.dp
            val plotHeight = maxBarHeight + if (weekly) with(density) { labelSizes.maxOf { it.height }.toDp() + 7.dp } else 0.dp
            Column {
                Column(Modifier.horizontalScroll(scroll)) {
                    Row(
                        modifier = Modifier
                            .width(plotWidth)
                            .height(plotHeight)
                            .clearAndSetSemantics { contentDescription = description; role = Role.Image },
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        values.forEachIndexed { index, value ->
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                if (weekly && value > 0) Text(labels[index], style = labelStyle,
                                    color = MaterialTheme.colorScheme.onSurface)
                                Box(
                                    Modifier
                                        .width(barWidth)
                                        .height((maxBarHeight * (value.toDouble() / max).toFloat()).coerceAtLeast(2.dp))
                                        .background(primary, RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp)),
                                )
                            }
                        }
                    }
                    Row(Modifier.width(plotWidth).padding(top = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        val ticks = if (weekly) daily else listOf(daily.first(), daily[daily.size / 2], daily.last())
                        ticks.forEach { Text(it.date.dayOfMonth.toString(),
                            modifier = if (weekly) Modifier.weight(1f) else Modifier,
                            textAlign = if (weekly) TextAlign.Center else TextAlign.Start, style = labelStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                if (plotWidth > viewportWidth) Text(stringResource(R.string.daily_scroll_hint),
                    Modifier.width(viewportWidth).padding(top = 8.dp), style = labelStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(stringResource(R.string.daily_axis, unit), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
