package com.example.wastenot.ui.main

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewTreeObserver
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.MainActivity
import com.example.wastenot.BuildConfig
import com.example.wastenot.theme.*
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource

class ScreenKitReviewTest {
    @get:Rule(order = -1) val navigationData = com.example.wastenot.NavigationFixtureRule()
    @get:Rule(order = 0) val fixtures = object : ExternalResource() {
        override fun before() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            com.example.wastenot.clearEditorDraftFixtures(context)
            listOf("dummy_session", "dummy_logs_dummy-owner", "appearance").forEach {
                assertTrue(context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit())
            }
        }
    }
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    private val tabs = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
    private val rootScreens = linkedMapOf("S04" to "Home", "S07" to "History", "S11" to "Guide", "S13" to "Stats", "S14" to "Profile")
    private val rail get() = compose.activity.resources.configuration.screenWidthDp >= 600

    @Test fun screensAndChromeStayReadableAndEveryVisibleRailCaptureActionWorks() {
        compose.waitUntil(30_000) { compose.onAllNodes(tabs).fetchSemanticsNodes().size == 5 }
        for (dark in listOf(false, true)) {
            theme(dark)
            for ((id, label) in rootScreens) {
                tab(label)
                when (id) {
                    "S04" -> compose.waitUntil(10_000) { compose.onAllNodesWithText("650 g").fetchSemanticsNodes().isNotEmpty() }
                    "S07" -> compose.waitUntil(10_000) { compose.onAllNodesWithText("250 g").fetchSemanticsNodes().isNotEmpty() }
                    "S11" -> compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Plastic bottles"))
                    "S13" -> compose.onNodeWithText("1.4 kg").assertExists()
                    "S14" -> compose.onNodeWithText("50 XP total").assertExists()
                }
                top()
                capture(id, label, dark)
            }
            tab("Home")
            compose.onNodeWithText("Log manually").performScrollTo().performTouchInput { click() }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("How you handled it").fetchSemanticsNodes().isNotEmpty() }
            capture("S05", "Log activity", dark)
            back("Home")
            compose.onNodeWithText("Recent activity").performScrollTo()
            val bodyScroll = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and !hasAnyDescendant(tabs))
            val scrollMax = bodyScroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].maxValue()
            bodyScroll.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, scrollMax) }
            compose.waitForIdle()
            compose.onNodeWithText("250 g").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Plastic").fetchSemanticsNodes().isNotEmpty() }
            capture("S08", "Log details", dark)
            railCapture("S08", "Log details", "Home")
            back("Home")
            tab("Guide")
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Plastic bottles"))
            compose.onNodeWithText("Plastic bottles").performScrollTo().performTouchInput { click() }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Plastic bottles").fetchSemanticsNodes().isNotEmpty() }
            capture("S12", "Guide details", dark)
            railCapture("S12", "Guide details", "Guide")
            back("Guide")
            tab("Profile")
            compose.onNodeWithText("Settings").performScrollTo().performTouchInput { click() }
            capture("S16", "Settings", dark)
            railCapture("S16", "Settings", "Profile")
            back("Profile")
        }
    }

    @Test fun remainingEditorFilterConfirmationAndDialogsHaveReviewCaptures() {
        compose.waitUntil(30_000) { compose.onAllNodes(tabs).fetchSemanticsNodes().size == 5 }
        for (dark in listOf(false, true)) {
            theme(dark)
            val suffix = if (dark) "dark" else "light"
            tab("History")
            compose.onNodeWithContentDescription("Filter history").performClick()
            compose.onNodeWithTag("history-filter-sheet").assertIsDisplayed()
            captureUiPreview(compose, "S10-$suffix")
            compose.onNodeWithText("Reset").performScrollTo().assertIsDisplayed()
            captureUiPreview(compose, "S10-end-$suffix")
            compose.onNodeWithText("Cancel").performScrollTo().performClick()
            compose.onNodeWithText("250 g").performScrollTo().performClick()
            compose.onNodeWithText("Delete log").performScrollTo().performClick()
            compose.onNode(hasText("Delete this log?") and hasAnyAncestor(isDialog())).assertIsDisplayed()
            captureUiPreview(compose, "D01-$suffix")
            compose.onNodeWithText("Cancel").performClick()
            compose.onNodeWithText("Edit log").performScrollTo().performClick()
            compose.onNodeWithText("How you handled it").assertIsDisplayed()
            captureUiPreview(compose, "S09-$suffix")
            compose.onNodeWithText("Save changes").performScrollTo().assertIsDisplayed()
            captureUiPreview(compose, "S09-end-$suffix")
            compose.onNodeWithContentDescription("Notes (optional)").performScrollTo().performTextReplacement("Preview note fix $suffix")
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithText("Discard changes?").assertIsDisplayed()
            captureUiPreview(compose, "D03-$suffix")
            compose.onNodeWithText("Keep editing").performClick()
            compose.onNodeWithText("Save changes").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Log saved").fetchSemanticsNodes().isNotEmpty() }
            captureUiPreview(compose, "S06-$suffix")
            compose.onNodeWithText("Back to home").performScrollTo().assertIsDisplayed()
            captureUiPreview(compose, "S06-end-$suffix")
            compose.onNodeWithText("Back to home").performClick()
            tab("Profile")
            compose.onNodeWithText("Sign out").performScrollTo().performClick()
            compose.onNodeWithText("Sign out?").assertIsDisplayed()
            captureUiPreview(compose, "D02-$suffix")
            compose.onNodeWithText("Cancel").performClick()
        }
    }

    @Test fun scrolledSectionsStayReachableAboveTheDock() {
        compose.waitUntil(30_000) { compose.onAllNodes(tabs).fetchSemanticsNodes().size == 5 }
        for (dark in listOf(false, true)) {
            theme(dark)
            tab("Home")
            section("S04", "Recent activity", "recent", dark)
            compose.onNodeWithText("250 g").performScrollTo().performTouchInput { click() }
            section("S08", "Delete log", "actions", dark)
            back("Home")
            tab("History")
            section("S07", "Paper", "last-record", dark, lazy = true)
            tab("Guide")
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Plastic bottles"))
            compose.onNodeWithText("Plastic bottles").performScrollTo().performTouchInput { click() }
            section("S12", "Log this activity", "article-end", dark)
            back("Guide")
            section("S11", "Paper and cardboard", "last-guide", dark, lazy = true)
            tab("Stats")
            compose.onNodeWithText("7 days").performScrollTo().performTouchInput { click() }
            section("S13", "Weight handled per day", "week", dark)
            compose.onNodeWithText("Show daily numbers").performScrollTo().performTouchInput { click() }
            section("S13", "Daily numbers", "daily-values", dark)
            section("S13", "Handling methods", "methods", dark)
            section("S13", "Other", "categories-end", dark)
            compose.onNodeWithText("30 days").performScrollTo().performTouchInput { click() }
            section("S13", "Weight handled per day", "month", dark)
            tab("Profile")
            section("S14", "Sign out", "account-end", dark)
            compose.onNodeWithText("Settings").performScrollTo().performTouchInput { click() }
            section("S16", "Versi ${BuildConfig.VERSION_NAME}", "settings-end", dark)
            back("Profile")
            tab("Home")
            compose.onNodeWithText("Log manually").performScrollTo().performTouchInput { click() }
            section("S05", "Save log", "editor-end", dark)
            back("Home")
        }
    }

    private fun section(id: String, anchor: String, name: String, dark: Boolean, lazy: Boolean = false) {
        val selector = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and !hasAnyDescendant(tabs)
        val scroll = compose.onNode(selector)
        if (lazy) scroll.performScrollToNode(hasText(anchor))
        else compose.onNodeWithText(anchor).performScrollTo()
        val target = compose.onNodeWithText(anchor).fetchSemanticsNode().boundsInWindow
        val viewport = scroll.fetchSemanticsNode().boundsInWindow
        scroll.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, target.top - viewport.top) }
        compose.waitForIdle()
        capture(id, anchor, dark, name)
    }

    private fun theme(dark: Boolean) {
        tab("Profile")
        compose.onNodeWithText("Settings").performScrollTo().performTouchInput { click() }
        compose.onNodeWithContentDescription("App theme").performScrollTo().performTouchInput { click() }
        compose.onNode(hasText(if (dark) "Dark" else "Light") and hasAnyAncestor(isDialog())).performTouchInput { click() }
        compose.onNodeWithText("Close").performTouchInput { click() }
        back("Profile")
    }

    private fun tab(label: String) {
        val point = compose.onNode(tabs and hasText(label)).assertIsDisplayed().fetchSemanticsNode().boundsInWindow.center
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val down = SystemClock.uptimeMillis()
        // Android dispatch updates system touch mode after hardware-key tests.
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, point.x, point.y, 0)
                .apply { source = InputDevice.SOURCE_TOUCHSCREEN }
            try { instrumentation.sendPointerSync(event) } finally { event.recycle() }
        }
        compose.waitForIdle()
        compose.waitUntil(5_000) { compose.activity.window.decorView.isInTouchMode }
        compose.onNode(tabs and hasText(label)).assertIsSelected()
    }

    private fun back(label: String) {
        compose.onNodeWithContentDescription("Back").performTouchInput { click() }
        compose.waitForIdle()
        compose.onNode(tabs and hasText(label)).assertIsSelected()
    }

    private fun top() {
        val selector = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and !hasAnyDescendant(tabs)
        val node = compose.onNode(selector)
        val data = node.fetchSemanticsNode().config
        if (data.contains(SemanticsActions.ScrollToIndex)) node.performScrollToIndex(0)
        else {
            val offset = data[SemanticsProperties.VerticalScrollAxisRange].value()
            node.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -offset) }
        }
        compose.waitForIdle()
    }

    private fun railCapture(id: String, title: String, selected: String) {
        if (!rail) return
        compose.onNodeWithContentDescription("Log or scan waste").assertIsDisplayed().assertIsEnabled().performTouchInput { click() }
        try {
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("capture-sheet").fetchSemanticsNodes().isNotEmpty() }
        } catch (error: ComposeTimeoutException) {
            throw AssertionError("$id: visible rail Log/Scan must open the sheet", error)
        }
        compose.onNodeWithText("Scan waste").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close scan").performTouchInput { click() }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("capture-sheet").fetchSemanticsNodes().isEmpty() }
        compose.onNode(hasText(title) and !hasAnyAncestor(tabs), useUnmergedTree = true).assertIsDisplayed()
        compose.onNode(tabs and hasText(selected)).assertIsSelected()
        compose.onNodeWithText("Save log").assertDoesNotExist()
    }

    private fun capture(id: String, title: String, dark: Boolean, section: String? = null) {
        compose.waitForIdle()
        val caption = if (id == "S04" && section == null) compose.onNodeWithText("Hi,", substring = true, useUnmergedTree = true)
            else compose.onNode(hasText(title) and !hasAnyAncestor(tabs), useUnmergedTree = true)
        caption.assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        caption.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single(); val titleBounds = caption.fetchSemanticsNode().boundsInWindow
        val violations = mutableListOf<String>()
        val lines = JSONArray()
        repeat(layout.lineCount) { line ->
            val left = layout.getLineLeft(line); val right = layout.getLineRight(line)
            val top = layout.getLineTop(line); val bottom = layout.getLineBottom(line)
            val ellipsized = layout.isLineEllipsized(line)
            lines.put(JSONObject().put("left", left).put("right", right).put("top", top).put("bottom", bottom).put("ellipsized", ellipsized))
            if (left < -1 || right > layout.size.width + 1 || top < -1 || bottom > layout.size.height + 1 || ellipsized)
                violations.add("$id title line $line clipped: ($left,$top)-($right,$bottom) vs ${layout.size}, ellipsized=$ellipsized")
        }
        if (titleBounds.height + 1 < layout.size.height) violations.add("$id title clipped: $titleBounds vs ${layout.size}")
        val needsTabs = id in rootScreens || (rail && id != "S05")
        compose.onAllNodes(tabs).assertCountEquals(if (needsTabs) 5 else 0)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        compose.waitUntil(5_000) { compose.activity.hasWindowFocus() }
        val drawn = CountDownLatch(1)
        instrumentation.runOnMainSync {
            val decor = compose.activity.window.decorView
            val listener = object : ViewTreeObserver.OnDrawListener {
                override fun onDraw() { decor.post { decor.viewTreeObserver.removeOnDrawListener(this); drawn.countDown() } }
            }
            decor.viewTreeObserver.addOnDrawListener(listener); decor.invalidate()
        }
        assertTrue("The app must draw its own window", drawn.await(5, TimeUnit.SECONDS))
        instrumentation.uiAutomation.waitForIdle(500, 5_000)
        instrumentation.uiAutomation.takeScreenshot()?.recycle(); android.os.SystemClock.sleep(150)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "screen-kit-review").apply { mkdirs() }
        val name = "$id${section?.let { "-$it" } ?: ""}-${if (dark) "dark" else "light"}"
        File(directory, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        val paint = JSONArray()
        val scheme = if (dark) DarkColorScheme else LightColorScheme
        fun px(value: Float) = with(compose.density) { value.dp.toPx() }
        fun count(area: Rect, color: Int): Int {
            var n = 0
            for (x in area.left.roundToInt() until area.right.roundToInt())
                for (y in area.top.roundToInt() until area.bottom.roundToInt()) if (near(bitmap.getPixel(x, y), color)) n++
            return n
        }
        if (needsTabs) {
            compose.onAllNodes(tabs).fetchSemanticsNodes().forEach { node ->
                val label = node.config[SemanticsProperties.Text].joinToString { it.text }
                compose.onNode(tabs and hasText(label)).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
                val bounds = node.boundsInWindow
                val ink = count(Rect(bounds.center.x - px(12f), bounds.top + px(4f), bounds.center.x + px(12f), bounds.top + px(32f)),
                    (if (node.config[SemanticsProperties.Selected]) scheme.primary else scheme.onSurfaceVariant).toArgb())
                if (ink < 20) violations.add("$id/$label icon has $ink painted pixels")
                paint.put(JSONObject().put("label", label).put("glyph_pixels", ink))
            }
            val label = compose.onNodeWithText("Log / Scan", useUnmergedTree = true).fetchSemanticsNode().boundsInWindow
            val action = compose.onNodeWithContentDescription("Log or scan waste").fetchSemanticsNode().boundsInWindow
            val camera = if (rail) Rect(action.center.x - px(12f), label.top - px(32f), action.center.x + px(12f), label.top - px(8f))
                else Rect(label.left - px(32f), label.center.y - px(12f), label.left - px(8f), label.center.y + px(12f))
            val textInk = count(label, OnAccent.toArgb()); val cameraInk = count(camera, OnAccent.toArgb())
            if (textInk < 30 || cameraInk < 20) violations.add("$id capture must paint text ($textInk) and camera ($cameraInk)")
            paint.put(JSONObject().put("capture_text_pixels", textInk).put("capture_camera_pixels", cameraInk))
        }
        bitmap.recycle()
        val config = compose.activity.resources.configuration
        File(directory, "$name.json").writeText(JSONObject().put("screen", id).put("theme", if (dark) "dark" else "light")
            .put("font_scale", config.fontScale).put("width_dp", config.screenWidthDp).put("density_dpi", config.densityDpi)
            .put("title_lines", layout.lineCount).put("title_height_px", layout.size.height).put("title_visible_height_px", titleBounds.height)
            .put("title_text", layout.layoutInput.text.text).put("title_width_px", layout.size.width)
            .put("paragraph_width_px", layout.multiParagraph.width).put("paragraph_height_px", layout.multiParagraph.height)
            .put("title_line_bounds", lines).put("raw_visual_overflow", layout.hasVisualOverflow)
            .put("overflow_width", layout.didOverflowWidth).put("overflow_height", layout.didOverflowHeight)
            .put("navigation", paint).put("violations", JSONArray(violations)).toString(2))
        assertTrue(violations.joinToString("\n"), violations.isEmpty())
    }

    private fun near(actual: Int, expected: Int) = listOf(24, 16, 8, 0).all { shift ->
        abs((actual shr shift and 255) - (expected shr shift and 255)) <= 4
    }
}
