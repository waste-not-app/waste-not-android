package com.example.wastenot.ui.main

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.wastenot.data.demo.DemoActivityStore
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.network.ApiFailure
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.data.repository.ActivityInput
import com.example.wastenot.data.repository.ActivityCreation
import com.example.wastenot.data.repository.ActivityStore
import com.example.wastenot.model.*
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkspaceViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val owners = mutableListOf<ViewModelStoreOwner>()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { owners.forEach { it.viewModelStore.clear() }; Dispatchers.resetMain() }
    private fun model(source: ActivityStore): Pair<WorkspaceViewModel, ViewModelStoreOwner> {
        val owner = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
        owners.add(owner)
        val factory = viewModelFactory { initializer { WorkspaceViewModel(source) } }
        return ViewModelProvider.create(owner, factory)[WorkspaceViewModel::class] to owner
    }

    @Test fun saveIsCorrelatedAndAcknowledgedWithoutAddingEditActivities() = runTest {
        val source = DemoActivityStore(ZoneId.of("Asia/Jakarta"))
        val (vm, _) = model(source)
        vm.load(); vm.load()
        advanceUntilIdle()
        assertEquals(5, vm.state.value.records.size)
        val input = ActivityInput(WasteCategory.METAL, DisposalMethod.RECYCLE, Quantity.Count.of(1), source.referenceDate, "contoh")
        val request = UUID.randomUUID().toString()
        vm.save(input, null, null, request)
        advanceUntilIdle()
        assertEquals(request, vm.state.value.save!!.requestId)
        val id = vm.state.value.save!!.id!!
        assertTrue(vm.state.value.save!!.created)
        assertEquals(6, vm.state.value.records.size)
        vm.acknowledgeSave("other-request")
        assertNotNull(vm.state.value.save)
        vm.acknowledgeSave(request)
        assertNull(vm.state.value.save)
        vm.save(input.copy(notes = "diubah"), id, 1, request)
        advanceUntilIdle()
        assertFalse(vm.state.value.save!!.created)
        assertEquals(6, vm.state.value.records.size)
        assertEquals(2, vm.state.value.records.first { it.id == id }.version)
    }

    @Test fun retryAfterCommittedResponseIsLostDoesNotAnnounceAnotherCreation() = runTest {
        val source = DemoActivityStore(ZoneId.of("Asia/Jakarta"))
        var loseResponse = true
        val gateway = object : ActivityStore by source {
            override suspend fun create(input: ActivityInput, clientRequestId: String): ApiResult<ActivityCreation> {
                val receipt = source.create(input, clientRequestId)
                return if (loseResponse) {
                    loseResponse = false
                    ApiResult.Failure(ApiFailure(ApiFailureKind.TIMEOUT))
                } else receipt
            }
        }
        val (vm, _) = model(gateway)
        vm.load(); advanceUntilIdle()
        val request = UUID.randomUUID().toString()
        val input = ActivityInput(WasteCategory.METAL, DisposalMethod.RECYCLE, Quantity.Count.of(1), source.referenceDate, "")
        vm.save(input, null, null, request); advanceUntilIdle()
        assertEquals(ApiFailureKind.TIMEOUT, vm.state.value.save!!.error)
        assertNull(vm.state.value.save!!.id)
        assertEquals(5, vm.state.value.records.size)
        vm.save(input, null, null, request); advanceUntilIdle()
        assertNotNull(vm.state.value.save!!.id)
        assertFalse(vm.state.value.save!!.created)
        assertEquals(6, vm.state.value.records.size)
        assertEquals(6, source.load().value.size)
    }

    @Test fun anUnresolvedCreateRejectsChangedPayloadBeforeCallingTheStore() = runTest {
        val source = DemoActivityStore(ZoneId.of("Asia/Jakarta"))
        val requests = mutableListOf<Pair<String, ActivityInput>>()
        val gateway = object : ActivityStore by source {
            override suspend fun create(input: ActivityInput, clientRequestId: String): ApiResult<ActivityCreation> {
                requests.add(clientRequestId to input)
                val receipt = source.create(input, clientRequestId)
                return if (requests.size == 1) ApiResult.Failure(ApiFailure(ApiFailureKind.TIMEOUT)) else receipt
            }
        }
        val (vm, _) = model(gateway)
        vm.load(); advanceUntilIdle()
        val request = UUID.randomUUID().toString()
        val input = ActivityInput(WasteCategory.METAL, DisposalMethod.RECYCLE, Quantity.Count.of(1), source.referenceDate, "awal")
        vm.save(input, null, null, request); advanceUntilIdle()
        vm.save(input.copy(notes = "koreksi"), null, null, request); advanceUntilIdle()
        assertEquals("Changed payload must never reach the store", 1, requests.size)
        assertEquals(ApiFailureKind.IDEMPOTENCY_CONFLICT, vm.state.value.save!!.error)
        vm.save(input, null, null, request); advanceUntilIdle()
        assertEquals(listOf(request to input, request to input), requests)
        assertNotNull(vm.state.value.save!!.id)
        assertFalse(vm.state.value.save!!.created)
        assertEquals(6, vm.state.value.records.size)
        assertEquals("awal", vm.state.value.records.first { it.id == vm.state.value.save!!.id }.notes)
    }

    @Test fun definiteCreateRejectionAllowsCorrectionsAndRapidTapsNeverStartAnotherWrite() = runTest {
        val source = DemoActivityStore(ZoneId.of("Asia/Jakarta"))
        val gate = CompletableDeferred<Unit>()
        val received = mutableListOf<ActivityInput>()
        val gateway = object : ActivityStore by source {
            override suspend fun create(input: ActivityInput, clientRequestId: String): ApiResult<ActivityCreation> {
                received.add(input)
                if (received.size == 1) {
                    gate.await()
                    return ApiResult.Failure(ApiFailure(ApiFailureKind.VALIDATION))
                }
                return source.create(input, clientRequestId)
            }
        }
        val (vm, _) = model(gateway)
        val request = UUID.randomUUID().toString()
        val input = ActivityInput(WasteCategory.METAL, DisposalMethod.RECYCLE, Quantity.Count.of(1), source.referenceDate, "awal")
        vm.save(input, null, null, request); runCurrent()
        vm.save(input, null, null, request)
        vm.save(input.copy(notes = "koreksi"), null, null, UUID.randomUUID().toString()); runCurrent()
        assertEquals(listOf(input), received)
        assertTrue(vm.state.value.saving)
        gate.complete(Unit); advanceUntilIdle()
        assertEquals(ApiFailureKind.VALIDATION, vm.state.value.save!!.error)
        vm.save(input.copy(notes = "koreksi"), null, null, request); advanceUntilIdle()
        assertEquals(listOf(input, input.copy(notes = "koreksi")), received)
        assertTrue(vm.state.value.save!!.created)
        assertEquals(6, vm.state.value.records.size)
    }

    @Test fun removingAccountOwnerClearsDataAndReplayCache() = runTest {
        val source = DemoActivityStore(ZoneId.of("Asia/Jakarta"))
        val (vm, owner) = model(source)
        vm.load()
        advanceUntilIdle()
        assertEquals(5, vm.state.value.records.size)
        owner.viewModelStore.clear()
        assertTrue(vm.state.value.records.isEmpty())
        assertTrue(source.load().value.isEmpty())
    }

    @Test fun lateLoadCannotRepopulateAClearedAccountOwner() = runTest {
        val demo = DemoActivityStore(ZoneId.of("Asia/Jakarta"))
        val gate = CompletableDeferred<Unit>()
        var cleared = false
        val source = object : ActivityStore by demo {
            override suspend fun load(): ApiResult<List<WasteLog>> {
                val snapshot = demo.load()
                withContext(NonCancellable) { gate.await() }
                return snapshot
            }
            override fun clear() { cleared = true; demo.clear() }
        }
        val (vm, owner) = model(source)
        vm.load()
        runCurrent()
        owner.viewModelStore.clear()
        gate.complete(Unit)
        advanceUntilIdle()
        assertTrue(cleared)
        assertTrue(vm.state.value.records.isEmpty())
    }
    @Test fun deleteRetainsTheRecordUntilConfirmationAndSerializesWrites() = runTest {
        val demo = DemoActivityStore(ZoneId.of("Asia/Jakarta"))
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val source = object : ActivityStore by demo {
            override suspend fun delete(id: String, version: Int): ApiResult<Unit> {
                calls++
                gate.await()
                return demo.delete(id, version)
            }
        }
        val (vm, _) = model(source)
        vm.load(); advanceUntilIdle()
        val first = vm.state.value.records[0]
        val other = vm.state.value.records[1]
        vm.delete(first.id, first.version); runCurrent()
        assertEquals(5, vm.state.value.records.size)
        assertEquals(first.id, vm.state.value.deletingId)
        vm.delete(other.id, other.version); runCurrent()
        assertEquals(1, calls)
        gate.complete(Unit); advanceUntilIdle()
        assertEquals(4, vm.state.value.records.size)
        assertFalse(vm.state.value.records.any { it.id == first.id })
        assertTrue(vm.state.value.deletion!!.deleted)
        vm.acknowledgeDelete("another-id")
        assertNotNull(vm.state.value.deletion)
        vm.acknowledgeDelete(first.id)
        assertNull(vm.state.value.deletion)
        assertNull(vm.state.value.deletingId)
    }

    @Test fun failedDeleteKeepsTheVisibleRecordAndCanBeRetried() = runTest {
        val demo = DemoActivityStore(ZoneId.of("Asia/Jakarta"))
        var failure: ApiFailureKind? = ApiFailureKind.STORAGE
        val source = object : ActivityStore by demo {
            override suspend fun delete(id: String, version: Int): ApiResult<Unit> =
                failure?.let { ApiResult.Failure(ApiFailure(it)) } ?: demo.delete(id, version)
        }
        val (vm, _) = model(source)
        vm.load(); advanceUntilIdle()
        val log = vm.state.value.records.first()
        listOf(ApiFailureKind.STORAGE, ApiFailureKind.NETWORK, ApiFailureKind.VERSION_CONFLICT, ApiFailureKind.PRECONDITION_REQUIRED).forEach { kind ->
            failure = kind
            vm.delete(log.id, log.version); advanceUntilIdle()
            assertEquals(5, vm.state.value.records.size)
            assertEquals(kind, vm.state.value.deletion!!.error)
            assertFalse(vm.state.value.deletion!!.deleted)
        }
        failure = null
        vm.delete(log.id, log.version); advanceUntilIdle()
        assertEquals(4, vm.state.value.records.size)
        assertTrue(vm.state.value.deletion!!.deleted)
    }

    @Test fun staleLoadCannotResurrectADeletedRecordEvenWhenRefreshFails() = runTest {
        val demo = DemoActivityStore(ZoneId.of("Asia/Jakarta"))
        val gate = CompletableDeferred<Unit>()
        var reads = 0
        val source = object : ActivityStore by demo {
            override suspend fun load(): ApiResult<List<WasteLog>> {
                val snapshot = demo.load()
                return when (++reads) {
                    2 -> { withContext(NonCancellable) { gate.await() }; snapshot }
                    3 -> ApiResult.Failure(ApiFailure(ApiFailureKind.NETWORK))
                    else -> snapshot
                }
            }
        }
        val (vm, _) = model(source)
        vm.load(); advanceUntilIdle()
        val log = vm.state.value.records.first()
        vm.load(true); runCurrent()
        vm.delete(log.id, log.version); runCurrent()
        assertEquals(4, vm.state.value.records.size)
        assertEquals(ApiFailureKind.NETWORK, vm.state.value.error)
        gate.complete(Unit); advanceUntilIdle()
        assertEquals(4, vm.state.value.records.size)
        assertFalse(vm.state.value.records.any { it.id == log.id })
        assertFalse(vm.state.value.loading)
    }

    @Test fun createWhileInitialLoadIsPendingRefreshesTheWholeSnapshot() = runTest {
        val demo = DemoActivityStore(ZoneId.of("Asia/Jakarta"))
        val gate = CompletableDeferred<Unit>()
        var reads = 0
        val source = object : ActivityStore by demo {
            override suspend fun load(): ApiResult<List<WasteLog>> {
                val snapshot = demo.load()
                if (++reads == 1) withContext(NonCancellable) { gate.await() }
                return snapshot
            }
        }
        val (vm, _) = model(source)
        vm.load(); runCurrent()
        vm.save(ActivityInput(WasteCategory.METAL, DisposalMethod.RECYCLE, Quantity.Count.of(1), demo.referenceDate, ""),
            null, null, UUID.randomUUID().toString())
        runCurrent()
        assertEquals(6, vm.state.value.records.size)
        gate.complete(Unit); advanceUntilIdle()
        assertEquals(6, vm.state.value.records.size)
    }

    @Test fun detailLoadsByIdEvenWhenTheListDoesNotContainTheRecord() = runTest {
        val demo = DemoActivityStore(ZoneId.of("Asia/Jakarta"))
        val record = demo.load().value.first()
        val source = object : ActivityStore by demo {
            override suspend fun load(): ApiResult<List<WasteLog>> = ApiResult.Success(emptyList())
        }
        val (vm, _) = model(source)
        vm.load(); advanceUntilIdle()
        assertTrue(vm.state.value.records.isEmpty())
        vm.loadDetail(record.id); advanceUntilIdle()
        assertEquals(record, vm.state.value.detail!!.log)
        assertFalse(vm.state.value.detail!!.loading)
        assertNull(vm.state.value.detail!!.error)
    }

    @Test fun aLateDetailResponseCannotResurrectADeletedRecord() = runTest {
        val demo = DemoActivityStore(ZoneId.of("Asia/Jakarta"))
        val gate = CompletableDeferred<Unit>()
        val source = object : ActivityStore by demo {
            override suspend fun get(id: String): ApiResult<WasteLog> {
                val snapshot = demo.get(id)
                withContext(NonCancellable) { gate.await() }
                return snapshot
            }
        }
        val (vm, _) = model(source)
        vm.load(); advanceUntilIdle()
        val record = vm.state.value.records.first()
        vm.loadDetail(record.id); runCurrent()
        vm.delete(record.id, record.version); runCurrent()
        gate.complete(Unit); advanceUntilIdle()
        assertEquals(4, vm.state.value.records.size)
        assertNull(vm.state.value.detail!!.log)
        assertEquals(ApiFailureKind.NOT_FOUND, vm.state.value.detail!!.error)
    }

    @Test fun missingDetailHidesTheCacheWhileNetworkFailureRetainsItForReading() = runTest {
        val demo = DemoActivityStore(ZoneId.of("Asia/Jakarta"))
        var failure = ApiFailureKind.NETWORK
        val source = object : ActivityStore by demo {
            override suspend fun get(id: String): ApiResult<WasteLog> = ApiResult.Failure(ApiFailure(failure))
        }
        val (vm, _) = model(source)
        vm.load(); advanceUntilIdle()
        val record = vm.state.value.records.first()
        vm.loadDetail(record.id); advanceUntilIdle()
        assertEquals(record, vm.state.value.detail!!.log)
        assertEquals(ApiFailureKind.NETWORK, vm.state.value.detail!!.error)
        failure = ApiFailureKind.NOT_FOUND
        vm.loadDetail(record.id); advanceUntilIdle()
        assertNull(vm.state.value.detail!!.log)
        assertEquals(ApiFailureKind.NOT_FOUND, vm.state.value.detail!!.error)
    }

}
