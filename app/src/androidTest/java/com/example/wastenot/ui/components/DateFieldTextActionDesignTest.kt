package com.example.wastenot.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.model.WasteFormat
import com.example.wastenot.theme.*
import java.io.File
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DateFieldTextActionDesignTest {
    @get:Rule val compose = createComposeRule()

    @Test fun cancelApplyAndUnavailableSelectionKeepTheirMeaningInBothThemes() {
        val initial = LocalDate.of(2026, 9, 21)
        val changed = initial.withDayOfMonth(14)
        var selected by mutableStateOf<LocalDate?>(initial)
        var dark by mutableStateOf(false)
        var updates = 0
        compose.setContent { WasteNotTheme(dark) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.padding(20.dp)) {
                    DateField(selected, initial.withDayOfMonth(1), initial.withDayOfMonth(30),
                        { updates++; selected = it }, Modifier.fillMaxWidth())
                }
            }
        } }
        for (theme in listOf(false, true)) {
            compose.runOnIdle { dark = theme; selected = initial }
            val before = updates
            compose.onNodeWithText(WasteFormat.activityDate(initial)).performClick()
            compose.onNodeWithText("Monday, September 14, 2026").performScrollTo().performClick()
            compose.onNodeWithText("Batal").assertIsDisplayed().assertHeightIsAtLeast(48.dp).performTouchInput { click() }
            compose.onNode(isDialog()).assertDoesNotExist()
            compose.runOnIdle { assertEquals(initial, selected); assertEquals(before, updates) }

            compose.onNodeWithText(WasteFormat.activityDate(initial)).performClick()
            compose.onNodeWithText("Monday, September 14, 2026").performScrollTo().performClick()
            compose.onNodeWithText("Terapkan").assertIsEnabled().assertHeightIsAtLeast(48.dp).performTouchInput { click() }
            compose.onNode(isDialog()).assertDoesNotExist()
            compose.runOnIdle { assertEquals(changed, selected); assertEquals(before + 1, updates); selected = null }

            compose.onNodeWithText("Pilih tanggal").performClick()
            val action = compose.onNodeWithText("Terapkan").assertIsDisplayed().assertIsNotEnabled().assertHeightIsAtLeast(48.dp)
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText("Terapkan", useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(if (theme) OnSurfaceVariantDark else OnSurfaceVariant, layouts.single().layoutInput.style.color)
            assertFalse(layouts.single().hasVisualOverflow)
            capture("date-text-${if (theme) "dark" else "light"}-unselected")
            action.performTouchInput { click() }
            compose.runOnIdle { assertNull(selected); assertEquals(before + 1, updates) }
            compose.onNode(isDialog()).assertExists()
            compose.onNodeWithText("Batal").performTouchInput { click() }
            compose.onNode(isDialog()).assertDoesNotExist()
        }
        compose.runOnIdle { assertEquals(2, updates) }
    }

    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.waitForIdle(300, 5_000)
        instrumentation.uiAutomation.takeScreenshot()?.recycle()
        android.os.SystemClock.sleep(150)
        val image = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "text-date-qa").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        image.recycle()
    }
}
