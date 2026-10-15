package com.example.wastenot.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.wastenot.R
import com.example.wastenot.data.demo.demoSummary
import com.example.wastenot.model.*
import com.example.wastenot.theme.*
import com.example.wastenot.ui.auth.MessageBanner
import com.example.wastenot.ui.components.*
import java.time.LocalDate

@Composable
private fun pagePadding(padding: PaddingValues): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(padding.calculateStartPadding(direction) + 20.dp, padding.calculateTopPadding() + 20.dp,
        padding.calculateEndPadding(direction) + 20.dp, padding.calculateBottomPadding() + 24.dp)
}

@Composable
private fun ScrollPage(padding: PaddingValues, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier, contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(pagePadding(padding)),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@Composable
private fun DemoCaption(date: LocalDate? = null) {
    Text(stringResource(if (date != null) R.string.demo_caption_date else R.string.demo_caption,
        date?.let(WasteFormat::activityDate).orEmpty()), style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun OverviewScreen(user: User, data: WorkspaceUiState, demo: Boolean, date: LocalDate, padding: PaddingValues,
    onRecord: () -> Unit, onHistory: () -> Unit, onDetail: (String) -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    if (data.records.isEmpty() && (data.loading || data.error != null)) {
        WorkspaceLoading(data, onRetry, padding, modifier)
        return
    }
    ScrollPage(padding, modifier) {
        if (demo) DemoCaption(date)
        Text(stringResource(R.string.greeting, user.name), style = MaterialTheme.typography.titleLarge)
        Text(WasteFormat.activityDate(date), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (data.error != null) MessageBanner(stringResource(R.string.refresh_failed), isError = true)
        if (demo) {
            val today = demoSummary(data.records.filter { it.activityDate == date })
            SummaryBlock(today, stringResource(R.string.handled_today))
            DemoProgress(data.records.size)
        } else MessageBanner(stringResource(R.string.summary_pending))
        TextButton(onClick = onRecord, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.manual_input)) }
        Text(stringResource(R.string.recent_activities), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        if (data.records.isEmpty()) Text(stringResource(R.string.logs_empty))
        data.records.take(3).forEach { log -> ActivityRow(log, { onDetail(log.id) }, Modifier.fillMaxWidth()) }
        TextButton(onClick = onHistory, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.see_history)) }
    }
}

@Composable
private fun SummaryBlock(summary: WasteSummary, title: String) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(LocalWasteNotGradients.current.summary, MaterialTheme.shapes.large)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, color = SummarySecondaryText)
            Text(formatted(WasteFormat.weight(summary.handled.weightGrams)), color = SummaryText, style = MaterialTheme.typography.displayLarge)
            if (summary.handled.itemCount > 0) Text(stringResource(R.string.handled_items, WasteFormat.integer(summary.handled.itemCount)), color = SummaryText)
        }
        if (summary.avoided.itemCount > 0 || summary.avoided.weightGrams > 0) Surface(color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer, shape = MaterialTheme.shapes.medium) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (summary.avoided.itemCount > 0) Text(stringResource(R.string.avoided_items, WasteFormat.integer(summary.avoided.itemCount)), style = MaterialTheme.typography.titleLarge)
                if (summary.avoided.weightGrams > 0) Text(stringResource(R.string.avoided_weight, formatted(WasteFormat.weight(summary.avoided.weightGrams))))
                Text(stringResource(R.string.reduce_separate), style = MaterialTheme.typography.bodySmall)
            }
        }
        val rate = WasteFormat.percentage(summary.recyclingRatePercent)
        Text(if (rate == null) stringResource(R.string.rate_unavailable) else stringResource(R.string.recycling_rate, rate, summary.recycledActivityCount, summary.handledActivityCount))
        Text(stringResource(R.string.activity_count, summary.activityCount))
    }
}

@Composable
private fun DemoProgress(count: Int) {
    val xp = count * 10
    Text(stringResource(R.string.progress_summary, 1 + xp / 100, xp % 100), style = MaterialTheme.typography.titleSmall)
    LinearProgressIndicator(progress = { (xp % 100) / 100f }, modifier = Modifier.fillMaxWidth())
}

@Composable
fun HistoryScreen(data: WorkspaceUiState, demo: Boolean, padding: PaddingValues, onRecord: () -> Unit,
    onDetail: (String) -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    var category by rememberSaveable { mutableStateOf<WasteCategory?>(null) }
    var method by rememberSaveable { mutableStateOf<DisposalMethod?>(null) }
    val visible = data.records.filter { (category == null || it.category == category) && (method == null || it.method == method) }
    LazyColumn(modifier = modifier, contentPadding = pagePadding(padding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { if (demo) DemoCaption() else Text(stringResource(R.string.loaded_records_only), style = MaterialTheme.typography.bodySmall) }
        item { TextButton(onClick = onRecord, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.manual_input)) } }
        item { CategoryPicker(category, { category = it }, Modifier.fillMaxWidth()) }
        item { MethodSelector(method, { method = it }, Modifier.fillMaxWidth()) }
        if (category != null || method != null) item {
            TextButton(onClick = { category = null; method = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.clear_filters)) }
        }
        if (data.loading) item { CircularProgressIndicator() }
        if (data.error != null) item {
            MessageBanner(stringResource(R.string.logs_load_failed), isError = true)
            TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
        if (!data.loading && data.error == null && visible.isEmpty()) item { Text(stringResource(if (data.records.isEmpty()) R.string.logs_empty else R.string.filtered_empty)) }
        items(visible, key = WasteLog::id) { log -> ActivityRow(log, { onDetail(log.id) }, Modifier.fillMaxWidth()) }
    }
}

@Composable
private fun ActivityRow(log: WasteLog, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, modifier = modifier.heightIn(min = 48.dp), shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(log.category.labelResource), style = MaterialTheme.typography.titleMedium)
                Text(formatted(WasteFormat.quantity(log.quantity)), style = MaterialTheme.typography.titleLarge)
            }
            Text(stringResource(log.method.labelResource) + if (log.method == DisposalMethod.REDUCE) stringResource(R.string.avoided_suffix) else "",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(WasteFormat.activityDate(log.activityDate), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (log.notes.isNotBlank()) Text(log.notes, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun StatisticsScreen(data: WorkspaceUiState, demo: Boolean, date: LocalDate, padding: PaddingValues, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    var days by rememberSaveable { mutableIntStateOf(7) }
    ScrollPage(padding, modifier) {
        if (demo) DemoCaption(date)
        RangeSelector(days, { days = it })
        Text(stringResource(R.string.date_range, WasteFormat.activityDate(date.minusDays(days - 1L)), WasteFormat.activityDate(date)))
        if (demo) {
            val records = data.records.filter { it.activityDate in ActivityDateRange.lastDays(date, days) }
            SummaryBlock(demoSummary(records), stringResource(R.string.handled_period))
            DisposalMethod.entries.forEach { method -> Text(stringResource(R.string.method_activity_count, stringResource(method.labelResource), records.count { it.method == method })) }
        } else MessageBanner(stringResource(R.string.summary_pending))
        if (data.error != null) { MessageBanner(stringResource(R.string.refresh_failed), isError = true); TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) } }
    }
}

@Composable
fun ProfileScreen(user: User, data: WorkspaceUiState, demo: Boolean, theme: ThemeMode, padding: PaddingValues,
    onTheme: () -> Unit, onLogout: () -> Unit, modifier: Modifier = Modifier) {
    ScrollPage(padding, modifier) {
        if (demo) DemoCaption()
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
            Text(user.name.split(' ').filter(String::isNotBlank).take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString(""),
                Modifier.padding(20.dp), style = MaterialTheme.typography.headlineMedium)
        }
        Text(user.name, style = MaterialTheme.typography.headlineSmall)
        Text(user.email)
        Text(stringResource(R.string.account_timezone, user.timezone.id))
        if (demo) DemoProgress(data.records.size) else user.progress?.let { Text(stringResource(R.string.progress_summary, it.level, it.xpIntoLevel)) }
        TextButton(onClick = onTheme, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.theme_setting, stringResource(theme.label))) }
        Text(stringResource(R.string.app_version, com.example.wastenot.BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodySmall)
        ActionButton(stringResource(R.string.logout_action), onLogout, Modifier.fillMaxWidth(), destructive = true)
    }
}

@Composable
fun GuidePlaceholderScreen(demo: Boolean, padding: PaddingValues, modifier: Modifier = Modifier) {
    ScrollPage(padding, modifier) {
        if (demo) DemoCaption()
        Text(stringResource(R.string.guide_pending_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.guide_pending_message))
    }
}

@Composable
fun LogDetailScreen(log: WasteLog?, demo: Boolean, padding: PaddingValues, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    ScrollPage(padding, modifier) {
        if (demo) DemoCaption()
        if (log == null) Text(stringResource(R.string.log_not_loaded)) else {
            Text(stringResource(log.category.labelResource), style = MaterialTheme.typography.headlineMedium)
            Text(formatted(WasteFormat.quantity(log.quantity)), style = MaterialTheme.typography.displayMedium)
            Text(stringResource(log.method.labelResource) + if (log.method == DisposalMethod.REDUCE) stringResource(R.string.avoided_suffix) else "")
            Text(WasteFormat.activityDate(log.activityDate))
            Text(log.activityTimezone.id)
            Text(log.notes.ifBlank { stringResource(R.string.no_notes) })
            Text(stringResource(R.string.record_version, log.version), style = MaterialTheme.typography.bodySmall)
            ActionButton(stringResource(R.string.log_edit), onEdit, Modifier.fillMaxWidth())
        }
    }
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
