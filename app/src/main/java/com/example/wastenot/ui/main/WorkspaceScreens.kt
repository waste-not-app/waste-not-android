package com.example.wastenot.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.wastenot.R
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.model.*
import com.example.wastenot.theme.*
import com.example.wastenot.ui.auth.MessageBanner
import com.example.wastenot.ui.components.*
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun pagePadding(padding: PaddingValues): PaddingValues {
    val direction = LocalLayoutDirection.current
    val scaffold = LocalWasteNotScaffoldPadding.current
    return PaddingValues(
        start = padding.calculateStartPadding(direction) + scaffold.calculateStartPadding(direction) + 20.dp,
        top = padding.calculateTopPadding() + scaffold.calculateTopPadding() + 16.dp,
        end = padding.calculateEndPadding(direction) + scaffold.calculateEndPadding(direction) + 20.dp,
        bottom = padding.calculateBottomPadding() + scaffold.calculateBottomPadding() + 24.dp,
    )
}

@Composable
internal fun ScrollPage(padding: PaddingValues, modifier: Modifier = Modifier, maxWidth: Dp = 840.dp, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier, contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(pagePadding(padding)),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@Composable
fun OverviewScreen(user: User, data: SnapshotState<DashboardSnapshot>, padding: PaddingValues,
    onRecord: () -> Unit, onHistory: () -> Unit, onDetail: (String) -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier,
    onProfile: () -> Unit = {}, onResumeDraft: (() -> Unit)? = null) {
    val snapshot = data.value
    if (snapshot == null) {
        ScrollPage(padding, modifier) {
            SummaryStatus(data.loading, data.error, false, onRetry)
            SecondaryActionButton(stringResource(R.string.manual_input), onRecord, Modifier.fillMaxWidth())
            TextActionButton(stringResource(R.string.see_history), onHistory)
        }
        return
    }
    ScrollPage(padding, modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(snapshot.date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.forLanguageTag("id-ID"))),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.greeting, user.name), style = MaterialTheme.typography.headlineMedium)
            }
            IconActionButton(R.drawable.ic_profile, stringResource(R.string.open_profile), onProfile, circle = true)
        }
        SummaryStatus(data.loading, data.error, true, onRetry, data.freshness, data.stale)
        if (onResumeDraft != null) PausedDraftNotice(onResumeDraft)
        if (!data.stale && snapshot.summary.activityCount == 0L && snapshot.recentLogs.none {
                it.activityDate == snapshot.date || it.syncState != ActivitySyncState.SYNCED
            }) MessageBanner(stringResource(R.string.home_empty_today))
        SummaryBlock(snapshot.summary, if (data.stale) stringResource(
            R.string.freshness_handled_date, WasteFormat.activityDate(snapshot.date),
        ) else stringResource(R.string.handled_today), personal = true)
        InsetGroupCard(contentPadding = true) {
            ProgressMeter(snapshot.progress)
        }
        SecondaryActionButton(stringResource(R.string.manual_input), onRecord, Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.recent_activities), style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f).semantics { heading() })
            TextActionButton(stringResource(R.string.see_history), onHistory)
        }
        if (snapshot.recentLogs.isEmpty()) {
            InsetGroupCard(contentPadding = true) {
                Text(stringResource(R.string.logs_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            InsetGroupCard {
                snapshot.recentLogs.forEachIndexed { idx, log ->
                    ActivityRow(
                        log = log,
                        onClick = { onDetail(log.id) },
                        modifier = Modifier.fillMaxWidth(),
                        showDivider = idx < snapshot.recentLogs.lastIndex,
                    )
                }
            }
        }
    }
}

@Composable
internal fun SummaryStatus(loading: Boolean, error: ApiFailureKind?, hasValue: Boolean, onRetry: () -> Unit,
    freshness: SnapshotFreshness? = null, stale: Boolean = false) {
    if (!hasValue) {
        AsyncStatePanel(
            title = stringResource(if (loading) R.string.summary_loading else R.string.summary_load_failed),
            icon = if (loading) R.drawable.ic_clock else R.drawable.ic_alert,
            modifier = Modifier.fillMaxWidth(),
            description = if (loading) null else loadFailureHint(error),
            loading = loading,
        ) {
            if (!loading) ActionButton(stringResource(R.string.retry), onRetry, Modifier.fillMaxWidth())
        }
        return
    }
    if (loading) {
        LinearProgressIndicator(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)))
    }
    SnapshotFreshnessBanner(freshness, stale)
    if (error != null) {
        MessageBanner(stringResource(R.string.summary_refresh_failed), isError = true)
        TextActionButton(stringResource(R.string.retry), onRetry)
    }
}

@Composable
internal fun SnapshotFreshnessBanner(freshness: SnapshotFreshness?, stale: Boolean) {
    if (stale) MessageBanner(stringResource(R.string.freshness_previous_period))
    if (freshness?.cached == true) {
        val confirmedAt = freshness.confirmedAt
        val label = if (confirmedAt == null) stringResource(R.string.freshness_cached) else stringResource(
            R.string.freshness_cached_at,
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm 'UTC'", Locale.forLanguageTag("id-ID"))
                .withZone(java.time.ZoneOffset.UTC).format(confirmedAt),
        )
        MessageBanner(label)
    }
}

@Composable
internal fun loadFailureHint(error: ApiFailureKind?): String = stringResource(when (error) {
    ApiFailureKind.NETWORK, ApiFailureKind.TIMEOUT -> R.string.error_network
    ApiFailureKind.RATE_LIMITED -> R.string.error_rate_limited
    else -> R.string.async_retry_hint
})

@Composable
internal fun SummaryBlock(summary: WasteSummary, title: String, modifier: Modifier = Modifier,
    personal: Boolean = false, afterPrimary: @Composable () -> Unit = {}, beforeRate: @Composable () -> Unit = {}) {
    var showMethodology by rememberSaveable { mutableStateOf(false) }
    val infoToggleDesc = stringResource(R.string.summary_info_toggle)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .summarySurface()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, color = SummarySecondaryText, style = MaterialTheme.typography.bodyMedium)
                if (personal) {
                    Surface(
                        onClick = { showMethodology = !showMethodology },
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.24f)),
                        modifier = Modifier.size(32.dp).semantics { contentDescription = infoToggleDesc },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("i", color = SummaryText, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
            val weight = WasteFormat.weight(summary.handled.weightGrams, summary = true)
            Text(buildAnnotatedString {
                append(weight.amount)
                withStyle(SpanStyle(fontSize = MaterialTheme.typography.headlineSmall.fontSize, fontWeight = FontWeight.SemiBold)) {
                    append(" " + stringResource(weight.unit.labelResource))
                }
            }, color = SummaryText, style = MaterialTheme.typography.displayLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SummaryPillChip(stringResource(R.string.summary_activity_count, summary.activityCount))
                if (summary.handled.itemCount > 0) {
                    SummaryPillChip(stringResource(R.string.handled_items, WasteFormat.integer(summary.handled.itemCount)))
                }
            }
            if (personal && showMethodology) {
                HorizontalDivider(Modifier.padding(top = 6.dp), color = SummarySecondaryText.copy(alpha = 0.3f))
                Text(stringResource(R.string.summary_footer), style = MaterialTheme.typography.bodySmall, color = SummarySecondaryText)
            }
        }
        afterPrimary()
        beforeRate()
        val rate = WasteFormat.percentage(summary.recyclingRatePercent)
        val rateText = if (rate == null) stringResource(R.string.rate_empty) else stringResource(R.string.rate_value, rate)
        val rateSub = if (rate != null) {
            stringResource(R.string.rate_fraction, summary.recycledActivityCount, summary.handledActivityCount)
        } else {
            stringResource(R.string.rate_no_handled)
        }
        val avoidedText = stringResource(R.string.avoided_items, WasteFormat.integer(summary.avoided.itemCount))
        val avoidedSub = if (summary.avoided.weightGrams > 0) {
            stringResource(R.string.avoided_weight, formatted(WasteFormat.weight(summary.avoided.weightGrams, summary = true)))
        } else null
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val stackTiles = maxWidth < 280.dp || LocalDensity.current.fontScale >= 1.5f
            if (stackTiles) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricTile(stringResource(R.string.rate_title), rateText, R.drawable.ic_recycle, Modifier.fillMaxWidth(), subtitle = rateSub)
                    MetricTile(stringResource(R.string.avoided_short_title), avoidedText, R.drawable.ic_leaf, Modifier.fillMaxWidth(), limeHighlight = true, subtitle = avoidedSub)
                }
            } else {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricTile(stringResource(R.string.rate_title), rateText, R.drawable.ic_recycle, Modifier.weight(1f).fillMaxHeight(), subtitle = rateSub)
                    MetricTile(stringResource(R.string.avoided_short_title), avoidedText, R.drawable.ic_leaf, Modifier.weight(1f).fillMaxHeight(), limeHighlight = true, subtitle = avoidedSub)
                }
            }
        }
    }
}

@Composable
private fun SummaryPillChip(label: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color.White.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = SummaryText,
        )
    }
}

@Composable
internal fun ProgressMeter(progress: UserProgress, modifier: Modifier = Modifier) {
    val xp = progress.xp
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Text(
                    stringResource(R.string.level_title, progress.level),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                )
            }
            Text(
                stringResource(R.string.xp_total, xp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { progress.xpIntoLevel / 100f },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(8.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        Text(
            stringResource(R.string.xp_next, progress.xpToNextLevel, progress.level + 1),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun ActivityRow(
    log: WasteLog,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 66.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryIconBadge(log.category, log.category.iconResource)
            if (LocalDensity.current.fontScale >= 1.5f) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ActivityMetadata(log, Modifier.fillMaxWidth())
                    ActivityQuantity(log)
                }
            } else {
                ActivityMetadata(log, Modifier.weight(1f))
                ActivityQuantity(log)
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 70.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

@Composable
private fun ActivityMetadata(log: WasteLog, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(stringResource(log.category.labelResource), style = MaterialTheme.typography.titleMedium)
        if (log.syncState != ActivitySyncState.SYNCED) Text(stringResource(syncLabel(log.syncState)),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.date_method, stringResource(log.method.labelResource), WasteFormat.activityDate(log.activityDate)),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (log.notes.isNotBlank()) Text(log.notes, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ActivityQuantity(log: WasteLog, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(formatted(WasteFormat.quantity(log.quantity)), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
        if (log.method == DisposalMethod.REDUCE) Text(stringResource(R.string.avoided_label),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal val WasteCategory.iconResource: Int get() = when (this) {
    WasteCategory.ORGANIC -> R.drawable.ic_leaf
    WasteCategory.PLASTIC -> R.drawable.ic_bottle
    WasteCategory.PAPER -> R.drawable.ic_paper
    WasteCategory.GLASS -> R.drawable.ic_jar
    WasteCategory.METAL -> R.drawable.ic_metal
    WasteCategory.ELECTRONIC -> R.drawable.ic_device
    WasteCategory.OTHER -> R.drawable.ic_more
}

@Composable
fun WorkspaceLoading(data: WorkspaceUiState, onRetry: () -> Unit, padding: PaddingValues, modifier: Modifier = Modifier) {
    ScrollPage(padding, modifier) {
        AsyncStatePanel(
            title = stringResource(if (data.loading) R.string.logs_loading else R.string.logs_load_failed),
            icon = if (data.loading) R.drawable.ic_clock else R.drawable.ic_alert,
            modifier = Modifier.fillMaxWidth(),
            description = if (data.loading) null else loadFailureHint(data.error),
            loading = data.loading,
        ) {
            if (!data.loading) ActionButton(stringResource(R.string.retry), onRetry, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun formatted(value: FormattedQuantity) = value.amount + " " + stringResource(value.unit.labelResource)
