package com.example.wastenot.data.repository

import com.example.wastenot.data.local.RefreshToken
import com.example.wastenot.data.local.RefreshTokenStore
import com.example.wastenot.data.local.TokenReadResult
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.data.network.WasteNotNetwork
import java.io.IOException
import java.time.ZoneId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AccountManagementTest {
    private lateinit var server: MockWebServer
    private lateinit var network: WasteNotNetwork
    private lateinit var credentials: AccountTokens
    private lateinit var sessions: SessionRepository
    private lateinit var accounts: RemoteAccountManagement
    private val purged = mutableListOf<String>()

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        network = requireNotNull(WasteNotNetwork.create(server.url("/v1/").toString(), allowLocalHttp = true))
        credentials = AccountTokens()
        sessions = SessionRepository(network, credentials)
        accounts = RemoteAccountManagement(network, sessions) { purged += it }
    }

    @After fun tearDown() {
        network.client.dispatcher.cancelAll()
        network.client.dispatcher.executorService.shutdown()
        network.client.connectionPool.evictAll()
        server.shutdown()
    }

    private suspend fun signIn() {
        val fixture = requireNotNull(javaClass.getResource("/contracts/auth-session.json")).readText()
        server.enqueue(json(200, fixture))
        assertTrue(sessions.login("fixture-a@example.com", "fixture-password-raw") is ApiResult.Success)
        server.takeRequest()
    }

    @Test fun wrongDeletionPasswordKeepsSessionAndPendingLocalData() = runTest {
        signIn()
        server.enqueue(error(403, "WRONG_PASSWORD"))
        val result = accounts.deleteAccount("wrong-password")
        assertTrue(result is ApiResult.Failure)
        assertTrue(sessions.state.value is SessionState.Authenticated)
        assertNotNull(credentials.token)
        assertTrue(purged.isEmpty())
        assertEquals(2, server.requestCount)
    }

    @Test fun confirmedDeletionPurgesOnlyAuthenticatedOwnerAndDropsCredentials() = runTest {
        signIn()
        val owner = (sessions.state.value as SessionState.Authenticated).user.id
        server.enqueue(MockResponse().setResponseCode(204))
        assertTrue(accounts.deleteAccount("fixture-password-raw") is ApiResult.Success)
        assertEquals(listOf(owner), purged)
        assertNull(credentials.token)
        assertEquals(SessionState.SignedOut(SignOutReason.ACCOUNT_DELETED), sessions.state.value)
        assertEquals(2, server.requestCount)
    }

    @Test fun committedDeletionWithPendingPhotoCleanupSignsOutWithoutClaimingSuccessOrRetryingAuth() = runTest {
        signIn()
        val owner = (sessions.state.value as SessionState.Authenticated).user.id
        server.enqueue(error(503, "ACCOUNT_DELETION_PENDING"))
        val result = accounts.deleteAccount("fixture-password-raw")
        assertTrue(result is ApiResult.Failure)
        assertEquals("ACCOUNT_DELETION_PENDING", (result as ApiResult.Failure).error.code)
        assertEquals(listOf(owner), purged)
        assertNull(credentials.token)
        assertEquals(SessionState.SignedOut(SignOutReason.ACCOUNT_DELETION_PENDING), sessions.state.value)
        assertEquals(2, server.requestCount)
    }

    @Test fun ordinaryServerFailureDoesNotDiscardUnsyncedOwnerData() = runTest {
        signIn()
        server.enqueue(error(503, "UNAVAILABLE"))
        assertTrue(accounts.deleteAccount("fixture-password-raw") is ApiResult.Failure)
        assertTrue(sessions.state.value is SessionState.Authenticated)
        assertNotNull(credentials.token)
        assertTrue(purged.isEmpty())
    }

    @Test fun failedLocalCleanupRemainsSignedOutAndReportsIncompleteCleanup() = runTest {
        signIn()
        val failing = RemoteAccountManagement(network, sessions) { throw IOException("disk unavailable") }
        server.enqueue(MockResponse().setResponseCode(204))
        val result = failing.deleteAccount("fixture-password-raw")
        assertEquals(ApiFailureKind.STORAGE, (result as ApiResult.Failure).error.kind)
        assertEquals(SessionState.SignedOut(SignOutReason.DELETION_CLEANUP_FAILED), sessions.state.value)
        assertNull(credentials.token)
    }

    @Test fun googleDeletionRejectionKeepsTheSessionAndPendingCleanupPurgesOnlyTheAuthenticatedOwner() = runTest {
        val fixture = requireNotNull(javaClass.getResource("/contracts/auth-session.json")).readText()
            .replace("\"timezone\":", "\"has_password\":false,\"timezone\":")
        server.enqueue(json(200, fixture))
        val owner = (sessions.googleLogin("fixture-sign-in", ZoneId.of("UTC"), null) as ApiResult.Success).value.id
        server.takeRequest()
        server.enqueue(error(403, "GOOGLE_REAUTH_FAILED"))
        val wrongAccount = accounts.deleteGoogleAccount("fixture-wrong") as ApiResult.Failure
        assertEquals("GOOGLE_REAUTH_FAILED", wrongAccount.error.code)
        assertEquals(2, server.requestCount)
        assertTrue(sessions.state.value is SessionState.Authenticated)
        assertNotNull(credentials.token)
        assertTrue(purged.isEmpty())
        server.takeRequest()

        server.enqueue(error(503, "ACCOUNT_DELETION_PENDING"))
        val pending = accounts.deleteGoogleAccount("fixture-fresh") as ApiResult.Failure
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/v1/me", request.path)
        assertEquals("Bearer fixture-access-old", request.getHeader("Authorization"))
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals(setOf("google_id_token"), body.keys)
        assertEquals("fixture-fresh", body["google_id_token"]?.jsonPrimitive?.content)
        assertEquals("ACCOUNT_DELETION_PENDING", pending.error.code)
        assertEquals(listOf(owner), purged)
        assertNull(credentials.token)
        assertEquals(SessionState.SignedOut(SignOutReason.ACCOUNT_DELETION_PENDING), sessions.state.value)
    }

    private fun json(status: Int, body: String) = MockResponse().setResponseCode(status)
        .setHeader("Content-Type", "application/json").setBody(body)
    private fun error(status: Int, code: String) = json(status, "{\"error\":{\"code\":\"$code\"}}")

    private class AccountTokens : RefreshTokenStore {
        var token: RefreshToken? = null
        override suspend fun read(): TokenReadResult = token?.let(TokenReadResult::Available) ?: TokenReadResult.Absent
        override suspend fun write(token: RefreshToken) { this.token = token }
        override suspend fun clear() { token = null }
    }
}
