package com.example.wastenot.ui.main

import android.graphics.Bitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.MainActivity
import com.example.wastenot.model.WasteFormat
import com.example.wastenot.theme.OnAccent
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource

class StatisticsNavigationTest {
    @get:Rule(order = -1) val navigationData = com.example.wastenot.NavigationFixtureRule()
    @get:Rule(order = 0) val fixtures = object : ExternalResource() {
        override fun before() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            com.example.wastenot.clearEditorDraftFixtures(context)
            listOf("dummy_session", "dummy_logs_dummy-owner", "appearance").forEach {
                context.getSharedPreferences(it, android.content.Context.MODE_PRIVATE).edit().clear().commit()
            }
        }
    }
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    private val tabs = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
    private val weightReadout = hasAnyAncestor(hasTestTag("daily-weight-chart")) and
        SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)

    @Test fun statisticsRangeTableAndScrollSurviveTabsRecreationAndManualEntry() {
        compose.waitUntil(30_000) { compose.onAllNodesWithText("650 g").fetchSemanticsNodes().isNotEmpty() }
        changeTheme("Light")
        tab("Stats")
        compose.onNodeWithText("1.4 kg").assertExists()
        capture("statistics-app-top-light")
        revealWeightReadout()
        capture("statistics-app-chart-light")
        compose.onNodeWithText("30 days").performScrollTo().performClick()
        val today = LocalDate.now(ZoneId.of("Asia/Jakarta"))
        val firstDay = WasteFormat.activityDate(today.minusDays(29))
        val lastDay = WasteFormat.activityDate(today)
        waitForText("$firstDay to $lastDay")
        compose.onNodeWithText("Show daily numbers").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("$firstDay · 0 g · 0 items").performScrollTo().assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        waitForText("Hide daily numbers")
        changeTheme("Dark")
        tab("Stats")
        compose.onNodeWithText("30 days").performScrollTo().assertIsSelected()
        capture("statistics-app-top-dark")
        revealWeightReadout()
        capture("statistics-app-chart-dark")
        val chartTop = compose.onNodeWithTag("daily-weight-chart").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithContentDescription("Log or scan waste").performClick()
        compose.onNode(hasText("Log manually") and hasAnyAncestor(hasTestTag("capture-sheet"))).assertIsDisplayed().performClick()
        compose.onNodeWithText("How you handled it").assertExists()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNode(tabs and hasText("Stats")).assertIsSelected()
        compose.onNodeWithText("Hide daily numbers").assertExists()
        assertEquals(chartTop, compose.onNodeWithTag("daily-weight-chart").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.onNodeWithText("50 XP total").assertDoesNotExist()
        tab("Profile")
        compose.onNodeWithText("50 XP total").performScrollTo().assertIsDisplayed()
    }

    private fun revealWeightReadout() {
        compose.onNode(weightReadout).performScrollTo()
        val page = compose.onNode(
            hasAnyDescendant(weightReadout) and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange),
        )
        page.performSemanticsAction(SemanticsActions.ScrollBy) { scroll ->
            scroll(0f, with(compose.density) { 160.dp.toPx() })
        }
        compose.onNode(weightReadout).assertIsDisplayed()
    }

    private fun waitForText(text: String) {
        compose.waitUntil(20_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().size == 1 }
    }

    private fun changeTheme(label: String) {
        tab("Profile")
        compose.onNodeWithText("Settings").performScrollTo().performClick()
        compose.onNodeWithContentDescription("App theme").performScrollTo().performClick()
        compose.onNode(hasText(label) and hasAnyAncestor(isDialog())).performClick().assertIsSelected()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
    }

    private fun tab(label: String) {
        compose.onNode(tabs and hasText(label)).performClick()
        compose.waitForIdle()
        compose.onNode(tabs and hasText(label)).assertIsSelected()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "statistics-qa").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val label = compose.onNodeWithText("Log / Scan", useUnmergedTree = true).fetchSemanticsNode().boundsInWindow
        val pixels = (label.top.toInt() until label.bottom.toInt()).sumOf { y ->
            (label.left.toInt() until label.right.toInt()).count { x -> bitmap.getPixel(x, y) == OnAccent.toArgb() }
        }
        assertTrue("Log/Scan label must be painted in $name: $pixels foreground pixels", pixels > 30)
        bitmap.recycle()
    }
}
