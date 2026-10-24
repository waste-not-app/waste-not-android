package com.example.wastenot.ui.main

import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.wastenot.LogEditor
import com.example.wastenot.data.network.*
import com.example.wastenot.data.repository.*
import com.example.wastenot.model.*
import java.time.LocalDate
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditorPersistenceViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val owners = mutableListOf<ViewModelStoreOwner>()
    private val key = LogEditor(null, "949aebd2-53d4-4e04-a66c-dcf50b161d80")
    private fun form() = EditorDraft.from(null, LocalDate.of(2026, 10, 24)).apply {
        change(EditorField.NOTES, values.copy(notes = "Isian pengguna"))
    }
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { owners.forEach { it.viewModelStore.clear() }; Dispatchers.resetMain() }
    private fun model(store: EditorDraftStore): EditorPersistenceViewModel {
        val owner = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
        owners.add(owner)
        return ViewModelProvider.create(owner, viewModelFactory { initializer { EditorPersistenceViewModel(store) } })[EditorPersistenceViewModel::class]
    }

    @Test fun failedOrCorruptReadsBlockOverwritingAndCanBeExplicitlyCleared() = runTest {
        val corrupt = form().stored(key, MainTab.HOME).copy(fields = listOf("broken"))
        val disk = Disk(corrupt); val model = model(disk)
        model.load(); advanceUntilIdle()
        assertEquals(DraftFailure.READ, model.state.value.failure)
        model.update(form().stored(key, MainTab.HOME)); advanceUntilIdle()
        assertEquals(corrupt, disk.saved)
        var cleared = false
        model.clear { cleared = true }; advanceUntilIdle()
        assertTrue(cleared); assertNull(disk.saved); assertNull(model.state.value.failure)
    }

    @Test fun aSubmissionWaitsForADurableCheckpointAndFailureNeverCallsTheWrite() = runTest {
        val disk = Disk(); val model = model(disk)
        model.load(); advanceUntilIdle()
        val record = form().stored(key, MainTab.HOME)
        disk.gate = CompletableDeferred()
        var writes = 0; var failures = 0
        model.checkpoint(record, { writes++ }, { failures++ }); runCurrent()
        assertTrue(model.state.value.busy); assertEquals(0, writes)
        model.checkpoint(record, { writes++ }, { failures++ }); runCurrent()
        assertEquals(1, disk.writes)
        disk.gate!!.complete(Unit); advanceUntilIdle()
        assertEquals(1, writes); assertEquals(record, disk.saved)
        disk.failWrite = true
        model.checkpoint(record, { writes++ }, { failures++ }); advanceUntilIdle()
        assertEquals(1, writes); assertEquals(1, failures)
        assertEquals(DraftFailure.WRITE, model.state.value.failure)
        assertFalse(model.state.value.busy)
    }

    @Test fun leavingWaitsForAnAlreadyRunningCommitThenPreventsLateResurrection() = runTest {
        val disk = Disk(); val model = model(disk)
        model.load(); advanceUntilIdle()
        val record = form().stored(key, MainTab.HOME)
        disk.gate = CompletableDeferred()
        model.update(record); runCurrent()
        var left = 0
        model.leave(record) { left++ }; runCurrent()
        assertEquals(0, left)
        disk.gate!!.complete(Unit); advanceUntilIdle()
        assertEquals(1, left); assertNull(disk.saved)
        val writes = disk.writes
        model.update(record); advanceUntilIdle()
        assertEquals(writes, disk.writes); assertNull(disk.saved)
    }

    @Test fun aFailedClearKeepsTheFormAndAllowsAnExplicitRetry() = runTest {
        val record = form().stored(key, MainTab.HOME)
        val disk = Disk(record); val model = model(disk)
        model.load(); advanceUntilIdle()
        disk.failClear = true
        var left = 0
        model.leave(record) { left++ }; advanceUntilIdle()
        assertEquals(0, left); assertEquals(record, disk.saved)
        assertEquals(DraftFailure.FINISH, model.state.value.failure)
        disk.failClear = false
        model.leave(record) { left++ }; advanceUntilIdle()
        assertEquals(1, left); assertNull(disk.saved)
    }

    @Test fun pausingKeepsTheExactDraftUntilTheUserResumesIt() = runTest {
        val pending = form().apply {
            change(EditorField.QUANTITY, values.copy(category = WasteCategory.PLASTIC, method = DisposalMethod.RECYCLE,
                quantity = QuantityInput("250")))
            prepareSubmission(values.date)
            submissionFailed(ApiFailureKind.NETWORK)
        }
        val record = pending.stored(key, MainTab.GUIDE)
        val disk = Disk(record); val model = model(disk)
        model.load(); advanceUntilIdle()
        var left = false
        model.leave(record, keepForReview = true) { left = true }; advanceUntilIdle()
        assertTrue(left); assertEquals(record.copy(paused = true), disk.saved)
        model.update(record); advanceUntilIdle()
        assertTrue(disk.saved!!.paused)
        assertEquals(disk.saved, model.resume())
        model.update(record); advanceUntilIdle()
        assertEquals(record, disk.saved)
    }

    private class Disk(var saved: StoredEditorDraft? = null) : EditorDraftStore {
        var failWrite = false
        var failClear = false
        var writes = 0
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun read() = ApiResult.Success(saved)
        override suspend fun write(draft: StoredEditorDraft): ApiResult<Unit> {
            writes++
            if (failWrite) return ApiResult.Failure(ApiFailure(ApiFailureKind.STORAGE))
            withContext(NonCancellable) { gate?.await(); saved = draft }
            return ApiResult.Success(Unit)
        }
        override suspend fun clear(requestId: String?): ApiResult<Unit> {
            if (failClear) return ApiResult.Failure(ApiFailure(ApiFailureKind.STORAGE))
            if (requestId == null || saved?.requestId == requestId) saved = null
            return ApiResult.Success(Unit)
        }
    }
}
