package com.example.wastenot.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wastenot.LogEditor
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.repository.EditorDraftStore
import com.example.wastenot.data.repository.StoredEditorDraft
import java.time.DateTimeException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DraftFailure { READ, WRITE, FINISH }

data class EditorPersistenceState(
    val loading: Boolean = true,
    val ready: Boolean = false,
    val active: StoredEditorDraft? = null,
    val persisted: StoredEditorDraft? = null,
    val writing: Boolean = false,
    val busy: Boolean = false,
    val failure: DraftFailure? = null,
)

internal fun StoredEditorDraft.editorKey() = LogEditor(logId, requestId, guideId, scanCategory, scanMethod, scanRecommendationId)
internal fun StoredEditorDraft.restoreForm(): EditorDraft {
    require(fields.size in setOf(22, 28) && origin in MainTab.entries.map { it.name })
    return EditorDraft.fromSnapshot(fields).also {
        require((logId != null) == (it.original.version != null))
        require(!paused || it.outcomeUnknown)
    }
}
internal fun EditorDraft.stored(key: LogEditor, origin: MainTab) = StoredEditorDraft(
    key.requestId, origin.name, snapshot(), key.logId, key.guideId, key.scanCategory, key.scanMethod,
    scanRecommendationId = key.scanRecommendationId,
)

class EditorPersistenceViewModel(private val store: EditorDraftStore) : ViewModel() {
    private val mutableState = MutableStateFlow(EditorPersistenceState())
    val state = mutableState.asStateFlow()
    private var started = false
    private var writer: Job? = null
    private var action: Job? = null
    private val retired = mutableSetOf<String>()

    fun load(retry: Boolean = false) {
        if (action?.isActive == true || (started && !retry)) return
        started = true
        mutableState.update { it.copy(loading = true, failure = null) }
        action = viewModelScope.launch {
            val result = store.read()
            currentCoroutineContext().ensureActive()
            val cached = (result as? ApiResult.Success)?.value
            val valid = try { cached?.restoreForm(); true }
            catch (_: IllegalArgumentException) { false }
            catch (_: DateTimeException) { false }
            mutableState.value = if (result is ApiResult.Success && valid) EditorPersistenceState(
                loading = false, ready = true, active = cached, persisted = cached,
            ) else EditorPersistenceState(loading = false, failure = DraftFailure.READ)
        }
    }

    fun update(draft: StoredEditorDraft) {
        val current = mutableState.value
        if (!current.ready || current.loading || current.busy || draft.requestId in retired) return
        if (current.active != null && current.active.requestId != draft.requestId) return
        writer?.cancel()
        mutableState.update { it.copy(active = draft, writing = true) }
        writer = viewModelScope.launch {
            val result = store.write(draft)
            currentCoroutineContext().ensureActive()
            mutableState.update { it.copy(writing = false,
                persisted = if (result is ApiResult.Success) draft else it.persisted,
                failure = if (result is ApiResult.Success) null else DraftFailure.WRITE) }
        }
    }

    fun checkpoint(draft: StoredEditorDraft, onStored: () -> Unit, onFailure: () -> Unit) {
        commit(draft.requestId, onStored, onFailure) { store.write(draft) to draft }
    }

    fun leave(draft: StoredEditorDraft, keepForReview: Boolean = false, onLeft: () -> Unit) {
        require(!keepForReview || draft.restoreForm().outcomeUnknown)
        commit(draft.requestId, onLeft, {}, finishing = true) {
            if (keepForReview) {
                val paused = draft.copy(paused = true)
                store.write(paused) to paused
            } else store.clear(draft.requestId) to null
        }
    }

    fun clear(onCleared: () -> Unit) {
        commit(mutableState.value.active?.requestId, onCleared, {}, finishing = true) { store.clear() to null }
    }

    fun resume(): StoredEditorDraft? = mutableState.value.active?.also { retired.remove(it.requestId) }

    private fun commit(requestId: String?, onStored: () -> Unit, onFailure: () -> Unit, finishing: Boolean = false,
        write: suspend () -> Pair<ApiResult<Unit>, StoredEditorDraft?>) {
        if (action?.isActive == true || mutableState.value.loading || mutableState.value.busy) return
        if (!finishing && requestId in retired) return
        if (finishing && requestId != null) retired.add(requestId)
        mutableState.update { it.copy(busy = true, failure = null) }
        val previous = writer
        previous?.cancel()
        action = viewModelScope.launch {
            previous?.cancelAndJoin()
            val (result, confirmed) = write()
            currentCoroutineContext().ensureActive()
            if (result is ApiResult.Success) {
                mutableState.update { it.copy(loading = false, ready = true, active = confirmed, persisted = confirmed, writing = false, busy = false, failure = null) }
                onStored()
            } else {
                if (finishing && requestId != null) retired.remove(requestId)
                mutableState.update { it.copy(writing = false, busy = false, failure = if (finishing) DraftFailure.FINISH else DraftFailure.WRITE) }
                onFailure()
            }
        }
    }
}
