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
    private val rootScreens = linkedMapOf("S04" to "Beranda", "S07" to "Riwayat", "S11" to "Panduan", "S13" to "Statistik", "S14" to "Profil")
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
                    "S11" -> compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Botol plastik"))
                    "S13" -> compose.onNodeWithText("1,4 kg").assertExists()
                    "S14" -> compose.onNodeWithText("50 XP total").assertExists()
                }
                top()
                capture(id, label, dark)
            }
            tab("Beranda")
            compose.onNodeWithText("Input manual").performScrollTo().performTouchInput { click() }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Cara pengelolaan").fetchSemanticsNodes().isNotEmpty() }
            capture("S05", "Catat aktivitas", dark)
            back("Beranda")
            compose.onNodeWithText("250 g").performScrollTo().performTouchInput { click() }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Plastik").fetchSemanticsNodes().isNotEmpty() }
            capture("S08", "Detail catatan", dark)
            railCapture("S08", "Detail catatan", "Beranda")
            back("Beranda")
            tab("Panduan")
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Botol plastik"))
            compose.onNodeWithText("Botol plastik").performScrollTo().performTouchInput { click() }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Botol plastik").fetchSemanticsNodes().isNotEmpty() }
            capture("S12", "Detail panduan", dark)
            railCapture("S12", "Detail panduan", "Panduan")
            back("Panduan")
            tab("Profil")
            compose.onNodeWithText("Pengaturan").performScrollTo().performTouchInput { click() }
            capture("S16", "Pengaturan", dark)
            railCapture("S16", "Pengaturan", "Profil")
            back("Profil")
        }
    }

    private fun theme(dark: Boolean) {
        tab("Profil")
        compose.onNodeWithText("Pengaturan").performScrollTo().performTouchInput { click() }
        compose.onNodeWithContentDescription("Tema aplikasi").performScrollTo().performTouchInput { click() }
        compose.onNode(hasText(if (dark) "Gelap" else "Terang") and hasAnyAncestor(isDialog())).performTouchInput { click() }
        compose.onNodeWithText("Tutup").performTouchInput { click() }
        back("Profil")
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
        compose.onNodeWithContentDescription("Kembali").performTouchInput { click() }
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
        compose.onNodeWithContentDescription("Catat atau scan sampah").assertIsDisplayed().assertIsEnabled().performTouchInput { click() }
        try {
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("capture-sheet").fetchSemanticsNodes().isNotEmpty() }
        } catch (error: ComposeTimeoutException) {
            throw AssertionError("$id: visible rail Catat/Scan must open the sheet", error)
        }
        compose.onNodeWithText("Scan sampah").assertIsDisplayed()
        compose.onNodeWithContentDescription("Tutup scan").performTouchInput { click() }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("capture-sheet").fetchSemanticsNodes().isEmpty() }
        compose.onNode(hasText(title) and !hasAnyAncestor(tabs), useUnmergedTree = true).assertIsDisplayed()
        compose.onNode(tabs and hasText(selected)).assertIsSelected()
        compose.onNodeWithText("Simpan catatan").assertDoesNotExist()
    }

    private fun capture(id: String, title: String, dark: Boolean) {
        compose.waitForIdle()
        val caption = if (id == "S04") compose.onNodeWithText("Halo,", substring = true, useUnmergedTree = true)
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
        val name = "$id-${if (dark) "dark" else "light"}"
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
                    (if (node.config[SemanticsProperties.Selected]) OnAccent else scheme.onSurface).toArgb())
                if (ink < 20) violations.add("$id/$label icon has $ink painted pixels")
                paint.put(JSONObject().put("label", label).put("glyph_pixels", ink))
            }
            val label = compose.onNodeWithText("Catat / Scan", useUnmergedTree = true).fetchSemanticsNode().boundsInWindow
            val action = compose.onNodeWithContentDescription("Catat atau scan sampah").fetchSemanticsNode().boundsInWindow
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
