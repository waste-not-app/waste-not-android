package com.example.wastenot.data.repository

import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.network.WasteNotNetwork
import com.example.wastenot.model.ActivityDateRange
import com.example.wastenot.model.DisposalMethod
import com.example.wastenot.model.QuantityTotals
import com.example.wastenot.model.WasteCategory
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ServerSummaryTest {
    private lateinit var server: MockWebServer
    private lateinit var network: WasteNotNetwork
    private lateinit var core: CoreRepository
    private val range = ActivityDateRange(LocalDate.parse("2026-11-04"), LocalDate.parse("2026-11-05"))

    @Before fun setup() {
        server = MockWebServer().apply { start() }
        network = requireNotNull(WasteNotNetwork.create(server.url("/v1/").toString(),
            accessToken = { "summary-fixture" }, allowLocalHttp = true))
        core = CoreRepository(network)
    }

    @After fun cleanup() {
        network.client.dispatcher.cancelAll()
        network.client.connectionPool.evictAll()
        network.client.dispatcher.executorService.shutdown()
        server.shutdown()
    }

    @Test fun dashboardKeepsServerTotalsIndependentFromTheThreeRecentRecords() = runTest {
        server.enqueue(reply(dashboard()))
        val value = success(core.dashboard())
        assertEquals(range.to, value.date)
        assertEquals(ZoneId.of("Asia/Jakarta"), value.timezone)
        assertEquals(25L, value.summary.activityCount)
        assertEquals(6250L, value.summary.handled.weightGrams)
        assertEquals(3, value.recentLogs.size)
        assertEquals(100, value.progress.activeLogCount)
        assertEquals(1000, value.progress.xp)
        val request = server.takeRequest()
        assertEquals("/v1/dashboard", request.path)
        assertEquals("Bearer summary-fixture", request.getHeader("Authorization"))
    }

    @Test fun statisticsUsesTheRequestedDatesAndKeepsReduceAndUnitsSeparate() = runTest {
        server.enqueue(reply(statistics()))
        val value = success(core.statistics(range))
        assertEquals(range, value.range)
        assertEquals(QuantityTotals(500, 3), value.summary.handled)
        assertEquals(QuantityTotals(0, 2), value.summary.avoided)
        assertEquals(BigDecimal("100.0"), value.summary.recyclingRatePercent)
        assertEquals(3L, value.summary.handledActivityCount)
        assertEquals(1L, value.methods[DisposalMethod.REDUCE])
        assertEquals(0L, value.methods[DisposalMethod.TRASH])
        assertEquals(QuantityTotals(0, 0), value.categories[WasteCategory.GLASS])
        assertEquals(listOf(range.from, range.to), value.daily.map { it.date })
        assertEquals(QuantityTotals(250, 3), value.daily.last().handled)
        val url = server.takeRequest().requestUrl!!
        assertEquals("/v1/statistics", url.encodedPath)
        assertEquals("2026-11-04", url.queryParameter("from"))
        assertEquals("2026-11-05", url.queryParameter("to"))
    }

    @Test fun aggregateTotalsAndMethodCountsAreNotTruncatedToInt() = runTest {
        val huge = 3_000_000_000L
        val body = statistics().changed("summary", summary(huge, huge, huge, 5_000_000_000L, items = 0, avoided = 0))
            .changed("method_counts", buildJsonObject {
                DisposalMethod.entries.forEach { put(it.name, if (it == DisposalMethod.RECYCLE) huge else 0L) }
            }).changed("daily", buildJsonArray {
                add(day("2026-11-04", 0, 0, 0))
                add(day("2026-11-05", 5_000_000_000L, 0, huge))
            }).changed("categories", categories(5_000_000_000L, 0))
        server.enqueue(reply(body))
        val value = success(core.statistics(range))
        assertEquals(huge, value.summary.activityCount)
        assertEquals(5_000_000_000L, value.summary.handled.weightGrams)
        assertEquals(huge, value.methods[DisposalMethod.RECYCLE])
        assertEquals(huge, value.daily.last().activityCount)
    }

    @Test fun anEmptyPeriodPreservesNullRateAndExplicitZeroDatesAndCategories() = runTest {
        val body = statistics().changed("summary", summary(0, 0, 0, 0, null, items = 0, avoided = 0))
            .changed("method_counts", buildJsonObject { DisposalMethod.entries.forEach { put(it.name, 0) } })
            .changed("categories", categories(0, 0))
            .changed("daily", buildJsonArray { add(day("2026-11-04", 0, 0, 0)); add(day("2026-11-05", 0, 0, 0)) })
        server.enqueue(reply(body))
        val value = success(core.statistics(range))
        assertNull(value.summary.recyclingRatePercent)
        assertEquals(0L, value.summary.activityCount)
        assertEquals(2, value.daily.size)
        assertEquals(WasteCategory.entries.toSet(), value.categories.keys)
        assertTrue(value.categories.values.all { it == QuantityTotals(0, 0) })
    }

    @Test fun incompleteOrMalformedStatisticsNeverBecomeAnEmptySuccess() = runTest {
        val body = statistics()
        val methods = body.getValue("method_counts").jsonObject
        val totals = body.getValue("summary").jsonObject
        val variants = listOf(
            body.changed("daily", buildJsonArray { add(day("2026-11-05", 500, 3, 3)) }),
            body.changed("daily", buildJsonArray { add(day("2026-11-04", 250, 0, 1)); add(day("2026-11-04", 250, 3, 2)) }),
            body.changed("categories", buildJsonArray { repeat(WasteCategory.entries.size) {
                add(buildJsonObject { put("category", "PLASTIC"); put("handled_weight_g", 0); put("handled_count", 0) })
            } }),
            body.changed("timezone", JsonPrimitive("not/a-zone")),
            body.changed("method_counts", JsonObject(methods - "REDUCE")),
            body.changed("method_counts", methods.changed("RECYCLE", JsonPrimitive(-1))),
            body.changed("summary", totals.changed("recycling_rate_percent", JsonPrimitive("100"))),
            body.changed("summary", totals.changed("recycling_rate_percent", JsonNull)),
            body.changed("summary", totals.changed("recycling_rate_basis", JsonPrimitive("WEIGHT"))),
            body.changed("summary", totals.changed("total_handled_count", JsonPrimitive(-1))),
            body.changed("method_counts", methods.changed("REDUCE", JsonPrimitive(2))),
            body.changed("method_counts", methods.changed("RECYCLE", JsonPrimitive(Long.MAX_VALUE))),
            body.changed("daily", buildJsonArray { add(day("2026-11-04", 250, 0, 1)); add(day("2026-11-05", 251, 3, 3)) }),
            body.changed("daily", buildJsonArray { add(day("2026-11-04", 250, 0, 1)); add(day("2026-11-05", 250, 2, 3)) }),
            body.changed("daily", buildJsonArray { add(day("2026-11-04", 250, 0, 1)); add(day("2026-11-05", 250, 3, 2)) }),
            body.changed("categories", categories(501, 3)),
            body.changed("categories", categories(500, 2)),
        )
        variants.forEachIndexed { index, invalid ->
            server.enqueue(reply(invalid))
            assertEquals("Malformed variant $index", ApiFailureKind.MALFORMED_RESPONSE,
                (core.statistics(range) as ApiResult.Failure).error.kind)
        }
    }

    @Test fun statisticsRejectsAResponseForAnotherRangeAndNon200Receipts() = runTest {
        server.enqueue(reply(statistics().changed("to", JsonPrimitive("2026-11-06"))
            .changed("daily", buildJsonArray {
                add(day("2026-11-04", 250, 0, 1)); add(day("2026-11-05", 250, 3, 2)); add(day("2026-11-06", 0, 0, 0))
            })))
        server.enqueue(reply(statistics(), 202))
        repeat(2) { assertEquals(ApiFailureKind.MALFORMED_RESPONSE, (core.statistics(range) as ApiResult.Failure).error.kind) }
        server.enqueue(reply(dashboard(), 201))
        assertEquals(ApiFailureKind.MALFORMED_RESPONSE, (core.dashboard() as ApiResult.Failure).error.kind)
    }

    @Test fun malformedDashboardIdentityDatesAndProgressAreRejected() = runTest {
        val body = dashboard()
        val logs = body.getValue("recent_logs").jsonArray
        val variants = listOf(
            body.changed("recent_logs", buildJsonArray { repeat(4) { add(logs[0]) } }),
            body.changed("recent_logs", buildJsonArray { add(logs[0]); add(logs[0]) }),
            body.changed("date", JsonPrimitive("2026-11-03")),
            body.changed("timezone", JsonPrimitive("Europe/Paris")),
            body.changed("progress", body.getValue("progress").jsonObject.changed("xp_into_level", JsonPrimitive(100))),
        )
        variants.forEach { invalid ->
            server.enqueue(reply(invalid))
            assertEquals(ApiFailureKind.MALFORMED_RESPONSE, (core.dashboard() as ApiResult.Failure).error.kind)
        }
    }

    @Test fun failuresPreserveServerDiagnosticsAndMissingConfigurationStaysExplicit() = runTest {
        server.enqueue(MockResponse().setResponseCode(422).setHeader("Content-Type", "application/json")
            .setBody("""{"error":{"code":"VALIDATION_ERROR","fields":{"from":"Date range rejected"}},"request_id":"summary-range"}"""))
        val failure = (core.statistics(range) as ApiResult.Failure).error
        assertEquals(ApiFailureKind.VALIDATION, failure.kind)
        assertEquals("summary-range", failure.requestId)
        assertEquals("Date range rejected", failure.fields["from"])
        server.enqueue(MockResponse().setResponseCode(503))
        assertEquals(ApiFailureKind.SERVER, (core.dashboard() as ApiResult.Failure).error.kind)
        val absent = CoreRepository(null)
        assertEquals(ApiFailureKind.NOT_CONFIGURED, (absent.dashboard() as ApiResult.Failure).error.kind)
        assertEquals(ApiFailureKind.NOT_CONFIGURED, (absent.statistics(range) as ApiResult.Failure).error.kind)
    }

    private fun <T> success(result: ApiResult<T>): T {
        assertTrue("Expected success: $result", result is ApiResult.Success)
        return (result as ApiResult.Success).value
    }

    private fun reply(value: JsonObject, status: Int = 200) = MockResponse().setResponseCode(status)
        .setHeader("Content-Type", "application/json")
        .setBody(buildJsonObject { put("data", value) }.toString())

    private fun JsonObject.changed(key: String, value: JsonElement) = JsonObject(this + (key to value))

    private fun summary(count: Long = 4, handled: Long = 3, recycled: Long = 3,
        weight: Long = 500, rate: String? = "100.0", items: Long = 3, avoided: Long = 2) = buildJsonObject {
        put("total_handled_weight_g", weight); put("total_handled_count", items)
        put("avoided_weight_g", 0); put("avoided_count", avoided)
        put("activity_count", count); put("handled_activity_count", handled); put("recycled_activity_count", recycled)
        put("recycling_rate_percent", rate?.let { JsonPrimitive(BigDecimal(it)) } ?: JsonNull)
        put("recycling_rate_basis", "HANDLED_ACTIVITY_COUNT")
    }

    private fun day(date: String, weight: Long, count: Long, activities: Long) = buildJsonObject {
        put("date", date); put("handled_weight_g", weight); put("handled_count", count); put("activity_count", activities)
    }

    private fun categories(weight: Long = 500, count: Long = 3) = buildJsonArray {
        WasteCategory.entries.forEach { category -> add(buildJsonObject {
            put("category", category.name); put("handled_weight_g", if (category == WasteCategory.PLASTIC) weight else 0)
            put("handled_count", if (category == WasteCategory.PLASTIC) count else 0)
        }) }
    }

    private fun statistics() = buildJsonObject {
        put("from", "2026-11-04"); put("to", "2026-11-05"); put("timezone", "Asia/Jakarta")
        put("summary", summary()); put("categories", categories())
        put("daily", buildJsonArray { add(day("2026-11-04", 250, 0, 1)); add(day("2026-11-05", 250, 3, 3)) })
        put("method_counts", buildJsonObject { put("REDUCE", 1); put("REUSE", 0); put("RECYCLE", 3); put("TRASH", 0) })
    }

    private fun dashboard() = buildJsonObject {
        put("date", "2026-11-05"); put("timezone", "Asia/Jakarta"); put("summary", summary(25, 25, 25, 6250, items = 0, avoided = 0))
        put("recent_logs", buildJsonArray { repeat(3) { index -> add(buildJsonObject {
            put("id", "00000000-0000-4000-8000-00000000000${index + 1}")
            put("category", "PLASTIC"); put("method", "RECYCLE"); put("quantity_kind", "WEIGHT")
            put("weight_g", 250); put("item_count", JsonNull); put("activity_date", "2026-11-05")
            put("activity_timezone", "Asia/Jakarta"); put("notes", ""); put("version", 1)
            put("created_at", "2026-11-05T00:00:00Z"); put("updated_at", "2026-11-05T00:00:00Z")
        }) } })
        put("progress", buildJsonObject {
            put("active_log_count", 100); put("xp", 1000); put("level", 11)
            put("xp_into_level", 0); put("xp_to_next_level", 100); put("has_logged_today", true)
        })
    }
}
