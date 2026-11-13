package com.example.wastenot.data.network

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.BuildConfig
import com.example.wastenot.data.local.*
import com.example.wastenot.data.repository.*
import com.example.wastenot.model.*
import java.io.File
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in real HTTPS test; all records and encrypted credentials belong to an isolated account. */
class OfflineActivityDeviceTest {
    @Test fun workManagerAutomaticallyDrainsProductionOutboxForRealQaAccount() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("verifyOfflineLive") == "true")
        // This test uses the real application container, so it must never run over a person's installed account.
        require(BuildConfig.APPLICATION_ID.endsWith(".offlineqa"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val container = (context.applicationContext as com.example.wastenot.WasteNotApplication).container
        val sessions = container.sessions
        sessions.bootstrap()
        assumeTrue(sessions.state.value is SessionState.SignedOut)
        val run = UUID.randomUUID().toString()
        val user = sessions.register("Android worker verification", "android-worker-$run@example.test",
            UUID.randomUUID().toString() + "Az9!", ZoneId.of("Asia/Jakarta")).success()
        val store = container.activityStore(user)
        val core = container.core
        val output = File(context.cacheDir, "qa/offline-sync/worker.json")
        val steps = JSONArray()
        fun record(step: String) {
            steps.put(step); output.parentFile!!.mkdirs()
            output.writeText(JSONObject().put("user_id", user.id).put("run_id", run).put("passed_steps", steps).toString(2))
        }
        try {
            val date = core.dashboard().success().date
            val local = store.create(ActivityInput(WasteCategory.PAPER, DisposalMethod.RECYCLE,
                Quantity.Count.of(1), date, "WorkManager verification"), UUID.randomUUID().toString()).success().log
            assertEquals(ActivitySyncState.PENDING, local.syncState)
            val confirmed = withTimeout(90_000) {
                store.changes.onStart { emit(Unit) }.map { (store.get(local.id) as? ApiResult.Success)?.value }
                    .first { it?.syncState == ActivitySyncState.SYNCED }!!
            }
            assertEquals(10, core.dashboard().success().progress.xp)
            assertEquals(1, core.wasteLogs().success().size)
            record("production_workmanager_confirms_create_without_manual_synchronize_call")
            store.delete(confirmed.id, confirmed.version).success()
            withTimeout(90_000) {
                store.changes.onStart { emit(Unit) }.map { core.wasteLogs().success() }.first { it.isEmpty() }
            }
            assertEquals(0, core.dashboard().success().progress.xp)
            record("production_workmanager_confirms_delete_without_manual_synchronize_call")
        } finally {
            val active = sessions.state.value as? SessionState.Authenticated
            if (active?.user?.id == user.id) {
                (core.wasteLogs() as? ApiResult.Success)?.value?.forEach { core.deleteLog(it.id, it.version) }
                sessions.logout()
            }
        }
    }

    @Test fun persistentOfflineWritesRecoverAndResolveConflictsAgainstPublicBackend() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("verifyOfflineLive") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val run = UUID.randomUUID().toString()
        val filename = "offline-live-$run.db"
        val tokenDirectory = File(context.cacheDir, "offline-live-$run")
        val tokens = KeystoreRefreshTokenStore(tokenDirectory, "wastenot.offline-test.$run")
        val online = requireNotNull(WasteNotNetwork.create(BuildConfig.API_BASE_URL))
        // A closed loopback socket produces an actual transport failure without disconnecting wireless ADB.
        val unavailable = requireNotNull(WasteNotNetwork.create("http://127.0.0.1:9/v1/", allowLocalHttp = true))
        var database = Room.databaseBuilder(context, ActivityDatabase::class.java, filename).build()
        var sessions = SessionRepository(online, tokens, identityStore = RoomSessionIdentityStore(database.activities()))
        var reachable = true
        val report = JSONObject().put("run_id", run).put("api", BuildConfig.API_BASE_URL)
            .put("offline_transport", "closed loopback socket; wireless ADB retained")
        val steps = JSONArray()
        val output = File(context.cacheDir, "qa/offline-sync/device.json")
        fun record(step: String) {
            steps.put(step)
            output.parentFile!!.mkdirs()
            output.writeText(report.put("passed_steps", steps).toString(2))
        }
        val password = UUID.randomUUID().toString() + "Az9!"
        try {
            val user = sessions.register("Android offline verification", "android-offline-$run@example.test", password,
                ZoneId.of("Asia/Jakarta")).success()
            report.put("user_id", user.id)
            record("registered_real_isolated_account")
            fun remote(): ActivityStore {
                val live = RemoteActivityStore(CoreRepository(online, sessions))
                val blocked = RemoteActivityStore(CoreRepository(unavailable, sessions))
                return object : ActivityStore by live {
                    override suspend fun create(input: ActivityInput, clientRequestId: String) =
                        (if (reachable) live else blocked).create(input, clientRequestId)
                    override suspend fun update(id: String, version: Int, input: ActivityInput) =
                        (if (reachable) live else blocked).update(id, version, input)
                    override suspend fun delete(id: String, version: Int) =
                        (if (reachable) live else blocked).delete(id, version)
                }
            }
            fun local() = OfflineActivityStore(user, database, remote(),
                { (sessions.state.value as? SessionState.Authenticated)?.user?.id == user.id }, {})
            val core = CoreRepository(online, sessions)
            val today = core.dashboard().success().date
            val input = ActivityInput(WasteCategory.PLASTIC, DisposalMethod.RECYCLE, Quantity.Count.of(2), today, "offline verification")
            assertTrue(local().synchronize())
            assertEquals(0, local().dashboard().success().progress.xp)

            reachable = false
            val request = UUID.randomUUID().toString()
            val id = local().create(input, request).success().log.id
            local().update(id, 1, input.copy(notes = "edited offline")).success()
            val discarded = local().create(input, UUID.randomUUID().toString()).success().log
            local().delete(discarded.id, discarded.version).success()
            assertFalse(local().synchronize())
            assertEquals(0, core.dashboard().success().progress.xp)
            assertEquals(1, local().page().success().size)
            assertEquals(ActivitySyncState.PENDING, local().get(id).success().syncState)
            record("offline_create_edit_delete_transport_failure_does_not_award_xp")

            database.close()
            database = Room.databaseBuilder(context, ActivityDatabase::class.java, filename).build()
            sessions = SessionRepository(online, tokens, identityStore = RoomSessionIdentityStore(database.activities()))
            assertTrue((sessions.bootstrap(networkAvailable = false) as SessionState.Authenticated).offline)
            assertEquals("edited offline", local().get(id).success().notes)
            record("cold_database_and_session_restore_reads_pending_changes")

            reachable = true
            assertFalse((sessions.bootstrap() as SessionState.Authenticated).offline)
            assertTrue(local().synchronize())
            val confirmed = local().get(id).success()
            assertEquals(ActivitySyncState.SYNCED, confirmed.syncState)
            val live = CoreRepository(online, sessions)
            assertEquals(1, live.wasteLogs().success().size)
            assertEquals(10, live.dashboard().success().progress.xp)
            assertFalse(local().create(input, request).success().created)
            assertTrue(local().synchronize())
            assertEquals(10, live.dashboard().success().progress.xp)
            record("reconnection_drains_ordered_outbox_with_one_record_and_10_xp")

            local().update(id, confirmed.version, input.copy(notes = "local review")).success()
            live.updateLog(confirmed.id, confirmed.version, input.copy(notes = "server review")).success()
            assertTrue(local().synchronize())
            assertEquals(ActivitySyncState.CONFLICT, local().get(id).success().syncState)
            assertEquals("local review", local().get(id).success().notes)
            assertEquals("server review", live.wasteLog(confirmed.id).success().notes)
            local().resolveSync(id, keepLocal = true).success()
            assertTrue(local().synchronize())
            assertEquals("local review", live.wasteLog(confirmed.id).success().notes)
            record("409_preserves_local_input_and_explicit_resolution_uses_remote_version")

            reachable = false
            val final = local().get(id).success()
            local().delete(id, final.version).success()
            assertTrue(local().page().success().isEmpty())
            assertFalse(local().synchronize())
            reachable = true
            assertTrue(local().synchronize())
            assertTrue(live.wasteLogs().success().isEmpty())
            assertEquals(0, live.dashboard().success().progress.xp)
            assertTrue(database.activities().mutations(user.id).isEmpty())
            record("offline_delete_reconnects_and_server_returns_to_zero_xp")
            assertTrue(sessions.logout().localCleared)
            record("logout_clears_only_test_credentials")
        } finally {
            val active = sessions.state.value as? SessionState.Authenticated
            if (active != null && !active.offline && active.user.id == report.optString("user_id")) {
                val cleanup = CoreRepository(online, sessions)
                (cleanup.wasteLogs() as? ApiResult.Success)?.value?.forEach { cleanup.deleteLog(it.id, it.version) }
                sessions.logout()
            }
            tokens.clear()
            database.close()
            context.deleteDatabase(filename)
            tokenDirectory.deleteRecursively()
            listOf(online, unavailable).forEach { it.client.dispatcher.executorService.shutdown(); it.client.connectionPool.evictAll() }
        }
    }

    private fun <T> ApiResult<T>.success(): T = when (this) {
        is ApiResult.Success -> value
        is ApiResult.Failure -> error("Unexpected API failure: ${error.kind}")
    }
}
