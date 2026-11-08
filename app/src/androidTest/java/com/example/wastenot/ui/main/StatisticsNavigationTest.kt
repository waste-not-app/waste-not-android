package com.example.wastenot.ui.main

import android.graphics.Bitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.graphics.toArgb
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.MainActivity
import com.example.wastenot.theme.OnAccent
import java.io.File
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

    @Test fun statisticsRangeTableAndScrollSurviveTabsRecreationAndManualEntry() {
        compose.waitUntil(30_000) { compose.onAllNodesWithText("650 g").fetchSemanticsNodes().isNotEmpty() }
        tab("Statistik")
        compose.onNodeWithText("1,4 kg").assertExists()
        capture("statistics-app-top-light")
        compose.onNodeWithTag("daily-weight-chart").performScrollTo().assertIsDisplayed()
        capture("statistics-app-chart-light")
        compose.onNodeWithText("30 hari").performScrollTo().performClick()
        compose.onNodeWithText("Lihat angka harian").performScrollTo().performClick()
        compose.onNodeWithText("Angka harian").performScrollTo().assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Tutup angka harian").assertExists()
        tab("Profil")
        compose.onNodeWithText("Pengaturan").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Tema aplikasi").performScrollTo().performClick()
        compose.onNode(hasText("Gelap") and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithText("Tutup").performClick()
        compose.onNodeWithContentDescription("Kembali").performClick()
        tab("Statistik")
        compose.onNodeWithText("30 hari").performScrollTo().assertIsSelected()
        capture("statistics-app-top-dark")
        compose.onNodeWithTag("daily-weight-chart").performScrollTo().assertIsDisplayed()
        capture("statistics-app-chart-dark")
        val chartTop = compose.onNodeWithTag("daily-weight-chart").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithContentDescription("Catat atau scan sampah").performClick()
        compose.onNode(hasText("Input manual") and hasAnyAncestor(hasTestTag("capture-sheet"))).assertIsDisplayed().performClick()
        compose.onNodeWithText("Cara pengelolaan").assertExists()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNode(tabs and hasText("Statistik")).assertIsSelected()
        compose.onNodeWithText("Tutup angka harian").assertExists()
        assertEquals(chartTop, compose.onNodeWithTag("daily-weight-chart").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.onNodeWithText("50 XP total").assertDoesNotExist()
        tab("Profil")
        compose.onNodeWithText("50 XP total").performScrollTo().assertIsDisplayed()
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
        val label = compose.onNodeWithText("Catat / Scan", useUnmergedTree = true).fetchSemanticsNode().boundsInWindow
        val pixels = (label.top.toInt() until label.bottom.toInt()).sumOf { y ->
            (label.left.toInt() until label.right.toInt()).count { x -> bitmap.getPixel(x, y) == OnAccent.toArgb() }
        }
        assertTrue("Catat/Scan label must be painted in $name: $pixels foreground pixels", pixels > 30)
        bitmap.recycle()
    }
}
