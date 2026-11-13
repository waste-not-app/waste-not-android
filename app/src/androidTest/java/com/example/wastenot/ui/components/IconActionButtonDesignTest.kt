package com.example.wastenot.ui.components

import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.R
import com.example.wastenot.theme.*
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class IconActionButtonDesignTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lightIconActionsUseSoftSmallCornersAndReadableGlyphs() = inspect(false)
    @Test fun darkIconActionsUseSoftSmallCornersAndReadableGlyphs() = inspect(true)

    private fun inspect(dark: Boolean) {
        val theme = if (dark) "dark" else "light"
        val scheme = if (dark) DarkColorScheme else LightColorScheme
        val labels = listOf("Tampilan", "Filter tidak tersedia")
        compose.setContent { WasteNotTheme(dark) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    IconActionButton(R.drawable.ic_device, labels[0], {})
                    IconActionButton(R.drawable.ic_filter, labels[1], {}, enabled = false)
                }
            }
        } }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "icon-action-qa").apply { mkdirs() }
        val metrics = JSONArray(); val violations = mutableListOf<String>()
        labels.forEachIndexed { index, label ->
            val node = compose.onNodeWithContentDescription(label).assertIsDisplayed().assertHasClickAction()
                .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
            if (index == 0) node.assertIsEnabled() else node.assertIsNotEnabled()
            val bounds = node.fetchSemanticsNode().boundsInWindow
            automation.waitForIdle(300, 5_000); automation.takeScreenshot()?.recycle(); android.os.SystemClock.sleep(150)
            val bitmap = checkNotNull(automation.takeScreenshot())
            val tint = if (index == 0) scheme.onPrimaryContainer else scheme.onSurfaceVariant
            val samples = JSONArray()
            for ((xDp, yDp) in listOf(4f to 4f, 24f to 4f, 44f to 4f)) {
                val actual = bitmap.getPixel((bounds.left + with(compose.density) { xDp.dp.toPx() }).roundToInt(),
                    (bounds.top + with(compose.density) { yDp.dp.toPx() }).roundToInt())
                if (!near(actual, scheme.surfaceContainer.toArgb())) violations.add("$label corner/background $actual != ${scheme.surfaceContainer.toArgb()}")
                val ratio = ColorUtils.calculateContrast(tint.toArgb(), actual)
                if (ratio < 3) violations.add("$label icon contrast $ratio < 3")
                samples.put(JSONObject().put("background", actual).put("contrast", ratio))
            }
            val left = (bounds.center.x - with(compose.density) { 12.dp.toPx() }).roundToInt()
            val top = (bounds.center.y - with(compose.density) { 12.dp.toPx() }).roundToInt()
            val side = with(compose.density) { 24.dp.toPx() }.roundToInt(); var glyph = 0
            for (x in left until left + side) for (y in top until top + side) if (near(bitmap.getPixel(x, y), tint.toArgb())) glyph++
            if (glyph < 20) violations.add("$label glyph color not visible: $glyph pixels")
            File(directory, "icons-$theme-$index.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }; bitmap.recycle()
            metrics.put(JSONObject().put("label", label).put("enabled", index == 0).put("foreground", tint.toArgb())
                .put("width_px", bounds.width).put("height_px", bounds.height).put("glyph_pixels", glyph).put("samples", samples))
        }
        File(directory, "icons-$theme.json").writeText(JSONObject().put("theme", theme)
            .put("font_scale", instrumentation.targetContext.resources.configuration.fontScale)
            .put("icons", metrics).put("violations", JSONArray(violations)).toString(2))
        assertTrue(violations.joinToString("\n"), violations.isEmpty())
    }

    @Test fun touchAndTabEnterRespectAvailabilityAndAccessibleLabels() {
        val enabled = mutableStateOf(true); var calls = 0
        compose.setContent { WasteNotTheme {
            IconActionButton(R.drawable.ic_device, "Tampilan", { calls++; enabled.value = false }, enabled = enabled.value)
        } }
        val action = compose.onNodeWithContentDescription("Tampilan")
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_TAB); compose.waitForIdle()
        action.assertIsFocused().performKeyInput { pressKey(Key.Enter) }
        action.assertIsNotEnabled().performTouchInput { click() }
        compose.onRoot().performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle { assertEquals(1, calls); enabled.value = true }
        action.performTouchInput { click() }; compose.runOnIdle { assertEquals(2, calls) }
    }

    private fun near(actual: Int, expected: Int) = listOf(24, 16, 8, 0).all { shift ->
        abs((actual shr shift and 255) - (expected shr shift and 255)) <= 4
    }
}
