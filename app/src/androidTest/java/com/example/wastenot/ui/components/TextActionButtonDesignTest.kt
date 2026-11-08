package com.example.wastenot.ui.components

import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.example.wastenot.theme.DarkColorScheme
import com.example.wastenot.theme.LightColorScheme
import com.example.wastenot.theme.WasteNotTheme
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TextActionButtonDesignTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lightTextActionsKeepKitColorsAndTransparentAvailableSurfaces() = inspect(false)
    @Test fun darkTextActionsKeepKitColorsAndTransparentAvailableSurfaces() = inspect(true)

    private fun inspect(dark: Boolean) {
        val theme = if (dark) "dark" else "light"
        val scheme = if (dark) DarkColorScheme else LightColorScheme
        val labels = listOf("Baca panduan", "Belum punya akun? Daftar", "Tampilkan rincian per hari", "Lihat riwayat")
        compose.setContent { WasteNotTheme(dark) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    labels.forEachIndexed { index, label ->
                        TextActionButton(label, {}, Modifier.fillMaxWidth(), enabled = index != 1)
                    }
                }
            }
        } }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "text-action-qa").apply { mkdirs() }
        val metrics = JSONArray()
        val violations = mutableListOf<String>()
        labels.forEachIndexed { index, label ->
            val node = compose.onNodeWithText(label).performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            if (index == 1) node.assertIsNotEnabled() else node.assertIsEnabled()
            val bounds = node.fetchSemanticsNode().boundsInWindow
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            if (layout.hasVisualOverflow) violations.add("$label: text overflows ${layout.size}")
            val expectedText = if (index == 1) scheme.onSurfaceVariant else scheme.primary
            assertEquals(label, expectedText, layout.layoutInput.style.color)
            automation.waitForIdle(300, 5_000)
            automation.takeScreenshot()?.recycle()
            android.os.SystemClock.sleep(150)
            val bitmap = checkNotNull(automation.takeScreenshot())
            val samples = JSONArray()
            val expectedBackground = if (index == 1) scheme.surfaceContainer.toArgb() else scheme.background.toArgb()
            for (fraction in listOf(.15f, .5f, .85f)) {
                val x = (bounds.left + bounds.width * fraction).roundToInt()
                val y = (bounds.top + with(compose.density) { 6.dp.toPx() }).roundToInt()
                val actual = bitmap.getPixel(x, y)
                if (!near(actual, expectedBackground)) violations.add("$label: surface $actual != $expectedBackground")
                val contrast = ColorUtils.calculateContrast(expectedText.toArgb(), actual)
                if (contrast < 4.5) violations.add("$label: contrast $contrast < 4.5")
                samples.put(JSONObject().put("background", actual).put("contrast", contrast))
            }
            val border = bitmap.getPixel((bounds.left + with(compose.density) { .5.dp.toPx() }).roundToInt(), bounds.center.y.roundToInt())
            val expectedBorder = if (index == 1) scheme.outlineVariant.toArgb() else expectedBackground
            if (!near(border, expectedBorder)) violations.add("$label: edge $border != $expectedBorder")
            File(directory, "text-$theme-$index.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
            metrics.put(JSONObject().put("label", label).put("enabled", index != 1).put("foreground", expectedText.toArgb())
                .put("edge", border).put("expected_edge", expectedBorder).put("width_px", bounds.width).put("height_px", bounds.height)
                .put("text_lines", layout.lineCount).put("overflow", layout.hasVisualOverflow).put("samples", samples))
        }
        File(directory, "text-$theme.json").writeText(JSONObject().put("theme", theme)
            .put("font_scale", instrumentation.targetContext.resources.configuration.fontScale)
            .put("buttons", metrics).put("violations", JSONArray(violations)).toString(2))
        assertTrue(violations.joinToString("\n"), violations.isEmpty())
    }

    @Test fun touchAndTabEnterRespectTheEnabledGateAndTheCallerWidth() {
        val enabled = mutableStateOf(true)
        var calls = 0
        compose.setContent { WasteNotTheme {
            Column(Modifier.fillMaxSize()) {
                TextActionButton("Lihat riwayat", { calls++; enabled.value = false }, enabled = enabled.value)
            }
        } }
        val action = compose.onNodeWithText("Lihat riwayat")
        assertTrue(action.fetchSemanticsNode().boundsInRoot.width < compose.onRoot().fetchSemanticsNode().boundsInRoot.width)
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_TAB)
        compose.waitForIdle()
        action.assertIsFocused().performKeyInput { pressKey(Key.Enter) }
        action.assertIsNotEnabled().performTouchInput { click() }
        compose.onRoot().performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle { assertEquals(1, calls); enabled.value = true }
        action.performTouchInput { click() }
        compose.runOnIdle { assertEquals(2, calls) }
    }

    private fun near(actual: Int, expected: Int) = listOf(24, 16, 8, 0).all { shift ->
        abs((actual shr shift and 255) - (expected shr shift and 255)) <= 4
    }
}
