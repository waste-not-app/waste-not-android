package com.example.wastenot.ui.main

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.wastenot.data.demo.DemoActivityStore
import com.example.wastenot.data.network.ApiFailure
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.repository.ActivityStore
import com.example.wastenot.model.*
import java.time.ZoneId
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SummariesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val owners = mutableListOf<ViewModelStoreOwner>()
    private val source = DemoActivityStore(ZoneId.of("Asia/Jakarta"))
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { owners.forEach { it.viewModelStore.clear() }; Dispatchers.resetMain() }

    private fun model(store: ActivityStore = source, handle: SavedStateHandle = SavedStateHandle(),
        today: () -> java.time.LocalDate = { source.referenceDate }): SummariesViewModel {
        val owner = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
        owners.add(owner)
        return ViewModelProvider.create(owner, viewModelFactory { initializer {
            SummariesViewModel(store, handle, today)
        } })[SummariesViewModel::class]
    }

    @Test fun aggregateReadsAreIndependentOfTheHistoryPageAndOfEachOther() = runTest {
        val dashboard = source.dashboard().value.copy(summary = source.dashboard().value.summary.copy(activityCount = 25))
        var dashboardCalls = 0
        val gateway = object : ActivityStore by source {
            override suspend fun load(): ApiResult<List<WasteLog>> = error("History must not supply summary data")
            override suspend fun dashboard(): ApiResult<DashboardSnapshot> { dashboardCalls++; return ApiResult.Success(dashboard) }
            override suspend fun statistics(range: ActivityDateRange): ApiResult<StatisticsSnapshot> =
                ApiResult.Failure(ApiFailure(ApiFailureKind.SERVER))
        }
        val vm = model(gateway)
        vm.dashboard(); vm.dashboard(); vm.statistics()
        advanceUntilIdle()
        assertEquals(1, dashboardCalls)
        assertEquals(25L, vm.state.value.dashboard.value!!.summary.activityCount)
        assertEquals(3, vm.state.value.dashboard.value!!.recentLogs.size)
        assertNull(vm.state.value.dashboard.error)
        assertNull(vm.state.value.statistics.value)
        assertEquals(ApiFailureKind.SERVER, vm.state.value.statistics.error)
    }

    @Test fun switchingPeriodClearsOldValuesAndAnOlderNonCooperativeResponseCannotOverwriteIt() = runTest {
        val old = CompletableDeferred<Unit>()
        var first = true
        val gateway = object : ActivityStore by source {
            override suspend fun statistics(range: ActivityDateRange): ApiResult<StatisticsSnapshot> {
                if (first) { first = false; withContext(NonCancellable) { old.await() } }
                return source.statistics(range)
            }
        }
        val vm = model(gateway)
        vm.statistics(); runCurrent()
        vm.statistics(30); runCurrent()
        assertEquals(30, vm.state.value.days)
        assertEquals(source.referenceDate.minusDays(29), vm.state.value.statistics.value!!.range.from)
        old.complete(Unit); advanceUntilIdle()
        assertEquals(30, vm.state.value.statistics.value!!.daily.size)
        assertFalse(vm.state.value.statistics.loading)
        assertNull(vm.state.value.statistics.error)
    }

    @Test fun aFailedNewPeriodRetainsTheLastSuccessfulPeriodsOwnDateRange() = runTest {
        val failure = CompletableDeferred<Unit>()
        val gateway = object : ActivityStore by source {
            override suspend fun statistics(range: ActivityDateRange): ApiResult<StatisticsSnapshot> {
                if (range.from == source.referenceDate.minusDays(29)) {
                    failure.await(); return ApiResult.Failure(ApiFailure(ApiFailureKind.NETWORK))
                }
                return source.statistics(range)
            }
        }
        val vm = model(gateway)
        vm.statistics(); advanceUntilIdle()
        assertNotNull(vm.state.value.statistics.value)
        vm.statistics(30); runCurrent()
        assertTrue(vm.state.value.statistics.loading)
        assertNull(vm.state.value.statistics.value)
        failure.complete(Unit); advanceUntilIdle()
        assertEquals(30, vm.state.value.days)
        assertEquals(ActivityDateRange.lastDays(source.referenceDate, 7), vm.state.value.statistics.value!!.range)
        assertEquals(ApiFailureKind.NETWORK, vm.state.value.statistics.error)
        vm.statistics(7); advanceUntilIdle()
        assertNotNull(vm.state.value.statistics.value)
    }

    @Test fun refreshRetainsOnlyTheSameLabeledPeriodAndBothFailuresAreExplicit() = runTest {
        var fail = false
        val gateway = object : ActivityStore by source {
            override suspend fun dashboard(): ApiResult<DashboardSnapshot> = if (fail)
                ApiResult.Failure(ApiFailure(ApiFailureKind.TIMEOUT)) else source.dashboard()
            override suspend fun statistics(range: ActivityDateRange): ApiResult<StatisticsSnapshot> = if (fail)
                ApiResult.Failure(ApiFailure(ApiFailureKind.RATE_LIMITED)) else source.statistics(range)
        }
        val vm = model(gateway)
        vm.dashboard(); vm.statistics(30); advanceUntilIdle()
        val previous = vm.state.value
        fail = true
        vm.refresh(); runCurrent()
        advanceUntilIdle()
        assertEquals(previous.dashboard.value, vm.state.value.dashboard.value)
        assertEquals(previous.statistics.value, vm.state.value.statistics.value)
        assertEquals(ApiFailureKind.TIMEOUT, vm.state.value.dashboard.error)
        assertEquals(ApiFailureKind.RATE_LIMITED, vm.state.value.statistics.error)
        fail = false; vm.refresh(); advanceUntilIdle()
        assertNull(vm.state.value.dashboard.error)
        assertNull(vm.state.value.statistics.error)
    }

    @Test fun periodSurvivesSavedStateRestorationAndNewAccountHasNoOldSnapshots() = runTest {
        val handle = SavedStateHandle()
        val vm = model(handle = handle)
        vm.dashboard(); vm.statistics(30); advanceUntilIdle()
        val restored = model(handle = SavedStateHandle(mapOf("statistics_days" to handle.get<Int>("statistics_days"))))
        assertEquals(30, restored.state.value.days)
        assertNull(restored.state.value.dashboard.value)
        assertNull(restored.state.value.statistics.value)
        restored.statistics(); advanceUntilIdle()
        assertEquals(30, restored.state.value.statistics.value!!.daily.size)
        val newAccount = model()
        assertEquals(7, newAccount.state.value.days)
        assertNull(newAccount.state.value.dashboard.value)
        assertEquals(7, model(handle = SavedStateHandle(mapOf("statistics_days" to 90))).state.value.days)
    }

    @Test fun accountDateIsReevaluatedAcrossMidnightAndClearCancelsPendingReads() = runTest {
        var date = source.referenceDate
        val pending = CompletableDeferred<Unit>()
        val gateway = object : ActivityStore by source {
            override suspend fun dashboard(): ApiResult<DashboardSnapshot> {
                withContext(NonCancellable) { pending.await() }
                return source.dashboard()
            }
        }
        val vm = model(gateway, today = { date })
        vm.statistics(); advanceUntilIdle()
        date = date.plusDays(1)
        vm.statistics(); advanceUntilIdle()
        assertEquals(date, vm.state.value.statistics.value!!.range.to)
        vm.dashboard(); runCurrent()
        owners.last().viewModelStore.clear()
        pending.complete(Unit); advanceUntilIdle()
        assertNull(vm.state.value.dashboard.value)
        assertNull(vm.state.value.statistics.value)
        assertFalse(vm.state.value.dashboard.loading)
    }

    @Test fun cachedSuccessCarriesRefreshFailureAndOriginalDatesAcrossAccountMidnight() = runTest {
        var date = source.referenceDate
        val savedAt = java.time.Instant.parse("2026-09-21T07:00:00Z")
        var freshness = SnapshotFreshness(cached = true, confirmedAt = savedAt)
        val confirmedDashboard = source.dashboard().value
        val confirmedRange = ActivityDateRange.lastDays(date, 7)
        val confirmedStatistics = source.statistics(confirmedRange).value
        val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val gateway = object : ActivityStore by source {
            override val changes = changes
            override suspend fun dashboard() = ApiResult.Success(confirmedDashboard.copy(freshness = freshness))
            override suspend fun statistics(range: ActivityDateRange) =
                ApiResult.Success(confirmedStatistics.copy(freshness = freshness))
        }
        val vm = model(gateway, today = { date })
        vm.dashboard(); vm.statistics(); advanceUntilIdle()
        assertTrue(vm.state.value.dashboard.freshness!!.cached)
        assertEquals(savedAt, vm.state.value.statistics.freshness!!.confirmedAt)
        assertFalse(vm.state.value.dashboard.stale)
        assertFalse(vm.state.value.statistics.stale)
        assertNull(vm.state.value.dashboard.error)

        freshness = freshness.copy(refreshError = ApiFailureKind.NETWORK)
        changes.emit(Unit); advanceUntilIdle()
        assertEquals(ApiFailureKind.NETWORK, vm.state.value.dashboard.error)
        assertEquals(ApiFailureKind.NETWORK, vm.state.value.statistics.error)
        assertEquals(confirmedDashboard.progress, vm.state.value.dashboard.value!!.progress)
        date = date.plusDays(1)
        vm.dashboard(); vm.statistics(); advanceUntilIdle()
        assertTrue(vm.state.value.dashboard.stale)
        assertTrue(vm.state.value.statistics.stale)
        assertEquals(confirmedDashboard.date, vm.state.value.dashboard.value!!.date)
        assertEquals(confirmedRange, vm.state.value.statistics.value!!.range)
        assertEquals(savedAt, vm.state.value.dashboard.freshness!!.confirmedAt)
        assertEquals(ApiFailureKind.NETWORK, vm.state.value.dashboard.error)
        assertFalse(vm.state.value.dashboard.loading)
    }

    @Test fun cachedRefreshErrorPersistsUntilTheStoreConfirmsSuccessfulRefresh() = runTest {
        var failed = true
        val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val gateway = object : ActivityStore by source {
            override val changes = changes
            override suspend fun dashboard() = source.dashboard().let { response ->
                ApiResult.Success(response.value.copy(freshness = SnapshotFreshness(cached = true,
                    confirmedAt = java.time.Instant.parse("2026-09-21T07:00:00Z"),
                    refreshError = ApiFailureKind.TIMEOUT.takeIf { failed })))
            }
        }
        val vm = model(gateway)
        vm.dashboard(); advanceUntilIdle()
        assertEquals(ApiFailureKind.TIMEOUT, vm.state.value.dashboard.error)
        vm.refresh(); advanceUntilIdle()
        assertEquals(ApiFailureKind.TIMEOUT, vm.state.value.dashboard.error)
        failed = false
        changes.emit(Unit); advanceUntilIdle()
        assertNull(vm.state.value.dashboard.error)
        assertTrue(vm.state.value.dashboard.freshness!!.cached)
    }
}
