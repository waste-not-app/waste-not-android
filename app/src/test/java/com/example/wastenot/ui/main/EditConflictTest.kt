package com.example.wastenot.ui.main

import androidx.compose.runtime.saveable.SaverScope
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.wastenot.data.demo.DemoActivityStore
import com.example.wastenot.data.network.*
import com.example.wastenot.data.repository.*
import com.example.wastenot.model.*
import java.time.ZoneId
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditConflictTest {
    private val dispatcher = StandardTestDispatcher()
    private val owners = mutableListOf<ViewModelStoreOwner>()
    private val source = DemoActivityStore(ZoneId.of("Asia/Jakarta"))
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { owners.forEach { it.viewModelStore.clear() }; Dispatchers.resetMain() }
    private fun model(store: ActivityStore = source): WorkspaceViewModel {
        val owner = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
        owners.add(owner)
        return ViewModelProvider.create(owner, viewModelFactory { initializer { WorkspaceViewModel(store) } })[WorkspaceViewModel::class]
    }
    private suspend fun initial() = (source.load() as ApiResult.Success).value.first()
    private fun draft(log: WasteLog) = EditorDraft.from(log, source.referenceDate).apply {
        change(EditorField.NOTES, values.copy(notes = "Draf pengguna"))
        assertNotNull(prepareSubmission(source.referenceDate))
        submissionFailed(ApiFailureKind.VERSION_CONFLICT)
    }

    @Test fun changingFieldsAndRestoringStateCannotBypassTheConflict() = runTest {
        val original = initial(); val form = draft(original)
        assertFalse(form.locked); assertTrue(form.needsConflictResolution)
        form.change(EditorField.NOTES, form.values.copy(notes = "Koreksi lagi"))
        assertNull(form.prepareSubmission(source.referenceDate))
        val scope = SaverScope { true }
        val saved = with(EditorDraft.Saver) { scope.save(form) }!!
        val restored = EditorDraft.Saver.restore(saved)!!
        assertTrue(restored.needsConflictResolution); assertEquals("Koreksi lagi", restored.values.notes)
        assertEquals(original.version, restored.values.version)
        assertNull(restored.prepareSubmission(source.referenceDate))
    }

    @Test fun choosingDraftKeepsAllUserFieldsAndOnlyAdoptsTheNewBaselineVersion() = runTest {
        val original = initial(); val form = draft(original); val mine = form.values
        val latest = original.copy(version = 2, category = WasteCategory.ELECTRONIC, method = DisposalMethod.TRASH,
            quantity = Quantity.Count.of(2), notes = "Versi perangkat lain")
        assertTrue(form.resolveConflict(latest, keepDraft = true))
        assertEquals(mine.copy(version = 2), form.values)
        assertEquals(EditorDraft.from(latest, source.referenceDate).values, form.original)
        assertTrue(form.dirty); assertFalse(form.needsConflictResolution)
        assertNull(form.submittedValues)
        assertEquals("Draf pengguna", form.prepareSubmission(source.referenceDate)!!.notes)
    }

    @Test fun choosingLatestReplacesEveryFieldAndDoesNotPrepareASubmission() = runTest {
        val original = initial(); val form = draft(original)
        val latest = original.copy(version = 3, category = WasteCategory.GLASS, method = DisposalMethod.TRASH,
            quantity = Quantity.Count.of(3), notes = "Isian terbaru")
        assertTrue(form.resolveConflict(latest, keepDraft = false))
        assertEquals(EditorDraft.from(latest, source.referenceDate).values, form.values)
        assertFalse(form.dirty); assertNull(form.submittedValues)
        assertFalse(form.resolveConflict(latest.copy(version = 4), true))
    }

    @Test fun staleVersionCannotUnlockTheDraftAndUnknownOutcomeCannotBeRebased() = runTest {
        val original = initial(); val form = draft(original); val mine = form.values
        assertFalse(form.resolveConflict(original, true)); assertEquals(mine, form.values)
        val unknown = EditorDraft.from(original, source.referenceDate)
        unknown.prepareSubmission(source.referenceDate); unknown.submissionFailed(ApiFailureKind.TIMEOUT)
        assertFalse(unknown.resolveConflict(original.copy(version = 2), true)); assertTrue(unknown.locked)
    }

    @Test fun readsValidateIdentityAndVersionAndNeverWrite() = runTest {
        val log = initial(); var response = log; var writes = 0
        val vm = model(object : ActivityStore by source {
            override suspend fun get(id: String) = ApiResult.Success(response)
            override suspend fun update(id: String, version: Int, input: ActivityInput): ApiResult<WasteLog> { writes++; return source.update(id, version, input) }
        })
        vm.loadConflict("editor", log.id, log.version); advanceUntilIdle()
        assertEquals(ApiFailureKind.PRECONDITION_REQUIRED, vm.state.value.conflict?.error)
        assertNull(vm.state.value.conflict?.latest)
        response = log.copy(id = "different", version = 2)
        vm.loadConflict("editor", log.id, log.version); advanceUntilIdle()
        assertEquals(ApiFailureKind.MALFORMED_RESPONSE, vm.state.value.conflict?.error)
        response = log.copy(version = 2)
        vm.loadConflict("editor", log.id, log.version); advanceUntilIdle()
        assertEquals(response, vm.state.value.conflict?.latest); assertEquals(0, writes)
    }

    @Test fun conflictReadAcceptsTheStoresVerifiedLocalAlias() = runTest {
        val log = initial().copy(version = 2)
        val alias = java.util.UUID.randomUUID().toString()
        val vm = model(object : ActivityStore by source {
            override suspend fun get(id: String) = ApiResult.Success(log)
            override suspend fun isSameActivity(requestedId: String, actualId: String) =
                requestedId == alias && actualId == log.id
        })
        vm.loadConflict("editor", alias, 1); advanceUntilIdle()
        assertEquals(log, vm.state.value.conflict?.latest)
        assertNull(vm.state.value.conflict?.error)
    }

    @Test fun cancelledReadCannotReplaceANewerEntryEvenWhenStoreIgnoresCancellation() = runTest {
        val log = initial(); val old = CompletableDeferred<ApiResult<WasteLog>>(); var calls = 0
        val vm = model(object : ActivityStore by source {
            override suspend fun get(id: String) = if (++calls == 1) withContext(NonCancellable) { old.await() }
                else ApiResult.Success(log.copy(version = 3))
        })
        vm.loadConflict("old", log.id, 1); runCurrent(); vm.clearConflict("old")
        vm.loadConflict("new", log.id, 1); runCurrent()
        vm.clearConflict("old")
        old.complete(ApiResult.Success(log.copy(version = 2))); advanceUntilIdle()
        assertEquals("new", vm.state.value.conflict?.requestId)
        assertEquals(3, vm.state.value.conflict?.latest?.version)
    }

    @Test fun failedOrMissingLatestKeepsResolutionPendingUntilExplicitRetry() = runTest {
        val log = initial(); var calls = 0; var failure = ApiFailureKind.NETWORK
        val vm = model(object : ActivityStore by source {
            override suspend fun get(id: String): ApiResult<WasteLog> { calls++; return ApiResult.Failure(ApiFailure(failure)) }
        })
        vm.loadConflict("editor", log.id, 1); advanceUntilIdle()
        assertEquals(1, calls); assertEquals(ApiFailureKind.NETWORK, vm.state.value.conflict?.error)
        failure = ApiFailureKind.NOT_FOUND; vm.loadConflict("editor", log.id, 1); advanceUntilIdle()
        assertEquals(2, calls); assertNull(vm.state.value.conflict?.latest)
        assertEquals(ApiFailureKind.NOT_FOUND, vm.state.value.conflict?.error)
    }

    @Test fun concurrentEditRequiresExplicitSelectionAndAnotherSaveWithoutAddingARecord() = runTest {
        val vm = model(); vm.load(); advanceUntilIdle(); val log = vm.state.value.records.first()
        val form = EditorDraft.from(log, source.referenceDate)
        form.change(EditorField.NOTES, form.values.copy(notes = "Draf saya"))
        val other = ActivityInput(WasteCategory.GLASS, DisposalMethod.TRASH, Quantity.Count.of(2), log.activityDate, "Perangkat lain")
        source.update(log.id, 1, other)
        vm.save(form.prepareSubmission(source.referenceDate)!!, log.id, 1, "editor"); advanceUntilIdle()
        form.submissionFailed(vm.state.value.save!!.error!!)
        assertTrue(form.needsConflictResolution)
        vm.loadConflict("editor", log.id, 1); advanceUntilIdle()
        val latest = vm.state.value.conflict!!.latest!!
        assertEquals(2, latest.version); assertEquals("Perangkat lain", latest.notes)
        assertEquals(2, (source.get(log.id) as ApiResult.Success).value.version)
        assertTrue(form.resolveConflict(latest, true))
        assertEquals(2, (source.get(log.id) as ApiResult.Success).value.version)
        vm.save(form.prepareSubmission(source.referenceDate)!!, log.id, form.values.version, "editor"); advanceUntilIdle()
        val updated = (source.get(log.id) as ApiResult.Success).value
        assertEquals(3, updated.version); assertEquals("Draf saya", updated.notes)
        assertEquals(log.quantity, updated.quantity); assertEquals(5, vm.state.value.records.size)
        assertFalse(vm.state.value.save!!.created)
    }
}
