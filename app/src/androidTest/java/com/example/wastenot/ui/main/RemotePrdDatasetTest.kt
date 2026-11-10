package com.example.wastenot.ui.main

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.BuildConfig
import com.example.wastenot.MainActivity
import com.example.wastenot.ThemeFixtureRule
import com.example.wastenot.WasteNotApplication
import com.example.wastenot.data.local.LocalEditorDraftStore
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.repository.ActivityInput
import com.example.wastenot.data.repository.RemoteAccountSession
import com.example.wastenot.data.repository.SessionState
import com.example.wastenot.model.*
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
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

class RemotePrdDatasetTest {
    @get:Rule(order = 0) val appearance = ThemeFixtureRule()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun fiveRealRecordsKeepHomeStatisticsAndProfileConsistentAcrossThemes() = runTest(timeout = 4.minutes) {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("verifyLivePrdDataset") == "true")
        assertTrue(BuildConfig.DEBUG)
        assertEquals("https://pred-autoscaler.bccdev.id/wastenot/api/v1/", BuildConfig.API_BASE_URL)
        val container = (compose.activity.application as WasteNotApplication).container
        assertTrue(container.accountSession is RemoteAccountSession)
        val session = container.sessions
        val core = container.core
        val runId = UUID.fromString(requireNotNull(args.getString("fixtureRunId"))).toString()
        val email = "prd-" + runId + "@example.test"
        val steps = JSONArray()
        val evidence = JSONObject().put("run_id", runId).put("email", email).put("name", "Android PRD probe")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.getExternalFilesDir(null), "prd-dataset/result.json")
        fun record(step: String) {
            file.parentFile!!.mkdirs()
            file.writeText(evidence.put("passed_steps", steps.put(step)).toString(2))
        }
        fun waitFor(text: String) {
            compose.waitUntil(30_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        }
        fun show(text: String) { compose.onNodeWithText(text).performScrollTo().assertIsDisplayed() }
        fun tab(label: String) { compose.onNode(hasText(label) and isSelectable()).performClick() }
        fun top() {
            compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
                .performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -100_000f) }
        }
        fun capture(name: String) { captureUiPreview(compose, name, "prd-dataset", capturePanel = false) }
        var ownerId: String? = null
        try {
            waitFor("Belum punya akun? Daftar")
            assertTrue("Do not replace an existing account", session.state.value is SessionState.SignedOut)
            val user = success(session.register("Android PRD probe", email, UUID.randomUUID().toString() + "Az9!", ZoneId.of("Asia/Jakarta")))
            ownerId = user.id
            evidence.put("user_id", user.id)
            record("register_real_account")
            val today = success(core.dashboard()).date
            evidence.put("server_today", today.toString())
            val inputs = listOf(
                ActivityInput(WasteCategory.PLASTIC, DisposalMethod.RECYCLE, Quantity.Weight.of(250), today, "PRD plastic"),
                ActivityInput(WasteCategory.GLASS, DisposalMethod.REUSE, Quantity.Weight.of(400), today, "PRD glass"),
                ActivityInput(WasteCategory.PLASTIC, DisposalMethod.REDUCE, Quantity.Count.of(2), today, "PRD avoided"),
                ActivityInput(WasteCategory.ORGANIC, DisposalMethod.TRASH, Quantity.Weight.of(600), today.minusDays(1), "PRD organic"),
                ActivityInput(WasteCategory.PAPER, DisposalMethod.RECYCLE, Quantity.Weight.of(150), today.minusDays(1), "PRD paper"),
            )
            val logs = inputs.map { success(core.createLog(it, UUID.randomUUID().toString())).log }
            evidence.put("log_ids", JSONArray(logs.map { it.id }))
            assertEquals(logs.map { it.id }.toSet(), success(core.wasteLogs()).map { it.id }.toSet())
            val home = success(core.dashboard())
            assertEquals(today, home.date)
            assertEquals(user.timezone, home.timezone)
            assertEquals(QuantityTotals(650, 0), home.summary.handled)
            assertEquals(QuantityTotals(0, 2), home.summary.avoided)
            assertEquals(3L, home.summary.activityCount)
            assertEquals(2L, home.summary.handledActivityCount)
            assertEquals(1L, home.summary.recycledActivityCount)
            assertEquals(0, BigDecimal("50").compareTo(home.summary.recyclingRatePercent))
            assertEquals(UserProgress(5, 50, 1, 50, 50, 2, 2, true), home.progress)
            for (days in listOf(7, 30)) assertPeriod(success(core.statistics(ActivityDateRange.lastDays(today, days))), today, days)
            record("server_prd_totals_daily_categories_methods_and_progress")

            waitFor("Input manual")
            compose.activityRule.scenario.recreate()
            waitFor("650 g")
            record("recreated_activity_reads_real_server_dataset")
            for (theme in listOf("Terang", "Gelap")) {
                val suffix = if (theme == "Terang") "light" else "dark"
                tab("Profil")
                waitFor("50 XP dari 5 catatan aktif")
                compose.onNodeWithText("Pengaturan").performScrollTo().performClick()
                compose.onNodeWithContentDescription("Tema aplikasi").performScrollTo().performClick()
                compose.onNode(hasText(theme) and hasAnyAncestor(isDialog())).performClick().assertIsSelected()
                compose.onNodeWithText("Tutup").performClick()
                compose.onNodeWithContentDescription("Kembali").performClick()
                tab("Beranda")
                show("650 g")
                show("2 benda dihindari")
                compose.onNodeWithText("1 dari 2 aktivitas penanganan", substring = true).performScrollTo().assertIsDisplayed()
                show("50%")
                show("3 aktivitas tercatat")
                show("50 XP total")
                top()
                capture("home-" + suffix)

                tab("Statistik")
                waitFor("1,4 kg")
                compose.onNodeWithText("7 hari").performScrollTo().performClick()
                waitFor("1,4 kg")
                compose.onNodeWithText("7 hari").assertIsSelected()
                compose.onNode(hasText("1,4 kg") and hasAnyAncestor(hasTestTag("statistics-summary"))).assertExists()
                show("5 aktivitas tercatat")
                compose.onNodeWithText("2 dari 4 aktivitas penanganan", substring = true).performScrollTo().assertIsDisplayed()
                show("50%")
                show("2 benda dihindari")
                top()
                capture("statistics-week-" + suffix)
                compose.onNodeWithText("Lihat angka harian").performScrollTo().performClick()
                for ((date, grams) in listOf(today.minusDays(1) to 750, today to 650)) {
                    compose.onNodeWithTag("statistics-day-" + date).performScrollTo()
                        .assertTextEquals(WasteFormat.activityDate(date) + " · " + grams + " g · 0 benda")
                }
                capture("statistics-daily-" + suffix)
                compose.onNodeWithText("Tutup angka harian").performScrollTo().performClick()
                compose.onNode(hasText("Recycle") and hasText("40%"))
                    .performScrollTo().assertIsDisplayed()
                show("Menurut kategori")
                capture("statistics-methods-" + suffix)
                compose.onNode(hasText("Organik") and hasText("600 g")).performScrollTo().assertIsDisplayed()
                compose.onNode(hasText("Plastik") and hasText("250 g")).performScrollTo().assertIsDisplayed()
                compose.onNode(hasText("Kertas") and hasText("150 g")).performScrollTo().assertIsDisplayed()
                compose.onNode(hasText("Kaca") and hasText("400 g")).performScrollTo().assertIsDisplayed()
                capture("statistics-categories-" + suffix)
                compose.onNodeWithText("30 hari").performScrollTo().performClick()
                compose.waitUntil(30_000) {
                    compose.onAllNodesWithText(WasteFormat.activityDate(today.minusDays(29)) + " – " + WasteFormat.activityDate(today))
                        .fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithText("30 hari").assertIsSelected()
                show("1,4 kg")
                top()
                capture("statistics-month-" + suffix)

                tab("Profil")
                waitFor("50 XP dari 5 catatan aktif")
                compose.onAllNodesWithText("Level 1").onFirst().performScrollTo().assertIsDisplayed()
                show("50 XP lagi ke level 2")
                compose.onAllNodesWithText("2 hari").assertCountEquals(2)
                compose.onNodeWithText("Belum mencatat hari ini.").assertDoesNotExist()
                top()
                capture("profile-" + suffix)
                record("ui_prd_home_week_month_daily_categories_methods_profile_" + suffix)
            }
        } catch (failure: Throwable) {
            file.parentFile!!.mkdirs()
            runCatching { File(file.parentFile, "failure-semantics.txt").writeText(compose.onRoot(useUnmergedTree = true).printToString()) }
            runCatching { capture("failure") }
            throw failure
        } finally {
            val own = (session.state.value as? SessionState.Authenticated)?.user
            if (own != null && own.id == ownerId && own.email == email) session.logout()
            ownerId?.let { success(LocalEditorDraftStore.create(context, it).clear(null)) }
        }
    }

    private fun assertPeriod(period: StatisticsSnapshot, today: LocalDate, days: Int) {
        assertEquals(ActivityDateRange.lastDays(today, days), period.range)
        assertEquals(QuantityTotals(1400, 0), period.summary.handled)
        assertEquals(QuantityTotals(0, 2), period.summary.avoided)
        assertEquals(5L, period.summary.activityCount)
        assertEquals(4L, period.summary.handledActivityCount)
        assertEquals(2L, period.summary.recycledActivityCount)
        assertEquals(0, BigDecimal("50").compareTo(period.summary.recyclingRatePercent))
        assertEquals(days, period.daily.size)
        for (day in period.daily) {
            val (weight, count) = when (day.date) {
                today -> 650L to 3L
                today.minusDays(1) -> 750L to 2L
                else -> 0L to 0L
            }
            assertEquals(QuantityTotals(weight, 0), day.handled)
            assertEquals(count, day.activityCount)
        }
        assertEquals(mapOf(DisposalMethod.REDUCE to 1L, DisposalMethod.REUSE to 1L,
            DisposalMethod.RECYCLE to 2L, DisposalMethod.TRASH to 1L), period.methods)
        assertEquals(WasteCategory.entries.associateWith {
            QuantityTotals(when (it) {
                WasteCategory.PLASTIC -> 250L
                WasteCategory.GLASS -> 400L
                WasteCategory.PAPER -> 150L
                WasteCategory.ORGANIC -> 600L
                else -> 0L
            }, 0)
        }, period.categories)
    }

    private fun <T> success(result: ApiResult<T>): T {
        assertTrue("Expected successful backend response: " + result, result is ApiResult.Success)
        return (result as ApiResult.Success).value
    }
}
