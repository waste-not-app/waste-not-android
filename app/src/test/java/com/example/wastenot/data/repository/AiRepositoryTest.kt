package com.example.wastenot.data.repository

import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.network.WasteNotNetwork
import com.example.wastenot.model.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AiRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var network: WasteNotNetwork
    private lateinit var repository: AiRepository
    private val id = "00000000-0000-0000-0000-000000000001"
    private val requestId = "00000000-0000-0000-0000-000000000032"
    private val clock = Clock.fixed(Instant.parse("2026-10-21T07:00:00Z"), ZoneOffset.UTC)
    private val jpeg = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xd9.toByte())

    @Before fun setup() {
        server = MockWebServer().apply { start() }
        network = requireNotNull(WasteNotNetwork.create(server.url("/v1/").toString(),
            accessToken = { "ai-test-credential" }, allowLocalHttp = true, clock = clock))
        repository = AiRepository(network, clock = clock)
    }

    @After fun cleanup() {
        network.client.dispatcher.cancelAll()
        network.client.connectionPool.evictAll()
        network.client.dispatcher.executorService.shutdown()
        server.shutdown()
    }

    @Test fun catalogRetainsServerKeysAndEditorialStatusWithoutInventingMaterial() = runTest {
        server.enqueue(reply(fixture("catalog-response")))
        val catalog = success(repository.catalog())
        assertEquals(85, catalog.items.size)
        assertEquals(catalog.items.size, catalog.items.map { it.key }.distinct().size)
        assertTrue(catalog.items.any { it.guideStatus == AiGuideStatus.DRAFT })
        assertEquals(7, catalog.items.count { it.key.startsWith("other_") })
        val request = server.takeRequest()
        assertEquals("/v1/ai/objects", request.path)
        assertEquals("Bearer ai-test-credential", request.getHeader("Authorization"))
    }

    @Test fun uploadUsesOneJpegAndStableRequestIdForCreatedAndExplicitReplay() = runTest {
        for (status in listOf(201, 200)) {
            server.enqueue(reply(fixture("detection-response"), status))
            val detection = success(repository.detect(jpeg, requestId))
            assertEquals(id, detection.id)
            assertEquals(AiConfidenceBand.OFFERED, detection.objects.single().band)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/v1/ai/detections", request.path)
            assertEquals("Bearer ai-test-credential", request.getHeader("Authorization"))
            val multipart = request.body.readUtf8()
            assertTrue(multipart.contains("name=\"image\"; filename=\"capture.jpg\""))
            assertTrue(multipart.contains("Content-Type: image/jpeg"))
            assertTrue(multipart.contains("name=\"client_request_id\""))
            assertTrue(multipart.contains(requestId))
            assertFalse(multipart.contains("category"))
            assertFalse(multipart.contains("/data/"))
        }
        assertEquals(2, server.requestCount)
    }

    @Test fun malformedUploadsAndPathsFailBeforeMakingAnyRequest() = runTest {
        for ((bytes, key) in listOf(byteArrayOf() to requestId, jpeg to "../escape",
            byteArrayOf(1, 2, 3) to requestId, ByteArray(5 * 1024 * 1024 + 1) to requestId)) {
            failure(ApiFailureKind.INVALID_REQUEST, repository.detect(bytes, key))
        }
        failure(ApiFailureKind.INVALID_REQUEST, repository.deleteDetection("../escape"))
        assertNull(server.takeRequest(20, TimeUnit.MILLISECONDS))
    }

    @Test fun calibratedUncertainCandidateBelowOldFixedCutoffIsRetained() = runTest {
        val data = fixture("detection-response").data()
        val limits = buildJsonObject { put("lower", .3); put("offered", .725) }
        val candidate = data.getValue("objects").jsonArray.single().jsonObject
            .changed("confidence", JsonPrimitive(.4)).changed("confidence_band", JsonPrimitive("UNCERTAIN"))
            .changed("review_thresholds", limits)
        server.enqueue(reply(envelope(data.changed("objects", JsonArray(listOf(candidate))))))
        assertEquals(.4, success(repository.detect(jpeg, requestId)).objects.single().confidence, 0.0)
    }

    @Test fun inconsistentDetectionClaimsAreRejectedInsteadOfPrefillingMaterial() = runTest {
        val data = fixture("detection-response").data()
        val candidate = data.getValue("objects").jsonArray.single().jsonObject
        for (row in listOf(candidate.changed("category", JsonPrimitive("GLASS")),
            candidate.changed("object_key", JsonPrimitive("glass_bottle")),
            candidate.changed("confidence_band", JsonPrimitive("UNCERTAIN")),
            candidate.changed("bbox", JsonArray(listOf(.8, .1, .1, .9).map(::JsonPrimitive))),
            candidate.changed("ambiguous_with", JsonArray(listOf(JsonPrimitive("absent")))))) {
            server.enqueue(reply(envelope(data.changed("objects", JsonArray(listOf(row))))))
            failure(ApiFailureKind.MALFORMED_RESPONSE, repository.detect(jpeg, requestId))
            server.takeRequest()
        }
        server.enqueue(reply(envelope(data.changed("objects", JsonArray(listOf(candidate, candidate))))))
        failure(ApiFailureKind.MALFORMED_RESPONSE, repository.detect(jpeg, requestId))
    }

    @Test fun noDetectionIsAnExplicitManualStateAndExpiredResponsesCannotBeUsed() = runTest {
        server.enqueue(reply(fixture("no-detections-response")))
        assertEquals(AiDetectionStatus.MANUAL_REQUIRED, success(repository.detect(jpeg, requestId)).status)
        server.enqueue(reply(envelope(fixture("detection-response").data().changed("expires_at", JsonPrimitive(clock.instant().toString())))))
        failure(ApiFailureKind.AI_EXPIRED, repository.detect(jpeg, requestId))
    }

    @Test fun recommendationSendsIndependentUnknownConditionsAndKeepsPrototypeClearlyMarked() = runTest {
        val input = detectedRequest().copy(condition = AiCondition(cleanliness = Cleanliness.DIRTY,
            cleanable = AiAnswer.YES, soiling = AiSoiling.FOOD_DRINK), recyclingAccess = AiAnswer.YES)
        server.enqueue(reply(fixture("detected-recycle-response"), 201))
        val recommendation = success(repository.recommend(input))
        assertTrue(recommendation.prototypeOnly)
        assertFalse(recommendation.canUseForTracking)
        assertEquals("US EPA", recommendation.sourceDetails.single().publisher)
        val request = server.takeRequest()
        assertEquals("/v1/ai/recommendations", request.path)
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        val condition = body.getValue("condition").jsonObject
        assertEquals("DIRTY", condition.getValue("cleanliness").jsonPrimitive.content)
        assertEquals("UNKNOWN", condition.getValue("integrity").jsonPrimitive.content)
        assertEquals("YES", condition.getValue("cleanable").jsonPrimitive.content)
        assertEquals("FOOD_DRINK", condition.getValue("soiling").jsonPrimitive.content)
        assertEquals("UNKNOWN", condition.getValue("former_contents").jsonPrimitive.content)
        assertEquals("HANDLING", body.getValue("recommendation_goal").jsonPrimitive.content)
        assertEquals(JsonNull, body.getValue("reuse_intention"))
        assertEquals(requestId, body.getValue("client_request_id").jsonPrimitive.content)
        assertFalse(body.keys.any { it in setOf("prompt", "confidence", "raw_class", "model_version") })
    }

    @Test fun specialHandlingDoesNotSuppressValidMethodAndStaleOrForeignRecommendationIsRejected() = runTest {
        val valid = fixture("detected-recycle-response").data()
            .changed("prototype_only", JsonPrimitive(false)).changed("special_handling", JsonPrimitive(true))
        server.enqueue(reply(envelope(valid)))
        assertTrue(success(repository.recommend(detectedRequest())).canUseForTracking)
        for (bad in listOf(valid.changed("context_revision", JsonPrimitive(2)),
            valid.changed("detection_id", JsonPrimitive(requestId)), valid.changed("object_id", JsonPrimitive("other")),
            valid.changed("confirmed_object_key", JsonPrimitive("plastic_bottle")),
            valid.changed("category", JsonPrimitive("PLASTIC")),
            valid.changed("source_urls", JsonArray(listOf(JsonPrimitive("http://example.org")))),
            valid.changed("recommendation_scope", JsonPrimitive("PREVENTION")))) {
            server.enqueue(reply(envelope(bad)))
            failure(ApiFailureKind.MALFORMED_RESPONSE, repository.recommend(detectedRequest()))
        }
        server.enqueue(reply(envelope(valid.changed("expires_at", JsonPrimitive(clock.instant().toString())))))
        failure(ApiFailureKind.AI_EXPIRED, repository.recommend(detectedRequest()))
    }

    @Test fun conditionalAndMissingContextAreDistinctStatesWithNoInventedConfirmation() = runTest {
        val input = AiRecommendationRequest(requestId, true, "ceramic_cup", WasteCategory.OTHER, AiInputSource.MANUAL)
        server.enqueue(reply(fixture("conditional-cup-response")))
        val conditional = success(repository.recommend(input))
        assertEquals(AiRecommendationBasis.CONDITIONAL, conditional.basis)
        assertEquals(4, conditional.conditionsToVerify.size)
        assertTrue(conditional.missingContext.isEmpty())
        server.enqueue(reply(fixture("needs-context-response")))
        val missing = success(repository.recommend(input.copy(riskFlag = true)))
        assertEquals(AiRecommendationStatus.NEEDS_CONTEXT, missing.status)
        assertEquals(listOf(AiContextField.RISK_TYPE), missing.missingContext)
        assertNull(missing.method)
        assertFalse(missing.canUseForTracking)
        assertTrue(missing.steps.isNotEmpty())
        assertTrue(missing.sourceDetails.isNotEmpty())
    }

    @Test fun unavailableAdviceKeepsSourcedHandlingStepsWithoutInventingATrackingMethod() = runTest {
        val input = AiRecommendationRequest(requestId, true, "glass_bottle", WasteCategory.GLASS, AiInputSource.MANUAL)
        server.enqueue(reply(fixture("provider-unavailable-response")))
        val result = success(repository.recommend(input))
        assertEquals(AiRecommendationStatus.UNAVAILABLE, result.status)
        assertEquals("pilot-policy-08", result.version)
        assertEquals(4, result.steps.size)
        assertTrue(result.sourceDetails.isNotEmpty())
        assertTrue(result.sourceDetails.all { it.url in result.sources })
        assertNull(result.method)
        assertNull(result.scope)
        assertNull(result.basis)
        assertFalse(result.canUseForTracking)
        assertEquals(1, server.requestCount)
    }

    @Test fun typedFailuresNeverInventResultsOrAutomaticallyRepeatInference() = runTest {
        for ((status, code, expected) in listOf(
            Triple(410, "AI_SESSION_EXPIRED", ApiFailureKind.AI_EXPIRED),
            Triple(422, "AI_REFERENCE_INVALID", ApiFailureKind.AI_REFERENCE_INVALID),
            Triple(409, "AI_ALREADY_LINKED", ApiFailureKind.AI_ALREADY_LINKED),
            Triple(429, "RATE_LIMITED", ApiFailureKind.RATE_LIMITED),
            Triple(503, "AI_UNAVAILABLE", ApiFailureKind.SERVER))) {
            server.enqueue(reply(buildJsonObject { put("error", buildJsonObject { put("code", code) }) }, status))
            failure(expected, repository.recommend(detectedRequest()))
            server.takeRequest()
        }
        assertEquals(5, server.requestCount)
        assertNull(server.takeRequest(20, TimeUnit.MILLISECONDS))
        failure(ApiFailureKind.NOT_CONFIGURED, AiRepository(null).catalog())
    }

    @Test fun cancellingKnownDetectionUsesDeleteAndRequiresNoContent() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        success(repository.deleteDetection(id))
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/v1/ai/detections/$id", request.path)
        server.enqueue(MockResponse().setResponseCode(200))
        failure(ApiFailureKind.MALFORMED_RESPONSE, repository.deleteDetection(id))
    }

    private fun detectedRequest() = AiRecommendationRequest(requestId, true, "glass_bottle", WasteCategory.GLASS,
        AiInputSource.DETECTION, id, "object-1")
    private fun fixture(name: String) = Json.parseToJsonElement(checkNotNull(javaClass.getResource("/ai-contract/$name.json")).readText()).jsonObject
    private fun JsonObject.data() = getValue("data").jsonObject
    private fun JsonObject.changed(key: String, value: JsonElement) = JsonObject(this + (key to value))
    private fun envelope(data: JsonObject) = buildJsonObject { put("data", data) }
    private fun reply(body: JsonObject, status: Int = 200) = MockResponse().setResponseCode(status)
        .setHeader("Content-Type", "application/json").setBody(body.toString())
    private fun <T> success(result: ApiResult<T>): T {
        assertTrue(result.toString(), result is ApiResult.Success)
        return (result as ApiResult.Success).value
    }
    private fun failure(kind: ApiFailureKind, result: ApiResult<*>) {
        assertTrue(result.toString(), result is ApiResult.Failure)
        assertEquals(kind, (result as ApiResult.Failure).error.kind)
    }
}
