package com.example.wastenot.ui.components

import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.theme.*
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ActionButtonDesignTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lightActionsMatchTheKitAndKeepUnavailableLabelsReadable() = inspect(false)
    @Test fun darkActionsMatchTheKitAndKeepUnavailableLabelsReadable() = inspect(true)

    private fun inspect(dark: Boolean) {
        val theme = if (dark) "dark" else "light"
        val scheme = if (dark) DarkColorScheme else LightColorScheme
        compose.setContent {
            WasteNotTheme(dark) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Log actions", style = MaterialTheme.typography.headlineMedium)
                        ActionButton("Save log", {}, Modifier.fillMaxWidth())
                        ActionButton("Save changes", {}, Modifier.fillMaxWidth(), enabled = false)
                        ActionButton("Save", {}, Modifier.fillMaxWidth(), loading = true, loadingLabel = "Saving log changes…")
                        ActionButton("Delete log", {}, Modifier.fillMaxWidth(), destructive = true)
                    }
                }
            }
        }
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        compose.waitUntil(10_000) {
            val frame = automation.takeScreenshot() ?: return@waitUntil false
            val ready = frame.getPixel(1, frame.height / 2) == scheme.background.toArgb()
            frame.recycle()
            ready
        }
        val metrics = JSONArray()
        val violations = mutableListOf<String>()
        listOf("Save log", "Save changes", "Saving log changes…", "Delete log").forEachIndexed { index, label ->
            val node = compose.onNodeWithText(label).performScrollTo().assertIsDisplayed()
            if (index in 1..2) node.assertIsNotEnabled() else node.assertIsEnabled()
            val bounds = node.fetchSemanticsNode().boundsInWindow
            val layouts = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            val widestLine = (0 until layout.lineCount).maxOf { layout.getLineRight(it) - layout.getLineLeft(it) }
            if (layout.didOverflowWidth || layout.didOverflowHeight || widestLine > layout.size.width + 1) {
                violations.add("$label layout overflow ${layout.size}, widest line $widestLine")
            }
            if (bounds.height < with(compose.density) { 52.dp.toPx() } - 1) violations.add("$label height below 52 dp: ${bounds.height}")
            automation.waitForIdle(100, 5_000)
            val bitmap = checkNotNull(automation.takeScreenshot())
            val image = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "qa/iteration-2/actions/$theme-$index.png")
            image.parentFile!!.mkdirs()
            image.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            val text = layout.layoutInput.style.color
            val expectedText = when (index) { 1, 2 -> scheme.onSurfaceVariant; 3 -> scheme.onError; else -> scheme.onPrimary }
            if (text != expectedText) violations.add("$label foreground $text != $expectedText")
            val samples = JSONArray()
            listOf(0.15f, 0.5f, 0.85f).forEach { fraction ->
                val x = (bounds.left + bounds.width * fraction).roundToInt()
                val y = (bounds.top + with(compose.density) { 6.dp.toPx() }).roundToInt()
                val actual = bitmap.getPixel(x, y)
                val t = ((x - bounds.left) * bounds.width + (y - bounds.top) * bounds.height) / (bounds.width * bounds.width + bounds.height * bounds.height)
                val expected = when (index) {
                    1, 2 -> scheme.surfaceContainer.toArgb()
                    3 -> scheme.error.toArgb()
                    else -> ColorUtils.setAlphaComponent(ColorUtils.blendARGB(
                        (if (dark) ActionStartDark else ActionStart).toArgb(), (if (dark) ActionEndDark else ActionEnd).toArgb(), t), 255)
                }
                if (listOf(16, 8, 0).any { shift -> abs((actual shr shift and 255) - (expected shr shift and 255)) > 4 }) {
                    violations.add("$label background $actual != $expected at $fraction")
                }
                val contrast = ColorUtils.calculateContrast(text.toArgb(), actual)
                if (contrast < 4.5) violations.add("$label contrast $contrast < 4.5")
                samples.put(JSONObject().put("fraction", fraction).put("background", actual).put("expected", expected).put("contrast", contrast))
            }
            bitmap.recycle()
            metrics.put(JSONObject().put("label", label).put("foreground", text.toArgb()).put("expected_foreground", expectedText.toArgb())
                .put("width_px", bounds.width).put("height_px", bounds.height).put("text_lines", layout.lineCount)
                .put("layout_width", layout.size.width).put("layout_height", layout.size.height)
                .put("widest_line_px", widestLine).put("paragraph_width", layout.multiParagraph.width).put("paragraph_height", layout.multiParagraph.height)
                .put("overflow_width", layout.didOverflowWidth).put("overflow_height", layout.didOverflowHeight).put("image", image.name).put("samples", samples))
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.cacheDir, "qa/iteration-2/actions/$theme.json").writeText(JSONObject().put("theme", theme)
            .put("font_scale", context.resources.configuration.fontScale).put("buttons", metrics).put("violations", JSONArray(violations)).toString(2))
        assertTrue(violations.joinToString("\n"), violations.isEmpty())
    }

    @Test fun touchAndKeyboardRespectLoadingAndEnabledState() {
        val loading = mutableStateOf(false)
        var calls = 0
        compose.setContent { WasteNotTheme {
            Column {
                ActionButton("Save log", { calls++; loading.value = true }, loading = loading.value, loadingLabel = "Saving log…")
                ActionButton("Delete log", { calls++ }, enabled = false, destructive = true)
            }
        } }
        compose.onNodeWithText("Delete log").assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle { assertEquals(0, calls) }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_TAB)
        compose.waitForIdle()
        compose.onNodeWithText("Save log").assertIsFocused().performKeyInput { pressKey(Key.Enter) }
        compose.onNodeWithText("Saving log…").assertIsNotEnabled().performTouchInput { click() }
        compose.onRoot().performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle { assertEquals(1, calls); loading.value = false }
        compose.onNodeWithText("Save log").performTouchInput { click() }
        compose.runOnIdle { assertEquals(2, calls) }
    }
}
