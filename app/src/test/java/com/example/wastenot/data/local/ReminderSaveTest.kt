package com.example.wastenot.data.local

import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.work.Operation
import com.example.wastenot.ui.main.ReminderSaveProgress
import com.example.wastenot.ui.main.ReminderSaveViewModel
import java.io.IOException
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderSaveTest {
    private val zone = ZoneId.of("Asia/Jakarta")

    @Test fun successWaitsForPersistenceAndForEnqueueCompletion() = runTest {
        val setting = ReminderSetting(enabled = true, hour = 18, minute = 35)
        val finishedWrite = CompletableDeferred<Unit>()
        val store = Store { finishedWrite.await() }
        val scheduler = Scheduler()
        val save = async { saveReminderSetting("alice", setting, zone, store, scheduler) }

        runCurrent()
        assertNull(scheduler.scheduled)
        assertFalse(save.isCompleted)

        finishedWrite.complete(Unit)
        runCurrent()
        assertEquals("alice" to setting, store.saved)
        assertEquals(Schedule("alice", 18, 35, zone), scheduler.scheduled)
        assertFalse(save.isCompleted)

        scheduler.pending.succeed()
        assertEquals(ReminderSaveResult.SAVED, save.await())
    }

    @Test fun storageFailureDoesNotStartCancellationAndFailedCancellationCanBeRetried() = runTest {
        val setting = ReminderSetting(enabled = false, hour = 18, minute = 35)
        val scheduler = Scheduler()
        val unavailable = Store { throw IOException("storage unavailable") }
        assertEquals(ReminderSaveResult.PERSISTENCE_FAILED,
            saveReminderSetting("alice", setting, zone, unavailable, scheduler))
        assertNull(scheduler.cancelledOwner)

        val store = Store {}
        val first = async { saveReminderSetting("alice", setting, zone, store, scheduler) }
        runCurrent()
        assertEquals("alice" to setting, store.saved)
        assertEquals("alice", scheduler.cancelledOwner)
        assertFalse(first.isCompleted)
        scheduler.pending.fail()
        assertEquals(ReminderSaveResult.SCHEDULING_FAILED, first.await())

        scheduler.pending = PendingOperation()
        val retry = async { saveReminderSetting("alice", setting, zone, store, scheduler) }
        runCurrent()
        assertFalse(retry.isCompleted)
        scheduler.pending.succeed()
        assertEquals(ReminderSaveResult.SAVED, retry.await())
    }

    @Test fun leavingTheScreenWhileSavingKeepsTheAccountOperationAndItsFailure() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val model = ReminderSaveViewModel()
            val setting = ReminderSetting(enabled = true, hour = 17, minute = 30)
            val finishedWrite = CompletableDeferred<Unit>()
            val store = Store { finishedWrite.await() }
            val scheduler = Scheduler()
            val screen = async { model.save("alice", setting, zone, store, scheduler) }

            runCurrent()
            assertEquals(ReminderSaveProgress(setting), model.progress.value)
            screen.cancelAndJoin()
            finishedWrite.complete(Unit)
            runCurrent()
            assertEquals(Schedule("alice", 17, 30, zone), scheduler.scheduled)
            scheduler.pending.fail()
            runCurrent()
            assertEquals(ReminderSaveProgress(setting, ReminderSaveResult.SCHEDULING_FAILED), model.progress.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class Store(private val write: suspend () -> Unit) : ReminderStore {
        var saved: Pair<String, ReminderSetting>? = null
        override fun observe(ownerId: String) = flowOf(ReminderSetting())
        override suspend fun save(ownerId: String, setting: ReminderSetting) {
            write()
            saved = ownerId to setting
        }
        override suspend fun clear(ownerId: String) = Unit
    }

    private data class Schedule(val ownerId: String, val hour: Int, val minute: Int, val zone: ZoneId)

    private class Scheduler : ReminderScheduler {
        var pending = PendingOperation()
        var scheduled: Schedule? = null
        var cancelledOwner: String? = null
        override fun schedule(ownerId: String, hour: Int, minute: Int, zoneId: ZoneId): Operation {
            scheduled = Schedule(ownerId, hour, minute, zoneId)
            return pending
        }
        override fun cancel(ownerId: String): Operation {
            cancelledOwner = ownerId
            return pending
        }
    }

    private class PendingOperation : Operation {
        private lateinit var completer: CallbackToFutureAdapter.Completer<Operation.State.SUCCESS>
        private val future = CallbackToFutureAdapter.getFuture<Operation.State.SUCCESS> {
            completer = it
            "reminder operation"
        }
        override fun getState(): LiveData<Operation.State> = MutableLiveData()
        override fun getResult() = future
        fun succeed() { completer.set(Operation.SUCCESS) }
        fun fail() { completer.setException(IllegalStateException("scheduler unavailable")) }
    }
}
