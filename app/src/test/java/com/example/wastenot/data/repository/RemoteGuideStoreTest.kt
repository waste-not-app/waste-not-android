package com.example.wastenot.data.repository

import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.network.WasteNotNetwork
import com.example.wastenot.model.DisposalMethod
import com.example.wastenot.model.WasteCategory
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RemoteGuideStoreTest {
    private lateinit var server: MockWebServer
    private lateinit var network: WasteNotNetwork
    private lateinit var store: RemoteGuideStore
    private val id = "00000000-0000-4000-8000-000000000001"

    @Before fun setup() {
        server = MockWebServer().apply { start() }
        network = requireNotNull(WasteNotNetwork.create(server.url("/v1/").toString(),
            accessToken = { "guide-test-credential" }, allowLocalHttp = true))
        store = RemoteGuideStore(CoreRepository(network))
    }

    @After fun cleanup() {
        network.client.dispatcher.cancelAll()
        network.client.connectionPool.evictAll()
        network.client.dispatcher.executorService.shutdown()
        server.shutdown()
    }

    @Test fun listUsesAuthenticatedGuideRouteFiltersAndOpaqueCursor() = runTest {
        server.enqueue(reply(list(listOf(article()), "next+/=")))
        val result = success(store.search("  botol & plastik  ", WasteCategory.PLASTIC, "previous+/="))
        val guide = result.value.single()
        assertEquals(DisposalMethod.RECYCLE, guide.method)
        assertEquals(listOf("plastik"), guide.keywords)
        val review = requireNotNull(guide.review)
        assertEquals("example.org", review.source.host)
        assertEquals(LocalDate.of(2026, 1, 15), review.date)
        assertEquals("Reviewer fixture", review.reviewer)
        assertEquals("next+/=", result.page!!.nextCursor)
        val request = server.takeRequest()
        assertEquals("Bearer guide-test-credential", request.getHeader("Authorization"))
        val url = request.requestUrl!!
        assertEquals("/v1/waste-guide", url.encodedPath)
        assertEquals("botol & plastik", url.queryParameter("q"))
        assertEquals("PLASTIC", url.queryParameter("category"))
        assertEquals("20", url.queryParameter("limit"))
        assertEquals("previous+/=", url.queryParameter("cursor"))
    }

    @Test fun detailRetainsServerContentAndOptionalReviewerIsNotInvented() = runTest {
        server.enqueue(reply(buildJsonObject { put("data", JsonObject(article() - "reviewer" - "publication_status" - "keywords")) }))
        val value = success(store.get(id)).value
        assertEquals(id, value.id)
        assertEquals(listOf("Langkah fixture"), value.steps)
        assertEquals(emptyList<String>(), value.keywords)
        assertNull(value.review!!.reviewer)
        assertEquals("/v1/waste-guide/$id", server.takeRequest().path)
    }

    @Test fun emptyPublishedCatalogIsEmptySuccessAndUnconfiguredStoreHasNoDummyFallback() = runTest {
        server.enqueue(reply(list(emptyList())))
        val result = success(store.search(""))
        assertTrue(result.value.isEmpty())
        assertFalse(result.page!!.hasMore)
        assertNull(server.takeRequest().requestUrl!!.queryParameter("q"))
        val absent = RemoteGuideStore(CoreRepository(null)).search("") as ApiResult.Failure
        assertEquals(ApiFailureKind.NOT_CONFIGURED, absent.error.kind)
    }

    @Test fun invalidQueriesIdsAndBlankCursorsDoNotReachTheServer() = runTest {
        assertEquals(ApiFailureKind.INVALID_REQUEST, (store.get("../me") as ApiResult.Failure).error.kind)
        assertEquals(ApiFailureKind.INVALID_REQUEST, (store.search("a".repeat(101)) as ApiResult.Failure).error.kind)
        assertEquals(ApiFailureKind.INVALID_REQUEST, (store.search("", cursor = " ") as ApiResult.Failure).error.kind)
        assertNull(server.takeRequest(20, TimeUnit.MILLISECONDS))
        server.enqueue(reply(list(emptyList())))
        success(store.search("♻️".repeat(50)))
        assertEquals("♻️".repeat(50), server.takeRequest().requestUrl!!.queryParameter("q"))
    }

    @Test fun malformedOrUnpublishedArticlesFailInsteadOfShowingUnreviewedGuidance() = runTest {
        val bad = listOf(
            article().changed("id", JsonPrimitive("not-a-uuid")),
            article().changed("name", JsonPrimitive(" ")),
            article().changed("category", JsonPrimitive("UNKNOWN")),
            article().changed("recommended_method", JsonPrimitive("UNKNOWN")),
            article().changed("summary", JsonPrimitive("")),
            article().changed("steps", JsonArray(emptyList())),
            article().changed("steps", JsonArray(listOf(JsonPrimitive(" ")))),
            article().changed("cautions", JsonArray(listOf(JsonPrimitive(" ")))),
            article().changed("source_url", JsonPrimitive("http://example.org/article")),
            article().changed("source_url", JsonPrimitive("https://user:pass@example.org/article")),
            article().changed("source_url", JsonPrimitive("https://[invalid")),
            article().changed("reviewed_at", JsonPrimitive("2026-02-30")),
            article().changed("reviewer", JsonPrimitive(" ")),
            article().changed("publication_status", JsonPrimitive("DRAFT")),
            article().changed("publication_status", JsonPrimitive("ARCHIVED")),
            JsonObject(article() - "source_url"),
        )
        for (value in bad) {
            server.enqueue(reply(list(listOf(value))))
            assertEquals(value.toString(), ApiFailureKind.MALFORMED_RESPONSE,
                (store.search("") as ApiResult.Failure).error.kind)
            server.takeRequest()
        }
    }

    @Test fun brokenPagesDuplicateIdsAndMismatchedCategoriesAreRejected() = runTest {
        val values = listOf(
            buildJsonObject { put("data", JsonArray(emptyList())) },
            buildJsonObject {
                put("data", JsonArray(emptyList())); put("page", buildJsonObject { put("has_more", false); put("next_cursor", "") })
            },
            list(emptyList(), "next"),
            list(listOf(article(), article())),
            list(listOf(article()), "same"),
            list(List(21) { article() }),
        )
        for (value in values) {
            server.enqueue(reply(value))
            assertEquals(ApiFailureKind.MALFORMED_RESPONSE,
                (store.search("", cursor = "same") as ApiResult.Failure).error.kind)
            server.takeRequest()
        }
        server.enqueue(reply(list(listOf(article()))))
        assertEquals(ApiFailureKind.MALFORMED_RESPONSE,
            (store.search("", WasteCategory.GLASS) as ApiResult.Failure).error.kind)
    }

    @Test fun differentDetailOrUnexpectedSuccessStatusIsMalformed() = runTest {
        server.enqueue(reply(buildJsonObject { put("data", article().changed("id", JsonPrimitive("00000000-0000-4000-8000-000000000002"))) }))
        assertEquals(ApiFailureKind.MALFORMED_RESPONSE, (store.get(id) as ApiResult.Failure).error.kind)
        server.enqueue(reply(list(emptyList())).setResponseCode(201))
        assertEquals(ApiFailureKind.MALFORMED_RESPONSE, (store.search("") as ApiResult.Failure).error.kind)
    }

    @Test fun httpErrorsRemainFailuresWithNoArticleFallback() = runTest {
        for ((status, kind) in listOf(401 to ApiFailureKind.UNAUTHENTICATED, 404 to ApiFailureKind.NOT_FOUND,
            429 to ApiFailureKind.RATE_LIMITED, 503 to ApiFailureKind.SERVER)) {
            server.enqueue(MockResponse().setResponseCode(status))
            assertEquals(kind, (store.get(id) as ApiResult.Failure).error.kind)
            server.takeRequest()
        }
    }

    private fun article() = buildJsonObject {
        put("id", id); put("slug", "fixture"); put("name", "Artikel fixture"); put("category", "PLASTIC")
        put("recommended_method", "RECYCLE"); put("keywords", JsonArray(listOf(JsonPrimitive("plastik"))))
        put("summary", "Konten pengujian transport, bukan petunjuk pengguna.")
        put("steps", JsonArray(listOf(JsonPrimitive("Langkah fixture")))); put("cautions", JsonArray(emptyList()))
        put("source_url", "https://example.org/article"); put("reviewed_at", "2026-01-15")
        put("reviewer", "Reviewer fixture"); put("publication_status", "PUBLISHED")
    }
    private fun list(values: List<JsonObject>, next: String? = null) = buildJsonObject {
        put("data", JsonArray(values)); put("page", buildJsonObject {
            put("has_more", next != null); put("next_cursor", next?.let(::JsonPrimitive) ?: JsonNull)
        })
    }
    private fun reply(body: JsonObject) = MockResponse().setHeader("Content-Type", "application/json").setBody(body.toString())
    private fun JsonObject.changed(key: String, value: JsonElement) = JsonObject(this + (key to value))
    private fun <T> success(result: ApiResult<T>): ApiResult.Success<T> {
        assertTrue(result.toString(), result is ApiResult.Success)
        return result as ApiResult.Success<T>
    }
}
