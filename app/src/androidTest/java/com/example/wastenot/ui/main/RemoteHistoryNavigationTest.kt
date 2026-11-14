package com.example.wastenot.ui.main

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.BuildConfig
import com.example.wastenot.MainActivity
import com.example.wastenot.ThemeFixtureRule
import com.example.wastenot.WasteNotApplication
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.repository.ActivityInput
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
import org.junit.Rule
import org.junit.Test

class RemoteHistoryNavigationTest {
    @get:Rule(order = 0) val appearance = ThemeFixtureRule()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun serverFiltersFindUnloadedRowsAndPagesSurviveDetailAndRefresh() = runTest(timeout = 4.minutes) {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("verifyLiveHistory") == "true")
        assertTrue(BuildConfig.DEBUG)
        assertEquals("https://waste-not-api.bccdev.id/v1/", BuildConfig.API_BASE_URL)
        val runId = UUID.fromString(requireNotNull(args.getString("fixtureRunId"))).toString()
        val email = "history-$runId@example.test"
        val container = (compose.activity.application as WasteNotApplication).container
        val session = container.sessions
        val core = container.core
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.cacheDir, "qa/history-live/result.json")
        val steps = JSONArray()
        val evidence = JSONObject().put("run_id", runId).put("email", email).put("name", "Android history probe")
        fun record(step: String) {
            file.parentFile!!.mkdirs()
            file.writeText(evidence.put("passed_steps", steps.put(step)).toString(2))
        }
        fun waitFor(text: String, scroll: Boolean = false) {
            try {
                compose.waitUntil(30_000) {
                    if (!scroll) compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
                    else try {
                        compose.onNodeWithTag("history-list").performScrollToNode(hasText(text))
                        compose.onNodeWithText(text).assertIsDisplayed()
                        true
                    } catch (_: AssertionError) { false }
                }
            } catch (failure: Throwable) {
                file.parentFile!!.mkdirs()
                runCatching { File(file.parentFile, "failure-semantics.txt").writeText(compose.onRoot(useUnmergedTree = true).printToString()) }
                runCatching { captureUiPreview(compose, "failure", "history-live", capturePanel = false) }
                throw failure
            }
        }
        fun scrollTo(text: String) { waitFor(text, scroll = true) }
        fun capture(name: String) {
            captureUiPreview(compose, name, "history-live", capturePanel = false)
        }
        try {
            waitFor("Belum punya akun? Daftar")
            val user = success(session.register("Android history probe", email, UUID.randomUUID().toString() + "Az9!", ZoneId.of("Asia/Jakarta")))
            evidence.put("user_id", user.id)
            record("register_real_account")
            val today = success(core.dashboard()).date
            val created = (1..22).map { index ->
                success(core.createLog(ActivityInput(if (index == 22) WasteCategory.PAPER else WasteCategory.PLASTIC,
                    DisposalMethod.RECYCLE, Quantity.Count.of(index), if (index == 22) today.minusDays(1) else today,
                    "History record $index"), UUID.randomUUID().toString())).log
            }
            evidence.put("log_ids", JSONArray(created.map { it.id }))
            val first = core.wasteLogs() as ApiResult.Success
            assertEquals(20, first.value.size)
            assertFalse(first.value.any { it.id == created.last().id })
            val next = core.wasteLogs(cursor = first.page!!.nextCursor) as ApiResult.Success
            assertEquals(2, next.value.size)
            assertEquals(created.map { it.id }.toSet(), (first.value + next.value).map { it.id }.toSet())
            assertNull(next.page!!.nextCursor)
            record("https_cursor_pages_20_and_2")
            compose.onNode(hasText("Riwayat") and isSelectable()).performClick()
            waitFor("20 catatan dimuat.")
            capture("first-page")
            compose.onNodeWithContentDescription("Filter riwayat").performClick()
            compose.onNodeWithContentDescription("Kategori").performScrollTo().performClick()
            compose.onNode(hasText("Kertas") and hasAnyAncestor(isPopup())).performClick()
            compose.onNodeWithText("Terapkan filter").performScrollTo().performClick()
            waitFor("1 catatan sesuai filter dimuat.")
            compose.onNodeWithText("History record 22").assertExists()
            capture("server-filter-unloaded-record")
            record("ui_filter_finds_record_beyond_first_page")
            compose.onNodeWithText("Hapus filter").performClick()
            waitFor("20 catatan dimuat.")
            scrollTo("Muat catatan berikutnya")
            compose.onNodeWithText("Muat catatan berikutnya").performClick()
            scrollTo("History record 22")
            evidence.put("header_composed_at_last_record", compose.onAllNodesWithText("22 catatan dimuat.").fetchSemanticsNodes().isNotEmpty())
            compose.onNodeWithText("History record 22").performClick()
            waitFor("Ubah catatan")
            compose.onNodeWithContentDescription("Kembali").performClick()
            compose.onNodeWithText("History record 22").assertIsDisplayed()
            capture("detail-back-preserves-scroll")
            record("ui_paging_and_back_keep_second_page")
            scrollTo("Semua catatan sudah dimuat.")
            scrollTo("22 catatan dimuat.")
            compose.onNodeWithTag("history-list").performTouchInput { swipeDown() }
            waitFor("20 catatan dimuat.")
            capture("pull-refresh-first-page")
            record("ui_pull_refresh_restarts_first_page")
        } finally {
            session.logout()
        }
    }

    private fun <T> success(result: ApiResult<T>): T {
        assertTrue("Expected successful backend response: $result", result is ApiResult.Success)
        return (result as ApiResult.Success).value
    }
}
