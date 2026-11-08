package com.example.wastenot.ui.main

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wastenot.data.network.ApiFailure
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.repository.ActivityStore
import com.example.wastenot.model.DisposalMethod
import com.example.wastenot.model.LogDateRange
import com.example.wastenot.model.WasteCategory
import com.example.wastenot.model.WasteLog
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistoryUiState(
    val records: List<WasteLog> = emptyList(),
    val loading: Boolean = true,
    val filter: HistoryFilter = HistoryFilter(),
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val nextCursor: String? = null,
    val error: ApiFailureKind? = null,
    val pageError: ApiFailureKind? = null,
    val pageRestartRequired: Boolean = false,
)

data class ConfirmedActivityChange(val revision: Int, val log: WasteLog? = null, val deletedId: String? = null)

class HistoryViewModel(
    private val store: ActivityStore,
    private val saved: SavedStateHandle,
    private val now: () -> Instant = Instant::now,
) : ViewModel() {
    private val mutableState = MutableStateFlow(HistoryUiState(filter = restoredFilter()))
    val state = mutableState.asStateFlow()
    private var started = false
    private var loaded = false
    private var request: Job? = null
    private var generation = 0
    private var retryAt: Instant? = null
    private var handledRevision = 0
    private val cursors = mutableSetOf<String>()

    fun load() { if (!started) refresh() }

    fun filter(value: HistoryFilter) {
        if (value.dateError != null || value == state.value.filter) return
        saved["history_category"] = value.category?.name
        saved["history_method"] = value.method?.name
        saved["history_from"] = value.from?.toString()
        saved["history_to"] = value.to?.toString()
        loaded = false
        mutableState.value = HistoryUiState(filter = value)
        refresh()
    }

    fun refresh() = fetch(more = false)

    fun more() {
        val current = state.value
        if (current.loading || current.refreshing || current.loadingMore || current.nextCursor == null) return
        fetch(more = true)
    }

    fun changed(change: ConfirmedActivityChange) {
        if (change.revision <= handledRevision) return
        handledRevision = change.revision
        change.deletedId?.let(::remove)
        change.log?.let { reconcile(it, insert = true) }
        if (started) refresh()
    }

    fun reconcile(log: WasteLog, insert: Boolean = false) {
        mutableState.update { current ->
            if (!insert && current.records.none { it.id == log.id }) current
            else {
                val newest = (current.records.filter { it.id == log.id } + log).maxBy { it.version }
                current.copy(records = merge(current.records.filterNot { it.id == log.id }, listOf(newest).filter(current.filter::matches)))
            }
        }
    }

    fun unavailable(id: String) {
        val present = state.value.records.any { it.id == id }
        remove(id)
        if (present && started) refresh()
    }

    private fun remove(id: String) {
        mutableState.update { it.copy(records = it.records.filterNot { record -> record.id == id }) }
    }

    private fun fetch(more: Boolean) {
        started = true
        val before = state.value
        val cursor = if (more) before.nextCursor ?: return else null
        generation++
        val stamp = generation
        request?.cancel()
        if (!more) cursors.clear()
        mutableState.update { it.copy(loading = !more && !loaded, refreshing = !more && loaded,
            loadingMore = more, nextCursor = if (more) cursor else null, error = null, pageError = null,
            pageRestartRequired = false) }
        if (retryAt?.isAfter(now()) == true) {
            failed(ApiFailure(ApiFailureKind.RATE_LIMITED), more)
            return
        }
        val filter = before.filter
        request = viewModelScope.launch {
            val dates = filter.from?.let { LogDateRange(it, checkNotNull(filter.to)) }
            val response = store.page(filter.category, filter.method, dates, cursor)
            currentCoroutineContext().ensureActive()
            if (stamp != generation) return@launch
            when (response) {
                is ApiResult.Success -> accept(response, filter, cursor, more)
                is ApiResult.Failure -> failed(response.error, more)
            }
        }
    }

    private fun accept(response: ApiResult.Success<List<WasteLog>>, filter: HistoryFilter, cursor: String?, more: Boolean) {
        val page = response.page
        val next = page?.nextCursor
        if (page == null || page.hasMore != (next != null) ||
            (next != null && (next.isBlank() || next == cursor || next in cursors)) ||
            response.value.size > 20 || (page.hasMore && response.value.isEmpty()) ||
            response.value.any { !filter.matches(it) }) {
            failed(ApiFailure(ApiFailureKind.MALFORMED_RESPONSE), more)
            return
        }
        cursor?.let(cursors::add)
        loaded = true
        mutableState.update { it.copy(records = merge(if (more) it.records else emptyList(), response.value),
            loading = false, refreshing = false, loadingMore = false, nextCursor = next,
            error = null, pageError = null, pageRestartRequired = false) }
    }

    private fun failed(error: ApiFailure, more: Boolean) {
        if (error.kind == ApiFailureKind.RATE_LIMITED && error.retryAt != null) {
            retryAt = maxOf(retryAt ?: Instant.MIN, error.retryAt)
        }
        // A cursor rejected by the server belongs to an expired or different query.
        val restart = more && error.kind in setOf(ApiFailureKind.INVALID_REQUEST, ApiFailureKind.MALFORMED_RESPONSE)
        mutableState.update { it.copy(loading = false, refreshing = false, loadingMore = false,
            error = if (more) null else error.kind, pageError = if (more) error.kind else null,
            nextCursor = if (restart) null else it.nextCursor, pageRestartRequired = restart) }
    }

    private fun restoredFilter(): HistoryFilter = runCatching {
        HistoryFilter(WasteCategory.entries.find { it.name == saved.get<String>("history_category") },
            DisposalMethod.entries.find { it.name == saved.get<String>("history_method") },
            saved.get<String>("history_from")?.let(LocalDate::parse), saved.get<String>("history_to")?.let(LocalDate::parse))
            .takeIf { it.dateError == null } ?: HistoryFilter()
    }.getOrDefault(HistoryFilter())

    override fun onCleared() {
        generation++
        request?.cancel()
        mutableState.value = HistoryUiState()
        super.onCleared()
    }

    private fun merge(old: List<WasteLog>, incoming: List<WasteLog>): List<WasteLog> =
        (old + incoming).groupBy { it.id }.values.map { versions -> versions.maxBy { it.version } }
            .sortedWith(compareByDescending<WasteLog> { it.activityDate }.thenByDescending { it.id })
}
