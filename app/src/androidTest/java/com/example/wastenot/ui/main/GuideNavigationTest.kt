package com.example.wastenot.ui.main

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.wastenot.MainActivity
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource

class GuideNavigationTest {
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

    @Test fun lightGuideSearchBackAndPrefillKeepInputExplicit() = journey(false)
    @Test fun darkGuideSearchBackAndPrefillKeepInputExplicit() = journey(true)
    @Test fun searchBackAndPrefillWithTheNaturalAnimationClock() = journey(false, trace = false)

    private fun journey(dark: Boolean, trace: Boolean = true) {
        waitFor("650 g")
        compose.onNode(tabs and hasText("Profile")).performClick()
        compose.onNodeWithText("Settings").performScrollTo().performClick()
        compose.onNodeWithContentDescription("App theme").performScrollTo().performClick()
        compose.onNode(hasText(if (dark) "Dark" else "Light") and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNode(tabs and hasText("Guide")).performClick()
        waitFor("Know the material, then pick your next step.")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Plastic bottles"))
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        val mode = if (dark) "dark" else if (trace) "light" else "natural"
        capture("guide-list-$mode")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Plastic bottles"))
        compose.onNodeWithText("Plastic bottles").performScrollTo().performClick()
        waitFor("Guide details")
        capture("guide-detail-$mode")
        compose.onNodeWithText("Log this activity").performScrollTo()
        capture("guide-detail-end-$mode")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.onNodeWithContentDescription("Search guides").performScrollTo().performTextReplacement(" KARTON ")
        compose.onNodeWithContentDescription("Search guides").performImeAction()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Paper and cardboard"))
        compose.onNodeWithText("Plastic bottles").assertDoesNotExist()
        compose.onNodeWithText("Paper and cardboard").performScrollTo()
        val paperTop = compose.onNodeWithText("Paper and cardboard").fetchSemanticsNode().boundsInWindow.top
        val before = geometry()
        val outgoing = JSONArray()
        if (trace) {
            compose.mainClock.autoAdvance = false
            try {
                compose.onNodeWithText("Paper and cardboard").performClick()
                repeat(4) {
                    compose.mainClock.advanceTimeByFrame()
                    compose.waitForIdle()
                    if (compose.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().isNotEmpty()) outgoing.put(geometry())
                }
            } finally { compose.mainClock.autoAdvance = true }
        } else {
            compose.onNodeWithText("Paper and cardboard").performClick()
        }
        waitFor("Guide details")
        compose.onNodeWithText("Steps to check").performScrollTo().assertIsDisplayed()
        compose.onAllNodes(tabs).assertCountEquals(if (compose.activity.resources.configuration.screenWidthDp >= 600) 5 else 0)
        compose.onNodeWithContentDescription("Back").performClick()
        compose.mainClock.advanceTimeBy(200)
        compose.waitForIdle()
        val after = geometry()
        val report = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "qa/iteration-2/guide-scroll-$mode.json")
        report.parentFile!!.mkdirs()
        report.writeText(JSONObject().put("sampled_paper_top", paperTop).put("before", before).put("outgoing", outgoing).put("after", after).toString(2))
        capture("guide-return-$mode")
        assertEquals(paperTop, compose.onNodeWithText("Paper and cardboard").fetchSemanticsNode().boundsInWindow.top, 1f)
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.onNodeWithContentDescription("Search guides").assertTextContains(" KARTON ")
        compose.onNodeWithText("All categories").performScrollTo().performClick()
        compose.onNode(hasText("Glass") and hasAnyAncestor(isPopup())).performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("No guides found"))
        capture("guide-empty-$mode")
        compose.onNodeWithText("Clear search").performScrollTo().performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Glass containers"))
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        assertEquals("", compose.onNodeWithContentDescription("Search guides").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Glass containers"))
        compose.onNodeWithText("Glass containers").performScrollTo().performClick()
        waitFor("Guide details")
        compose.onNodeWithText("Log this activity").performScrollTo()
        assertRecordCount(5)
        compose.onNodeWithText("Log this activity").performClick()
        waitFor("Log activity")
        compose.onNodeWithText("Reuse").assertIsSelected()
        compose.onNodeWithContentDescription("Category").assertTextContains("Glass")
        assertEquals("", compose.onNodeWithContentDescription("Weight").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        assertRecordCount(5)
        capture("guide-prefill-initial-$mode")
        compose.activityRule.scenario.recreate()
        waitFor("Log activity")
        compose.onNodeWithText("Reuse").assertIsSelected()
        compose.onNodeWithContentDescription("Category").assertTextContains("Glass")
        compose.onNodeWithText("Save log").performScrollTo().performClick()
        compose.onNodeWithText("Enter a weight or an item count.").performScrollTo().assertIsDisplayed()
        assertRecordCount(5)
        compose.onNodeWithText("Recycle").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Unit").performScrollTo().performClick()
        compose.onNode(hasText("Items") and hasAnyAncestor(isPopup())).performClick()
        compose.onNodeWithContentDescription("Item count").performScrollTo().performTextReplacement("2")
        compose.onNodeWithText("Save log").performScrollTo()
        capture("guide-prefill-$mode")
        compose.onNodeWithText("Save log").performClick()
        waitFor("+10 XP for this new activity.")
        assertRecordCount(6)
        compose.onNodeWithText("Back to home").performScrollTo().performClick()
        compose.onNode(tabs and hasText("Home")).assertIsSelected()
    }

    private fun waitFor(text: String) {
        compose.waitUntil(30_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun geometry(): JSONObject {
        var ime = false
        val window = android.graphics.Rect()
        compose.runOnUiThread {
            ime = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
            compose.activity.window.decorView.getWindowVisibleDisplayFrame(window)
        }
        val list = compose.onNode(hasScrollToIndexAction()).fetchSemanticsNode()
        val viewport = list.boundsInWindow
        val paper = compose.onAllNodes(hasText("Paper and cardboard") and hasAnyAncestor(hasScrollToIndexAction())).fetchSemanticsNodes().firstOrNull()?.boundsInWindow
        val field = compose.onAllNodes(hasContentDescription("Search guides")).fetchSemanticsNodes().firstOrNull()
        return JSONObject().put("ime_visible", ime).put("viewport_top", viewport.top).put("viewport_bottom", viewport.bottom)
            .put("window_visible_bottom", window.bottom).put("search_present", field != null)
            .put("search_focused", field?.config?.getOrNull(SemanticsProperties.Focused) ?: false)
            .put("paper_top", paper?.top ?: JSONObject.NULL).put("paper_bottom", paper?.bottom ?: JSONObject.NULL)
            .put("scroll_value", list.config[SemanticsProperties.VerticalScrollAxisRange].value())
    }

    private fun assertRecordCount(expected: Int) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.runOnIdle { assertEquals(expected, JSONArray(context.getSharedPreferences("dummy_logs_dummy-owner", Context.MODE_PRIVATE).getString("records", null)).length()) }
    }

    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(200)
        compose.waitForIdle()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.waitForIdle(300, 5_000)
        val image = checkNotNull(automation.takeScreenshot())
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "qa/iteration-2/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        image.recycle()
    }
}
