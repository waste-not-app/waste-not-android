package com.example.wastenot.ui.main

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.wastenot.data.demo.DemoActivityStore
import com.example.wastenot.data.network.*
import com.example.wastenot.data.repository.ActivityStore
import com.example.wastenot.model.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val owners = mutableListOf<ViewModelStoreOwner>()
    private val zone = ZoneId.of("Asia/Jakarta")
    private val date = LocalDate.of(2026, 11, 8)
    private val base = DemoActivityStore(zone)
    private data class Request(val category: WasteCategory?, val method: DisposalMethod?, val dates: LogDateRange?, val cursor: String?)
    private val requests = mutableListOf<Request>()
    private fun record(index: Int) = WasteLog(index.toString().padStart(5, '0'), WasteCategory.PLASTIC,
        DisposalMethod.RECYCLE, Quantity.Count.of(1), date, zone, "", 1, Instant.EPOCH, Instant.EPOCH)
    private fun page(records: List<WasteLog>, next: String? = null) = ApiResult.Success(records, PageInfo(next, next != null))
    private fun store(answer: suspend (Request) -> ApiResult<List<WasteLog>>) = object : ActivityStore by base {
        override suspend fun page(category: WasteCategory?, method: DisposalMethod?, dates: LogDateRange?, cursor: String?): ApiResult<List<WasteLog>> {
            val request = Request(category, method, dates, cursor)
            requests.add(request)
            return answer(request)
        }
    }
    private fun model(source: ActivityStore, saved: SavedStateHandle = SavedStateHandle(), now: () -> Instant = Instant::now): HistoryViewModel {
        val owner = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
        owners.add(owner)
        return ViewModelProvider.create(owner, viewModelFactory { initializer { HistoryViewModel(source, saved, now) } })[HistoryViewModel::class]
    }
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { owners.forEach { it.viewModelStore.clear() }; Dispatchers.resetMain() }

    @Test fun thousandRecordsAreRequestedTwentyAtATimeWithoutEagerLoading() = runTest {
        val records = (1000 downTo 1).map(::record)
        val vm = model(store { request ->
            val offset = request.cursor?.toInt() ?: 0
            page(records.drop(offset).take(20), (offset + 20).takeIf { it < records.size }?.toString())
        })
        vm.load(); vm.load(); advanceUntilIdle()
        assertEquals(1, requests.size)
        assertEquals(20, vm.state.value.records.size)
        repeat(49) { vm.more(); vm.more(); advanceUntilIdle() }
        assertEquals(50, requests.size)
        assertEquals(records, vm.state.value.records)
        assertEquals(1000, vm.state.value.records.map { it.id }.toSet().size)
        assertNull(vm.state.value.nextCursor)
        vm.more(); advanceUntilIdle(); assertEquals(50, requests.size)
    }

    @Test fun nextPageFailureKeepsRowsAndRetriesTheSameCursor() = runTest {
        var fail = true
        val vm = model(store { if (it.cursor == null) page(listOf(record(3)), "second")
            else if (fail) ApiResult.Failure(ApiFailure(ApiFailureKind.NETWORK)) else page(listOf(record(2))) })
        vm.load(); advanceUntilIdle(); vm.more(); advanceUntilIdle()
        assertEquals(listOf(record(3)), vm.state.value.records)
        assertEquals(ApiFailureKind.NETWORK, vm.state.value.pageError)
        assertNull(vm.state.value.error)
        fail = false; vm.more(); advanceUntilIdle()
        assertEquals(listOf(null, "second", "second"), requests.map { it.cursor })
        assertEquals(listOf(record(3), record(2)), vm.state.value.records)
        assertNull(vm.state.value.pageError)
    }

    @Test fun overlapDeduplicatesAndKeepsTheNewestVersionInServerOrder() = runTest {
        val updated = record(2).copy(version = 2, notes = "updated")
        val vm = model(store { when (it.cursor) {
            null -> page(listOf(record(2), record(3)), "next")
            "next" -> page(listOf(record(1), updated, record(3).copy(version = 0)))
            else -> error("unexpected cursor")
        } })
        vm.load(); advanceUntilIdle(); vm.more(); advanceUntilIdle()
        assertEquals(listOf(record(3), updated, record(1)), vm.state.value.records)
    }

    @Test fun rapidFilterChangesIgnoreUncooperativeOldResponsesAndSendAllFilters() = runTest {
        val old = CompletableDeferred<ApiResult<List<WasteLog>>>()
        val vm = model(store { if (it.category == null) withContext(NonCancellable) { old.await() }
            else page(listOf(record(7))) })
        vm.load(); runCurrent()
        val filter = HistoryFilter(WasteCategory.PLASTIC, DisposalMethod.RECYCLE, date.minusDays(7), date)
        vm.filter(filter); advanceUntilIdle()
        assertEquals(filter, vm.state.value.filter)
        assertEquals(listOf(record(7)), vm.state.value.records)
        assertEquals(Request(filter.category, filter.method, LogDateRange(filter.from!!, filter.to!!), null), requests.last())
        old.complete(page(listOf(record(99)), "old")); advanceUntilIdle()
        assertEquals(listOf(record(7)), vm.state.value.records)
        assertNull(vm.state.value.nextCursor)
    }

    @Test fun refreshFailureKeepsDataButInvalidatesTheOldCursor() = runTest {
        var fail = false
        val vm = model(store { if (fail) ApiResult.Failure(ApiFailure(ApiFailureKind.TIMEOUT)) else page(listOf(record(1)), "next") })
        vm.load(); advanceUntilIdle(); fail = true; vm.refresh(); runCurrent()
        assertEquals(listOf(record(1)), vm.state.value.records)
        assertNull(vm.state.value.nextCursor)
        advanceUntilIdle(); assertEquals(ApiFailureKind.TIMEOUT, vm.state.value.error)
        vm.more(); advanceUntilIdle(); assertEquals(2, requests.size)
        fail = false; vm.refresh(); advanceUntilIdle()
        assertEquals("next", vm.state.value.nextCursor)
        assertNull(vm.state.value.error)
    }

    @Test fun cursorCycleRequiresRestartAndPreservesLoadedRows() = runTest {
        val vm = model(store { when (it.cursor) {
            null -> page(listOf(record(3)), "a")
            "a" -> page(listOf(record(2)), "b")
            else -> page(listOf(record(1)), "a")
        } })
        vm.load(); advanceUntilIdle(); vm.more(); advanceUntilIdle(); vm.more(); advanceUntilIdle()
        assertEquals(listOf(record(3), record(2)), vm.state.value.records)
        assertEquals(ApiFailureKind.MALFORMED_RESPONSE, vm.state.value.pageError)
        assertTrue(vm.state.value.pageRestartRequired)
        assertNull(vm.state.value.nextCursor)
        vm.more(); advanceUntilIdle(); assertEquals(3, requests.size)
        vm.refresh(); advanceUntilIdle(); assertEquals(listOf(record(3)), vm.state.value.records)
    }

    @Test fun malformedPagesAndOutOfFilterRecordsAreRejected() = runTest {
        val bad = listOf<ApiResult<List<WasteLog>>>(ApiResult.Success(listOf(record(1))),
            ApiResult.Success(listOf(record(1)), PageInfo(null, true)), page(emptyList(), "next"),
            page((1..21).map(::record)), page(listOf(record(1).copy(category = WasteCategory.GLASS))))
        bad.forEach { response ->
            val vm = model(store { response })
            vm.filter(HistoryFilter(category = WasteCategory.PLASTIC)); advanceUntilIdle()
            assertEquals(ApiFailureKind.MALFORMED_RESPONSE, vm.state.value.error)
            assertTrue(vm.state.value.records.isEmpty())
        }
    }

    @Test fun filtersRestoreAndInvalidDatesCannotStartRequests() = runTest {
        val saved = SavedStateHandle()
        val vm = model(store { page(emptyList()) }, saved)
        val filter = HistoryFilter(WasteCategory.PLASTIC, DisposalMethod.REDUCE, date.minusDays(1), date)
        vm.filter(filter); advanceUntilIdle()
        val restored = model(store { page(emptyList()) }, saved)
        assertEquals(filter, restored.state.value.filter)
        restored.filter(filter.copy(to = null)); advanceUntilIdle()
        assertEquals(1, requests.size)
        assertEquals(filter, restored.state.value.filter)
        val corrupted = model(store { page(emptyList()) }, SavedStateHandle(mapOf("history_from" to "bad-date", "history_to" to date.toString())))
        assertEquals(HistoryFilter(), corrupted.state.value.filter)
    }

    @Test fun ownerDisposalCannotPublishLateResponses() = runTest {
        val answer = CompletableDeferred<ApiResult<List<WasteLog>>>()
        val vm = model(store { withContext(NonCancellable) { answer.await() } })
        vm.load(); runCurrent(); owners.last().viewModelStore.clear()
        answer.complete(page(listOf(record(1)))); advanceUntilIdle()
        assertTrue(vm.state.value.records.isEmpty())
    }

    @Test fun historyCanQueryAcrossYearsWithoutTheStatisticsRangeLimit() = runTest {
        val vm = model(store { page(emptyList()) })
        val beginning = date.minusYears(3)
        vm.filter(HistoryFilter(from = beginning, to = date)); advanceUntilIdle()
        assertEquals(LogDateRange(beginning, date), requests.single().dates)
        assertNull(vm.state.value.error)
    }

    @Test fun rejectedCursorOffersFirstPageRecoveryInsteadOfRetryingForever() = runTest {
        val vm = model(store { if (it.cursor == null) page(listOf(record(1)), "expired")
            else ApiResult.Failure(ApiFailure(ApiFailureKind.INVALID_REQUEST, status = 400, code = "INVALID_REQUEST")) })
        vm.load(); advanceUntilIdle(); vm.more(); advanceUntilIdle()
        assertTrue(vm.state.value.pageRestartRequired)
        assertNull(vm.state.value.nextCursor)
        assertEquals(listOf(record(1)), vm.state.value.records)
        vm.refresh(); advanceUntilIdle()
        assertNull(requests.last().cursor)
        assertNull(vm.state.value.pageError)
    }

    @Test fun detailRefreshCannotDowngradeANewerRowAndRemovesRowsThatNoLongerMatch() = runTest {
        val vm = model(store { page(listOf(record(1).copy(version = 3))) })
        vm.filter(HistoryFilter(category = WasteCategory.PLASTIC)); advanceUntilIdle()
        vm.reconcile(record(1).copy(version = 2))
        assertEquals(3, vm.state.value.records.single().version)
        vm.reconcile(record(1).copy(version = 4, category = WasteCategory.PAPER))
        assertTrue(vm.state.value.records.isEmpty())
    }

    @Test fun rateLimitBlocksFirstAndNextPageUntilDeadline() = runTest {
        var now = Instant.EPOCH
        var fail = true
        val vm = model(store { if (fail) ApiResult.Failure(ApiFailure(ApiFailureKind.RATE_LIMITED, retryAt = Instant.EPOCH.plusSeconds(60))) else page(listOf(record(1)), "next") }, now = { now })
        vm.load(); advanceUntilIdle(); fail = false
        vm.refresh(); vm.filter(HistoryFilter(category = WasteCategory.PLASTIC)); advanceUntilIdle()
        assertEquals(1, requests.size)
        now = Instant.EPOCH.plusSeconds(60); vm.refresh(); advanceUntilIdle()
        assertEquals(2, requests.size)
        fail = true; vm.more(); advanceUntilIdle()
        assertEquals(ApiFailureKind.RATE_LIMITED, vm.state.value.pageError)
    }

    @Test fun confirmedDeleteAndEditReconcileCachedRowsEvenWhenRefreshFails() = runTest {
        var fail = false
        val vm = model(store { if (fail) ApiResult.Failure(ApiFailure(ApiFailureKind.NETWORK)) else page(listOf(record(3), record(2)), "next") })
        vm.load(); advanceUntilIdle(); fail = true
        vm.changed(ConfirmedActivityChange(1, deletedId = record(3).id)); advanceUntilIdle()
        assertEquals(listOf(record(2)), vm.state.value.records)
        vm.changed(ConfirmedActivityChange(2, log = record(2).copy(version = 2))); advanceUntilIdle()
        assertEquals(2, vm.state.value.records.single().version)
        val count = requests.size
        vm.changed(ConfirmedActivityChange(2, log = record(2))); advanceUntilIdle()
        assertEquals(count, requests.size)
    }
}
