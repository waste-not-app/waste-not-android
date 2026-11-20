package com.example.wastenot.ui.main

import android.view.ViewTreeObserver
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.data.dummy.dummyDashboard
import com.example.wastenot.data.dummy.dummyStatistics
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.model.*
import com.example.wastenot.theme.LocalWasteNotGradients
import com.example.wastenot.theme.WasteNotTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CoreAsyncStateDesignTest {
    @get:Rule val compose = createComposeRule()
    private val date = LocalDate.of(2026, 11, 8)
    private val zone = ZoneId.of("Asia/Jakarta")
    private val user = User("fixture", "Pengguna", "fixture@example.invalid", zone, null)
    private val log = WasteLog("fixture-log", WasteCategory.PLASTIC, DisposalMethod.RECYCLE,
        Quantity.Weight.of(250), date, zone, "", 1, Instant.EPOCH, Instant.EPOCH)
    private val problems = mutableListOf<String>()
    private lateinit var nativeActivity: ComponentActivity

    private fun show(dark: State<Boolean>, page: State<String>, content: @Composable () -> Unit) {
        compose.setContent { WasteNotTheme(dark.value) {
            val activity = checkNotNull(LocalActivity.current as? ComponentActivity)
            SideEffect {
                nativeActivity = activity
                activity.enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(0, 0) { dark.value },
                    navigationBarStyle = SystemBarStyle.auto(0, 0) { dark.value },
                )
            }
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(Modifier.fillMaxSize().background(LocalWasteNotGradients.current.canvas).safeDrawingPadding()) {
                    key(dark.value, page.value) { content() }
                }
            }
        } }
    }

    private fun inspect(name: String, title: String, symbol: Boolean = true) {
        compose.waitForIdle()
        val drawn = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val decor = nativeActivity.window.decorView
            val listener = object : ViewTreeObserver.OnDrawListener {
                override fun onDraw() {
                    decor.post { decor.viewTreeObserver.removeOnDrawListener(this); drawn.countDown() }
                }
            }
            decor.viewTreeObserver.addOnDrawListener(listener)
            decor.invalidate()
        }
        assertTrue("The fixture must draw before capture", drawn.await(5, TimeUnit.SECONDS))
        captureUiPreview(compose, name, "core-async-review")
        val heading = hasText(title) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
        if (compose.onAllNodes(heading).fetchSemanticsNodes().size != 1) problems.add("$name: missing state heading '$title'")
        val symbols = compose.onAllNodesWithTag("async-state-symbol").fetchSemanticsNodes()
        if (symbol && symbols.size != 1) problems.add("$name: missing functional state symbol")
        if (!symbol && symbols.isNotEmpty()) problems.add("$name: refresh must retain its compact banner")
        if (symbol && symbols.size == 1) compose.onNodeWithTag("async-state-symbol")
            .assertWidthIsEqualTo(64.dp).assertHeightIsEqualTo(64.dp)
    }

    private fun waitForQuery(value: String) {
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription("Search guides").fetchSemanticsNodes().any {
                it.config.contains(SemanticsProperties.EditableText) && it.config[SemanticsProperties.EditableText].text == value
            }
        }
        compose.onNodeWithContentDescription("Search guides").assertTextContains(value)
    }

    @Test fun historyUnknownIsNotZeroAndRecoveryKeepsCachedRecords() {
        val dark = mutableStateOf(false)
        val page = mutableStateOf("loading")
        var data by mutableStateOf(HistoryUiState())
        var retries = 0
        var moreRetries = 0
        var records = 0
        show(dark, page) {
            HistoryScreen(data, PaddingValues(), { records++ }, {}, { retries++ }, {}, { moreRetries++ }, Modifier.fillMaxSize(), date)
        }
        for (theme in listOf(false, true)) {
            val suffix = if (theme) "dark" else "light"
            compose.runOnIdle { dark.value = theme; page.value = "loading"; data = HistoryUiState() }
            inspect("history-loading-$suffix", "Loading logs…")
            if (compose.onAllNodesWithText("Showing 0 logs").fetchSemanticsNodes().isNotEmpty())
                problems.add("history-loading-$suffix: unresolved data presented as zero")
            compose.runOnIdle { page.value = "error"; data = HistoryUiState(loading = false, error = ApiFailureKind.NETWORK) }
            inspect("history-error-$suffix", "Couldn't load your logs.")
            compose.onNodeWithText("Try again").performScrollTo().assertIsEnabled().performClick()
            compose.runOnIdle { page.value = "empty"; data = HistoryUiState(loading = false) }
            inspect("history-empty-$suffix", "No logs yet")
            compose.onNodeWithText("Log manually").performScrollTo().assertIsEnabled().performClick()
            compose.runOnIdle { page.value = "cached"; data = HistoryUiState(listOf(log), false, error = ApiFailureKind.NETWORK) }
            compose.onNodeWithText("250 g").assertExists()
            captureUiPreview(compose, "history-cached-error-$suffix", "core-async-review")
            compose.onNodeWithTag("async-state-symbol").assertDoesNotExist()
            compose.onNodeWithText("Try again").performScrollTo().performClick()
            compose.runOnIdle { page.value = "paging"; data = HistoryUiState(listOf(log), false, loadingMore = true) }
            compose.onNodeWithTag("history-list").performScrollToNode(hasText("Loading more logs…"))
            compose.onNodeWithText("Loading more logs…").assertIsDisplayed()
            captureUiPreview(compose, "history-paging-$suffix", "core-async-review")
            compose.runOnIdle { page.value = "page-error"; data = HistoryUiState(listOf(log), false,
                nextCursor = "next", pageError = ApiFailureKind.NETWORK) }
            compose.onNodeWithText("Couldn't load more logs.").assertIsDisplayed()
            compose.onNodeWithText("250 g").assertExists()
            captureUiPreview(compose, "history-page-error-$suffix", "core-async-review")
            compose.onNodeWithText("Try again").performScrollTo().performClick()
            compose.runOnIdle { page.value = "page-restart"; data = data.copy(nextCursor = null, pageRestartRequired = true) }
            compose.onNodeWithText("Reload history").performScrollTo().assertIsEnabled().performClick()
        }
        compose.runOnIdle { assertEquals(6, retries); assertEquals(2, moreRetries); assertEquals(2, records) }
        assertTrue(problems.joinToString("; "), problems.isEmpty())
    }

    @Test fun guideCatalogAndSearchHaveDifferentRecoveryAndMissingDetailsCannotPrefill() {
        val dark = mutableStateOf(false)
        val page = mutableStateOf("loading")
        var data by mutableStateOf(GuideUiState())
        var detail by mutableStateOf<GuideDetailState?>(null)
        var searches = 0
        var exits = 0
        show(dark, page) {
            if (page.value.startsWith("detail")) GuideDetailScreen(detail, PaddingValues(), { searches++ }, { exits++ },
                { error("An unavailable guide cannot prefill an editor") }, {}, Modifier.fillMaxSize())
            else GuideListScreen(data, PaddingValues(), { data = data.copy(query = it) }, { data = data.copy(category = it) },
                { searches++ }, {}, {}, Modifier.fillMaxSize())
        }
        for (theme in listOf(false, true)) {
            val suffix = if (theme) "dark" else "light"
            compose.runOnIdle { dark.value = theme; page.value = "loading"; data = GuideUiState() }
            inspect("guide-loading-$suffix", "Loading guides…")
            compose.runOnIdle { page.value = "catalog"; data = GuideUiState(loading = false) }
            inspect("guide-catalog-empty-$suffix", "No guides found")
            val reload = compose.onAllNodesWithText("Reload guides").fetchSemanticsNodes()
            if (reload.size == 1) compose.onNodeWithText("Reload guides").performScrollTo().performClick()
            else problems.add("guide-catalog-empty-$suffix: no reload action for an unfiltered empty catalog")
            compose.runOnIdle { page.value = "filtered"; data = GuideUiState(query = "karton", loading = false) }
            waitForQuery("karton")
            inspect("guide-search-empty-$suffix", "No guides found")
            compose.onNodeWithText("Clear search").performScrollTo().performClick()
            compose.runOnIdle { assertEquals("", data.query); assertNull(data.category) }
            compose.runOnIdle { page.value = "error"; data = GuideUiState(query = "karton", loading = false, error = ApiFailureKind.NETWORK) }
            waitForQuery("karton")
            inspect("guide-error-$suffix", "Couldn't load the guides")
            compose.onNodeWithText("Try again").performScrollTo().performClick()
            compose.runOnIdle { page.value = "detail-loading"; detail = null }
            inspect("guide-detail-loading-$suffix", "Loading guides…")
            compose.runOnIdle { page.value = "detail-missing"; detail = GuideDetailState("missing", loading = false, error = ApiFailureKind.NOT_FOUND) }
            inspect("guide-detail-missing-$suffix", "This guide is no longer available")
            compose.onNodeWithText("Log this activity").assertDoesNotExist()
            compose.onNodeWithText("Back to guides").performScrollTo().performClick()
        }
        compose.runOnIdle {
            if (searches != 4) problems.add("guide recovery callbacks: expected 4, actual $searches")
            assertEquals(2, exits)
        }
        assertTrue(problems.joinToString("; "), problems.isEmpty())
    }

    @Test fun summariesAndMissingLogsKeepTheirDestinationsWithoutInventingValues() {
        val dark = mutableStateOf(false)
        val page = mutableStateOf("home-loading")
        var dashboard by mutableStateOf(SnapshotState<DashboardSnapshot>())
        var statistics by mutableStateOf(SnapshotState<StatisticsSnapshot>())
        var retries = 0
        var records = 0
        var histories = 0
        show(dark, page) {
            when {
                page.value.startsWith("home") -> OverviewScreen(user, dashboard, PaddingValues(), {}, {}, {}, { retries++ }, Modifier.fillMaxSize())
                page.value.startsWith("log") -> LogDetailScreen(null, page.value == "log-loading", page.value == "log-error",
                    PaddingValues(), { error("No record to edit") }, { error("No record to delete") }, { histories++ }, { retries++ }, Modifier.fillMaxSize())
                else -> StatisticsScreen(statistics, 30, PaddingValues(), { retries++ }, { records++ }, {}, Modifier.fillMaxSize())
            }
        }
        for (theme in listOf(false, true)) {
            val suffix = if (theme) "dark" else "light"
            compose.runOnIdle { dark.value = theme; page.value = "home-loading"; dashboard = SnapshotState() }
            inspect("home-loading-$suffix", "Loading summary…")
            compose.runOnIdle { page.value = "home-error"; dashboard = SnapshotState(loading = false, error = ApiFailureKind.NETWORK) }
            inspect("home-error-$suffix", "Couldn't load the summary")
            compose.onNodeWithText("Try again").performScrollTo().performClick()
            compose.runOnIdle { page.value = "statistics-loading"; statistics = SnapshotState() }
            inspect("statistics-loading-$suffix", "Loading summary…")
            compose.onNodeWithText("30 days").assertIsSelected()
            compose.onNodeWithText("7 days").assertIsEnabled()
            compose.runOnIdle { page.value = "statistics-empty"; statistics = SnapshotState(dummyStatistics(emptyList(), ActivityDateRange.lastDays(date, 30), zone), false) }
            inspect("statistics-empty-$suffix", "No logs in this period")
            compose.onNodeWithText("Log activity").performScrollTo().performClick()
            compose.runOnIdle { page.value = "home-cached"; dashboard = SnapshotState(dummyDashboard(listOf(log), date, zone), false, ApiFailureKind.NETWORK) }
            compose.onAllNodesWithText("250 g").assertCountEquals(2)
            compose.onNodeWithTag("async-state-symbol").assertDoesNotExist()
            captureUiPreview(compose, "home-cached-error-$suffix", "core-async-review")
            for ((mode, title) in listOf("log-loading" to "Loading logs…", "log-error" to "Couldn't load your logs.", "log-missing" to "Log unavailable")) {
                compose.runOnIdle { page.value = mode }
                inspect("$mode-$suffix", title)
                if (mode == "log-error") compose.onNodeWithText("Try again").performScrollTo().performClick()
                if (mode == "log-missing") compose.onNodeWithText("See history").performScrollTo().performClick()
                compose.onNodeWithText("Edit log").assertDoesNotExist()
            }
        }
        compose.runOnIdle { assertEquals(4, retries); assertEquals(2, records); assertEquals(2, histories) }
        assertTrue(problems.joinToString("; "), problems.isEmpty())
    }
}
