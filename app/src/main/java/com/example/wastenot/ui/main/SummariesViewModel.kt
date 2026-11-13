package com.example.wastenot.ui.main

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.repository.ActivityStore
import com.example.wastenot.model.ActivityDateRange
import com.example.wastenot.model.DashboardSnapshot
import com.example.wastenot.model.StatisticsSnapshot
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SnapshotState<T>(val value: T? = null, val loading: Boolean = true, val error: ApiFailureKind? = null)

data class SummariesUiState(
    val dashboard: SnapshotState<DashboardSnapshot> = SnapshotState(),
    val statistics: SnapshotState<StatisticsSnapshot> = SnapshotState(),
    val days: Int = 7,
)

class SummariesViewModel(
    private val store: ActivityStore,
    private val savedState: SavedStateHandle,
    private val today: () -> LocalDate,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SummariesUiState(days =
        savedState.get<Int>("statistics_days")?.takeIf { it == 7 || it == 30 } ?: 7))
    val state = mutableState.asStateFlow()
    private var dashboardJob: Job? = null
    private var statisticsJob: Job? = null
    private var dashboardStarted = false
    private var statisticsStarted = false
    private var dashboardGeneration = 0
    private var statisticsGeneration = 0
    private var lastStatistics: StatisticsSnapshot? = null

    init {
        viewModelScope.launch { store.changes.collect {
            if (dashboardStarted) { dashboardStarted = false; dashboard() }
            if (statisticsStarted) { statisticsStarted = false; statistics() }
        } }
    }

    fun dashboard(retry: Boolean = false) {
        if (dashboardStarted && !retry) return
        if (retry) store.requestSync()
        dashboardStarted = true
        val generation = ++dashboardGeneration
        dashboardJob?.cancel()
        mutableState.update { it.copy(dashboard = it.dashboard.copy(loading = true, error = null)) }
        dashboardJob = viewModelScope.launch {
            val result = store.dashboard()
            currentCoroutineContext().ensureActive()
            if (generation != dashboardGeneration) return@launch
            mutableState.update { it.copy(dashboard = when (result) {
                is ApiResult.Success -> SnapshotState(result.value, loading = false)
                is ApiResult.Failure -> it.dashboard.copy(loading = false, error = result.error.kind)
            }) }
        }
    }

    fun statistics(days: Int = mutableState.value.days, retry: Boolean = false) {
        require(days == 7 || days == 30)
        if (retry) store.requestSync()
        val range = ActivityDateRange.lastDays(today(), days)
        val current = mutableState.value
        if (statisticsStarted && days == current.days && !retry &&
            (statisticsJob?.isActive == true || current.statistics.value?.range == range)) return
        statisticsStarted = true
        val generation = ++statisticsGeneration
        statisticsJob?.cancel()
        savedState["statistics_days"] = days
        mutableState.update { it.copy(days = days, statistics = SnapshotState(
            value = it.statistics.value?.takeIf { value -> value.range == range }, loading = true)) }
        statisticsJob = viewModelScope.launch {
            val result = store.statistics(range)
            currentCoroutineContext().ensureActive()
            if (generation != statisticsGeneration) return@launch
            if (result is ApiResult.Success) lastStatistics = result.value
            mutableState.update { it.copy(statistics = when (result) {
                is ApiResult.Success -> SnapshotState(result.value, loading = false)
                is ApiResult.Failure -> SnapshotState(lastStatistics, loading = false, error = result.error.kind)
            }) }
        }
    }

    fun refresh() {
        dashboard(retry = true)
        if (statisticsStarted) statistics(retry = true)
    }

    override fun onCleared() {
        dashboardGeneration++
        statisticsGeneration++
        lastStatistics = null
        mutableState.value = SummariesUiState(
            dashboard = SnapshotState(loading = false), statistics = SnapshotState(loading = false))
    }
}
