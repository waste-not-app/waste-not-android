package com.example.wastenot.ui.main

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.repository.GuideStore
import com.example.wastenot.model.WasteCategory
import com.example.wastenot.model.WasteGuide
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GuideDetailState(val id: String, val article: WasteGuide? = null, val loading: Boolean = true, val error: ApiFailureKind? = null)

data class GuideUiState(
    val query: String = "",
    val category: WasteCategory? = null,
    val articles: List<WasteGuide> = emptyList(),
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val nextCursor: String? = null,
    val error: ApiFailureKind? = null,
    val detail: GuideDetailState? = null,
)

class GuideViewModel(private val store: GuideStore, private val saved: SavedStateHandle) : ViewModel() {
    private val mutableState = MutableStateFlow(GuideUiState(
        query = saved.get<String>("guide_query").orEmpty().take(100),
        category = WasteCategory.entries.find { it.name == saved.get<String>("guide_category") },
    ))
    val state = mutableState.asStateFlow()
    private var searching: Job? = null
    private var reading: Job? = null
    private var started = false

    fun load() { if (!started) { started = true; search() } }

    fun query(value: String) {
        val query = value.take(100)
        if (query == mutableState.value.query) return
        saved["guide_query"] = query
        mutableState.update { it.copy(query = query, articles = emptyList()) }
        search(debounce = true)
    }

    fun category(value: WasteCategory?) {
        if (value == mutableState.value.category) return
        saved["guide_category"] = value?.name
        mutableState.update { it.copy(category = value, articles = emptyList()) }
        search()
    }

    fun search(more: Boolean = false, debounce: Boolean = false) {
        val before = mutableState.value
        val cursor = if (more) before.nextCursor?.takeUnless { before.loading || before.loadingMore } ?: return else null
        searching?.cancel()
        val query = before.query.trim()
        val category = before.category
        mutableState.update { it.copy(
            loading = !more, loadingMore = more,
            nextCursor = if (more) it.nextCursor else null, error = null,
        ) }
        searching = viewModelScope.launch {
            if (debounce) delay(300)
            val response = store.search(query, category, cursor)
            currentCoroutineContext().ensureActive()
            mutableState.update { current -> when (response) {
                is ApiResult.Success -> {
                    val page = response.page
                    val next = page?.nextCursor
                    if (page == null || page.hasMore != (next != null) || (next != null && (next.isBlank() || next == cursor))) {
                        current.copy(loading = false, loadingMore = false, error = ApiFailureKind.MALFORMED_RESPONSE)
                    } else current.copy(
                        articles = (if (more) current.articles + response.value else response.value).distinctBy { it.id },
                        loading = false, loadingMore = false, nextCursor = next,
                    )
                }
                is ApiResult.Failure -> current.copy(loading = false, loadingMore = false, error = response.error.kind)
            } }
        }
    }

    fun detail(id: String, retry: Boolean = false) {
        if (!retry && mutableState.value.detail?.let { it.id == id && (it.loading || it.error == null) } == true) return
        reading?.cancel()
        mutableState.update { it.copy(detail = GuideDetailState(id)) }
        reading = viewModelScope.launch {
            val response = store.get(id)
            currentCoroutineContext().ensureActive()
            mutableState.update { it.copy(detail = when (response) {
                is ApiResult.Success -> if (response.value.id == id) GuideDetailState(id, response.value, loading = false)
                    else GuideDetailState(id, loading = false, error = ApiFailureKind.MALFORMED_RESPONSE)
                is ApiResult.Failure -> GuideDetailState(id, loading = false, error = response.error.kind)
            }) }
        }
    }
}
