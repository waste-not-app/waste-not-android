package com.example.wastenot.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import org.junit.Rule
import org.junit.Test

class FormControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun unitChangesUseExactParserAndClearOtherQuantityKind() {
        val input = mutableStateOf(QuantityInput("250"))
        compose.setContent { WasteNotTheme { QuantityField(input.value, { input.value = it }) } }
        compose.onNodeWithText("kg").performClick().assertIsSelected()
        compose.onNode(hasSetTextAction()).assertTextContains("0.25")
        compose.runOnIdle {
            assertEquals(Quantity.Weight.of(250), (parseQuantity(input.value.rawText, input.value.unit) as QuantityParseResult.Valid).quantity)
        }
        compose.onNodeWithText("benda").performClick()
        compose.runOnIdle { assertEquals(QuantityInput(unit = QuantityUnit.ITEM), input.value) }
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

    @Test fun methodChoiceSelectsOneWithoutFillingAnInitialChoice() {
        val selected = mutableStateOf<DisposalMethod?>(null)
        compose.setContent { WasteNotTheme { MethodSelector(selected.value, { selected.value = it }) } }
        compose.runOnIdle { assertEquals(null, selected.value) }
        compose.onNodeWithText("Recycle").performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(DisposalMethod.RECYCLE, selected.value) }
        compose.onNodeWithText("Reduce").performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(DisposalMethod.REDUCE, selected.value) }
    }
}
