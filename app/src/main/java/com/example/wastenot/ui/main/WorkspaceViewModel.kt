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
import kotlinx.coroutines.CancellationException
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
    val deletingId: String? = null,
    val deletion: ActivityDeleteResult? = null,
    val detail: ActivityDetailState? = null,
    val conflict: ConflictReadState? = null,
    val mutationRevision: Int = 0,
    val confirmedChange: ConfirmedActivityChange? = null,
)

data class ConflictReadState(val requestId: String, val id: String, val loading: Boolean = false,
    val latest: WasteLog? = null, val error: ApiFailureKind? = null)

data class ActivityDetailState(val id: String, val log: WasteLog? = null, val loading: Boolean = false, val error: ApiFailureKind? = null)

data class ActivityDeleteResult(val id: String, val deleted: Boolean = false, val error: ApiFailureKind? = null)

data class ActivitySaveResult(val requestId: String, val id: String? = null, val error: ApiFailureKind? = null, val created: Boolean = false)

internal fun ApiFailureKind.saveOutcomeUnknown() = when (this) {
    ApiFailureKind.TIMEOUT, ApiFailureKind.NETWORK, ApiFailureKind.SERVER,
    ApiFailureKind.HTTP, ApiFailureKind.MALFORMED_RESPONSE -> true
    else -> false
}

class WorkspaceViewModel(private val store: ActivityStore) : ViewModel() {
    private val mutableState = MutableStateFlow(WorkspaceUiState())
    val state = mutableState.asStateFlow()
    val referenceDate: LocalDate? get() = store.referenceDate
    private var loading: Job? = null
    private var writing: Job? = null
    private var detailLoading: Job? = null
    private var conflictLoading: Job? = null
    private var conflictGeneration = 0
    private var started = false
    private data class PendingCreate(val input: ActivityInput, val outcomeUnknown: Boolean)
    private val unresolvedCreates = mutableMapOf<String, PendingCreate>()

    init {
        viewModelScope.launch { store.changes.collect {
            if (started) load(retry = true)
            state.value.detail?.id?.let(::loadDetail)
        } }
    }

    fun loadConflict(requestId: String, id: String, version: Int) {
        if (mutableState.value.conflict?.let { it.requestId == requestId && it.loading } == true) return
        conflictGeneration++
        val generation = conflictGeneration
        conflictLoading?.cancel()
        mutableState.update { it.copy(conflict = ConflictReadState(requestId, id, loading = true)) }
        conflictLoading = viewModelScope.launch {
            val result = store.get(id)
            currentCoroutineContext().ensureActive()
            if (generation != conflictGeneration) return@launch
            val latest = (result as? ApiResult.Success)?.value
            val failure = when {
                latest == null -> (result as? ApiResult.Failure)?.error?.kind
                latest.id != id -> ApiFailureKind.MALFORMED_RESPONSE
                latest.version <= version -> ApiFailureKind.PRECONDITION_REQUIRED
                else -> null
            }
            mutableState.update { it.copy(conflict = ConflictReadState(requestId, id,
                latest = latest?.takeIf { failure == null }, error = failure)) }
            if (latest != null && failure == null) {
                loading?.cancel(); loading = null
                load(true)
            }
        }
    }

    fun clearConflict(requestId: String) {
        if (mutableState.value.conflict?.requestId != requestId) return
        conflictGeneration++; conflictLoading?.cancel(); conflictLoading = null
        mutableState.update { it.copy(conflict = null) }
    }

    fun load(retry: Boolean = false) {
        if (writing?.isActive == true || loading?.isActive == true || (started && !retry)) return
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

    fun loadDetail(id: String) {
        if (detailLoading?.isActive == true && mutableState.value.detail?.id == id) return
        detailLoading?.cancel()
        val cached = mutableState.value.detail?.takeIf { it.id == id }?.log ?: mutableState.value.records.find { it.id == id }
        mutableState.update { it.copy(detail = ActivityDetailState(id, cached, loading = true)) }
        detailLoading = viewModelScope.launch {
            val result = store.get(id)
            currentCoroutineContext().ensureActive()
            when (result) {
                is ApiResult.Success -> {
                    mutableState.update { it.copy(detail = ActivityDetailState(id, result.value)) }
                    if (cached != result.value) {
                        loading?.cancel()
                        loading = null
                        load(true)
                    }
                }
                is ApiResult.Failure -> {
                    val missing = result.error.kind == ApiFailureKind.NOT_FOUND || result.error.kind == ApiFailureKind.DELETED
                    mutableState.update { it.copy(
                        detail = ActivityDetailState(id, if (missing) null else cached, error = result.error.kind),
                        records = if (missing) it.records.filterNot { record -> record.id == id } else it.records,
                    ) }
                    if (missing && cached != null) {
                        loading?.cancel()
                        loading = null
                        load(true)
                    }
                }
            }
        }
    }

    fun save(input: ActivityInput, id: String?, version: Int?, requestId: String, previousOutcomeUnknown: Boolean = false) {
        if (writing?.isActive == true) return
        if (id == null) {
            val pending = unresolvedCreates[requestId]
            if (pending != null && pending.input != input) {
                mutableState.update { it.copy(save = ActivitySaveResult(requestId, error = ApiFailureKind.IDEMPOTENCY_CONFLICT)) }
                return
            }
            unresolvedCreates[requestId] = PendingCreate(input, pending?.outcomeUnknown == true || previousOutcomeUnknown)
        }
        mutableState.update { it.copy(saving = true, save = null) }
        writing = viewModelScope.launch {
            try {
                var created = false
                val result = if (id == null) when (val receipt = store.create(input, requestId)) {
                    is ApiResult.Success -> {
                        created = receipt.value.created
                        ApiResult.Success(receipt.value.log)
                    }
                    is ApiResult.Failure -> receipt
                } else store.update(id, checkNotNull(version), input)
                currentCoroutineContext().ensureActive()
                when (result) {
                    is ApiResult.Success -> {
                        if (id == null) unresolvedCreates.remove(requestId)
                        refreshAfterMutation(log = result.value) { records -> records.filterNot { it.id == result.value.id } + result.value }
                        mutableState.update { it.copy(save = ActivitySaveResult(requestId, id = result.value.id, created = created)) }
                    }
                    is ApiResult.Failure -> {
                        if (id == null) unresolvedCreates[requestId]?.let { pending ->
                            val unknown = pending.outcomeUnknown || result.error.kind.saveOutcomeUnknown()
                            if (unknown) unresolvedCreates[requestId] = pending.copy(outcomeUnknown = true)
                            else unresolvedCreates.remove(requestId)
                        }
                        mutableState.update { it.copy(save = ActivitySaveResult(requestId, error = result.error.kind)) }
                    }
                }
            } catch (error: CancellationException) {
                if (id == null) unresolvedCreates[requestId]?.let {
                    unresolvedCreates[requestId] = it.copy(outcomeUnknown = true)
                }
                throw error
            } finally {
                mutableState.update { it.copy(saving = false) }
            }
        }
    }

    fun delete(id: String, version: Int) {
        if (writing?.isActive == true) return
        mutableState.update { it.copy(deletingId = id, deletion = null) }
        writing = viewModelScope.launch {
            try {
                val result = store.delete(id, version)
                currentCoroutineContext().ensureActive()
                when (result) {
                    is ApiResult.Success -> {
                        refreshAfterMutation(deletedId = id) { records -> records.filterNot { it.id == id } }
                        mutableState.update { it.copy(deletion = ActivityDeleteResult(id, deleted = true),
                            detail = ActivityDetailState(id, error = ApiFailureKind.NOT_FOUND)) }
                    }
                    is ApiResult.Failure -> mutableState.update { it.copy(deletion = ActivityDeleteResult(id, error = result.error.kind)) }
                }
            } finally {
                mutableState.update { it.copy(deletingId = null) }
            }
        }
    }

    private suspend fun refreshAfterMutation(log: WasteLog? = null, deletedId: String? = null, fallback: (List<WasteLog>) -> List<WasteLog>) {
        loading?.cancel()
        loading = null
        detailLoading?.cancel()
        detailLoading = null
        started = true
        val refreshed = store.load()
        currentCoroutineContext().ensureActive()
        val records = ((refreshed as? ApiResult.Success)?.value ?: fallback(mutableState.value.records))
            .sortedWith(compareByDescending<WasteLog> { it.activityDate }.thenByDescending { it.createdAt })
        mutableState.update { it.copy(records = records, loading = false, error = (refreshed as? ApiResult.Failure)?.error?.kind,
            detail = null, mutationRevision = it.mutationRevision + 1,
            confirmedChange = ConfirmedActivityChange(it.mutationRevision + 1, log, deletedId)) }
    }

    fun acknowledgeDelete(id: String) {
        mutableState.update { if (it.deletion?.id == id) it.copy(deletion = null) else it }
    }

    fun acknowledgeSave(requestId: String) {
        mutableState.update { if (it.save?.requestId == requestId) it.copy(save = null) else it }
    }

    override fun onCleared() {
        unresolvedCreates.clear()
        store.clear()
        mutableState.value = WorkspaceUiState(loading = false)
    }
}
