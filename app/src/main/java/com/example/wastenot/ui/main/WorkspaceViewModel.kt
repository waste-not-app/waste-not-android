package com.example.wastenot.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.repository.ActivityInput
import com.example.wastenot.data.repository.ActivityStore
import com.example.wastenot.model.WasteLog
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WorkspaceUiState(
    val records: List<WasteLog> = emptyList(),
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: ApiFailureKind? = null,
    val save: ActivitySaveResult? = null,
)

data class ActivitySaveResult(val requestId: String, val id: String? = null, val error: ApiFailureKind? = null)

class WorkspaceViewModel(private val store: ActivityStore) : ViewModel() {
    private val mutableState = MutableStateFlow(WorkspaceUiState())
    val state = mutableState.asStateFlow()
    val referenceDate: LocalDate? get() = store.referenceDate
    private var loading: Job? = null
    private var writing: Job? = null
    private var started = false

    fun load(retry: Boolean = false) {
        if (loading?.isActive == true || (started && !retry)) return
        started = true
        mutableState.update { it.copy(loading = true, error = null) }
        loading = viewModelScope.launch {
            val result = store.load()
            currentCoroutineContext().ensureActive()
            mutableState.update { current -> when (result) {
                is ApiResult.Success -> current.copy(records = result.value, loading = false, error = null)
                is ApiResult.Failure -> current.copy(loading = false, error = result.error.kind)
            } }
        }
    }

    fun save(input: ActivityInput, id: String?, version: Int?, requestId: String) {
        if (writing?.isActive == true) return
        mutableState.update { it.copy(saving = true, save = null) }
        writing = viewModelScope.launch {
            try {
                val result = if (id == null) store.create(input, requestId) else store.update(id, checkNotNull(version), input)
                currentCoroutineContext().ensureActive()
                mutableState.update { current -> when (result) {
                    is ApiResult.Success -> current.copy(
                        records = (current.records.filterNot { it.id == result.value.id } + result.value)
                            .sortedWith(compareByDescending<WasteLog> { it.activityDate }.thenByDescending { it.createdAt }),
                        save = ActivitySaveResult(requestId, id = result.value.id),
                    )
                    is ApiResult.Failure -> current.copy(save = ActivitySaveResult(requestId, error = result.error.kind))
                } }
            } finally {
                mutableState.update { it.copy(saving = false) }
            }
        }
    }

    fun acknowledgeSave(requestId: String) {
        mutableState.update { if (it.save?.requestId == requestId) it.copy(save = null) else it }
    }

    override fun onCleared() {
        store.clear()
        mutableState.value = WorkspaceUiState(loading = false)
    }
}
