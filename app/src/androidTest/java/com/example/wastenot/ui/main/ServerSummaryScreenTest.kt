package com.example.wastenot.ui.main

import android.graphics.Bitmap
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.data.dummy.dummyDashboard
import com.example.wastenot.data.dummy.dummyStatistics
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.model.*
import com.example.wastenot.theme.WasteNotTheme
import java.io.File
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ServerSummaryScreenTest {
    @get:Rule val compose = createComposeRule()
    private val zone = ZoneId.of("Asia/Jakarta")
    private val date = LocalDate.of(2026, 11, 5)
    private val log = WasteLog("00000000-0000-4000-8000-000000000001", WasteCategory.PLASTIC,
        DisposalMethod.RECYCLE, Quantity.Weight.of(250), date, zone, "", 1, Instant.EPOCH, Instant.EPOCH)
    private val dark = InstrumentationRegistry.getArguments().getString("statisticsDark") == "true"

    @Test fun dashboardRendersServerTotalsAndProgressIndependentOfRecentRows() {
        val snapshot = dummyDashboard(listOf(log), date, zone).copy(
            summary = WasteSummary(QuantityTotals(6250, 0), QuantityTotals(0, 0), 25, 25, 25, BigDecimal(100)),
            progress = UserProgress(100, 1000, 11, 0, 100, null, null, true))
        var manual = 0; var histories = 0; var profiles = 0; var detail: String? = null
        compose.setContent { WasteNotTheme(dark) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                OverviewScreen(User("account", "Pengguna", "test@example.test", zone, null), SnapshotState(snapshot, false),
                    PaddingValues(), { manual++ }, { histories++ }, { detail = it }, {},
                    onProfile = { profiles++ })
            }
        } }
        compose.onNodeWithText("6,25 kg").assertExists()
        compose.onNodeWithText("25 activities logged").assertExists()
        compose.onNodeWithText("Level 11").performScrollTo().assertIsDisplayed()
        capture("server-home")
        compose.onNodeWithText("Log manually").performScrollTo().performClick()
        compose.onNodeWithText("See history").performScrollTo().performClick()
        compose.onNode(hasText("Plastic") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithContentDescription("Open profile").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, manual); assertEquals(1, histories); assertEquals(1, profiles); assertEquals(log.id, detail) }
    }

    @Test fun changingPeriodKeepsControlsAndNeverRelabelsOldNumbersAsNewData() {
        val week = dummyStatistics(listOf(log), ActivityDateRange.lastDays(date, 7), zone)
        var state by mutableStateOf(SnapshotState<StatisticsSnapshot>(week, false))
        var days by mutableIntStateOf(7)
        var retries = 0; var records = 0
        compose.setContent { WasteNotTheme(dark) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                StatisticsScreen(state, days, PaddingValues(), { retries++ }, { records++ },
                    onDays = { days = it; state = SnapshotState() })
            }
        } }
        compose.onNode(hasText("250 g") and hasAnyAncestor(hasTestTag("statistics-summary"))).assertExists()
        compose.onNodeWithText("30 days").performClick()
        compose.onNodeWithText("Loading summary…").assertIsDisplayed()
        compose.onNodeWithText("250 g").assertDoesNotExist()
        compose.onNodeWithText("30 days").assertIsSelected()
        capture("server-statistics-loading")
        compose.runOnIdle { state = SnapshotState(week, loading = false, error = ApiFailureKind.NETWORK) }
        compose.onNodeWithText("Try again").performScrollTo().assertHeightIsAtLeast(androidx.compose.ui.unit.Dp(48f)).performClick()
        compose.onNode(hasText("250 g") and hasAnyAncestor(hasTestTag("statistics-summary"))).assertExists()
        compose.onNodeWithText("Last data · 7 days · 30 Oct 2026 to 5 Nov 2026").assertExists()
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("Couldn't refresh the summary. The previously loaded data is still shown.", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertFalse(layouts.single().hasVisualOverflow)
        capture("server-statistics-error")
        compose.runOnIdle { state = SnapshotState(dummyStatistics(emptyList(), ActivityDateRange.lastDays(date, 30), zone), false) }
        compose.onNodeWithText("7 Okt 2026 to 5 Nov 2026").assertExists()
        compose.onNodeWithText("No logs in this period").assertExists()
        compose.onNodeWithText("Log activity").performScrollTo().performClick()
        capture("server-statistics-empty")
        compose.runOnIdle { assertEquals(1, retries); assertEquals(1, records); assertEquals(30, days) }
    }

    @Test fun refreshingKeepsTheLabeledSnapshotAndMethodPercentagesDoNotOverflowLong() {
        val count = Long.MAX_VALUE
        val period = dummyStatistics(listOf(log), ActivityDateRange.lastDays(date, 7), zone).copy(
            summary = WasteSummary(QuantityTotals(250, 0), QuantityTotals(0, 0), count, count, count, BigDecimal(100)),
            methods = DisposalMethod.entries.associateWith { if (it == DisposalMethod.RECYCLE) count else 0L })
        var retries = 0
        compose.setContent { WasteNotTheme(dark) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                StatisticsScreen(SnapshotState(period, false, ApiFailureKind.TIMEOUT), 7, PaddingValues(), { retries++ }, {}, {})
            }
        } }
        compose.onNodeWithText("30 Okt 2026 to 5 Nov 2026").assertExists()
        compose.onNodeWithText("Try again").performScrollTo().performClick()
        compose.onNode(hasText("250 g") and hasAnyAncestor(hasTestTag("statistics-summary"))).assertExists()
        compose.onAllNodesWithText("100%").onLast().performScrollTo().assertIsDisplayed()
        capture("server-statistics-long-method")
        compose.runOnIdle { assertEquals(1, retries) }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.waitForIdle(300, 5000)
        assertEquals("Screenshot must show the app, without a system dialog", instrumentation.targetContext.packageName,
            instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString())
        val image = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val file = File(instrumentation.targetContext.cacheDir, "qa/summary-screens/$name-${if (dark) "dark" else "light"}.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        image.recycle()
    }
}
