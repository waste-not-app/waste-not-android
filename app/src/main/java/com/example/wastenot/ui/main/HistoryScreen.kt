package com.example.wastenot.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.wastenot.R
import com.example.wastenot.model.DisposalMethod
import com.example.wastenot.model.WasteCategory
import com.example.wastenot.model.WasteFormat
import com.example.wastenot.ui.auth.MessageBanner
import com.example.wastenot.ui.components.ActionButton
import com.example.wastenot.ui.components.DateField
import com.example.wastenot.ui.components.labelResource
import java.time.LocalDate
import kotlinx.coroutines.launch

private val FilterSaver = listSaver<HistoryFilter, String>(
    save = { listOf(it.category?.name.orEmpty(), it.method?.name.orEmpty(), it.from?.toString().orEmpty(), it.to?.toString().orEmpty()) },
    restore = { HistoryFilter(it[0].takeIf(String::isNotEmpty)?.let(WasteCategory::valueOf),
        it[1].takeIf(String::isNotEmpty)?.let(DisposalMethod::valueOf),
        it[2].takeIf(String::isNotEmpty)?.let(LocalDate::parse), it[3].takeIf(String::isNotEmpty)?.let(LocalDate::parse)) },
)

@Composable
fun HistoryScreen(
    data: WorkspaceUiState,
    demo: Boolean,
    padding: PaddingValues,
    onRecord: () -> Unit,
    onDetail: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
    filterOpen: Boolean = false,
    onOpenFilter: () -> Unit = {},
    onDismissFilter: () -> Unit = {},
    deletedNotice: Boolean = false,
    onDismissNotice: () -> Unit = {},
) {
    var filter by rememberSaveable(stateSaver = FilterSaver) { mutableStateOf(HistoryFilter()) }
    // shortcut: paginates the loaded dummy snapshot; replace with repository cursors for A18.
    var limit by rememberSaveable { mutableIntStateOf(20) }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val visible = remember(data.records, filter) { filter.apply(data.records) }
    val groups = remember(visible, limit) { visible.take(limit).groupBy { it.activityDate } }
    fun applyFilter(next: HistoryFilter) {
        filter = next
        limit = 20
        scope.launch { list.scrollToItem(0) }
    }
    if (filterOpen) HistoryFilterSheet(filter, today, onDismissFilter, {
        applyFilter(it)
        onDismissFilter()
    })
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (deletedNotice) Row(Modifier.widthIn(max = 840.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MessageBanner(stringResource(R.string.log_deleted_notice), Modifier.weight(1f))
            TextButton(onClick = onDismissNotice) { Text(stringResource(R.string.close)) }
        }
        LazyColumn(
            modifier = Modifier.weight(1f).widthIn(max = 840.dp).fillMaxWidth().testTag("history-list"),
            state = list,
            contentPadding = pagePadding(padding),
        ) {
            item(key = "count") {
                Text(stringResource(if (filter.active) R.string.history_filtered_count else R.string.history_count,
                    WasteFormat.integer(visible.size.toLong())), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!demo) item(key = "partial") {
                Text(stringResource(R.string.loaded_records_only), style = MaterialTheme.typography.bodySmall)
            }
            if (filter.active) item(key = "filters") {
                FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    filter.category?.let { FilterChip(true, onOpenFilter, label = { Text(stringResource(it.labelResource)) }, modifier = Modifier.heightIn(min = 48.dp)) }
                    filter.method?.let { FilterChip(true, onOpenFilter, label = { Text(stringResource(it.labelResource)) }, modifier = Modifier.heightIn(min = 48.dp)) }
                    if (filter.from != null && filter.to != null) FilterChip(true, onOpenFilter, label = {
                        Text(stringResource(R.string.history_date_range, WasteFormat.activityDate(filter.from!!), WasteFormat.activityDate(filter.to!!)))
                    }, modifier = Modifier.heightIn(min = 48.dp))
                    TextButton(onClick = { applyFilter(HistoryFilter()) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.clear_filters)) }
                }
            }
            if (data.loading) item(key = "loading") { CircularProgressIndicator(Modifier.padding(vertical = 20.dp)) }
            if (data.error != null) item(key = "error") {
                Column(Modifier.padding(vertical = 16.dp)) {
                    MessageBanner(stringResource(R.string.logs_load_failed), isError = true)
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                }
            }
            if (!data.loading && data.error == null && visible.isEmpty()) item(key = "empty") {
                Column(Modifier.padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(if (filter.active) R.string.filtered_empty else R.string.logs_empty))
                    if (filter.active) TextButton(onClick = { applyFilter(HistoryFilter()) }) { Text(stringResource(R.string.clear_filters)) }
                }
            }
            groups.forEach { (date, records) ->
                item(key = "date:$date") {
                    Text(when (date) {
                        today -> stringResource(R.string.history_today)
                        today.minusDays(1) -> stringResource(R.string.history_yesterday)
                        else -> WasteFormat.activityDate(date)
                    }, modifier = Modifier.padding(top = 24.dp, bottom = 4.dp).semantics { heading() },
                        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(records, key = { "log:${it.id}" }) { log -> ActivityRow(log, { onDetail(log.id) }, Modifier.fillMaxWidth()) }
            }
            if (visible.isNotEmpty()) item(key = "pages") {
                Column(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.history_shown, WasteFormat.integer(minOf(limit, visible.size).toLong()), WasteFormat.integer(visible.size.toLong())),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (limit < visible.size) TextButton(onClick = { limit += 20 }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.load_more_logs)) }
                }
            }
            item(key = "manual") {
                OutlinedButton(onClick = onRecord, shape = MaterialTheme.shapes.small, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp).heightIn(min = 52.dp)) { Text(stringResource(R.string.manual_input)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryFilterSheet(
    active: HistoryFilter,
    today: LocalDate,
    onDismiss: () -> Unit,
    onApply: (HistoryFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by rememberSaveable(stateSaver = FilterSaver) { mutableStateOf(active) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    val error = if (!attempted) null else when (draft.dateError) {
        HistoryFilter.DateError.INCOMPLETE -> stringResource(R.string.filter_dates_incomplete)
        HistoryFilter.DateError.REVERSED -> stringResource(R.string.filter_dates_reversed)
        null -> null
    }
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), dragHandle = { SheetDragHandle() }) {
        SheetSystemBar()
        Column(
            modifier = Modifier.fillMaxWidth().testTag("history-filter-sheet").verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.filter_history), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f).semantics { heading() })
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.cancel)) }
            }
            Text(stringResource(R.string.filter_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilterChoice(draft.category, WasteCategory.entries, R.string.category, R.string.all_categories,
                { it.labelResource }, { draft = draft.copy(category = it) }, Modifier.fillMaxWidth())
            FilterChoice(draft.method, DisposalMethod.entries, R.string.method, R.string.all_methods,
                { it.labelResource }, { draft = draft.copy(method = it) }, Modifier.fillMaxWidth())
            DateField(draft.from, LocalDate.of(1970, 1, 1), today, { draft = draft.copy(from = it) },
                Modifier.fillMaxWidth(), label = stringResource(R.string.filter_from))
            DateField(draft.to, LocalDate.of(1970, 1, 1), today, { draft = draft.copy(to = it) },
                Modifier.fillMaxWidth(), label = stringResource(R.string.filter_to), error = error)
            ActionButton(stringResource(R.string.apply_filters), {
                attempted = true
                if (draft.dateError == null) onApply(draft)
            }, Modifier.fillMaxWidth())
            OutlinedButton(onClick = { draft = HistoryFilter(); attempted = false }, shape = MaterialTheme.shapes.small, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.reset_filter_draft)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> FilterChoice(
    value: T?,
    options: List<T>,
    label: Int,
    all: Int,
    optionLabel: (T) -> Int,
    onSelect: (T?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val fieldLabel = stringResource(label)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(fieldLabel, style = MaterialTheme.typography.titleSmall)
        ExposedDropdownMenuBox(expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(value = stringResource(value?.let(optionLabel) ?: all), onValueChange = {}, readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics { contentDescription = fieldLabel }
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable))
            ExposedDropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text(stringResource(all)) }, onClick = { onSelect(null); expanded = false })
                options.forEach { option ->
                    DropdownMenuItem(text = { Text(stringResource(optionLabel(option))) }, onClick = { onSelect(option); expanded = false })
                }
            }
        }
    }
}
