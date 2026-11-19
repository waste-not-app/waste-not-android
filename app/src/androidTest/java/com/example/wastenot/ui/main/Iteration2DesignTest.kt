package com.example.wastenot.ui.main

import android.content.Context
import android.graphics.Bitmap
import android.view.ViewTreeObserver
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.MainActivity
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.rules.ExternalResource

class Iteration2DesignTest {
    @get:Rule(order = -1) val navigationData = com.example.wastenot.NavigationFixtureRule()
    @get:Rule(order = 0) val fixtures = object : ExternalResource() {
        override fun before() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            com.example.wastenot.clearEditorDraftFixtures(context)
            listOf("dummy_session", "dummy_logs_dummy-owner", "appearance").forEach {
                context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
            }
        }
    }
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    private val tabs = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    @Test fun directDummyHomeDockAndManualFallbackRetainAllFiveStacks() {
        compose.waitUntil(30_000) { compose.onAllNodes(tabs).fetchSemanticsNodes().size == 5 }
        compose.onNodeWithText("See sample data").assertDoesNotExist()
        compose.onNodeWithText("Sample data", substring = true).assertDoesNotExist()
        waitForHome()
        val label = "Log / Scan"
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(label, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        for (line in 0 until layout.lineCount - 1) {
            val end = layout.getLineEnd(line, visibleEnd = true)
            assertTrue("Scan action must wrap between words, not inside them", end == 0 || end >= label.length ||
                !label[end - 1].isLetter() || !label[end].isLetter())
        }
        capture("home-light")
        compose.onNodeWithContentDescription("Open profile").performClick()
        compose.onNode(tabs and hasText("Profile")).assertIsSelected()
        compose.onNode(tabs and hasText("Home")).performClick()
        compose.onNodeWithText("Log manually").performScrollTo().performClick()
        compose.onNodeWithText("Log activity").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("See history").performScrollTo().performClick()
        compose.onNode(tabs and hasText("History")).assertIsSelected()
        compose.onNode(tabs and hasText("Home")).performClick()
        compose.onNodeWithText("250 g").performScrollTo().performClick()
        compose.onNodeWithText("Log details").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        listOf("Home", "History", "Guide", "Stats", "Profile").forEach { label ->
            compose.onNode(tabs and hasText(label)).performClick().assertIsSelected()
            compose.onAllNodes(tabs).fetchSemanticsNodes().forEach { node ->
                val text = node.config[SemanticsProperties.Text].joinToString { it.text }
                compose.onNode(tabs and hasText(text)).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
                val labelLayouts = mutableListOf<TextLayoutResult>()
                compose.onNode(hasText(text) and hasAnyAncestor(tabs), useUnmergedTree = true)
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(labelLayouts) }
                val labelLayout = labelLayouts.single()
                assertTrue("Navigation label must remain whole and visible: $text; lines=${labelLayout.lineCount}, " +
                    "size=${labelLayout.size}, paragraph=${labelLayout.multiParagraph.width}x${labelLayout.multiParagraph.height}, " +
                    "overflowWidth=${labelLayout.didOverflowWidth}, overflowHeight=${labelLayout.didOverflowHeight}, " +
                    "constraints=${labelLayout.layoutInput.constraints}",
                    labelLayout.lineCount == 1 && !labelLayout.hasVisualOverflow)
            }
            compose.onNodeWithContentDescription("Log or scan waste").performClick()
            compose.onNodeWithText("Scan waste").assertIsDisplayed()
            if (label == "Home") capture("capture-sheet")
            compose.onNode(hasText("Log manually") and hasAnyAncestor(hasTestTag("capture-sheet"))).assertIsDisplayed().performClick()
            compose.onAllNodes(tabs).assertCountEquals(0)
            compose.onNodeWithText("Log activity").assertIsDisplayed()
            compose.onNodeWithText("Save sample").assertDoesNotExist()
            compose.onNodeWithText("Save log").performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNode(tabs and hasText(label)).assertIsSelected()
        }
        compose.onNode(tabs and hasText("Profile")).performClick()
        compose.onNodeWithText("Settings").performScrollTo().performClick()
        compose.onNodeWithContentDescription("App theme").performScrollTo().performClick()
        compose.onNode(hasText("Dark") and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNode(tabs and hasText("Home")).performClick()
        waitForHome()
        capture("home-dark")
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        waitForHome()
        compose.onNodeWithText("See sample data").assertDoesNotExist()
    }

    private fun waitForHome() {
        compose.waitUntil(30_000) { compose.onAllNodesWithText("650 g").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("650 g").performScrollTo().assertIsDisplayed()
    }

    private fun capture(name: String) {
        if (name.startsWith("home-")) compose.onNodeWithText("Hi,", substring = true).performScrollTo()
        compose.mainClock.advanceTimeBy(64)
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        if (name.startsWith("home-")) {
            compose.waitUntil(5_000) { compose.activity.hasWindowFocus() }
            val drawn = CountDownLatch(1)
            instrumentation.runOnMainSync {
                val decor = compose.activity.window.decorView
                val listener = object : ViewTreeObserver.OnDrawListener {
                    override fun onDraw() { decor.post { decor.viewTreeObserver.removeOnDrawListener(this); drawn.countDown() } }
                }
                decor.viewTreeObserver.addOnDrawListener(listener)
                decor.invalidate()
            }
            assertTrue("The owned app window must draw before capture", drawn.await(5, TimeUnit.SECONDS))
        }
        instrumentation.uiAutomation.waitForIdle(500, 5_000)
        instrumentation.uiAutomation.takeScreenshot()?.recycle()
        android.os.SystemClock.sleep(150)
        val file = File(instrumentation.targetContext.cacheDir, "qa/iteration-2/$name.png")
        file.parentFile!!.mkdirs()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
}
