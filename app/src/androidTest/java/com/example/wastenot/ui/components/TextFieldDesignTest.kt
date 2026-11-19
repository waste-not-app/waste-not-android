package com.example.wastenot.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.data.repository.SignOutReason
import com.example.wastenot.theme.*
import com.example.wastenot.ui.auth.AuthScreen
import com.example.wastenot.ui.auth.AuthUiState
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TextFieldDesignTest {
    @get:Rule val compose = createComposeRule()
    private val violations = mutableListOf<String>()

    @Test fun pendingAccountKeepsFilledValuesBorderAndPasswordActionReadable() {
        var dark by mutableStateOf(false)
        var changes = 0
        compose.setContent { WasteNotTheme(dark) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                AuthScreen(false, AuthUiState(email = "pengguna@example.com", password = "contoh-aman-12", submitting = true),
                    SignOutReason.NONE, { _, _ -> changes++ }, {}, {}, {}, Modifier.fillMaxSize())
            }
        } }
        for (theme in listOf(false, true)) {
            compose.runOnIdle { dark = theme }
            val scheme = if (theme) DarkColorScheme else LightColorScheme
            val email = compose.onNodeWithContentDescription("Email").performScrollTo().assertIsNotEnabled()
            assertEquals("pengguna@example.com", layout(email).layoutInput.text.text)
            checkColor("pending value", layout(email).layoutInput.style.color, scheme.onSurfaceVariant)
            capture(email, theme, "account-email", scheme.outlineVariant, scheme.surfaceContainer)
            val password = compose.onNodeWithContentDescription("Password").performScrollTo().assertIsNotEnabled()
            password.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
            val visibility = compose.onNodeWithContentDescription("Show password").assertIsNotEnabled()
            capture(password, theme, "account-password", scheme.outlineVariant, scheme.surfaceContainer, visibility)
            assertEquals("•".repeat("contoh-aman-12".length), layout(password).layoutInput.text.text)
            compose.runOnIdle { assertEquals(0, changes) }
        }
        assertTrue(violations.joinToString("\n"), violations.isEmpty())
    }

    @Test fun disabledEmptyFieldKeepsAnOpaquePlaceholderAndBorder() {
        var dark by mutableStateOf(false)
        compose.setContent { WasteNotTheme(dark) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                WasteTextField("", {}, "Email", Modifier.padding(20.dp).fillMaxWidth(), enabled = false,
                    labelAbove = true, placeholder = "name@example.com")
            }
        } }
        for (theme in listOf(false, true)) {
            compose.runOnIdle { dark = theme }
            val scheme = if (theme) DarkColorScheme else LightColorScheme
            val field = compose.onNodeWithContentDescription("Email").assertIsNotEnabled()
            val hint = layout(compose.onNodeWithText("name@example.com", useUnmergedTree = true))
            checkColor("disabled placeholder", hint.layoutInput.style.color, scheme.onSurfaceVariant)
            capture(field, theme, "disabled-empty", scheme.outlineVariant, scheme.surfaceContainer)
        }
        assertTrue(violations.joinToString("\n"), violations.isEmpty())
    }

    @Test fun lightFieldsKeepDistinctIdleFocusErrorAndEditingFeedback() = inspectEditing(false)

    @Test fun darkFieldsKeepDistinctIdleFocusErrorAndEditingFeedback() = inspectEditing(true)

    private fun inspectEditing(dark: Boolean) {
        var value by mutableStateOf("")
        var error by mutableStateOf<String?>(null)
        compose.setContent { WasteNotTheme(dark) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                WasteTextField(value, { value = it }, "Notes", Modifier.padding(20.dp).fillMaxWidth(),
                    helper = "Extra information", errorText = error, labelAbove = true, placeholder = "Write a note")
            }
        } }
        val scheme = if (dark) DarkColorScheme else LightColorScheme
        val fieldWell = if (dark) FieldWellDark else FieldWell
        val fieldFocus = if (dark) FieldWellFocusDark else FieldWellFocus
        val field = compose.onNodeWithContentDescription("Notes").assertIsEnabled().assertIsNotFocused()
        capture(field, dark, "idle", null, fieldWell)
        field.performClick().performTextReplacement("Updated note")
        field.assertIsFocused().assertTextContains("Updated note")
        capture(field, dark, "focus", scheme.primary, fieldFocus)
        compose.runOnIdle { error = "Check the note" }
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "Check the note"))
        compose.onNodeWithText("Check the note").assertIsDisplayed()
        capture(field, dark, "error", scheme.error, fieldFocus)
        assertTrue(violations.joinToString("\n"), violations.isEmpty())
    }

    private fun layout(node: SemanticsNodeInteraction): TextLayoutResult {
        val values = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(values) }
        return values.single()
    }

    private fun checkColor(name: String, actual: Color, expected: Color) {
        if (actual != expected) violations.add("$name $actual != $expected")
    }

    private fun capture(node: SemanticsNodeInteraction, dark: Boolean, name: String, border: Color?,
        background: Color, icon: SemanticsNodeInteraction? = null) {
        val scheme = if (dark) DarkColorScheme else LightColorScheme
        val theme = if (dark) "dark" else "light"
        val bounds = node.fetchSemanticsNode().boundsInWindow
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        compose.waitUntil(5_000) {
            val frame = automation.takeScreenshot() ?: return@waitUntil false
            val ready = frame.getPixel(1, frame.height / 2) == scheme.background.toArgb()
            frame.recycle(); ready
        }
        automation.waitForIdle(300, 5_000); automation.takeScreenshot()?.recycle(); android.os.SystemClock.sleep(150)
        val image = checkNotNull(automation.takeScreenshot())
        fun px(dp: Float) = with(compose.density) { dp.dp.toPx() }
        val actualBorder = image.getPixel(bounds.center.x.roundToInt(), (bounds.top + px(.5f)).roundToInt())
        val actualBackground = image.getPixel((bounds.left + px(4f)).roundToInt(), bounds.center.y.roundToInt())
        if (border != null && !near(actualBorder, border.toArgb())) violations.add("$name/$theme border $actualBorder != ${border.toArgb()}")
        if (!near(actualBackground, background.toArgb())) violations.add("$name/$theme background $actualBackground != ${background.toArgb()}")
        var glyph: Int? = null
        if (icon != null) {
            val area = icon.fetchSemanticsNode().boundsInWindow
            glyph = 0
            for (x in (area.center.x - px(12f)).roundToInt() until (area.center.x + px(12f)).roundToInt())
                for (y in (area.center.y - px(12f)).roundToInt() until (area.center.y + px(12f)).roundToInt())
                    if (near(image.getPixel(x, y), scheme.onSurfaceVariant.toArgb())) glyph++
            if (glyph < 20) violations.add("$name/$theme disabled visibility glyph has $glyph muted pixels")
        }
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "text-field-qa").apply { mkdirs() }
        File(directory, "$name-$theme.png").outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }; image.recycle()
        File(directory, "$name-$theme.json").writeText(JSONObject().put("theme", theme).put("field", name)
            .put("font_scale", InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration.fontScale)
            .put("border", actualBorder).put("expected_border", border?.toArgb()).put("background", actualBackground)
            .put("expected_background", background.toArgb()).put("muted_glyph_pixels", glyph)
            .put("violations", JSONArray(violations)).toString(2))
    }

    private fun near(actual: Int, expected: Int) = listOf(24, 16, 8, 0).all { shift ->
        abs((actual shr shift and 255) - (expected shr shift and 255)) <= 4
    }
}
