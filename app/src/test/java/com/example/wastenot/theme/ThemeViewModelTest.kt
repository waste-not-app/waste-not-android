package com.example.wastenot.theme

import com.example.wastenot.data.repository.ThemeStore
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThemeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val owners = mutableListOf<ViewModelStoreOwner>()
    private class Store : ThemeStore {
        val durable = MutableStateFlow(ThemeMode.SYSTEM)
        var unreadable = false
        val writes = mutableListOf<ThemeMode>()
        var saving: suspend (ThemeMode) -> Unit = { durable.value = it }
        override val modes: Flow<ThemeMode> get() = flow {
            if (unreadable) throw IOException("cannot read appearance")
            emitAll(durable)
        }
        override suspend fun write(mode: ThemeMode) { writes.add(mode); saving(mode) }
    }
    private fun model(store: ThemeStore): ThemeViewModel {
        val owner = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
        owners.add(owner)
        return ViewModelProvider.create(owner, viewModelFactory { initializer { ThemeViewModel(store) } })[ThemeViewModel::class]
    }
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { owners.forEach { it.viewModelStore.clear() }; Dispatchers.resetMain() }

    @Test fun asynchronousPreferenceIsUnknownUntilItIsRead() = runTest {
        val store = Store().apply { durable.value = ThemeMode.DARK }
        val vm = model(store)
        assertNull(vm.state.value.mode)
        vm.load(); advanceUntilIdle()
        assertEquals(ThemeMode.DARK, vm.state.value.mode)
        assertNull(vm.state.value.failure)
        store.durable.value = ThemeMode.LIGHT; advanceUntilIdle()
        assertEquals(ThemeMode.LIGHT, vm.state.value.mode)
    }

    @Test fun choiceUpdatesBeforeDiskFinishesAndSurvivesAStaleEmission() = runTest {
        val store = Store()
        val release = CompletableDeferred<Unit>()
        store.saving = { mode -> release.await(); store.durable.value = mode }
        val vm = model(store); vm.load(); advanceUntilIdle()
        vm.choose(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, vm.state.value.mode)
        assertTrue(vm.state.value.saving)
        runCurrent(); store.durable.value = ThemeMode.LIGHT; runCurrent()
        assertEquals(ThemeMode.DARK, vm.state.value.mode)
        release.complete(Unit); advanceUntilIdle()
        assertEquals(ThemeMode.DARK, vm.state.value.mode)
        assertEquals(ThemeMode.DARK, store.durable.value)
        assertFalse(vm.state.value.saving)
    }

    @Test fun rapidChoicesSerializeWritesAndTheLatestIntentWins() = runTest {
        val store = Store()
        val release = CompletableDeferred<Unit>()
        var active = 0
        var peak = 0
        store.saving = { mode ->
            active++; peak = maxOf(peak, active)
            if (mode == ThemeMode.DARK) release.await()
            store.durable.value = mode; active--
        }
        val vm = model(store); vm.load(); advanceUntilIdle()
        vm.choose(ThemeMode.DARK); runCurrent()
        vm.choose(ThemeMode.LIGHT); vm.choose(ThemeMode.SYSTEM); runCurrent()
        assertEquals(ThemeMode.SYSTEM, vm.state.value.mode)
        release.complete(Unit); advanceUntilIdle()
        assertEquals(listOf(ThemeMode.DARK, ThemeMode.SYSTEM), store.writes)
        assertEquals(1, peak)
        assertEquals(ThemeMode.SYSTEM, store.durable.value)
        assertEquals(ThemeMode.SYSTEM, vm.state.value.mode)
        assertFalse(vm.state.value.saving)
    }

    @Test fun failedWriteRetainsVisibleChoiceAndRetryPersistsIt() = runTest {
        val store = Store()
        store.saving = { throw IOException("disk unavailable") }
        val vm = model(store); vm.load(); advanceUntilIdle()
        vm.choose(ThemeMode.DARK); advanceUntilIdle()
        assertEquals(ThemeMode.DARK, vm.state.value.mode)
        assertEquals(ThemeMode.SYSTEM, store.durable.value)
        assertEquals(ThemeFailure.WRITE, vm.state.value.failure)
        assertFalse(vm.state.value.saving)
        store.durable.value = ThemeMode.LIGHT; advanceUntilIdle()
        assertEquals(ThemeMode.DARK, vm.state.value.mode)
        store.saving = { store.durable.value = it }
        vm.retry(); advanceUntilIdle()
        assertEquals(listOf(ThemeMode.DARK, ThemeMode.DARK), store.writes)
        assertEquals(ThemeMode.DARK, store.durable.value)
        assertNull(vm.state.value.failure)
    }

    @Test fun unreadablePreferenceShowsAnExplicitFallbackAndCanRecover() = runTest {
        val store = Store().apply { unreadable = true; durable.value = ThemeMode.DARK }
        val vm = model(store); vm.load(); advanceUntilIdle()
        assertEquals(ThemeMode.SYSTEM, vm.state.value.mode)
        assertEquals(ThemeFailure.READ, vm.state.value.failure)
        store.unreadable = false
        vm.retry(); advanceUntilIdle()
        assertEquals(ThemeMode.DARK, vm.state.value.mode)
        assertNull(vm.state.value.failure)
        assertTrue(store.writes.isEmpty())
    }

    @Test fun initialReadTimeoutReleasesStartupAndOwnerDisposalRejectsLateValues() = runTest {
        val response = CompletableDeferred<ThemeMode>()
        val store = object : ThemeStore {
            override val modes: Flow<ThemeMode> = flow { emit(response.await()) }
            override suspend fun write(mode: ThemeMode) = Unit
        }
        val vm = model(store); vm.load(); runCurrent()
        advanceTimeBy(4_999); runCurrent(); assertNull(vm.state.value.mode)
        advanceTimeBy(1); runCurrent()
        assertEquals(ThemeFailure.READ, vm.state.value.failure)
        assertEquals(ThemeMode.SYSTEM, vm.state.value.mode)
        owners.last().viewModelStore.clear()
        response.complete(ThemeMode.DARK); advanceUntilIdle()
        assertNotEquals(ThemeMode.DARK, vm.state.value.mode)
    }

    @Test fun reloadingObservationDoesNotResetAnInFlightUserChoice() = runTest {
        val store = Store()
        val release = CompletableDeferred<Unit>()
        store.saving = { mode -> release.await(); store.durable.value = mode }
        val vm = model(store); vm.load(); advanceUntilIdle()
        vm.choose(ThemeMode.DARK); runCurrent(); vm.load(retry = true); runCurrent()
        assertEquals(ThemeMode.DARK, vm.state.value.mode)
        release.complete(Unit); advanceUntilIdle()
        assertEquals(ThemeMode.DARK, vm.state.value.mode)
        assertNull(vm.state.value.failure)
    }
}
