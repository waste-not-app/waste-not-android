package com.example.wastenot.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.semantics.testTag as semanticsTestTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.wastenot.R
import com.example.wastenot.model.*
import com.example.wastenot.ui.components.*
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.layer.cartesianLayerPadding
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.core.cartesian.Zoom
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.core.cartesian.data.ColumnCartesianLayerModel
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarkerController
import com.patrykandpatrick.vico.core.cartesian.marker.Interaction
import com.patrykandpatrick.vico.core.common.shape.CorneredShape
import com.patrykandpatrick.vico.core.common.shape.DashedShape
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.format.DateTimeFormatter

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
    var expanded by rememberSaveable { mutableStateOf(false) }
    ScrollPage(padding, modifier) {
        RangeSelector(days, onDays)
        SummaryStatus(data.loading, data.error, data.value != null, onRetry, data.freshness, data.stale)
        val period = data.value ?: return@ScrollPage
        val previousPeriod = period.daily.size != days
        Text(
            if (previousPeriod) stringResource(
                R.string.summary_previous_period, period.daily.size,
                WasteFormat.activityDate(period.range.from), WasteFormat.activityDate(period.range.to),
            ) else stringResource(R.string.date_range, WasteFormat.activityDate(period.range.from), WasteFormat.activityDate(period.range.to)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val hasActivity = period.summary.activityCount > 0
        if (!hasActivity) {
            AsyncStatePanel(
                stringResource(R.string.statistics_empty),
                R.drawable.ic_statistics,
                Modifier.fillMaxWidth(),
                description = stringResource(R.string.statistics_empty_hint),
            ) {
                ActionButton(stringResource(R.string.record_activity), onRecord, Modifier.fillMaxWidth())
            }
        } else {
            SummaryBlock(period.summary, stringResource(R.string.handled_period), Modifier.testTag("statistics-summary"))
            InsetGroupCard(contentPadding = true) {
                DailyChart(period.daily, DailyMetric.Weight, Modifier.fillMaxWidth())
            }
            if (period.summary.handled.itemCount > 0) {
                InsetGroupCard(contentPadding = true) {
                    DailyChart(period.daily, DailyMetric.Count, Modifier.fillMaxWidth())
                }
            }
        }
        DailyNumbers(period.daily, expanded, onToggle = { expanded = !expanded })
        if (hasActivity) {
            GroupSectionLabel(stringResource(R.string.methods_title))
            val methodLabels = DisposalMethod.entries.map { stringResource(it.labelResource) }
            val percentages = DisposalMethod.entries.map { method ->
                val count = period.methods.getValue(method)
                val percent = BigDecimal(count).multiply(BigDecimal(100)).divide(BigDecimal(period.summary.activityCount), 1, RoundingMode.HALF_UP)
                stringResource(R.string.rate_value, checkNotNull(WasteFormat.percentage(percent)))
            }
            val labelStyle = MaterialTheme.typography.bodySmall
            val percentStyle = MaterialTheme.typography.labelMedium
            InsetGroupCard(contentPadding = true) {
                DisposalMethod.entries.forEachIndexed { index, method ->
                    val count = period.methods.getValue(method)
                    val fraction = count.toFloat() / period.summary.activityCount
                    val description = stringResource(R.string.method_fraction, count, period.summary.activityCount)
                    Column(
                        Modifier.fillMaxWidth().semantics(mergeDescendants = true) { stateDescription = description },
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FlowRow(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(methodLabels[index], style = labelStyle)
                            Text(percentages[index], style = percentStyle)
                        }
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(8.dp)).clearAndSetSemantics {},
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            gapSize = 0.dp,
                            drawStopIndicator = {},
                        )
                    }
                }
                Text(
                    stringResource(R.string.methods_basis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val categoryBasis = stringResource(R.string.category_totals_basis)
            GroupSectionLabel(stringResource(R.string.categories_title), Modifier.semantics { stateDescription = categoryBasis })
            InsetGroupCard {
                WasteCategory.entries.forEachIndexed { idx, category ->
                    val totals = period.categories.getValue(category)
                    val weight = WasteFormat.weight(totals.weightGrams, summary = true)
                    val weightLabel = weight.amount + " " + stringResource(weight.unit.labelResource)
                    val totalLabel = if (totals.itemCount == 0L) weightLabel else stringResource(
                        R.string.category_totals,
                        weightLabel,
                        WasteFormat.integer(totals.itemCount),
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp).semantics(mergeDescendants = true) {},
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CategoryIconBadge(category, category.iconResource, sizeDp = 34)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(category.labelResource), style = MaterialTheme.typography.bodyMedium)
                            Text(totalLabel, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                        }
                    }
                    if (idx < WasteCategory.entries.lastIndex) {
                        HorizontalDivider(Modifier.padding(start = 62.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
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
private fun DailyNumbers(
    daily: List<StatisticsDayTotal>,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val expansionState = stringResource(if (expanded) R.string.daily_expanded else R.string.daily_collapsed)
    InsetGroupCard(modifier, contentPadding = true) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(MaterialTheme.shapes.small)
                .clickable(role = Role.Button, onClick = onToggle)
                .semantics { stateDescription = expansionState },
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(if (expanded) R.string.daily_hide else R.string.daily_show),
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                painterResource(R.drawable.ic_chevron_down), null,
                Modifier.size(20.dp).rotate(if (expanded) 180f else 0f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) {
            StatisticsHeading(stringResource(R.string.daily_values))
            daily.forEachIndexed { index, day ->
                Text(
                    stringResource(
                        R.string.daily_row,
                        WasteFormat.activityDate(day.date),
                        dailyAmount(DailyMetric.Weight, day.handled.weightGrams),
                        dailyAmount(DailyMetric.Count, day.handled.itemCount),
                    ),
                    modifier = Modifier.testTag("statistics-day-${day.date}"),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (index < daily.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

private enum class DailyMetric(val key: String, val title: Int) {
    Weight("weight", R.string.daily_weight_title),
    Count("count", R.string.daily_count_title);

    fun value(day: StatisticsDayTotal): Long = when (this) {
        Weight -> day.handled.weightGrams
        Count -> day.handled.itemCount
    }
}

@Composable
private fun dailyAmount(metric: DailyMetric, value: Long): String {
    val formatted = WasteFormat.integer(value)
    return when (metric) {
        DailyMetric.Weight -> stringResource(R.string.daily_grams, formatted)
        DailyMetric.Count -> pluralStringResource(
            R.plurals.daily_items, value.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), formatted,
        )
    }
}

@Composable
private fun DailyChart(daily: List<StatisticsDayTotal>, metric: DailyMetric, modifier: Modifier = Modifier) {
    val first = daily.first().date.toEpochDay()
    val last = daily.last().date.toEpochDay()
    var selectedDate by rememberSaveable { mutableStateOf<Long?>(null) }
    val selected = selectedDate?.takeIf { it in first..last } ?: last
    LaunchedEffect(first, last) {
        if (selectedDate?.let { it !in first..last } == true) selectedDate = null
    }
    val day = daily[(selected - first).toInt()]
    val peak = remember(daily, metric) { daily.maxOf(metric::value) }
    val title = stringResource(metric.title)
    val dateRange = stringResource(
        R.string.date_range, WasteFormat.activityDate(daily.first().date), WasteFormat.activityDate(daily.last().date),
    )
    val description = stringResource(R.string.daily_chart_description, title, dateRange)

    // A synchronous model keeps the bars and their exact readout on the same snapshot.
    val model = remember(daily, metric) {
        CartesianChartModel(ColumnCartesianLayerModel(listOf(daily.map {
            ColumnCartesianLayerModel.Entry(it.date.toEpochDay(), metric.value(it))
        })))
    }
    val columnSpacing = if (daily.size == 7) 16.dp else 5.dp
    val column = rememberLineComponent(
        fill = fill(MaterialTheme.colorScheme.primary),
        thickness = if (daily.size == 7) 20.dp else 7.dp,
        shape = CorneredShape.rounded(topLeftPercent = 35, topRightPercent = 35),
    )
    val layer = rememberColumnCartesianLayer(
        columnProvider = remember(column) { ColumnCartesianLayer.ColumnProvider.series(column) },
        columnCollectionSpacing = columnSpacing,
    )
    val guideline = rememberLineComponent(
        fill = fill(MaterialTheme.colorScheme.onSurface),
        thickness = 3.dp,
        shape = DashedShape(dashLengthDp = 3f, gapLengthDp = 3f),
        strokeFill = fill(MaterialTheme.colorScheme.surface),
        strokeThickness = 1.dp,
    )
    val marker = remember(guideline) {
        object : CartesianMarker {
            override fun drawOverLayers(context: CartesianDrawingContext, targets: List<CartesianMarker.Target>) {
                guideline.drawVertical(context, targets.first().canvasX, context.layerBounds.top, context.layerBounds.bottom)
            }
        }
    }
    val controller = remember(first, last) {
        object : CartesianMarkerController {
            override val acceptsLongPress = false

            override fun shouldAcceptInteraction(interaction: Interaction, targets: List<CartesianMarker.Target>) =
                interaction is Interaction.Tap && targets.isNotEmpty()

            override fun shouldShowMarker(interaction: Interaction, targets: List<CartesianMarker.Target>): Boolean {
                selectedDate = targets.first().x.toLong().takeIf { it in first..last }
                return false // The same persistent marker serves taps and the day buttons.
            }
        }
    }
    val axisLocale = LocalConfiguration.current.locales[0]
    val axisDate = remember(axisLocale) { DateTimeFormatter.ofPattern("d MMM", axisLocale) }
    val axisStyle = MaterialTheme.typography.bodySmall
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier.testTag("daily-${metric.key}-chart"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            StatisticsHeading(title)
            Text(stringResource(R.string.daily_scale, dailyAmount(metric, peak)), style = axisStyle, color = muted)
            if (metric == DailyMetric.Weight) {
                Text(stringResource(R.string.daily_chart_hint), style = axisStyle, color = muted)
            }
        }
        Column(Modifier.fillMaxWidth()) {
            CartesianChartHost(
                chart = rememberCartesianChart(
                    layer,
                    marker = marker,
                    markerController = controller,
                    layerPadding = remember(columnSpacing) { { cartesianLayerPadding(columnSpacing / 2, columnSpacing / 2) } },
                    persistentMarkers = remember(marker, selected) { { marker at selected } },
                ),
                model = model,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(136.dp)
                    .clearAndSetSemantics {
                        semanticsTestTag = "daily-${metric.key}-plot"
                        contentDescription = description
                        role = Role.Image
                    },
                scrollState = rememberVicoScrollState(scrollEnabled = false),
                zoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.Content),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp).clearAndSetSemantics {},
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(axisDate.format(daily.first().date), Modifier.weight(1f), style = axisStyle, color = muted)
                Text(
                    axisDate.format(daily.last().date), Modifier.weight(1f),
                    textAlign = TextAlign.End, style = axisStyle, color = muted,
                )
            }
        }
        DailySelection(
            date = WasteFormat.activityDate(day.date),
            amount = dailyAmount(metric, metric.value(day)),
            canGoBack = selected > first,
            canGoForward = selected < last,
            onPrevious = { selectedDate = selected - 1 },
            onNext = { selectedDate = selected + 1 },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DailySelection(
    date: String,
    amount: String,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val previousRotation = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 90f else -90f
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            val stacked = maxWidth < 240.dp * LocalDensity.current.fontScale
            if (stacked) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                    DailySelectionLabel(date, amount, Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        DayArrow(R.string.daily_previous, previousRotation, canGoBack, onPrevious)
                        DayArrow(R.string.daily_next, -previousRotation, canGoForward, onNext)
                    }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    DayArrow(R.string.daily_previous, previousRotation, canGoBack, onPrevious)
                    DailySelectionLabel(date, amount, Modifier.weight(1f))
                    DayArrow(R.string.daily_next, -previousRotation, canGoForward, onNext)
                }
            }
        }
    }
}

@Composable
private fun DailySelectionLabel(date: String, amount: String, modifier: Modifier = Modifier) {
    Column(
        modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(date, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Text(
            amount,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DayArrow(label: Int, rotation: Float, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick, Modifier.size(48.dp), enabled = enabled) {
        Icon(painterResource(R.drawable.ic_chevron_down), stringResource(label), Modifier.rotate(rotation))
    }
}
