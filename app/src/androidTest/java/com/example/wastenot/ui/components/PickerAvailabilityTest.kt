package com.example.wastenot.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.R
import com.example.wastenot.model.*
import com.example.wastenot.theme.*
import java.io.File
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PickerAvailabilityTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun disabledCategoryKeepsItsValuePlaceholderBorderAndArrowReadable() {
        var dark by mutableStateOf(false)
        var category by mutableStateOf<WasteCategory?>(null)
        var calls = 0
        compose.setContent { WasteNotTheme(dark) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                CategoryPicker(category, { calls++ }, Modifier.padding(20.dp).fillMaxWidth(), enabled = false)
            }
        } }
        val violations = mutableListOf<String>()
        for (theme in listOf(false, true)) for (value in listOf(null, WasteCategory.METAL)) {
            compose.runOnIdle { dark = theme; category = value }
            val scheme = if (theme) DarkColorScheme else LightColorScheme
            val field = compose.onNodeWithContentDescription(context.getString(R.string.category)).assertIsNotEnabled()
                .assertHeightIsAtLeast(56.dp)
            val textNode = if (value == null) compose.onNodeWithText(context.getString(R.string.choose_category), useUnmergedTree = true) else field
            val layouts = mutableListOf<TextLayoutResult>()
            textNode.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            if (layouts.single().layoutInput.style.color != scheme.onSurfaceVariant) violations.add("Category value/placeholder must use opaque muted")
            val bounds = field.fetchSemanticsNode().boundsInWindow
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            compose.waitUntil(5_000) {
                val frame = automation.takeScreenshot() ?: return@waitUntil false
                val ready = frame.getPixel(1, frame.height / 2) == scheme.background.toArgb(); frame.recycle(); ready
            }
            automation.waitForIdle(300, 5_000); automation.takeScreenshot()?.recycle(); android.os.SystemClock.sleep(150)
            val image = checkNotNull(automation.takeScreenshot())
            fun px(dp: Float) = with(compose.density) { dp.dp.toPx() }
            val border = image.getPixel(bounds.center.x.roundToInt(), (bounds.top + px(.5f)).roundToInt())
            val background = image.getPixel((bounds.left + px(4f)).roundToInt(), (bounds.top + px(24f)).roundToInt())
            if (!near(border, scheme.outlineVariant.toArgb())) violations.add("Category border $border != ${scheme.outlineVariant.toArgb()}")
            if (!near(background, scheme.surfaceContainer.toArgb())) violations.add("Category background $background != ${scheme.surfaceContainer.toArgb()}")
            var arrow = 0
            for (x in (bounds.right - px(48f)).roundToInt() until (bounds.right - px(8f)).roundToInt())
                for (y in (bounds.top + px(8f)).roundToInt() until minOf(bounds.bottom - px(8f), bounds.top + px(80f)).roundToInt())
                    if (near(image.getPixel(x, y), scheme.onSurfaceVariant.toArgb())) arrow++
            if (arrow < 20) violations.add("Category arrow has $arrow muted pixels")
            val directory = File(context.getExternalFilesDir(null), "picker-qa").apply { mkdirs() }
            val name = "category-${if (value == null) "empty" else "filled"}-${if (theme) "dark" else "light"}"
            File(directory, "$name.png").outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }; image.recycle()
            File(directory, "$name.json").writeText(JSONObject().put("theme", if (theme) "dark" else "light")
                .put("value", value?.name).put("font_scale", context.resources.configuration.fontScale)
                .put("border", border).put("expected_border", scheme.outline.toArgb()).put("background", background)
                .put("expected_background", scheme.surfaceContainer.toArgb()).put("muted_arrow_pixels", arrow)
                .put("violations", JSONArray(violations)).toString(2))
            field.performTouchInput { click() }; compose.runOnIdle { assertEquals(0, calls) }
        }
        assertTrue(violations.joinToString("\n"), violations.isEmpty())
    }

    @Test fun lockingCategoryClosesItsOpenMenuWithoutSelectingOrReopeningIt() {
        var enabled by mutableStateOf(true)
        var category by mutableStateOf<WasteCategory?>(null)
        var calls = 0
        compose.setContent { WasteNotTheme {
            CategoryPicker(category, { category = it; calls++ }, Modifier.fillMaxWidth(), enabled = enabled)
        } }
        val field = compose.onNodeWithContentDescription(context.getString(R.string.category))
        field.performTouchInput { click() }
        compose.onNodeWithText(context.getString(WasteCategory.PLASTIC.labelResource)).assertIsDisplayed()
        compose.runOnIdle { enabled = false }
        compose.onAllNodes(isPopup()).assertCountEquals(0)
        field.assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle { assertNull(category); assertEquals(0, calls); enabled = true }
        compose.onAllNodes(isPopup()).assertCountEquals(0)
        field.performTouchInput { click() }
        compose.onNodeWithText(context.getString(WasteCategory.PLASTIC.labelResource)).performTouchInput { click() }
        compose.runOnIdle { assertEquals(WasteCategory.PLASTIC, category); assertEquals(1, calls) }
    }

    @Test fun lockingQuantityClosesTheUnitMenuAndKeepsTheOriginalAmount() {
        var enabled by mutableStateOf(true)
        var quantity by mutableStateOf(QuantityInput("250"))
        var calls = 0
        compose.setContent { WasteNotTheme {
            QuantityField(quantity, { quantity = it; calls++ }, Modifier.fillMaxWidth(), enabled = enabled)
        } }
        val field = compose.onNodeWithContentDescription(context.getString(R.string.quantity_unit))
        field.performTouchInput { click() }
        compose.onNodeWithText("kg").assertIsDisplayed()
        compose.runOnIdle { enabled = false }
        compose.onAllNodes(isPopup()).assertCountEquals(0)
        field.assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle { assertEquals(QuantityInput("250"), quantity); assertEquals(0, calls); enabled = true }
        compose.onAllNodes(isPopup()).assertCountEquals(0)
        field.performTouchInput { click() }
        compose.onNodeWithText("kg").performTouchInput { click() }
        compose.onNode(hasSetTextAction()).assertTextContains("0.25")
        compose.runOnIdle { assertEquals(QuantityUnit.KILOGRAM, quantity.unit); assertEquals(1, calls) }
    }

    @Test fun lockingDateClosesTheCalendarAndNeverAppliesAnUnconfirmedChoice() {
        val original = LocalDate.of(2026, 11, 3)
        var enabled by mutableStateOf(true)
        var date by mutableStateOf(original)
        var calls = 0
        compose.setContent { WasteNotTheme {
            DateField(date, original.minusDays(30), original, { date = it; calls++ }, enabled = enabled)
        } }
        val field = compose.onNodeWithText(WasteFormat.activityDate(original))
        field.performTouchInput { click() }
        compose.onAllNodes(isDialog()).assertCountEquals(1)
        compose.runOnIdle { enabled = false }
        compose.onAllNodes(isDialog()).assertCountEquals(0)
        field.assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle { assertEquals(original, date); assertEquals(0, calls); enabled = true }
        compose.onAllNodes(isDialog()).assertCountEquals(0)
        field.performTouchInput { click() }
        compose.onNodeWithText(context.getString(R.string.cancel)).performTouchInput { click() }
        compose.runOnIdle { assertEquals(original, date); assertEquals(0, calls) }
    }

    private fun near(actual: Int, expected: Int) = listOf(24, 16, 8, 0).all { shift ->
        abs((actual shr shift and 255) - (expected shr shift and 255)) <= 4
    }
}
