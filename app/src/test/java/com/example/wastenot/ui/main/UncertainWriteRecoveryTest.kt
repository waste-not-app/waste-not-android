package com.example.wastenot.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.wastenot.LogEditor
import com.example.wastenot.data.demo.DemoActivityStore
import com.example.wastenot.data.local.DraftDiskCodec
import com.example.wastenot.data.network.ApiFailure
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.repository.ActivityCreation
import com.example.wastenot.data.repository.ActivityInput
import com.example.wastenot.data.repository.ActivityStore
import com.example.wastenot.data.repository.EditorDraftStore
import com.example.wastenot.data.repository.StoredEditorDraft
import com.example.wastenot.model.DisposalMethod
import com.example.wastenot.model.QuantityInput
import com.example.wastenot.model.QuantityUnit
import com.example.wastenot.model.Quantity
import com.example.wastenot.model.WasteCategory
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UncertainWriteRecoveryTest {
    private val date = LocalDate.of(2026, 10, 24)
    private val key = LogEditor(null, "7b20f6b4-278c-492c-bddf-8ef2daaf28e5")
    private val owners = mutableListOf<ViewModelStoreOwner>()

    @Before fun before() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun after() { owners.forEach { it.viewModelStore.clear() }; Dispatchers.resetMain() }

    private inline fun <reified T : ViewModel> model(crossinline factory: () -> T): T {
        val owner = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
        owners.add(owner)
        return ViewModelProvider.create(owner, viewModelFactory { initializer { factory() } })[T::class]
    }

    private fun form() = EditorDraft.from(null, date).apply {
        change(EditorField.QUANTITY, values.copy(category = WasteCategory.PLASTIC, method = DisposalMethod.RECYCLE,
            quantity = QuantityInput("0,250", QuantityUnit.KILOGRAM), notes = "  Original entry  "))
    }

    @Test fun aLaterRejectionDoesNotResolveTheFirstUnknownCreate() {
        listOf(ApiFailureKind.RATE_LIMITED, ApiFailureKind.VALIDATION, ApiFailureKind.INVALID_REQUEST,
            ApiFailureKind.UNAUTHENTICATED, ApiFailureKind.NOT_CONFIGURED, ApiFailureKind.STORAGE).forEach { error ->
            val draft = form()
            val original = draft.prepareSubmission(date)
            val raw = draft.values
            draft.submissionFailed(ApiFailureKind.NETWORK)
            draft.submissionFailed(error)
            assertTrue("The first write is still unknown after $error", draft.locked)
            assertTrue(draft.outcomeUnknown)
            assertEquals(error, draft.submissionError)
            draft.change(EditorField.NOTES, raw.copy(notes = "Do not adopt this edit"))
            assertEquals(raw, draft.values)
            assertEquals(original, draft.prepareSubmission(date.plusDays(60)))
            draft.submissionFailed(ApiFailureKind.DELETED)
            assertTrue(draft.retryBlocked)
            assertFalse(draft.outcomeUnknown)
            assertNull(draft.prepareSubmission(date))
        }
    }

    @Test fun aFollowupErrorCanBePausedAndRestoredWithItsOriginalUuidAndPayload() = runTest {
        val form = form()
        val input = form.prepareSubmission(date)
        form.submissionFailed(ApiFailureKind.TIMEOUT)
        form.submissionFailed(ApiFailureKind.RATE_LIMITED)
        val record = form.stored(key, MainTab.GUIDE)
        val disk = Disk(record)
        val persistence = model { EditorPersistenceViewModel(disk) }
        persistence.load(); advanceUntilIdle()
        var left = false
        persistence.leave(record, keepForReview = true) { left = true }
        advanceUntilIdle()
        assertTrue(left)
        val saved = requireNotNull(disk.read().value)
        assertTrue(saved.paused)
        assertEquals(key, saved.editorKey())
        val restored = saved.restoreForm()
        assertTrue(restored.locked)
        assertEquals(ApiFailureKind.RATE_LIMITED, restored.submissionError)
        assertEquals(input, restored.prepareSubmission(date.plusDays(60)))
        val restarted = model { EditorPersistenceViewModel(disk) }
        restarted.load(); advanceUntilIdle()
        assertTrue(restarted.state.value.ready)
        assertEquals(saved, restarted.resume())
    }

    @Test fun aRateLimitedReplayCannotReleaseTheViewModelsPayloadGuard() = runTest {
        val source = DemoActivityStore(ZoneId.of("Asia/Jakarta"))
        val calls = mutableListOf<Pair<String, ActivityInput>>()
        val gateway = object : ActivityStore by source {
            override suspend fun create(input: ActivityInput, clientRequestId: String): ApiResult<ActivityCreation> {
                calls.add(clientRequestId to input)
                if (calls.size == 2) return ApiResult.Failure(ApiFailure(ApiFailureKind.RATE_LIMITED))
                val result = source.create(input, clientRequestId)
                return if (calls.size == 1) ApiResult.Failure(ApiFailure(ApiFailureKind.NETWORK)) else result
            }
        }
        val vm = model { WorkspaceViewModel(gateway) }
        val input = ActivityInput(WasteCategory.PLASTIC, DisposalMethod.RECYCLE,
            Quantity.Weight.of(250), source.referenceDate, "Original entry")
        vm.save(input, null, null, key.requestId); advanceUntilIdle()
        vm.save(input, null, null, key.requestId); advanceUntilIdle()
        vm.save(input.copy(notes = "Changed"), null, null, key.requestId); advanceUntilIdle()
        assertEquals("Changed payload must not reach the backend", 2, calls.size)
        assertEquals(ApiFailureKind.IDEMPOTENCY_CONFLICT, vm.state.value.save!!.error)
        vm.save(input, null, null, key.requestId); advanceUntilIdle()
        assertEquals(listOf(key.requestId to input, key.requestId to input, key.requestId to input), calls)
        assertNotNull(vm.state.value.save!!.id)
        assertFalse(vm.state.value.save!!.created)
        assertEquals(6, source.load().value.size)
    }

    @Test fun aRestoredPendingCreateSeedsTheGuardBeforeItsFirstNewAttempt() = runTest {
        val source = DemoActivityStore(ZoneId.of("Asia/Jakarta"))
        val input = ActivityInput(WasteCategory.PLASTIC, DisposalMethod.RECYCLE,
            Quantity.Weight.of(250), source.referenceDate, "Original entry")
        assertTrue(source.create(input, key.requestId) is ApiResult.Success)
        val calls = mutableListOf<ActivityInput>()
        val gateway = object : ActivityStore by source {
            override suspend fun create(input: ActivityInput, clientRequestId: String): ApiResult<ActivityCreation> {
                calls.add(input)
                return when (calls.size) {
                    1 -> ApiResult.Failure(ApiFailure(ApiFailureKind.RATE_LIMITED))
                    2 -> ApiResult.Failure(ApiFailure(ApiFailureKind.VALIDATION))
                    else -> source.create(input, clientRequestId)
                }
            }
        }
        val vm = model { WorkspaceViewModel(gateway) }
        vm.save(input, null, null, key.requestId, previousOutcomeUnknown = true); advanceUntilIdle()
        vm.save(input, null, null, key.requestId); advanceUntilIdle()
        vm.save(input.copy(notes = "Changed"), null, null, key.requestId); advanceUntilIdle()
        assertEquals(2, calls.size)
        assertEquals(ApiFailureKind.IDEMPOTENCY_CONFLICT, vm.state.value.save!!.error)
        vm.save(input, null, null, key.requestId); advanceUntilIdle()
        assertEquals(listOf(input, input, input), calls)
        assertNotNull(vm.state.value.save!!.id)
        assertFalse(vm.state.value.save!!.created)
        assertEquals(6, source.load().value.size)
    }

    @Test fun cancellationRemainsCancellationAndDoesNotReleaseAnUnconfirmedCreate() = runTest {
        val source = DemoActivityStore(ZoneId.of("Asia/Jakarta"))
        val calls = mutableListOf<ActivityInput>()
        val gateway = object : ActivityStore by source {
            override suspend fun create(input: ActivityInput, clientRequestId: String): ApiResult<ActivityCreation> {
                calls.add(input)
                if (calls.size == 2) return ApiResult.Failure(ApiFailure(ApiFailureKind.RATE_LIMITED))
                val result = source.create(input, clientRequestId)
                if (calls.size == 1) throw CancellationException("Cancelled after acceptance")
                return result
            }
        }
        val vm = model { WorkspaceViewModel(gateway) }
        val input = ActivityInput(WasteCategory.PLASTIC, DisposalMethod.RECYCLE,
            Quantity.Weight.of(250), source.referenceDate, "Original entry")
        vm.save(input, null, null, key.requestId); advanceUntilIdle()
        assertNull(vm.state.value.save)
        assertFalse(vm.state.value.saving)
        assertEquals(1, calls.size)
        vm.save(input, null, null, key.requestId); advanceUntilIdle()
        vm.save(input.copy(notes = "Changed"), null, null, key.requestId); advanceUntilIdle()
        assertEquals(2, calls.size)
        assertEquals(ApiFailureKind.IDEMPOTENCY_CONFLICT, vm.state.value.save!!.error)
        vm.save(input, null, null, key.requestId); advanceUntilIdle()
        assertFalse(vm.state.value.save!!.created)
        assertEquals(6, source.load().value.size)
    }

    @Test fun aVersionConflictStillAllowsAnUnknownEditToReconcileWithTheLatestRecord() = runTest {
        val source = DemoActivityStore(ZoneId.of("Asia/Jakarta"))
        val log = source.load().value.first()
        val draft = EditorDraft.from(log, source.referenceDate)
        draft.change(EditorField.NOTES, draft.values.copy(notes = "My draft"))
        val input = draft.prepareSubmission(source.referenceDate)
        draft.submissionFailed(ApiFailureKind.NETWORK)
        draft.submissionFailed(ApiFailureKind.RATE_LIMITED)
        assertTrue(draft.outcomeUnknown)
        assertEquals(input, draft.prepareSubmission(source.referenceDate.plusDays(60)))
        draft.submissionFailed(ApiFailureKind.VERSION_CONFLICT)
        assertTrue(draft.needsConflictResolution)
        assertFalse(draft.locked)
        assertFalse(draft.outcomeUnknown)
        assertTrue(draft.resolveConflict(log.copy(version = log.version + 1, notes = "Server edit"), keepDraft = true))
        assertEquals(log.version + 1, draft.values.version)
        assertEquals("My draft", draft.prepareSubmission(source.referenceDate)!!.notes)
    }

    private class Disk(initial: StoredEditorDraft) : EditorDraftStore {
        private var encoded: String? = DraftDiskCodec.encode("owner-A", initial)
        override suspend fun read() = ApiResult.Success(encoded?.let { DraftDiskCodec.decode("owner-A", it) })
        override suspend fun write(draft: StoredEditorDraft): ApiResult<Unit> {
            encoded = DraftDiskCodec.encode("owner-A", draft)
            return ApiResult.Success(Unit)
        }
        override suspend fun clear(requestId: String?): ApiResult<Unit> {
            if (requestId == null || read().value?.requestId == requestId) encoded = null
            return ApiResult.Success(Unit)
        }
    }
}
