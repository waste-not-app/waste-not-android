package com.example.wastenot.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import com.example.wastenot.model.DisposalMethod
import com.example.wastenot.model.Quantity
import com.example.wastenot.model.QuantityInput
import com.example.wastenot.model.QuantityParseResult
import com.example.wastenot.model.QuantityUnit
import com.example.wastenot.model.parseQuantity
import com.example.wastenot.theme.WasteNotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class FormControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun unitChangesUseExactParserAndClearOtherQuantityKind() {
        val input = mutableStateOf(QuantityInput("250"))
        compose.setContent { WasteNotTheme { QuantityField(input.value, { input.value = it }) } }
        val unitLabel = compose.onNodeWithText("Satuan").fetchSemanticsNode().boundsInWindow
        val unitField = compose.onNodeWithContentDescription("Satuan").fetchSemanticsNode().boundsInWindow
        assertEquals("Unit label must align with its control", unitField.left, unitLabel.left, 1f)
        compose.onNodeWithContentDescription("Satuan").performClick()
        compose.onNodeWithText("kg").performClick()
        compose.onNodeWithContentDescription("Satuan").assertTextContains("kg")
        compose.onNode(hasSetTextAction()).assertTextContains("0.25")
        compose.runOnIdle {
            assertEquals(Quantity.Weight.of(250), (parseQuantity(input.value.rawText, input.value.unit) as QuantityParseResult.Valid).quantity)
        }
        compose.onNodeWithContentDescription("Satuan").performClick()
        compose.onNodeWithText("Benda").performClick()
        compose.runOnIdle { assertEquals(QuantityInput(unit = QuantityUnit.ITEM), input.value) }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithContentDescription("Satuan").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1, layouts.single().lineCount)
        assertFalse(layouts.single().hasVisualOverflow)
        compose.onNode(hasSetTextAction()).performTextReplacement("2,5")
        compose.runOnIdle { assertEquals(QuantityParseResult.Invalid.FORMAT, parseQuantity(input.value.rawText, input.value.unit)) }
    }

    @Test fun visibilityToggleChangesRenderedTextAndLoadingDisablesSubmit() {
        compose.setContent {
            WasteNotTheme {
                Column {
                    WasteTextField("contoh-aman-12", {}, "Kata sandi", password = true)
                    ActionButton("Masuk", {}, loading = true, loadingLabel = "Sedang masuk…")
                }
            }
        }
        compose.onNode(hasSetTextAction()).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        assertEquals("•".repeat("contoh-aman-12".length), displayedInput())
        compose.onNodeWithContentDescription("Tampilkan kata sandi").performClick()
        assertEquals("contoh-aman-12", displayedInput())
        compose.onNodeWithContentDescription("Sembunyikan kata sandi").performClick()
        assertEquals("•".repeat("contoh-aman-12".length), displayedInput())
        compose.onNodeWithText("Sedang masuk…").assertIsNotEnabled()
    }

    private fun displayedInput(): String {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNode(hasSetTextAction()).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single().layoutInput.text.text
    }

    private fun visibleCalendarDay(label: String): SemanticsNodeInteraction {
        val day = compose.onNodeWithText(label)
        val target = with(compose.density) { 48.dp.toPx() } - 1
        repeat(5) {
            if (day.fetchSemanticsNode().boundsInWindow.height >= target) return day
            // The month has a horizontal LazyRow; scroll the outer vertical viewport by touch.
            compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
                .performTouchInput { swipeUp() }
        }
        error("Calendar day is not fully reachable: $label")
    }

    @Test fun methodChoiceSelectsOneWithoutFillingAnInitialChoice() {
        val selected = mutableStateOf<DisposalMethod?>(null)
        compose.setContent { WasteNotTheme { MethodSelector(selected.value, { selected.value = it }) } }
        compose.runOnIdle { assertEquals(null, selected.value) }
        compose.onNodeWithText("Recycle").performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(DisposalMethod.RECYCLE, selected.value) }
        compose.onNodeWithText("Reduce").performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(DisposalMethod.REDUCE, selected.value) }
    }

    @Test fun calendarAppliesLocalDateAndRejectsFutureDay() {
        val today = LocalDate.of(2026, 9, 21)
        val selected = mutableStateOf(today)
        compose.setContent { WasteNotTheme { DateField(selected.value, today.minusDays(30), today, { selected.value = it }) } }
        compose.onNodeWithText("21 Sep 2026").performClick()
        compose.onNodeWithText("Tuesday, September 22, 2026").assertIsNotEnabled()
        visibleCalendarDay("Sunday, September 20, 2026").performClick()
        compose.onNodeWithText("Sunday, September 20, 2026").assertIsSelected()
        compose.onNodeWithText("Terapkan").performClick()
        compose.runOnIdle { assertEquals(LocalDate.of(2026, 9, 20), selected.value) }
        compose.onNodeWithText("20 Sep 2026").performClick()
        visibleCalendarDay("Saturday, September 19, 2026").performClick()
        compose.onNodeWithText("Batal").performClick()
        compose.runOnIdle { assertEquals(LocalDate.of(2026, 9, 20), selected.value) }
        compose.onNodeWithText("20 Sep 2026").performClick()
        compose.onNodeWithContentDescription("Change to previous month").performScrollTo().performClick()
        compose.onNodeWithText("Friday, August 21, 2026").assertIsNotEnabled()
        visibleCalendarDay("Monday, August 31, 2026").performClick()
        compose.onNodeWithText("Terapkan").performClick()
        compose.runOnIdle { assertEquals(LocalDate.of(2026, 8, 31), selected.value) }
    }

    @Test fun editCalendarAllowsOnlyOriginalOldDateOutsideCreateWindow() {
        val today = LocalDate.of(2026, 9, 21)
        val original = today.minusDays(90)
        val selected = mutableStateOf(original)
        compose.setContent { WasteNotTheme {
            DateField(selected.value, today.minusDays(30), today, { selected.value = it }, preservedDate = original)
        } }
        compose.onNodeWithText("23 Jun 2026").performClick()
        compose.onNodeWithText("Monday, June 22, 2026").assertIsNotEnabled()
        visibleCalendarDay("Tuesday, June 23, 2026").performClick()
        compose.onNodeWithText("Terapkan").performClick()
        compose.runOnIdle { assertEquals(original, selected.value) }
    }

    @Test fun staleCreateDateCanBeReviewedWithoutAllowingItOutsideTheWindow() {
        val today = LocalDate.of(2026, 9, 21)
        val stale = today.minusYears(2)
        var changes = 0
        compose.setContent { WasteNotTheme {
            DateField(stale, today.minusDays(30), today, { changes++ })
        } }
        compose.onNodeWithText("21 Sep 2024").performClick()
        compose.onNodeWithText("Terapkan").assertIsNotEnabled()
        compose.onNodeWithText("Batal").performClick()
        compose.runOnIdle { assertEquals(0, changes) }
    }
}
