package com.example.wastenot.ui.auth

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
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
import com.example.wastenot.data.repository.SignOutReason
import com.example.wastenot.theme.*
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AuthTextActionDesignTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pendingFormsKeepSwitchActionsReadableAndOnlyAnExplicitIdleClickSwitches() {
        var dark by mutableStateOf(false)
        var register by mutableStateOf(false)
        var pending by mutableStateOf(true)
        var switches = 0
        compose.setContent { WasteNotTheme(dark) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                AuthScreen(register, AuthUiState(submitting = pending), false, SignOutReason.NONE,
                    { _, _ -> }, {}, { switches++ }, {}, Modifier.fillMaxSize())
            }
        } }
        for (theme in listOf(false, true)) for (registration in listOf(false, true)) {
            compose.runOnIdle { dark = theme; register = registration }
            val label = if (registration) "Sudah punya akun? Masuk" else "Belum punya akun? Daftar"
            val node = compose.onNodeWithText(label).performScrollTo().assertIsDisplayed().assertIsNotEnabled().assertHeightIsAtLeast(48.dp)
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            capture("auth-text-${if (theme) "dark" else "light"}-${if (registration) "register" else "login"}")
            assertEquals("Pending form switch must use the opaque kit muted label",
                if (theme) OnSurfaceVariantDark else OnSurfaceVariant, layouts.single().layoutInput.style.color)
            assertFalse(label, layouts.single().hasVisualOverflow)
            node.performTouchInput { click() }
            compose.runOnIdle { assertEquals(0, switches) }
        }
        compose.runOnIdle { pending = false }
        compose.onNodeWithText("Sudah punya akun? Masuk").performScrollTo().assertIsEnabled().performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, switches) }
    }

    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.waitForIdle(300, 5_000)
        instrumentation.uiAutomation.takeScreenshot()?.recycle()
        android.os.SystemClock.sleep(150)
        val image = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "text-action-qa").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        image.recycle()
    }
}
