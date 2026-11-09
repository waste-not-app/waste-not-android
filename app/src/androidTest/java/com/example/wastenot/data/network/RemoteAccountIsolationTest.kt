package com.example.wastenot.data.network

import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.BuildConfig
import com.example.wastenot.data.local.RefreshToken
import com.example.wastenot.data.local.RefreshTokenStore
import com.example.wastenot.data.local.TokenReadResult
import com.example.wastenot.data.repository.*
import com.example.wastenot.model.*
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

class RemoteAccountIsolationTest {
    @Test fun publicApiRejectsCrossAccountReadsAndWritesWithoutChangingEitherOwner() = runTest(timeout = 3.minutes) {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("verifyLiveAccountIsolation") == "true")
        assertTrue(BuildConfig.DEBUG)
        val base = "https://pred-autoscaler.bccdev.id/wastenot/api/v1/"
        assertEquals(base, BuildConfig.API_BASE_URL)
        val runId = UUID.fromString(requireNotNull(args.getString("fixtureRunId"))).toString()
        val network = requireNotNull(WasteNotNetwork.create(base))
        val first = Client(network)
        val second = Client(network)
        val evidence = JSONObject().put("run_id", runId)
        val steps = JSONArray()
        val output = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "qa/account-isolation/transport.json")
        fun record(step: String) {
            output.parentFile!!.mkdirs()
            output.writeText(evidence.put("passed_steps", steps.put(step)).toString(2))
        }
        try {
            val firstUser = success(first.session.register("Android isolation A", "android-isolation-a-$runId@example.test",
                first.password, ZoneId.of("Asia/Jakarta")))
            val secondUser = success(second.session.register("Android isolation B", "android-isolation-b-$runId@example.test",
                second.password, ZoneId.of("Asia/Jakarta")))
            assertNotEquals(firstUser.id, secondUser.id)
            evidence.put("first_user_id", firstUser.id).put("second_user_id", secondUser.id)
            record("two_distinct_real_accounts")
            val today = success(first.core.dashboard()).date
            val inputA = ActivityInput(WasteCategory.PLASTIC, DisposalMethod.RECYCLE, Quantity.Weight.of(250), today, "Owner A record")
            val inputB = ActivityInput(WasteCategory.PAPER, DisposalMethod.REDUCE, Quantity.Count.of(2), today, "Owner B record")
            val requestId = UUID.randomUUID().toString()
            val a = success(first.records.create(inputA, requestId)).log
            val b = success(second.records.create(inputB, UUID.randomUUID().toString())).log
            evidence.put("first_log_id", a.id).put("second_log_id", b.id)
            assertEquals(a, success(first.records.create(inputA, requestId)).log)
            assertEquals(1L, success(first.core.dashboard()).summary.activityCount)
            assertEquals(10, success(first.core.dashboard()).progress.xp)
            assertNull(success(second.core.dashboard()).summary.recyclingRatePercent)
            record("owned_records_and_idempotent_replay")

            val denials = JSONArray()
            suspend fun denied(label: String, response: ApiResult<*>) {
                assertTrue("Expected cross-account denial for $label", response is ApiResult.Failure)
                val error = (response as ApiResult.Failure).error
                assertEquals(ApiFailureKind.NOT_FOUND, error.kind)
                assertEquals(404, error.status)
                assertNotNull(error.requestId)
                denials.put(JSONObject().put("operation", label).put("status", error.status).put("code", error.code))
            }
            denied("b_reads_a", second.records.get(a.id))
            denied("b_updates_a", second.records.update(a.id, a.version, inputA.copy(notes = "Rejected cross-account edit")))
            denied("b_deletes_a", second.records.delete(a.id, a.version))
            denied("a_reads_b", first.records.get(b.id))
            denied("a_updates_b", first.records.update(b.id, b.version, inputB.copy(notes = "Rejected cross-account edit")))
            denied("a_deletes_b", first.records.delete(b.id, b.version))
            evidence.put("cross_account_denials", denials)
            assertEquals(a, success(first.records.get(a.id)))
            assertEquals(b, success(second.records.get(b.id)))
            assertEquals(listOf(a), success(first.records.load()))
            assertEquals(listOf(b), success(second.records.load()))
            assertEquals(QuantityTotals(250, 0), success(first.core.dashboard()).summary.handled)
            assertEquals(QuantityTotals(0, 0), success(second.core.dashboard()).summary.handled)
            assertEquals(QuantityTotals(0, 2), success(second.core.dashboard()).summary.avoided)
            record("six_cross_account_operations_are_404_and_owners_are_unchanged")

            val updated = success(first.records.update(a.id, a.version, inputA.copy(quantity = Quantity.Weight.of(300))))
            assertEquals(QuantityTotals(300, 0), success(first.core.dashboard()).summary.handled)
            assertEquals(10, success(first.core.dashboard()).progress.xp)
            assertEquals(b, success(second.records.get(b.id)))
            success(first.records.delete(updated.id, updated.version))
            assertEquals(0L, success(first.core.dashboard()).summary.activityCount)
            assertEquals(0, success(first.core.dashboard()).progress.xp)
            assertEquals(1L, success(second.core.dashboard()).summary.activityCount)
            assertEquals(10, success(second.core.dashboard()).progress.xp)
            record("owner_edit_delete_update_only_that_accounts_totals")

            var old: RequestCredential? = null
            success(first.session.authenticated { old = it; ApiResult.Success(Unit) })
            assertTrue(first.session.logout().serverRevoked)
            assertNull(first.token)
            val revoked = network.executor.execute({ it.toModel() }) { network.api.me(requireNotNull(old)) }
            assertTrue(revoked is ApiResult.Failure)
            assertEquals(ApiFailureKind.UNAUTHENTICATED, (revoked as ApiResult.Failure).error.kind)
            assertEquals(firstUser.id, success(first.session.login(firstUser.email, first.password)).id)
            assertEquals(0L, success(first.core.dashboard()).summary.activityCount)
            assertEquals(b, success(second.records.get(b.id)))
            record("logout_revokes_old_credential_and_relogin_restores_only_own_identity")
        } finally {
            first.session.logout()
            second.session.logout()
            network.client.dispatcher.cancelAll()
            network.client.connectionPool.evictAll()
            network.client.dispatcher.executorService.shutdown()
        }
    }

    private class Client(network: WasteNotNetwork) {
        val password = UUID.randomUUID().toString() + "Az9!"
        var token: RefreshToken? = null
        val session = SessionRepository(network, object : RefreshTokenStore {
            override suspend fun read(): TokenReadResult = token?.let(TokenReadResult::Available) ?: TokenReadResult.Absent
            override suspend fun write(token: RefreshToken) { this@Client.token = token }
            override suspend fun clear() { token = null }
        })
        val core = CoreRepository(network, session)
        val records = RemoteActivityStore(core)
    }

    private fun <T> success(result: ApiResult<T>): T {
        assertTrue("Expected a successful owned request", result is ApiResult.Success)
        return (result as ApiResult.Success).value
    }
}
