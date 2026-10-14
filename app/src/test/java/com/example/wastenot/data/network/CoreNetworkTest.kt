package com.example.wastenot.data.network

import com.example.wastenot.data.repository.CoreRepository
import com.example.wastenot.model.ActivityDateRange
import com.example.wastenot.model.DisposalMethod
import com.example.wastenot.model.Quantity
import com.example.wastenot.model.WasteCategory
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CoreNetworkTest {
    private lateinit var server: MockWebServer
    private lateinit var network: WasteNotNetwork
    private lateinit var repository: CoreRepository
    private val clock = MutableClock(Instant.parse("2026-09-21T00:00:00Z"))
    private val diagnostics = mutableListOf<ApiDiagnostic>()

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        network = requireNotNull(WasteNotNetwork.create(server.url("/v1/").toString(),
            accessToken = { "private-access-token" }, diagnostics = diagnostics::add,
            clock = clock, allowLocalHttp = true))
        repository = CoreRepository(network)
    }

    @After
    fun tearDown() {
        network.client.dispatcher.executorService.shutdown()
        network.client.connectionPool.evictAll()
        server.shutdown()
    }

    @Test
    fun metadataMapsCanonicalEnumsLimitsAndCapabilities() = runTest {
        server.enqueue(jsonResponse(fixture("metadata")))
        val result = repository.metadata() as ApiResult.Success
        assertEquals(WasteCategory.entries, result.value.categories)
        assertEquals(DisposalMethod.entries, result.value.methods)
        assertEquals(1..100_000, result.value.limits.weightGrams)
        assertEquals(12..128, result.value.limits.passwordLength)
        assertFalse(result.value.isEnabled("ai_scan"))
        assertFalse(result.value.isEnabled("unknown_feature"))
        assertNull(result.value.aiObjectCatalogVersion)
        assertEquals("/v1/metadata", server.takeRequest().path)
    }

    @Test
    fun listMapsBothQuantityKindsAndOpaquePaginationWithEncodedFilters() = runTest {
        server.enqueue(jsonResponse(fixture("waste-logs-page")))
        val result = repository.wasteLogs(WasteCategory.PLASTIC, DisposalMethod.RECYCLE,
            ActivityDateRange.lastDays(LocalDate.parse("2026-09-21"), 7), "page +/=") as ApiResult.Success
        assertEquals(Quantity.Weight.of(250), result.value[0].quantity)
        assertEquals(Quantity.Count.of(2), result.value[1].quantity)
        assertEquals(DisposalMethod.REDUCE, result.value[1].method)
        assertEquals(PageInfo("opaque-cursor-fixture", true), result.page)
        val url = requireNotNull(server.takeRequest().requestUrl)
        assertEquals("PLASTIC", url.queryParameter("category"))
        assertEquals("RECYCLE", url.queryParameter("method"))
        assertEquals("2026-09-15", url.queryParameter("from"))
        assertEquals("2026-09-21", url.queryParameter("to"))
        assertEquals("page +/=", url.queryParameter("cursor"))
        assertEquals("20", url.queryParameter("limit"))
    }

    @Test
    fun validationErrorsRetainFieldsAndRequestIdWithoutReturningServerMessage() = runTest {
        server.enqueue(jsonResponse(fixture("validation-error")).setResponseCode(422))
        val failure = (repository.metadata() as ApiResult.Failure).error
        assertEquals(ApiFailureKind.VALIDATION, failure.kind)
        assertEquals(422, failure.status)
        assertEquals("Berat minimal 1 gram.", failure.fields["weight_g"])
        assertEquals("fixture-request-id", failure.requestId)
    }

    @Test
    fun httpStatusesProduceDirectedFailuresWithoutAutomaticRetries() = runTest {
        listOf(
            Triple(400, "INVALID_REQUEST", ApiFailureKind.INVALID_REQUEST),
            Triple(401, "SESSION_EXPIRED", ApiFailureKind.UNAUTHENTICATED),
            Triple(404, "NOT_FOUND", ApiFailureKind.NOT_FOUND),
            Triple(409, "EMAIL_EXISTS", ApiFailureKind.EMAIL_EXISTS),
            Triple(409, "VERSION_CONFLICT", ApiFailureKind.VERSION_CONFLICT),
            Triple(409, "IDEMPOTENCY_CONFLICT", ApiFailureKind.IDEMPOTENCY_CONFLICT),
            Triple(410, "LOG_DELETED", ApiFailureKind.DELETED),
            Triple(428, "PRECONDITION_REQUIRED", ApiFailureKind.PRECONDITION_REQUIRED),
            Triple(500, "INTERNAL_ERROR", ApiFailureKind.SERVER),
            Triple(503, "UNAVAILABLE", ApiFailureKind.SERVER),
        ).forEach { (status, code, expected) ->
            server.enqueue(jsonResponse("""{"error":{"code":"$code","message":"private server detail"}}""")
                .setResponseCode(status).setHeader("X-Request-ID", "header-request-id"))
            val error = (repository.metadata() as ApiResult.Failure).error
            assertEquals(status.toString(), expected, error.kind)
            assertEquals(status, error.status)
            assertEquals("header-request-id", error.requestId)
            assertFalse(error.toString().contains("private server detail"))
        }
        assertEquals(10, server.requestCount)
    }

    @Test
    fun malformedJsonEnvelopeEnumQuantityTimezoneAndPageDoNotCrash() = runTest {
        listOf("not-json", "{}", "{\"data\":null}",
            fixture("metadata").replace("PLASTIC", "UNKNOWN"),
            fixture("metadata").replace("100000", "100001")).forEach { body ->
            server.enqueue(jsonResponse(body))
            assertEquals(ApiFailureKind.MALFORMED_RESPONSE, (repository.metadata() as ApiResult.Failure).error.kind)
        }
        listOf(
            fixture("waste-logs-page").replace("\"item_count\": null", "\"item_count\": 1"),
            fixture("waste-logs-page").replace("Asia/Jakarta", "Invalid/Zone"),
            fixture("waste-logs-page").replace("2026-09-21\"", "2026-02-30\""),
            fixture("waste-logs-page").replace("\"has_more\": true", "\"has_more\": false"),
        ).forEach { body ->
            server.enqueue(jsonResponse(body))
            assertEquals(ApiFailureKind.MALFORMED_RESPONSE, (repository.wasteLogs() as ApiResult.Failure).error.kind)
        }
    }

    @Test
    fun malformedHttpErrorKeepsStatusAndSafeRequestId() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("<private stack trace>")
            .setHeader("X-Request-ID", "request-500"))
        val error = (repository.metadata() as ApiResult.Failure).error
        assertEquals(ApiFailureKind.SERVER, error.kind)
        assertEquals(500, error.status)
        assertEquals("request-500", error.requestId)
        assertNull(error.code)
        assertTrue(error.fields.isEmpty())
    }

    @Test
    fun bearerIsLimitedToApiOriginAndPathAndRemovedFromExternalRequests() {
        MockWebServer().use { external ->
            external.start()
            server.enqueue(MockResponse())
            network.client.newCall(Request.Builder().url(server.url("/v1/metadata")).build()).execute().close()
            assertEquals("Bearer private-access-token", server.takeRequest().getHeader("Authorization"))
            external.enqueue(MockResponse())
            network.client.newCall(Request.Builder().url(external.url("/source"))
                .header("Authorization", "Bearer caller-token").header("Cookie", "private-cookie").build()).execute().close()
            val request = external.takeRequest()
            assertNull(request.getHeader("Authorization"))
            assertNull(request.getHeader("Cookie"))
            server.enqueue(MockResponse())
            network.client.newCall(Request.Builder().url(server.url("/source"))
                .header("Authorization", "Bearer caller-token").build()).execute().close()
            assertNull(server.takeRequest().getHeader("Authorization"))
        }
    }

    @Test
    fun publicAuthEndpointsNeverReceiveExistingBearerAndRedirectIsNotFollowed() {
        listOf("login", "register", "refresh").forEach { endpoint ->
            server.enqueue(MockResponse())
            network.client.newCall(Request.Builder().url(server.url("/v1/auth/$endpoint"))
                .post("fixture-password".toRequestBody()).build()).execute().close()
            assertNull(server.takeRequest().getHeader("Authorization"))
        }
        MockWebServer().use { external ->
            external.start()
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", external.url("/image")))
            network.client.newCall(Request.Builder().url(server.url("/v1/metadata")).build()).execute().use {
                assertEquals(302, it.code)
            }
            assertEquals(0, external.requestCount)
        }
    }

    @Test
    fun retryAfterSecondsBlocksEarlyRetryAndAllowsItAfterTheDeadline() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "60"))
        val first = (repository.metadata() as ApiResult.Failure).error
        assertEquals(ApiFailureKind.RATE_LIMITED, first.kind)
        assertEquals(clock.instant().plusSeconds(60), first.retryAt)
        assertEquals(ApiFailureKind.RATE_LIMITED, (repository.metadata() as ApiResult.Failure).error.kind)
        assertEquals(1, server.requestCount)
        clock.advance(Duration.ofSeconds(60))
        server.enqueue(jsonResponse(fixture("metadata")))
        assertTrue(repository.metadata() is ApiResult.Success)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun retryAfterSupportsHttpDateAndRejectsInvalidHeaders() {
        val now = clock.instant()
        assertEquals(now.plusSeconds(60), retryAfter("Mon, 21 Sep 2026 00:01:00 GMT", now))
        assertNull(retryAfter("-1", now))
        assertNull(retryAfter("bad", now))
        assertNull(retryAfter("Sun, 20 Sep 2026 00:00:00 GMT", now))
        assertEquals(Instant.MAX, retryAfter("999999999999999999999999999", now))
    }

    @Test
    fun writeIsNotReplayedFor503EvenWhenRetryAfterIsZero() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) =
                MockResponse().setResponseCode(503).setHeader("Retry-After", "0")
        }
        listOf("POST", "PUT", "PATCH", "DELETE").forEach { method ->
            network.client.newCall(Request.Builder().url(server.url("/v1/waste-logs"))
                .method(method, if (method == "DELETE") null else "private-notes".toRequestBody())
                .build()).execute().use {
                assertEquals(503, it.code)
            }
        }
        assertEquals(4, server.requestCount)
    }

    @Test
    fun listRequiresPaginationButEmptyPageIsARealSuccess() = runTest {
        server.enqueue(jsonResponse("""{"data":[]}"""))
        assertEquals(ApiFailureKind.MALFORMED_RESPONSE, (repository.wasteLogs() as ApiResult.Failure).error.kind)
        server.enqueue(jsonResponse("""{"data":[],"page":{"next_cursor":null,"has_more":false}}"""))
        val empty = repository.wasteLogs() as ApiResult.Success
        assertTrue(empty.value.isEmpty())
        assertEquals(PageInfo(null, false), empty.page)
    }

    @Test
    fun timeoutProducesADirectedFailure() = runTest {
        val timedNetwork = requireNotNull(WasteNotNetwork.create(server.url("/v1/").toString(),
            allowLocalHttp = true, callTimeoutSeconds = 1))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        try {
            assertEquals(ApiFailureKind.TIMEOUT,
                (CoreRepository(timedNetwork).metadata() as ApiResult.Failure).error.kind)
        } finally {
            timedNetwork.client.dispatcher.executorService.shutdown()
            timedNetwork.client.connectionPool.evictAll()
        }
    }

    @Test
    fun cancellationStopsAnInFlightHttpRequest() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val request = async(Dispatchers.Default) { repository.metadata() }
        assertNotNull(withContext(Dispatchers.IO) { server.takeRequest(10, TimeUnit.SECONDS) })
        request.cancelAndJoin()
        assertTrue(request.isCancelled)
    }

    @Test
    fun executorDoesNotSwallowCallerCancellation() = runTest {
        try {
            network.executor.execute<Unit, Unit>({ it }) { throw CancellationException("fixture cancellation") }
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
    }

    @Test
    fun disconnectedWriteIsNotReplayedAndDiagnosticsContainNoPayloadOrCredentials() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        assertThrows(IOException::class.java) {
            network.client.newCall(Request.Builder().url(server.url("/v1/waste-logs"))
                .post("fixture-password private-notes".toRequestBody()).build()).execute().close()
        }
        assertEquals(1, server.requestCount)
        assertEquals(1, diagnostics.size)
        assertNull(diagnostics[0].status)
        listOf("private-access-token", "private-notes", "fixture-password", "/v1/", "Authorization")
            .forEach { assertFalse(diagnostics.toString().contains(it)) }
        assertNull(safeRequestId("email@example.com"))
        assertNull(safeRequestId("a".repeat(81)))
    }

    @Test
    fun missingEndpointIsAnExplicitFailureAndSharedHttpIsRejected() = runTest {
        assertNull(WasteNotNetwork.create(""))
        assertEquals(ApiFailureKind.NOT_CONFIGURED, (CoreRepository(null).metadata() as ApiResult.Failure).error.kind)
        assertThrows(IllegalArgumentException::class.java) { WasteNotNetwork.create("http://example.com/v1/") }
        assertThrows(IllegalArgumentException::class.java) { WasteNotNetwork.create("https://user:password@example.com/v1/") }
    }

    private fun fixture(name: String): String = requireNotNull(javaClass.getResource("/contracts/$name.json")).readText()
    private fun jsonResponse(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
}

private class MutableClock(private var now: Instant) : Clock() {
    fun advance(duration: Duration) { now = now.plus(duration) }
    override fun instant(): Instant = now
    override fun getZone(): ZoneId = ZoneId.of("UTC")
    override fun withZone(zone: ZoneId): Clock = Clock.fixed(now, zone)
}
