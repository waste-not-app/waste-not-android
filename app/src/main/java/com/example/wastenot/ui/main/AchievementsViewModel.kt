package com.example.wastenot.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.repository.AchievementStore
import com.example.wastenot.model.Achievement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AchievementsUiState(
    val items: List<Achievement>? = null,
    val loading: Boolean = false,
    val error: ApiFailureKind? = null,
)

class AchievementsViewModel(
    private val store: AchievementStore,
    synchronizationAcknowledgements: Flow<String>,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AchievementsUiState())
    val state = mutableState.asStateFlow()
    private var loadingJob: Job? = null
    private var generation = 0
    private var requested = false

    init {
        viewModelScope.launch {
            synchronizationAcknowledgements.distinctUntilChanged().collect {
                if (requested) load(force = true)
            }
        }
    }

    fun load(force: Boolean = false) {
        if (!force && (loadingJob?.isActive == true || mutableState.value.items != null)) return
        requested = true
        loadingJob?.cancel()
        val currentGen = ++generation
        loadingJob = viewModelScope.launch {
            mutableState.update { it.copy(loading = true, error = null) }
            try {
                val result = store.achievements()
                currentCoroutineContext().ensureActive()
                when (result) {
                    is ApiResult.Success -> if (generation == currentGen) {
                        mutableState.update { it.copy(items = result.value, loading = false, error = null) }
                    }
                    is ApiResult.Failure -> if (generation == currentGen) {
                        mutableState.update { it.copy(loading = false, error = result.error.kind) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    fun refresh() = load(force = true)

    override fun onCleared() {
        generation++
        loadingJob?.cancel()
    }
}
