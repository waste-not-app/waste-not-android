package com.example.wastenot.ui.main

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import java.io.File

class WideNavigationTest {
    @get:Rule(order = -1) val navigationData = com.example.wastenot.NavigationFixtureRule()
    @get:Rule(order = 0) val fixtures = object : ExternalResource() {
        override fun before() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            com.example.wastenot.clearEditorDraftFixtures(context)
            listOf("dummy_session", "dummy_logs_dummy-owner", "appearance").forEach {
                check(context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit())
            }
        }
    }
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    private val tabs = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    private fun SemanticsNodeInteraction.reveal(): SemanticsNodeInteraction {
        if (!isDisplayed()) performScrollTo()
        return this
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.waitForIdle(300, 5_000)
        val image = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val file = File(instrumentation.targetContext.cacheDir, "qa/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        image.recycle()
    }

    @Test fun railScrollsAndManualEditorFitsWideWindow() {
        val density = compose.activity.resources.displayMetrics.density
        val windowWidth = compose.runOnUiThread { compose.activity.window.decorView.width / density }
        assumeTrue("Run this case in an edge-to-edge window at least 600 dp wide ($windowWidth)", windowWidth >= 600)
        compose.waitUntil(30_000) { compose.onAllNodes(tabs).fetchSemanticsNodes().size == 5 }
        compose.waitForIdle()
        compose.onAllNodes(tabs).assertCountEquals(5)
        val tabNodes = compose.onAllNodes(tabs).fetchSemanticsNodes()
        assertTrue("All five tabs form a vertical starting rail", tabNodes.map { it.boundsInWindow.left }.distinct().size == 1)
        assertTrue("Rail leaves most of the window for content", tabNodes.all { it.boundsInWindow.right <= windowWidth * density / 3 })
        capture("wide-rail")
        listOf("Home", "History", "Guide", "Stats", "Profile").forEach { label ->
            compose.onNode(tabs and hasText(label)).reveal().performClick().assertIsSelected()
            compose.onNodeWithContentDescription("Log or scan waste").reveal().performClick()
            compose.onNode(hasText("Log manually") and hasAnyAncestor(hasTestTag("capture-sheet"))).assertIsDisplayed().performClick()
            compose.onAllNodes(tabs).assertCountEquals(0)
            compose.onNodeWithText("Log activity").assertIsDisplayed()
            compose.onNodeWithContentDescription("Weight").reveal().assertIsDisplayed()
            val field = compose.onNodeWithContentDescription("Weight").fetchSemanticsNode().boundsInWindow
            assertTrue("Editor respects the 560 dp form cap", field.width <= 560 * density + 1)
            if (label == "Profile") capture("wide-editor")
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNode(tabs and hasText(label)).reveal().assertIsSelected()
        }
    }
}
