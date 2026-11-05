package com.example.wastenot.data.repository

import com.example.wastenot.data.local.RefreshToken
import com.example.wastenot.data.local.RefreshTokenStore
import com.example.wastenot.data.local.TokenReadResult
import com.example.wastenot.data.network.*
import com.example.wastenot.model.*
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RemoteActivityStoreTest {
    private lateinit var server: MockWebServer
    private lateinit var network: WasteNotNetwork
    private lateinit var store: RemoteActivityStore
    private val id = "12345678-9abc-4def-8123-456789abcdef"
    private val requestId = "00000000-0000-4000-8000-000000000011"
    private val input = ActivityInput(WasteCategory.PLASTIC, DisposalMethod.RECYCLE,
        Quantity.Weight.of(250), LocalDate.parse("2026-09-21"), "Catatan pengguna")

    @Before fun setup() {
        server = MockWebServer().apply { start() }
        network = requireNotNull(WasteNotNetwork.create(server.url("/v1/").toString(),
            accessToken = { "fixture-fallback-token" }, allowLocalHttp = true))
        store = RemoteActivityStore(CoreRepository(network))
    }

    @After fun cleanup() {
        network.client.dispatcher.cancelAll()
        network.client.connectionPool.evictAll()
        network.client.dispatcher.executorService.shutdown()
        server.shutdown()
    }

    @Test fun createAndExplicitReplayKeepTheSamePayloadAndDistinguishReceipts() = runTest {
        server.enqueue(reply(record(), 201))
        server.enqueue(reply(record(), 200))
        val created = (store.create(input, requestId) as ApiResult.Success).value
        val replay = (store.create(input, requestId) as ApiResult.Success).value
        assertTrue(created.created); assertFalse(replay.created)
        assertEquals(id, created.log.id); assertEquals(created.log, replay.log)
        val requests = List(2) { server.takeRequest() }
        val bodies = requests.map { it.body.readUtf8() }
        assertEquals(bodies[0], bodies[1])
        val body = Json.parseToJsonElement(bodies[0]).jsonObject
        assertEquals(setOf("category", "method", "quantity_kind", "weight_g", "item_count", "activity_date", "notes", "client_request_id"), body.keys)
        assertEquals(requestId, body.getValue("client_request_id").jsonPrimitive.content)
        assertEquals(250, body.getValue("weight_g").jsonPrimitive.int)
        assertEquals(JsonNull, body["item_count"])
        assertEquals(input.notes, body.getValue("notes").jsonPrimitive.content)
        requests.forEach { assertEquals("POST", it.method); assertEquals("/v1/waste-logs", it.path)
            assertEquals("Bearer fixture-fallback-token", it.getHeader("Authorization")) }
    }

    @Test fun detailAcceptsTheRequestedUuidAndRejectsAnUnrelatedRecord() = runTest {
        server.enqueue(reply(record()))
        assertEquals(id, (store.get(id.uppercase()) as ApiResult.Success).value.id)
        assertEquals("/v1/waste-logs/${id.uppercase()}", server.takeRequest().path)
        server.enqueue(reply(buildJsonObject { record().forEach { (name, value) -> put(name, value) }; put("id", requestId) }))
        assertEquals(ApiFailureKind.MALFORMED_RESPONSE, (store.get(id) as ApiResult.Failure).error.kind)
    }

    @Test fun changingQuantityKindClearsTheOtherUnitAndSendsTheReadVersion() = runTest {
        server.enqueue(reply(record(version = 2, count = 20)))
        val counted = (store.update(id, 1, input.copy(quantity = Quantity.Count.of(20))) as ApiResult.Success).value
        assertEquals(Quantity.Count.of(20), counted.quantity)
        val first = server.takeRequest();assertEquals("PATCH", first.method);assertEquals("/v1/waste-logs/$id", first.path)
        val countBody = Json.parseToJsonElement(first.body.readUtf8()).jsonObject
        assertEquals(JsonNull, countBody["weight_g"]);assertEquals(20, countBody.getValue("item_count").jsonPrimitive.int)
        assertEquals("COUNT", countBody.getValue("quantity_kind").jsonPrimitive.content)
        assertEquals(1, countBody.getValue("version").jsonPrimitive.int)
        assertFalse(countBody.containsKey("client_request_id"));assertFalse(countBody.containsKey("activity_timezone"))
        server.enqueue(reply(record(version = 3, weight = 750)))
        val weighed = (store.update(id, 2, input.copy(quantity = Quantity.Weight.of(750))) as ApiResult.Success).value
        assertEquals(Quantity.Weight.of(750), weighed.quantity)
        val weightBody = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals(JsonNull, weightBody["item_count"]);assertEquals(750, weightBody.getValue("weight_g").jsonPrimitive.int)
        assertEquals("WEIGHT", weightBody.getValue("quantity_kind").jsonPrimitive.content)
        assertEquals(2, weightBody.getValue("version").jsonPrimitive.int)
    }

    @Test fun deleteUsesQuotedIfMatchAndAcceptsOnlyAnEmpty204Success() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        assertTrue(store.delete(id, 2) is ApiResult.Success)
        val request = server.takeRequest()
        assertEquals("DELETE", request.method);assertEquals("/v1/waste-logs/$id", request.path)
        assertEquals("\"2\"", request.getHeader("If-Match"))
        server.enqueue(reply(record(), 200))
        assertEquals(ApiFailureKind.MALFORMED_RESPONSE, (store.delete(id, 2) as ApiResult.Failure).error.kind)
        server.enqueue(MockResponse().setResponseCode(204))
        assertTrue(store.delete(id, 2) is ApiResult.Success)
        assertEquals(3, server.requestCount)
    }

    @Test fun writeErrorsKeepConflictValidationAndRetryMetadata() = runTest {
        for ((status, code, expected) in listOf(
            Triple(409, "IDEMPOTENCY_CONFLICT", ApiFailureKind.IDEMPOTENCY_CONFLICT),
            Triple(410, "LOG_DELETED", ApiFailureKind.DELETED),
            Triple(422, "VALIDATION_ERROR", ApiFailureKind.VALIDATION),
            Triple(429, "RATE_LIMITED", ApiFailureKind.RATE_LIMITED),
        )) {
            server.enqueue(error(status, code).addHeader("Retry-After", "5"))
            val failure = (store.create(input, requestId) as ApiResult.Failure).error
            assertEquals(expected, failure.kind);assertEquals(status, failure.status);assertEquals(code, failure.code)
            assertEquals("fixture-request", failure.requestId);assertNotNull(failure.retryAt)
            if (status == 422) assertEquals("Periksa jumlah.", failure.fields["weight_g"])
        }
        assertEquals(4, server.requestCount)
    }

    @Test fun updateAndDeletePreserveVersionAndMissingRecordFailures() = runTest {
        server.enqueue(error(409, "VERSION_CONFLICT"))
        assertEquals(ApiFailureKind.VERSION_CONFLICT, (store.update(id, 1, input) as ApiResult.Failure).error.kind)
        server.enqueue(error(409, "VERSION_CONFLICT"))
        assertEquals(ApiFailureKind.VERSION_CONFLICT, (store.delete(id, 1) as ApiResult.Failure).error.kind)
        server.enqueue(error(428, "PRECONDITION_REQUIRED"))
        assertEquals(ApiFailureKind.PRECONDITION_REQUIRED, (store.delete(id, 1) as ApiResult.Failure).error.kind)
        server.enqueue(error(404, "NOT_FOUND"))
        assertEquals(ApiFailureKind.NOT_FOUND, (store.get(id) as ApiResult.Failure).error.kind)
    }

    @Test fun malformedOrUnconfirmedWriteResultsNeverBecomeSuccessfulReceipts() = runTest {
        server.enqueue(reply(record(), 202))
        assertEquals(ApiFailureKind.MALFORMED_RESPONSE, (store.create(input, requestId) as ApiResult.Failure).error.kind)
        server.enqueue(reply(record(version = 1)))
        assertEquals(ApiFailureKind.MALFORMED_RESPONSE, (store.update(id, 1, input) as ApiResult.Failure).error.kind)
        server.enqueue(reply(buildJsonObject { record(count = 20).forEach { (name, value) -> put(name, value) };put("weight_g", 250) }))
        assertEquals(ApiFailureKind.MALFORMED_RESPONSE, (store.get(id) as ApiResult.Failure).error.kind)
        server.enqueue(MockResponse().setResponseCode(204))
        assertEquals(ApiFailureKind.MALFORMED_RESPONSE, (store.get(id) as ApiResult.Failure).error.kind)
    }

    @Test fun lostCreateResponseDoesNotRetryUntilTheCallerExplicitlyRetries() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        assertEquals(ApiFailureKind.NETWORK, (store.create(input, requestId) as ApiResult.Failure).error.kind)
        assertEquals(1, server.requestCount)
        val first = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS)).body.readUtf8()
        // Restore a fresh connection before exercising the caller's explicit retry.
        network.client.connectionPool.evictAll()
        server.enqueue(reply(record(), 200))
        val retried = store.create(input, requestId)
        assertTrue("Explicit replay: $retried; requests=${server.requestCount}", retried is ApiResult.Success)
        val replay = (retried as ApiResult.Success).value
        assertFalse(replay.created)
        assertEquals(first, requireNotNull(server.takeRequest(5, TimeUnit.SECONDS)).body.readUtf8())
        assertEquals(2, server.requestCount)
    }

    @Test fun cancellingAnInFlightWriteRemainsCancellationAndDoesNotReplay() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val pending = async(Dispatchers.IO) { store.create(input, requestId) }
        withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(5, TimeUnit.SECONDS)) }
        pending.cancelAndJoin()
        assertTrue(pending.isCancelled);assertEquals(1, server.requestCount)
    }

    @Test fun invalidLocalKeysNeverReachTheServerAndMissingConfigurationStaysExplicit() = runTest {
        assertEquals(ApiFailureKind.INVALID_REQUEST, (store.get("../me") as ApiResult.Failure).error.kind)
        assertEquals(ApiFailureKind.INVALID_REQUEST, (store.create(input, "invalid") as ApiResult.Failure).error.kind)
        assertEquals(ApiFailureKind.INVALID_REQUEST, (store.update(id, 0, input) as ApiResult.Failure).error.kind)
        assertEquals(ApiFailureKind.INVALID_REQUEST, (store.delete(id, 0) as ApiResult.Failure).error.kind)
        store.clear();assertEquals(0, server.requestCount)
        val unavailable = RemoteActivityStore(CoreRepository(null))
        for (result in listOf(unavailable.get(id), unavailable.create(input, requestId), unavailable.update(id, 1, input), unavailable.delete(id, 1))) {
            assertEquals(ApiFailureKind.NOT_CONFIGURED, (result as ApiResult.Failure).error.kind)
        }
    }

    @Test fun authenticatedWritesUseAccountCredentialsAndReusePayloadAfterA401Refresh() = runTest {
        var saved: RefreshToken? = null
        val tokens = object : RefreshTokenStore {
            override suspend fun read(): TokenReadResult = saved?.let { TokenReadResult.Available(it) } ?: TokenReadResult.Absent
            override suspend fun write(token: RefreshToken) { saved = token }
            override suspend fun clear() { saved = null }
        }
        val session = SessionRepository(network, tokens)
        val remote = RemoteActivityStore(CoreRepository(network, session))
        server.enqueue(json(fixture("auth-session")))
        assertTrue(session.login("fixture@example.com", "fixture-password") is ApiResult.Success)
        assertNull(server.takeRequest().getHeader("Authorization"))
        server.enqueue(reply(record(), 201))
        assertTrue(remote.create(input, requestId) is ApiResult.Success)
        val created = server.takeRequest()
        val access = Json.parseToJsonElement(fixture("auth-session")).jsonObject.getValue("data").jsonObject.getValue("access_token").jsonPrimitive.content
        assertEquals("Bearer $access", created.getHeader("Authorization"))
        server.enqueue(error(401, "SESSION_EXPIRED"))
        server.enqueue(json(fixture("auth-refresh")))
        server.enqueue(reply(record(version = 2)))
        assertTrue(remote.update(id, 1, input) is ApiResult.Success)
        val first = server.takeRequest();val refresh = server.takeRequest();val retry = server.takeRequest()
        assertEquals("/v1/auth/refresh", refresh.path);assertNull(refresh.getHeader("Authorization"))
        assertEquals(first.body.readUtf8(), retry.body.readUtf8())
        assertNotEquals(first.getHeader("Authorization"), retry.getHeader("Authorization"))
        assertEquals(5, server.requestCount)
    }

    private fun fixture(name: String) = requireNotNull(javaClass.getResource("/contracts/$name.json")).readText()
    private fun record(version: Int = 1, weight: Int = 250, count: Int? = null): JsonObject = buildJsonObject {
        Json.parseToJsonElement(fixture("waste-logs-page")).jsonObject.getValue("data").jsonArray.first().jsonObject
            .forEach { (name, value) -> put(name, value) }
        put("id", id);put("version", version)
        put("quantity_kind", if (count == null) "WEIGHT" else "COUNT")
        put("weight_g", if (count == null) JsonPrimitive(weight) else JsonNull)
        put("item_count", count?.let(::JsonPrimitive) ?: JsonNull)
    }
    private fun json(body: String, status: Int = 200) = MockResponse().setResponseCode(status)
        .addHeader("Content-Type", "application/json").setBody(body)
    private fun reply(record: JsonObject, status: Int = 200) = json(buildJsonObject { put("data", record) }.toString(), status)
    private fun error(status: Int, code: String) = json("""{"error":{"code":"$code","fields":{"weight_g":"Periksa jumlah."}},"request_id":"fixture-request"}""", status)
}
