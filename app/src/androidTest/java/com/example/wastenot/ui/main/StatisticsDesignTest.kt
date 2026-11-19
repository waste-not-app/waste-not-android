package com.example.wastenot.ui.main

import android.graphics.Bitmap
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.data.demo.DemoActivityStore
import com.example.wastenot.data.dummy.dummyStatistics
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.model.*
import com.example.wastenot.theme.WasteNotTheme
import java.io.File
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StatisticsDesignTest {
    @get:Rule val compose = createComposeRule()
    private val store = DemoActivityStore(ZoneId.of("Asia/Jakarta"))
    private val today = store.referenceDate

    private fun show(data: () -> WorkspaceUiState, onRetry: () -> Unit = {}, onRecord: () -> Unit = {}) {
        val dark = InstrumentationRegistry.getArguments().getString("statisticsDark") == "true"
        compose.setContent { WasteNotTheme(dark) {
            var days by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(7) }
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                val records = data()
                StatisticsScreen(SnapshotState(dummyStatistics(records.records, ActivityDateRange.lastDays(today, days),
                    ZoneId.of("Asia/Jakarta")), records.loading, records.error), days, PaddingValues(), onRetry, onRecord,
                    onDays = { days = it }, modifier = Modifier.fillMaxSize())
            }
        } }
    }

    @Test fun canonicalWeekHasAChartAndZeroFilledTextAlternative() = runTest {
        val records = store.load().value
        show({ WorkspaceUiState(records = records, loading = false) })
        compose.onNodeWithText("1.4 kg").assertExists()
        compose.onNodeWithText("2 items avoided").assertExists()
        compose.onNodeWithTag("daily-weight-chart").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("daily-count-chart").assertDoesNotExist()
        capture("statistics-chart")
        compose.onNodeWithText("Show daily numbers").performScrollTo().performClick()
        compose.onNodeWithText("15 Sep 2026 · 0 g · 0 items").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("21 Sep 2026 · 650 g · 0 items").performScrollTo().assertIsDisplayed()
        capture("statistics-daily")
        compose.onNodeWithText("2 of 4 handling activities").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Handling methods").performScrollTo().assertIsDisplayed()
        compose.onNode(hasText("Recycle") and SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "2 of 5 activities"))
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Trash").performScrollTo().assertIsDisplayed()
        capture("statistics-methods")
        compose.onNodeWithText("By category").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Other").performScrollTo().assertIsDisplayed()
        capture("statistics-categories")
    }

    @Test fun countSeriesUsesItsOwnUnitAndUpdatesAfterDataChanges() = runTest {
        val record = store.load().value.first { it.method == DisposalMethod.RECYCLE }
        var records by mutableStateOf(listOf(record.copy(quantity = Quantity.Count.of(20))))
        show({ WorkspaceUiState(records = records, loading = false) })
        compose.onNodeWithTag("daily-count-chart").performScrollTo().assertIsDisplayed()
        capture("statistics-count")
        compose.onNodeWithText("Show daily numbers").performScrollTo().performClick()
        compose.onNodeWithText("21 Sep 2026 · 0 g · 20 items").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { records = listOf(record.copy(method = DisposalMethod.REDUCE, quantity = Quantity.Count.of(20))) }
        compose.onNodeWithTag("daily-count-chart").assertDoesNotExist()
        compose.onNodeWithText("20 items avoided").assertExists()
        compose.onNodeWithText("No handling activities yet").performScrollTo().assertIsDisplayed()
        capture("statistics-reduce-only")
    }

    @Test fun emptyRangeOffersAnActionAndThirtyDaysFindsOlderRecords() = runTest {
        val records = store.load().value.take(1).map { it.copy(activityDate = today.minusDays(10)) }
        var actions = 0
        show({ WorkspaceUiState(records = records, loading = false) }, onRecord = { actions++ })
        compose.onNodeWithText("No logs in this period").assertIsDisplayed()
        compose.onNodeWithText("Log activity").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, actions) }
        capture("statistics-empty")
        compose.onNodeWithText("30 days").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("${WasteFormat.activityDate(today.minusDays(29))} to ${WasteFormat.activityDate(today)}").assertExists()
        compose.onNodeWithText("No logs in this period").assertDoesNotExist()
        compose.onNodeWithTag("daily-weight-chart").performScrollTo().assertIsDisplayed()
        capture("statistics-month")
        compose.onNodeWithText("Show daily numbers").performScrollTo().performClick()
        compose.onNodeWithTag("statistics-day-2026-08-23").assertExists()
        compose.onNodeWithTag("statistics-day-2026-09-21").assertExists()
    }

    @Test fun refreshFailureRetainsTheLabeledPeriodAndRetryIsExplicit() = runTest {
        val records = store.load().value
        var retries = 0
        show({ WorkspaceUiState(records = records, loading = false, error = ApiFailureKind.NETWORK) }, onRetry = { retries++ })
        compose.onNodeWithText("1.4 kg").assertExists()
        compose.onNodeWithText("Try again").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, retries) }
        compose.onNodeWithText("30 days").performScrollTo().performClick()
        compose.onNodeWithText("1.4 kg").assertExists()
        capture("statistics-refresh-error")
    }

    @Test fun largeDailyValuesRemainReachableWithoutShrinkingText() = runTest {
        val template = store.load().value.first { it.method == DisposalMethod.RECYCLE }
        val records = (0..6).map { offset -> template.copy(id = "large-$offset",
            activityDate = today.minusDays(offset.toLong()), quantity = Quantity.Weight.of(100_000)) }
        show({ WorkspaceUiState(records = records, loading = false) })
        val plot = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange)
            and hasAnyAncestor(hasTestTag("daily-weight-chart")))
        plot.performScrollTo()
        compose.onNodeWithText("Scroll the chart to see other dates.").assertExists()
        val range = plot.fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange]
        assertTrue("The seven full-size values need a scrollable plot", range.maxValue() > 0)
        plot.performSemanticsAction(SemanticsActions.ScrollBy) { it(range.maxValue(), 0f) }
        compose.waitForIdle()
        val end = plot.fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange]
        assertEquals(end.maxValue(), end.value(), 1f)
        compose.onNodeWithText("21").assertIsDisplayed()
        capture("statistics-large-values")
        compose.onNodeWithText("Show daily numbers").performScrollTo().performClick()
        compose.onNodeWithText("21 Sep 2026 · 100.000 g · 0 items").performScrollTo().assertIsDisplayed()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "statistics-qa").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
