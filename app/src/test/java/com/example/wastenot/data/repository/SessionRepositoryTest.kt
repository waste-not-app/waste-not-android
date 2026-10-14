package com.example.wastenot.data.repository

import com.example.wastenot.data.local.RefreshToken
import com.example.wastenot.data.local.RefreshTokenStore
import com.example.wastenot.data.local.TokenReadResult
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.network.WasteNotNetwork
import com.example.wastenot.data.network.dto.DataEnvelope
import com.example.wastenot.data.network.dto.LoginDto
import com.example.wastenot.data.network.dto.RegisterDto
import com.example.wastenot.data.network.dto.RefreshDto
import com.example.wastenot.data.network.dto.SessionDto
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Tag

class SessionRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var network: WasteNotNetwork
    private lateinit var store: MemoryTokenStore
    private lateinit var session: SessionRepository
    private lateinit var core: CoreRepository
    private val login = LoginDto("fixture-a@example.com", "  fixture-password-raw  ")

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        network = requireNotNull(WasteNotNetwork.create(server.url("/v1/").toString(), allowLocalHttp = true))
        store = MemoryTokenStore()
        session = SessionRepository(network, store)
        core = CoreRepository(network, session)
    }

    @After
    fun tearDown() {
        network.client.dispatcher.cancelAll()
        network.client.dispatcher.executorService.shutdown()
        network.client.connectionPool.evictAll()
        server.shutdown()
    }

    @Test
    fun loginKeepsAccessOnlyInMemoryAndPreservesRawPassword() = runTest {
        server.enqueue(json(fixture("auth-session")))
        val result = session.login(" FIXTURE-A@example.com ", login.password) as ApiResult.Success
        assertEquals(USER_A, result.value.id)
        assertEquals(RefreshToken.of("fixture-refresh-old"), store.token)
        assertEquals(listOf(RefreshToken.of("fixture-refresh-old")), store.writes)
        assertTrue(session.state.value is SessionState.Authenticated)
        val request = server.takeRequest()
        assertNull(request.getHeader("Authorization"))
        assertEquals(login.password, Json.parseToJsonElement(request.body.readUtf8()).jsonObject["password"]?.jsonPrimitive?.content)
        assertFalse(session.state.value.toString().contains("fixture-access-old"))
        assertFalse(session.state.value.toString().contains("fixture-refresh-old"))
    }

    @Test
    fun registerUsesServerIdentityAndNeverSendsConfirmationPassword() = runTest {
        server.enqueue(json(fixture("auth-session")).setResponseCode(201))
        assertTrue(session.register(" Nama ", " FIXTURE-A@example.com ", "fixture-password", java.time.ZoneId.of("Asia/Jakarta")) is ApiResult.Success)
        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals(setOf("name", "email", "password", "timezone"), body.keys)
        assertEquals("Nama", body["name"]?.jsonPrimitive?.content)
        assertEquals("fixture-a@example.com", body["email"]?.jsonPrimitive?.content)
        assertEquals(USER_A, (session.state.value as SessionState.Authenticated).user.id)
    }

    @Test
    fun bootstrapWithoutCredentialAndWithInvalidatedCredentialDoesNotUseNetwork() = runTest {
        assertEquals(SessionState.SignedOut(), session.bootstrap())
        store.saved.set(TokenReadResult.Invalidated)
        assertEquals(SessionState.SignedOut(SignOutReason.INVALID_CREDENTIAL), session.bootstrap())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun knownOfflineBootstrapRetainsEncryptedCredentialWithoutPublishingIdentity() = runTest {
        store.saved.set(TokenReadResult.Available(RefreshToken.of("fixture-refresh-old")))
        assertTrue(session.bootstrap(networkAvailable = false) is SessionState.ConnectionRequired)
        assertNotNull(store.token)
        assertEquals(0, server.requestCount)
        assertEquals(ApiFailureKind.UNAUTHENTICATED, (core.metadata() as ApiResult.Failure).error.kind)
    }

    @Test
    fun offlineRetryAfterKnownRotationDoesNotCallMeOrRotateAgain() = runTest {
        store.saved.set(TokenReadResult.Available(RefreshToken.of("fixture-refresh-old")))
        server.enqueue(json(fixture("auth-refresh")))
        server.enqueue(MockResponse().setResponseCode(503))
        assertTrue(session.bootstrap() is SessionState.ConnectionRequired)
        assertEquals(RefreshToken.of("fixture-refresh-new"), store.token)
        server.enqueue(json(fixture("me")))
        assertTrue(session.bootstrap(networkAvailable = false) is SessionState.ConnectionRequired)
        assertEquals(2, server.requestCount)
        assertTrue(session.bootstrap() is SessionState.Authenticated)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun uuidCaseDoesNotChangeTheIdentityOrSessionAcrossRotation() = runTest {
        val user = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"
        val sid = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        server.enqueue(json(fixture("auth-session").replace(USER_A, user.uppercase())
            .replace("00000000-0000-4000-8000-000000000099", sid.uppercase())))
        assertTrue(session.login(login.email, login.password) is ApiResult.Success)
        assertEquals(user, (session.state.value as SessionState.Authenticated).user.id)
        server.enqueue(unauthorized())
        server.enqueue(json(fixture("auth-refresh").replace("00000000-0000-4000-8000-000000000099", sid)))
        server.enqueue(json(fixture("metadata")))
        assertTrue(core.metadata() is ApiResult.Success)
        assertEquals(user, (session.state.value as SessionState.Authenticated).user.id)
    }

    @Test
    fun bootstrapPublishesIdentityOnlyAfterMeSucceeds() = runTest {
        store.saved.set(TokenReadResult.Available(RefreshToken.of("fixture-refresh-old")))
        val arrived = CountDownLatch(1)
        val release = CountDownLatch(1)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/v1/auth/refresh" -> json(fixture("auth-refresh"))
                "/v1/me" -> {
                    arrived.countDown()
                    check(release.await(10, TimeUnit.SECONDS))
                    json(fixture("me"))
                }
                else -> MockResponse().setResponseCode(500)
            }
        }
        val bootstrap = async(Dispatchers.Default) { session.bootstrap() }
        assertTrue(withContext(Dispatchers.IO) { arrived.await(10, TimeUnit.SECONDS) })
        assertEquals(SessionState.Checking, session.state.value)
        assertEquals(RefreshToken.of("fixture-refresh-new"), store.token)
        assertEquals(ApiFailureKind.UNAUTHENTICATED, (core.metadata() as ApiResult.Failure).error.kind)
        release.countDown()
        assertTrue(bootstrap.await() is SessionState.Authenticated)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun concurrent401sShareOneRotationAndEachRetryUsesTheNewToken() = runTest {
        signIn()
        val original = AtomicInteger()
        val retried = AtomicInteger()
        val rotations = AtomicInteger()
        val arrived = CountDownLatch(3)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/v1/metadata" -> if (request.getHeader("Authorization") == "Bearer fixture-access-old") {
                    original.incrementAndGet(); arrived.countDown()
                    check(arrived.await(10, TimeUnit.SECONDS)); unauthorized()
                } else {
                    assertEquals("Bearer fixture-access-new", request.getHeader("Authorization"))
                    retried.incrementAndGet(); json(fixture("metadata"))
                }
                "/v1/auth/refresh" -> {
                    rotations.incrementAndGet()
                    assertNull(request.getHeader("Authorization"))
                    json(fixture("auth-refresh"))
                }
                else -> MockResponse().setResponseCode(500)
            }
        }
        val results = supervisorScope { List(3) { async(Dispatchers.Default) { core.metadata() } }.awaitAll() }
        assertTrue(results.all { it is ApiResult.Success })
        assertEquals(1, rotations.get())
        assertEquals(3, original.get()); assertEquals(3, retried.get())
        assertEquals(RefreshToken.of("fixture-refresh-new"), store.token)
    }

    @Test
    fun second401EndsSessionWithoutAnotherRefresh() = runTest {
        signIn()
        val rotations = AtomicInteger()
        val reads = AtomicInteger()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/v1/auth/refresh" -> { rotations.incrementAndGet(); json(fixture("auth-refresh")) }
                else -> { reads.incrementAndGet(); unauthorized() }
            }
        }
        assertEquals(ApiFailureKind.UNAUTHENTICATED, (core.metadata() as ApiResult.Failure).error.kind)
        assertEquals(1, rotations.get()); assertEquals(2, reads.get())
        assertEquals(SessionState.SignedOut(SignOutReason.EXPIRED), session.state.value)
        assertNull(store.token)
    }

    @Test
    fun revokedRefreshClearsOldCredentialWithoutLoop() = runTest {
        signIn()
        server.enqueue(unauthorized()); server.enqueue(unauthorized())
        assertTrue(core.metadata() is ApiResult.Failure)
        assertNull(store.token)
        assertEquals(SessionState.SignedOut(SignOutReason.EXPIRED), session.state.value)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun uncertainRefreshTimeoutClearsCredential() = runTest {
        val timed = requireNotNull(WasteNotNetwork.create(server.url("/v1/").toString(),
            allowLocalHttp = true, callTimeoutSeconds = 1))
        store.saved.set(TokenReadResult.Available(RefreshToken.of("fixture-refresh-old")))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        try {
            val repository = SessionRepository(timed, store)
            assertEquals(SessionState.SignedOut(SignOutReason.ROTATION_UNCERTAIN), repository.bootstrap())
            assertNull(store.token)
            assertTrue(server.requestCount <= 1)
        } finally {
            timed.client.dispatcher.cancelAll(); timed.client.dispatcher.executorService.shutdown()
            timed.client.connectionPool.evictAll()
        }
    }

    @Test
    fun nonRotatedRefreshTokenIsRejectedRatherThanRetried() = runTest {
        signIn()
        server.enqueue(unauthorized())
        server.enqueue(json(fixture("auth-refresh").replace("fixture-refresh-new", "fixture-refresh-old")))
        assertTrue(core.metadata() is ApiResult.Failure)
        assertEquals(SessionState.SignedOut(SignOutReason.ROTATION_UNCERTAIN), session.state.value)
        assertNull(store.token)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun rotationCanUpdateSessionIdWithoutChangingAccountLifetime() = runTest {
        signIn()
        val initialEpoch = (session.state.value as SessionState.Authenticated).epoch
        server.enqueue(unauthorized())
        server.enqueue(json(fixture("auth-refresh").replace("00000000-0000-4000-8000-000000000099",
            "00000000-0000-4000-8000-000000000088")))
        server.enqueue(json(fixture("metadata")))
        assertTrue(core.metadata() is ApiResult.Success)
        val state = session.state.value as SessionState.Authenticated
        assertEquals(USER_A, state.user.id)
        assertEquals(initialEpoch, state.epoch)
        assertEquals(RefreshToken.of("fixture-refresh-new"), store.token)
        assertEquals(4, server.requestCount)
    }

    @Test
    fun refreshCannotReplaceTheVerifiedAccount() = runTest {
        signIn()
        server.enqueue(unauthorized())
        server.enqueue(json(fixture("auth-session").replace(USER_A, USER_B)
            .replace("fixture-access-old", "fixture-access-new").replace("fixture-refresh-old", "fixture-refresh-new")))
        assertTrue(core.metadata() is ApiResult.Failure)
        assertEquals(SessionState.SignedOut(SignOutReason.ROTATION_UNCERTAIN), session.state.value)
        assertNull(store.token)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun loginWithoutAnIssuedIdentityCannotPublishASession() = runTest {
        server.enqueue(json(fixture("auth-refresh")))
        assertEquals(ApiFailureKind.MALFORMED_RESPONSE, (session.login(login.email, login.password) as ApiResult.Failure).error.kind)
        assertEquals(SessionState.SignedOut(), session.state.value)
        assertNull(store.token)
        assertTrue(store.writes.isEmpty())
    }

    @Test
    fun simultaneousLoginDoesNotCreateAnotherServerSession() = runTest {
        val arrived = CountDownLatch(1)
        val release = CountDownLatch(1)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                arrived.countDown()
                check(release.await(10, TimeUnit.SECONDS))
                return json(fixture("auth-session"))
            }
        }
        val first = async(Dispatchers.Default) { session.login(login.email, login.password) }
        assertTrue(withContext(Dispatchers.IO) { arrived.await(10, TimeUnit.SECONDS) })
        assertEquals(ApiFailureKind.BUSY, (session.login(login.email, login.password) as ApiResult.Failure).error.kind)
        assertEquals(1, server.requestCount)
        release.countDown()
        assertTrue(first.await() is ApiResult.Success)
    }

    @Test
    fun malformedRefreshClearsCredentialAndCannotCreateAPhantomSession() = runTest {
        signIn()
        server.enqueue(unauthorized()); server.enqueue(json("{\"data\":{}}"))
        assertTrue(core.metadata() is ApiResult.Failure)
        assertNull(store.token)
        assertEquals(SessionState.SignedOut(SignOutReason.ROTATION_UNCERTAIN), session.state.value)
    }

    @Test
    fun logoutCancelsAccountRequestsAndRevokesWithTheCapturedCredential() = runTest {
        signIn()
        val arrived = CountDownLatch(1)
        val cleanup = CopyOnWriteArrayList<Pair<String, Long>>()
        val observedBearer = AtomicReference<String?>()
        val owned = SessionRepository(network, MemoryTokenStore()) { user, epoch -> cleanup.add(user to epoch) }
        server.enqueue(json(fixture("auth-session")))
        assertTrue(owned.login(login.email, login.password) is ApiResult.Success)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/v1/metadata" -> { arrived.countDown(); MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE) }
                "/v1/auth/logout" -> {
                    observedBearer.set(request.getHeader("Authorization")); MockResponse().setResponseCode(204)
                }
                else -> MockResponse().setResponseCode(500)
            }
        }
        val pending = async(Dispatchers.Default) { CoreRepository(network, owned).metadata() }
        assertTrue(withContext(Dispatchers.IO) { arrived.await(10, TimeUnit.SECONDS) })
        assertEquals(LogoutResult(true, true), owned.logout())
        pending.join()
        assertTrue(pending.isCancelled)
        assertEquals("Bearer fixture-access-old", observedBearer.get())
        assertEquals(listOf(USER_A), cleanup.map { it.first })
        assertEquals(SessionState.SignedOut(SignOutReason.LOGOUT_CONFIRMED), owned.state.value)
    }

    @Test
    fun disconnectedLogoutWipesLocalCredentialWithoutClaimingRevocation() = runTest {
        signIn()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        assertEquals(LogoutResult(true, false), session.logout())
        assertNull(store.token)
        assertEquals(SessionState.SignedOut(SignOutReason.LOGOUT_LOCAL_ONLY), session.state.value)
    }

    @Test
    fun signingIntoTheSameUserAgainCreatesANewAccountLifetime() = runTest {
        signIn()
        val first = session.state.value as SessionState.Authenticated
        server.enqueue(MockResponse().setResponseCode(204))
        session.logout()
        server.takeRequest()
        signIn()
        val second = session.state.value as SessionState.Authenticated
        assertEquals(first.user, second.user)
        assertTrue(second.epoch > first.epoch)
    }

    @Test
    fun lateRotationCannotRestoreAccountAOrEraseAccountB() = runTest {
        signIn()
        val writing = CompletableDeferred<Unit>()
        val finishWrite = CompletableDeferred<Unit>()
        store.blockedWrite = RefreshToken.of("fixture-refresh-new")
        store.writing = writing; store.finishWrite = finishWrite
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/v1/metadata" -> unauthorized()
                "/v1/auth/refresh" -> json(fixture("auth-refresh"))
                "/v1/auth/logout" -> MockResponse().setResponseCode(204)
                "/v1/auth/login" -> json(accountB())
                else -> MockResponse().setResponseCode(500)
            }
        }
        supervisorScope {
            val old = async(Dispatchers.Default) { core.metadata() }
            writing.await()
            val logout = async(Dispatchers.Default) { session.logout() }
            withContext(Dispatchers.Default) {
                withTimeout(10_000) { session.state.first { it is SessionState.SignedOut } }
            }
            val next = async(Dispatchers.Default) { session.login("fixture-b@example.com", "fixture-password") }
            finishWrite.complete(Unit)
            old.join(); logout.await()
            assertTrue(next.await() is ApiResult.Success)
            assertTrue(old.isCancelled)
        }
        assertEquals(USER_B, (session.state.value as SessionState.Authenticated).user.id)
        assertEquals(RefreshToken.of("fixture-refresh-b"), store.token)
    }

    @Test
    fun canceledLoginCannotPublishCredentialsAfterStorageFinishes() = runTest {
        store.blockedWrite = RefreshToken.of("fixture-refresh-old")
        store.writing = CompletableDeferred()
        store.finishWrite = CompletableDeferred()
        server.enqueue(json(fixture("auth-session")))
        val pending = async(Dispatchers.Default) { session.login(login.email, login.password) }
        store.writing.await()
        pending.cancel()
        store.finishWrite.complete(Unit)
        pending.join()
        assertTrue(pending.isCancelled)
        assertNull(store.token)
        assertEquals(SessionState.SignedOut(SignOutReason.ROTATION_UNCERTAIN), session.state.value)
    }

    @Test
    fun storageFailureDoesNotPublishSuccessfulLoginOrLeaveTokens() = runTest {
        store.failWrites = true
        server.enqueue(json(fixture("auth-session")))
        assertEquals(ApiFailureKind.STORAGE, (session.login(login.email, login.password) as ApiResult.Failure).error.kind)
        assertEquals(SessionState.SignedOut(SignOutReason.STORAGE_UNAVAILABLE), session.state.value)
        assertNull(store.token)
    }

    @Test
    fun bootstrapRejectsAnIdentityDifferentFromTheRefreshResponse() = runTest {
        store.saved.set(TokenReadResult.Available(RefreshToken.of("fixture-refresh-prior")))
        server.enqueue(json(fixture("auth-session")))
        server.enqueue(json(fixture("me").replace(USER_A, USER_B)))
        assertEquals(SessionState.SignedOut(SignOutReason.INVALID_CREDENTIAL), session.bootstrap())
        assertNull(store.token)
    }

    @Test
    fun writeRetryReusesExactlyTheSamePayloadAndRequestId() = runTest {
        signIn()
        val api = Retrofit.Builder().baseUrl(server.url("/v1/")).client(network.client)
            .addConverterFactory(Json.asConverterFactory("application/json".toMediaType()))
            .build().create(FixtureWriteApi::class.java)
        val bodies = CopyOnWriteArrayList<String>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/v1/auth/refresh" -> json(fixture("auth-refresh"))
                else -> {
                    bodies.add(request.body.readUtf8())
                    if (bodies.size == 1) unauthorized() else json("{\"data\":{\"id\":\"fixture-created-id\"}}").setResponseCode(201)
                }
            }
        }
        val payload = Json.parseToJsonElement("""{"client_request_id":"00000000-0000-4000-8000-000000000011","category":"PLASTIC","method":"RECYCLE","quantity_kind":"WEIGHT","weight_g":250,"item_count":null,"activity_date":"2026-09-21","notes":"fixture notes"}""").jsonObject
        val result = session.authenticated { credential ->
            network.executor.execute({ it }) { api.create(credential, payload) }
        }
        assertTrue(result is ApiResult.Success)
        assertEquals(2, bodies.size)
        assertEquals(bodies[0], bodies[1])
    }

    @Test
    fun credentialDtosAndTokensNeverExposeTheirValuesInToString() {
        val dto = Json.decodeFromString<DataEnvelope<SessionDto>>(fixture("auth-session")).data
        listOf(login.toString(), RegisterDto("name", "private-email", "private-password", "UTC").toString(),
            RefreshDto("private-refresh").toString(), dto.toString(), dto.toModel().toString(),
            RequestCredential(AccessToken.of("private-access"), 1, 1).toString()).forEach { text ->
            listOf("fixture-access-old", "fixture-refresh-old", login.password, "private-password", "private-refresh", "private-access")
                .forEach { assertFalse(text, text.contains(it)) }
        }
    }

    private suspend fun signIn() {
        server.enqueue(json(fixture("auth-session")))
        assertTrue(session.login(login.email, login.password) is ApiResult.Success)
        server.takeRequest()
    }

    private fun fixture(name: String) = requireNotNull(javaClass.getResource("/contracts/$name.json")).readText()
    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
    private fun unauthorized() = json("{\"error\":{\"code\":\"SESSION_EXPIRED\"}}").setResponseCode(401)
    private fun accountB() = fixture("auth-session").replace(USER_A, USER_B)
        .replace("00000000-0000-4000-8000-000000000099", "00000000-0000-4000-8000-000000000088")
        .replace("fixture-access-old", "fixture-access-b").replace("fixture-refresh-old", "fixture-refresh-b")
        .replace("Fixture A", "Fixture B").replace("fixture-a@example.com", "fixture-b@example.com")

    companion object {
        private const val USER_A = "00000000-0000-4000-8000-000000000001"
        private const val USER_B = "00000000-0000-4000-8000-000000000002"
    }
}

private interface FixtureWriteApi {
    @POST("waste-logs")
    suspend fun create(@Tag credential: RequestCredential, @Body body: JsonObject): Response<DataEnvelope<Map<String, String>>>
}

private class MemoryTokenStore : RefreshTokenStore {
    val saved = AtomicReference<TokenReadResult>(TokenReadResult.Absent)
    val writes = CopyOnWriteArrayList<RefreshToken>()
    val token: RefreshToken? get() = (saved.get() as? TokenReadResult.Available)?.token
    @Volatile var failWrites = false
    @Volatile var blockedWrite: RefreshToken? = null
    lateinit var writing: CompletableDeferred<Unit>
    lateinit var finishWrite: CompletableDeferred<Unit>

    override suspend fun read(): TokenReadResult = saved.get().also {
        if (it == TokenReadResult.Invalidated) saved.compareAndSet(it, TokenReadResult.Absent)
    }

    override suspend fun write(token: RefreshToken) {
        if (failWrites) throw IOException("Fixture storage failure")
        if (blockedWrite == token) withContext(NonCancellable) {
            writing.complete(Unit); finishWrite.await()
            saved.set(TokenReadResult.Available(token)); writes.add(token)
        } else {
            saved.set(TokenReadResult.Available(token)); writes.add(token)
        }
    }

    override suspend fun clear() { saved.set(TokenReadResult.Absent) }
}
