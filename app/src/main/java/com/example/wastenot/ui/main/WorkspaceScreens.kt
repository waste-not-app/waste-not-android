package com.example.wastenot.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.example.wastenot.R
import com.example.wastenot.data.dummy.dummyProgress
import com.example.wastenot.data.demo.demoSummary
import com.example.wastenot.model.*
import com.example.wastenot.theme.*
import com.example.wastenot.ui.auth.MessageBanner
import com.example.wastenot.ui.components.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun pagePadding(padding: PaddingValues): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(padding.calculateStartPadding(direction) + 20.dp, padding.calculateTopPadding() + 20.dp,
        padding.calculateEndPadding(direction) + 20.dp, padding.calculateBottomPadding() + 24.dp)
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
fun OverviewScreen(user: User, data: WorkspaceUiState, demo: Boolean, date: LocalDate, padding: PaddingValues,
    onRecord: () -> Unit, onHistory: () -> Unit, onDetail: (String) -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier,
    onProfile: () -> Unit = {}, onResumeDraft: (() -> Unit)? = null) {
    if (data.records.isEmpty() && (data.loading || data.error != null)) {
        WorkspaceLoading(data, onRetry, padding, modifier)
        return
    }
    ScrollPage(padding, modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.forLanguageTag("id-ID"))),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.greeting, user.name), style = MaterialTheme.typography.headlineMedium)
            }
            FilledIconButton(onClick = onProfile, shape = MaterialTheme.shapes.medium,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.ic_profile), stringResource(R.string.open_profile))
            }
        }
        if (data.error != null) MessageBanner(stringResource(R.string.refresh_failed), isError = true)
        if (onResumeDraft != null) PausedDraftNotice(onResumeDraft)
        if (demo) {
            val today = demoSummary(data.records.filter { it.activityDate == date })
            SummaryBlock(today, stringResource(R.string.handled_today), afterPrimary = {
                OutlinedButton(onClick = onRecord, shape = MaterialTheme.shapes.small,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.manual_input)) }
            }, personal = true)
            ProgressMeter(dummyProgress(data.records, date))
        } else MessageBanner(stringResource(R.string.summary_pending))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.recent_activities), style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f).semantics { heading() })
            TextButton(onClick = onHistory) { Text(stringResource(R.string.see_history)) }
        }
        if (data.records.isEmpty()) Text(stringResource(R.string.logs_empty))
        data.records.take(3).forEach { log -> ActivityRow(log, { onDetail(log.id) }, Modifier.fillMaxWidth()) }
    }
}

@Composable
internal fun SummaryBlock(summary: WasteSummary, title: String, modifier: Modifier = Modifier,
    personal: Boolean = false, afterPrimary: @Composable () -> Unit = {}, beforeRate: @Composable () -> Unit = {}) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(LocalWasteNotGradients.current.summary, MaterialTheme.shapes.large)
                .drawBehind {
                    drawCircle(Color.White.copy(alpha = 0.06f), radius = 82.dp.toPx(),
                        center = Offset(size.width, 0f), style = Stroke(width = 20.dp.toPx()))
                }
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, color = SummarySecondaryText)
            val weight = WasteFormat.weight(summary.handled.weightGrams, summary = true)
            Text(buildAnnotatedString {
                append(weight.amount)
                withStyle(SpanStyle(fontSize = MaterialTheme.typography.headlineSmall.fontSize)) {
                    append(" " + stringResource(weight.unit.labelResource))
                }
            }, color = SummaryText, style = MaterialTheme.typography.displayLarge)
            Text(stringResource(R.string.summary_activity_count, summary.activityCount), color = SummarySecondaryText,
                style = MaterialTheme.typography.bodyMedium)
            if (summary.handled.itemCount > 0) Text(stringResource(R.string.handled_items, WasteFormat.integer(summary.handled.itemCount)), color = SummaryText)
            if (personal) {
                HorizontalDivider(Modifier.padding(top = 12.dp, bottom = 8.dp), color = SummarySecondaryText.copy(alpha = 0.4f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_history), null, Modifier.size(20.dp), tint = SummarySecondaryText)
                    Text(stringResource(R.string.summary_footer), style = MaterialTheme.typography.bodySmall, color = SummarySecondaryText)
                }
            }
        }
        afterPrimary()
        if (summary.avoided.itemCount > 0 || summary.avoided.weightGrams > 0) Row(
            modifier = Modifier.fillMaxWidth().background(LocalWasteNotGradients.current.accent, MaterialTheme.shapes.medium).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_leaf), null, Modifier.size(24.dp), tint = OnAccent)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (summary.avoided.itemCount > 0) Text(stringResource(R.string.avoided_items, WasteFormat.integer(summary.avoided.itemCount)), style = MaterialTheme.typography.titleMedium, color = OnAccent)
                if (summary.avoided.weightGrams > 0) Text(stringResource(R.string.avoided_weight, formatted(WasteFormat.weight(summary.avoided.weightGrams, summary = true))), color = OnAccent)
                Text(stringResource(R.string.reduce_separate), style = MaterialTheme.typography.bodyMedium, color = OnAccent)
            }
        }
        beforeRate()
        val rate = WasteFormat.percentage(summary.recyclingRatePercent)
        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.rate_title), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.rate_basis), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(if (rate == null) stringResource(R.string.rate_empty) else stringResource(R.string.rate_value, rate), style = MaterialTheme.typography.headlineSmall)
            }
            if (rate != null) Text(stringResource(R.string.rate_fraction, summary.recycledActivityCount, summary.handledActivityCount),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else Text(stringResource(R.string.rate_no_handled), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
internal fun ProgressMeter(progress: UserProgress, modifier: Modifier = Modifier) {
    val xp = progress.xp
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.level_title, progress.level), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.xp_total, xp), Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearProgressIndicator(progress = { progress.xpIntoLevel / 100f }, modifier = Modifier.fillMaxWidth().height(7.dp),
            color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.outlineVariant,
            drawStopIndicator = {})
        Text(stringResource(R.string.xp_next, progress.xpToNextLevel, progress.level + 1), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun ActivityRow(log: WasteLog, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, modifier = modifier.heightIn(min = 64.dp), color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface) {
        Column {
            Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.small), contentAlignment = Alignment.Center) {
                    Icon(painterResource(log.category.iconResource), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                }
                if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.5f) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActivityMetadata(log, Modifier.fillMaxWidth())
                        ActivityQuantity(log)
                    }
                } else {
                    ActivityMetadata(log, Modifier.weight(1f))
                    ActivityQuantity(log)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun ActivityMetadata(log: WasteLog, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(log.category.labelResource), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.date_method, stringResource(log.method.labelResource), WasteFormat.activityDate(log.activityDate)),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (log.notes.isNotBlank()) Text(log.notes, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ActivityQuantity(log: WasteLog, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(formatted(WasteFormat.quantity(log.quantity)), style = MaterialTheme.typography.titleSmall)
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
        if (data.loading) { CircularProgressIndicator(); Text(stringResource(R.string.logs_loading)) }
        else { Text(stringResource(R.string.logs_load_failed)); ActionButton(stringResource(R.string.retry), onRetry) }
    }
}

@Composable
private fun formatted(value: FormattedQuantity) = value.amount + " " + stringResource(value.unit.labelResource)
