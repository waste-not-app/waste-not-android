package com.example.wastenot.ui.main

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.wastenot.data.demo.DemoActivityStore
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.repository.ActivityInput
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
        assertEquals(6, vm.state.value.records.size)
        vm.acknowledgeSave("other-request")
        assertNotNull(vm.state.value.save)
        vm.acknowledgeSave(request)
        assertNull(vm.state.value.save)
        vm.save(input.copy(notes = "diubah"), id, 1, request)
        advanceUntilIdle()
        assertEquals(6, vm.state.value.records.size)
        assertEquals(2, vm.state.value.records.first { it.id == id }.version)
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
}
