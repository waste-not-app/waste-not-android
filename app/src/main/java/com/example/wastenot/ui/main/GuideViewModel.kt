package com.example.wastenot.ui.main

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.data.network.ApiFailure
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.repository.GuideStore
import com.example.wastenot.model.WasteCategory
import com.example.wastenot.model.WasteGuide
import java.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GuideDetailState(val id: String, val article: WasteGuide? = null, val loading: Boolean = true, val error: ApiFailureKind? = null)

data class GuideSearchFilters(val query: String, val category: WasteCategory?)

data class GuideUiState(
    val query: String = "",
    val category: WasteCategory? = null,
    val articles: List<WasteGuide> = emptyList(),
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val nextCursor: String? = null,
    val error: ApiFailureKind? = null,
    val detail: GuideDetailState? = null,
    val resultsFor: GuideSearchFilters? = null,
    val searchPending: Boolean = false,
) {
    val showingPreviousResults: Boolean
        get() = resultsFor?.let { it.query != query.trim() || it.category != category } == true
    }

class GuideViewModel(private val store: GuideStore, private val saved: SavedStateHandle,
    private val now: () -> Instant = Instant::now) : ViewModel() {
    private val mutableState = MutableStateFlow(GuideUiState(
        query = saved.get<String>("guide_query").orEmpty().truncateQuery(),
        category = WasteCategory.entries.find { it.name == saved.get<String>("guide_category") },
    ))
    val state = mutableState.asStateFlow()
    private var searching: Job? = null
    private var reading: Job? = null
    private var started = false
    private var retryAt: Instant? = null

    fun load() { if (!started) search() }

    fun query(value: String) {
        val query = value.truncateQuery()
        val before = mutableState.value
        if (query == before.query) return
        saved["guide_query"] = query
        if (query.trim() == before.query.trim()) {
            mutableState.update { it.copy(query = query) }
        } else {
            request(before, query = query, debounce = true)
        }
    }

    fun category(value: WasteCategory?) {
        val before = mutableState.value
        if (value == before.category) return
        saved["guide_category"] = value?.name
        request(before, category = value)
    }

    fun search(more: Boolean = false) {
        val before = mutableState.value
        if (!more && before.loading && searching?.isActive == true) return
        val cursor = if (more) {
            before.nextCursor?.takeUnless {
                before.loading || before.loadingMore || before.searchPending || before.showingPreviousResults
            } ?: return
        } else null
        request(before, cursor)
    }

    private fun request(
        before: GuideUiState,
        cursor: String? = null,
        query: String = before.query,
        category: WasteCategory? = before.category,
        debounce: Boolean = false,
    ) {
        started = true
        searching?.cancel()
        val more = cursor != null
        val filters = GuideSearchFilters(query.trim(), category)
        mutableState.value = before.copy(
            query = query, category = category,
            loading = !more && !debounce, loadingMore = more, searchPending = debounce,
            nextCursor = cursor, error = null,
        )
        searching = viewModelScope.launch {
            if (debounce) delay(300)
            if (retryAt?.isAfter(now()) == true) {
                mutableState.update { it.copy(loading = false, loadingMore = false, searchPending = false,
                    error = ApiFailureKind.RATE_LIMITED) }
                return@launch
            }
            if (debounce) mutableState.update { it.copy(loading = true, searchPending = false) }
            val response = store.search(filters.query, filters.category, cursor)
            currentCoroutineContext().ensureActive()
            if (response is ApiResult.Failure) rememberRetryAfter(response.error)
            mutableState.update { current -> when (response) {
                is ApiResult.Success -> {
                    val page = response.page
                    val next = page?.nextCursor
                    if (page == null || page.hasMore != (next != null) || (next != null && (next.isBlank() || next == cursor))) {
                        current.copy(loading = false, loadingMore = false, error = ApiFailureKind.MALFORMED_RESPONSE)
                    } else current.copy(
                        articles = (if (more) current.articles + response.value else response.value).distinctBy { it.id },
                        loading = false, loadingMore = false, nextCursor = next, resultsFor = filters,
                    )
                }
                is ApiResult.Failure -> {
                    current.copy(loading = false, loadingMore = false, error = response.error.kind)
                }
            } }
        }
    }

    fun detail(id: String, retry: Boolean = false) {
        if (!retry && mutableState.value.detail?.let { it.id == id && (it.loading || it.error == null) } == true) return
        reading?.cancel()
        if (retryAt?.isAfter(now()) == true) {
            mutableState.update { it.copy(detail = GuideDetailState(id, loading = false, error = ApiFailureKind.RATE_LIMITED)) }
            return
        }
        mutableState.update { it.copy(detail = GuideDetailState(id)) }
        reading = viewModelScope.launch {
            val response = store.get(id)
            currentCoroutineContext().ensureActive()
            if (response is ApiResult.Failure) rememberRetryAfter(response.error)
            mutableState.update { it.copy(detail = when (response) {
                is ApiResult.Success -> if (response.value.id == id) GuideDetailState(id, response.value, loading = false)
                    else GuideDetailState(id, loading = false, error = ApiFailureKind.MALFORMED_RESPONSE)
                is ApiResult.Failure -> {
                    GuideDetailState(id, loading = false, error = response.error.kind)
                }
            }) }
        }
    }

    private fun rememberRetryAfter(error: ApiFailure) {
        if (error.kind == ApiFailureKind.RATE_LIMITED && error.retryAt != null) {
            retryAt = maxOf(retryAt ?: Instant.MIN, error.retryAt)
        }
    }
}

private fun String.truncateQuery(): String = substring(0, offsetByCodePoints(0, codePointCount(0, length).coerceAtMost(100)))
