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
import com.example.wastenot.model.Quantity
import com.example.wastenot.model.WasteCategory
import java.io.File
import java.time.LocalDate
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
        assertFalse(BuildConfig.USE_DUMMY_DATA)
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

            val input = ActivityInput(WasteCategory.PLASTIC, DisposalMethod.RECYCLE,
                Quantity.Weight.of(250), LocalDate.now(user.timezone), "Isolated Android transport probe")
            val requestId = UUID.randomUUID().toString()
            val created = success(store.create(input, requestId))
            assertTrue(created.created)
            val replay = success(store.create(input, requestId))
            assertFalse(replay.created)
            assertEquals(created.log, replay.log)
            assertEquals(created.log, success(store.get(created.log.id)))
            assertEquals(listOf(created.log), success(store.load()))
            record("create_replay_detail_list")

            val counted = input.copy(quantity = Quantity.Count.of(3))
            val updated = success(store.update(created.log.id, created.log.version, counted))
            assertEquals(Quantity.Count.of(3), updated.quantity)
            assertTrue(updated.version > created.log.version)
            failure(ApiFailureKind.VERSION_CONFLICT, store.update(updated.id, created.log.version, counted))
            failure(ApiFailureKind.VERSION_CONFLICT, store.delete(updated.id, created.log.version))
            record("quantity_switch_and_stale_version_conflicts")

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
