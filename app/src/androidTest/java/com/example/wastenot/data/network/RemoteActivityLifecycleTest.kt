package com.example.wastenot.data.network

import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.BuildConfig
import com.example.wastenot.data.local.RefreshToken
import com.example.wastenot.data.local.RefreshTokenStore
import com.example.wastenot.data.local.TokenReadResult
import com.example.wastenot.data.repository.ActivityInput
import com.example.wastenot.data.repository.CoreRepository
import com.example.wastenot.data.repository.RemoteActivityStore
import com.example.wastenot.data.repository.RequestCredential
import com.example.wastenot.data.repository.SessionRepository
import com.example.wastenot.data.repository.SessionState
import com.example.wastenot.model.DisposalMethod
import com.example.wastenot.model.ActivityDateRange
import com.example.wastenot.model.Quantity
import com.example.wastenot.model.QuantityTotals
import com.example.wastenot.model.WasteCategory
import java.io.File
import java.time.ZoneId
import java.util.UUID
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class RemoteActivityLifecycleTest {
    @Test fun isolatedAccountCompletesVersionedRecordLifecycle() = runTest(timeout = 2.minutes) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("verifyLiveFixture") == "true")
        assertTrue(BuildConfig.DEBUG)
        assertTrue((androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as com.example.wastenot.WasteNotApplication).container.accountSession is com.example.wastenot.data.repository.RemoteAccountSession)
        val runId = UUID.fromString(requireNotNull(args.getString("fixtureRunId"))).toString()
        val base = requireNotNull(args.getString("recordsTestBaseUrl"))
        // The host driver owns this loopback SSH tunnel and removes only this fixture account.
        require(base == "http://10.0.2.2:18088/v1/")
        val network = requireNotNull(WasteNotNetwork.create(base, allowLocalHttp = BuildConfig.DEBUG))
        var saved: RefreshToken? = null
        val tokens = object : RefreshTokenStore {
            override suspend fun read(): TokenReadResult = saved?.let(TokenReadResult::Available) ?: TokenReadResult.Absent
            override suspend fun write(token: RefreshToken) { saved = token }
            override suspend fun clear() { saved = null }
        }
        val session = SessionRepository(network, tokens)
        val core = CoreRepository(network, session)
        val store = RemoteActivityStore(core)
        val email = "android-$runId@example.test"
        val name = "Android transport probe"
        val password = UUID.randomUUID().toString() + "Az9!"
        val evidence = JSONObject().put("run_id", runId).put("email", email).put("name", name)
        val steps = JSONArray()
        val output = File(instrumentation.targetContext.cacheDir, "qa/backend/records-fixture.json")
        fun record(step: String) {
            steps.put(step)
            output.parentFile!!.mkdirs()
            output.writeText(evidence.put("passed_steps", steps).toString(2))
        }
        try {
            val user = success(session.register(name, email, password, ZoneId.of("Asia/Jakarta")))
            evidence.put("user_id", user.id)
            record("register")
            assertTrue(session.logout().serverRevoked)
            assertNull(saved)
            assertEquals(user.id, success(session.login(email, password)).id)
            record("logout_and_login")
            success(core.metadata())
            record("authenticated_metadata")

            val emptyDashboard = success(core.dashboard())
            val range = ActivityDateRange.lastDays(emptyDashboard.date, 2)
            assertEquals(user.timezone, emptyDashboard.timezone)
            assertTrue(emptyDashboard.recentLogs.isEmpty())
            assertEquals(0L, emptyDashboard.summary.activityCount)
            assertNull(emptyDashboard.summary.recyclingRatePercent)
            assertEquals(2, success(core.statistics(range)).daily.size)
            record("empty_server_dashboard_and_statistics")

            val input = ActivityInput(WasteCategory.PLASTIC, DisposalMethod.RECYCLE,
                Quantity.Weight.of(250), emptyDashboard.date, "Isolated Android transport probe")
            val requestId = UUID.randomUUID().toString()
            val created = success(store.create(input, requestId))
            assertTrue(created.created)
            val replay = success(store.create(input, requestId))
            assertFalse(replay.created)
            assertEquals(created.log, replay.log)
            assertEquals(created.log, success(store.get(created.log.id)))
            assertEquals(listOf(created.log), success(store.load()))
            record("create_replay_detail_list")

            val dashboard = success(core.dashboard())
            assertEquals(QuantityTotals(250, 0), dashboard.summary.handled)
            assertEquals(listOf(created.log), dashboard.recentLogs)
            assertEquals(10, dashboard.progress.xp)
            val period = success(core.statistics(range))
            assertEquals(QuantityTotals(250, 0), period.summary.handled)
            assertEquals(1L, period.methods[DisposalMethod.RECYCLE])
            assertEquals(QuantityTotals(250, 0), period.categories[WasteCategory.PLASTIC])
            assertEquals(QuantityTotals(0, 0), period.daily.first().handled)
            assertEquals(QuantityTotals(250, 0), period.daily.last().handled)
            record("created_record_updates_server_aggregates")

            val counted = input.copy(quantity = Quantity.Count.of(3))
            val updated = success(store.update(created.log.id, created.log.version, counted))
            assertEquals(Quantity.Count.of(3), updated.quantity)
            assertTrue(updated.version > created.log.version)
            failure(ApiFailureKind.VERSION_CONFLICT, store.update(updated.id, created.log.version, counted))
            failure(ApiFailureKind.VERSION_CONFLICT, store.delete(updated.id, created.log.version))
            record("quantity_switch_and_stale_version_conflicts")
            assertEquals(QuantityTotals(0, 3), success(core.dashboard()).summary.handled)
            assertEquals(QuantityTotals(0, 3), success(core.statistics(range)).summary.handled)
            record("quantity_switch_updates_server_aggregates")

            suspend fun progress() = success(session.authenticated { credential ->
                network.executor.execute({ it.toModel() }) { network.api.me(credential) }
            }).progress ?: error("Server omitted progress")
            assertEquals(1, progress().activeLogCount)
            assertEquals(10, progress().xp)
            success(store.delete(updated.id, updated.version))
            failure(ApiFailureKind.NOT_FOUND, store.get(updated.id))
            failure(ApiFailureKind.DELETED, store.create(input, requestId))
            success(store.delete(updated.id, updated.version))
            assertTrue(success(store.load()).isEmpty())
            assertEquals(0, progress().activeLogCount)
            assertEquals(0, progress().xp)
            record("delete_replay_and_server_progress")
            val deletedDashboard = success(core.dashboard())
            assertEquals(QuantityTotals(0, 0), deletedDashboard.summary.handled)
            assertTrue(deletedDashboard.recentLogs.isEmpty())
            assertEquals(0, deletedDashboard.progress.xp)
            val deletedPeriod = success(core.statistics(range))
            assertEquals(0L, deletedPeriod.summary.activityCount)
            assertNull(deletedPeriod.summary.recyclingRatePercent)
            assertTrue(deletedPeriod.daily.all { it.handled == QuantityTotals(0, 0) })
            record("delete_clears_server_aggregates")

            var old: RequestCredential? = null
            success(session.authenticated { credential -> old = credential; ApiResult.Success(Unit) })
            val logout = session.logout()
            assertTrue(logout.localCleared && logout.serverRevoked)
            assertNull(saved)
            assertTrue(session.state.value is SessionState.SignedOut)
            failure(ApiFailureKind.UNAUTHENTICATED, network.executor.execute({ it.toModel() }) {
                network.api.me(requireNotNull(old))
            })
            record("logout_revokes_session")
        } finally {
            session.logout()
            network.client.dispatcher.cancelAll()
            network.client.connectionPool.evictAll()
            network.client.dispatcher.executorService.shutdown()
        }
    }

    private fun <T> success(result: ApiResult<T>): T {
        assertTrue("Expected success: $result", result is ApiResult.Success)
        return (result as ApiResult.Success).value
    }

    private fun failure(kind: ApiFailureKind, result: ApiResult<*>) {
        assertTrue("Expected $kind: $result", result is ApiResult.Failure)
        assertEquals(kind, (result as ApiResult.Failure).error.kind)
    }
}
