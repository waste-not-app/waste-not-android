package com.example.wastenot.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
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
import com.example.wastenot.ui.components.AsyncStatePanel
import com.example.wastenot.ui.components.SecondaryActionButton
import com.example.wastenot.ui.components.TextActionButton
import com.example.wastenot.ui.components.DateField
import com.example.wastenot.ui.components.labelResource
import com.example.wastenot.ui.components.GroupSectionLabel
import com.example.wastenot.ui.components.InsetGroupItemContainer
import com.example.wastenot.ui.components.wasteTextFieldColors
import java.time.LocalDate
import kotlinx.coroutines.launch

private val FilterSaver = listSaver<HistoryFilter, String>(
    save = { listOf(it.category?.name.orEmpty(), it.method?.name.orEmpty(), it.from?.toString().orEmpty(), it.to?.toString().orEmpty()) },
    restore = { HistoryFilter(it[0].takeIf(String::isNotEmpty)?.let(WasteCategory::valueOf),
        it[1].takeIf(String::isNotEmpty)?.let(DisposalMethod::valueOf),
        it[2].takeIf(String::isNotEmpty)?.let(LocalDate::parse), it[3].takeIf(String::isNotEmpty)?.let(LocalDate::parse)) },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    data: HistoryUiState,
    padding: PaddingValues,
    onRecord: () -> Unit,
    onDetail: (String) -> Unit,
    onRetry: () -> Unit,
    onFilter: (HistoryFilter) -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
    filterOpen: Boolean = false,
    onOpenFilter: () -> Unit = {},
    onDismissFilter: () -> Unit = {},
    deletedNotice: Boolean = false,
    onDismissNotice: () -> Unit = {},
) {
    val filter = data.filter
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val groups = remember(data.records) { data.records.groupBy { it.activityDate } }
    fun applyFilter(next: HistoryFilter) {
        if (next != filter) {
            onFilter(next)
            scope.launch { list.scrollToItem(0) }
        }
    }
    if (filterOpen) HistoryFilterSheet(filter, today, onDismissFilter, {
        applyFilter(it)
        onDismissFilter()
    })
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (deletedNotice) Column(
            Modifier.widthIn(max = 840.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            MessageBanner(stringResource(R.string.log_deleted_notice), Modifier.fillMaxWidth())
            TextActionButton(stringResource(R.string.close), onDismissNotice, Modifier.align(Alignment.End))
        }
        PullToRefreshBox(isRefreshing = data.refreshing, onRefresh = onRetry,
            modifier = Modifier.weight(1f).widthIn(max = 840.dp).fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("history-list"),
            state = list,
            contentPadding = pagePadding(padding),
        ) {
            if (data.records.isNotEmpty() || (!data.loading && data.error == null)) item(key = "count") {
                Text(pluralStringResource(if (filter.active) R.plurals.history_filtered_count else R.plurals.history_count,
                    data.records.size, WasteFormat.integer(data.records.size.toLong())),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (filter.active) item(key = "filters") {
                FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    filter.category?.let { FilterChip(true, onOpenFilter, label = { Text(stringResource(it.labelResource)) }, modifier = Modifier.heightIn(min = 48.dp)) }
                    filter.method?.let { FilterChip(true, onOpenFilter, label = { Text(stringResource(it.labelResource)) }, modifier = Modifier.heightIn(min = 48.dp)) }
                    val from = filter.from
                    val to = filter.to
                    if (from != null && to != null) FilterChip(true, onOpenFilter, label = {
                        Text(stringResource(R.string.history_date_range, WasteFormat.activityDate(from), WasteFormat.activityDate(to)))
                    }, modifier = Modifier.heightIn(min = 48.dp))
                    TextActionButton(stringResource(R.string.clear_filters), onClick = { applyFilter(HistoryFilter()) })
                }
            }
            if (data.loading) item(key = "loading") {
                if (data.records.isEmpty()) AsyncStatePanel(stringResource(R.string.logs_loading), R.drawable.ic_clock,
                    Modifier.fillMaxWidth(), loading = true)
                else Column(Modifier.padding(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.logs_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
            if (data.error != null && (!data.loading || data.records.isNotEmpty())) item(key = "error") {
                if (data.records.isEmpty()) AsyncStatePanel(stringResource(R.string.logs_load_failed), R.drawable.ic_alert,
                    Modifier.fillMaxWidth(), description = loadFailureHint(data.error)) {
                    ActionButton(stringResource(R.string.retry), onRetry, Modifier.fillMaxWidth())
                } else Column(Modifier.padding(vertical = 16.dp)) {
                    MessageBanner(stringResource(R.string.logs_load_failed), isError = true)
                    TextActionButton(stringResource(R.string.retry), onRetry)
                }
            }
            if (!data.loading && !data.refreshing && data.error == null && data.records.isEmpty()) item(key = "empty") {
                AsyncStatePanel(
                    title = stringResource(if (filter.active) R.string.history_filter_empty_title else R.string.history_empty_title),
                    icon = if (filter.active) R.drawable.ic_filter else R.drawable.ic_history,
                    modifier = Modifier.fillMaxWidth(),
                    description = stringResource(if (filter.active) R.string.history_filter_empty_hint else R.string.history_empty_hint),
                ) {
                    if (filter.active) ActionButton(stringResource(R.string.clear_filters), { applyFilter(HistoryFilter()) }, Modifier.fillMaxWidth())
                    else ActionButton(stringResource(R.string.manual_input), onRecord, Modifier.fillMaxWidth())
                }
            }
            groups.forEach { (date, records) ->
                item(key = "date:$date") {
                    GroupSectionLabel(
                        when (date) {
                            today -> stringResource(R.string.history_today)
                            today.minusDays(1) -> stringResource(R.string.history_yesterday)
                            else -> WasteFormat.activityDate(date)
                        },
                        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
                    )
                }
                itemsIndexed(records, key = { _, log -> log.id }) { idx, log ->
                    InsetGroupItemContainer(
                        isFirst = idx == 0,
                        isLast = idx == records.lastIndex,
                    ) {
                        ActivityRow(
                            log = log,
                            onClick = { onDetail(log.id) },
                            modifier = Modifier.fillMaxWidth(),
                            showDivider = idx < records.lastIndex,
                        )
                    }
                }
            }
            if (data.records.isNotEmpty() || data.pageError != null) item(key = "pages") {
                Column(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    when {
                        data.loadingMore -> {
                            CircularProgressIndicator(Modifier.padding(12.dp))
                            Text(stringResource(R.string.history_loading_more), style = MaterialTheme.typography.bodySmall)
                        }
                        data.pageError != null -> {
                            MessageBanner(stringResource(R.string.history_page_failed), isError = true)
                            Text(loadFailureHint(data.pageError), style = MaterialTheme.typography.bodySmall)
                            TextActionButton(stringResource(if (data.pageRestartRequired) R.string.history_reload else R.string.retry),
                                if (data.pageRestartRequired) onRetry else onMore)
                        }
                        data.nextCursor != null -> TextActionButton(stringResource(R.string.load_more_logs), onMore)
                        data.error == null && !data.refreshing -> Text(stringResource(R.string.history_all_loaded),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (data.records.isNotEmpty()) item(key = "manual") {
                SecondaryActionButton(stringResource(R.string.manual_input), onRecord, Modifier.fillMaxWidth().padding(top = 20.dp))
            }
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
        val filterScroll = rememberScrollState()
        Column(
            modifier = Modifier.fillMaxWidth().testTag("history-filter-sheet").sheetVerticalScroll(filterScroll)
                .navigationBarsPadding().imePadding()
                .padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.filter_history), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                TextActionButton(stringResource(R.string.cancel), onDismiss, Modifier.align(Alignment.End))
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
            SecondaryActionButton(stringResource(R.string.reset_filter_draft), { draft = HistoryFilter(); attempted = false }, Modifier.fillMaxWidth())
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
            OutlinedTextField(
                value = stringResource(value?.let(optionLabel) ?: all),
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                shape = RoundedCornerShape(15.dp),
                colors = wasteTextFieldColors(),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics { contentDescription = fieldLabel }
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text(stringResource(all)) }, onClick = { onSelect(null); expanded = false })
                options.forEach { option ->
                    DropdownMenuItem(text = { Text(stringResource(optionLabel(option))) }, onClick = { onSelect(option); expanded = false })
                }
            }
        }
    }
}
