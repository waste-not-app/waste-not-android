package com.example.wastenot.ui.main

import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
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
import com.example.wastenot.theme.WasteNotTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ExitDialogsTest {
    @get:Rule val compose = createComposeRule()
    private val titles = listOf("Keluar dari akun?", "Buang perubahan?", "Tutup formulir?")
    private val confirms = listOf("Keluar", "Buang perubahan", "Tutup formulir")
    private fun cancel(kind: Int) = if (kind == 0) "Batal" else "Lanjut mengisi"

    @Composable private fun Dialog(kind: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
        if (kind == 0) LogoutDialog(onDismiss, onConfirm)
        else DiscardChangesDialog(kind == 2, onDismiss, onConfirm)
    }

    @Test fun allExitStatesStayReadableAndKeepFullWidthActionsInBothThemes() {
        var dark by mutableStateOf(false)
        var kind by mutableIntStateOf(0)
        var dismisses = 0; var confirms = 0
        compose.setContent { WasteNotTheme(dark) {
            Surface(color = MaterialTheme.colorScheme.background) { Box(Modifier.fillMaxSize()) }
            key(kind) { Dialog(kind, { dismisses++ }, { confirms++ }) }
        } }
        for (theme in listOf(false, true)) for (state in 0..2) {
            compose.runOnIdle { dark = theme; kind = state }
            assertReadable(titles[state])
            capture("exit-${if (theme) "dark" else "light"}-$state-heading")
            val body = if (state == 0) "Kamu perlu masuk lagi" else if (state == 1) "Perubahan belum disimpan." else "Menutup formulir tidak membatalkan"
            val message = compose.onNodeWithText(body, substring = true).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].single().text
            assertReadable(message)
            listOf(this.confirms[state], cancel(state)).forEach { label ->
                assertReadable(label)
                val action = compose.onNodeWithText(label).performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
                val bounds = action.getUnclippedBoundsInRoot()
                val root = compose.onNode(isDialog()).getUnclippedBoundsInRoot()
                assertTrue(label, bounds.left >= root.left && bounds.right <= root.right && bounds.top >= root.top && bounds.bottom <= root.bottom)
            }
            capture("exit-${if (theme) "dark" else "light"}-$state-actions")
            if (state == 2) {
                compose.onNodeWithText("Buang perubahan").assertDoesNotExist()
                compose.onNodeWithText("Hasilnya mungkin sudah tersimpan.", substring = true).assertExists()
            }
            compose.onNodeWithText("simulasi", substring = true, ignoreCase = true).assertDoesNotExist()
            compose.onNodeWithText("demo", substring = true, ignoreCase = true).assertDoesNotExist()
        }
        assertEquals(0, dismisses); assertEquals(0, confirms)
    }

    @Test fun backEscapeAndInitialKeyboardFocusCancelWithoutConfirming() {
        var visible by mutableStateOf(true)
        var kind by mutableIntStateOf(0)
        var dismisses = 0; var confirms = 0
        compose.setContent { WasteNotTheme {
            if (visible) Dialog(kind, { dismisses++; visible = false }, { confirms++; visible = false })
        } }
        for (state in 0..2) {
            for (code in listOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_ENTER)) {
                compose.runOnIdle { kind = state; visible = true }
                waitForCancelFocus(state)
                compose.onNodeWithText(cancel(state)).assertIsFocused().assertIsDisplayed()
                capture("exit-$state-cancel-focus-$code")
                key(code)
                compose.onNode(isDialog()).assertDoesNotExist()
            }
            compose.runOnIdle { visible = true }
            compose.onNodeWithText(cancel(state)).performScrollTo().performClick()
            compose.onNode(isDialog()).assertDoesNotExist()
        }
        assertEquals(12, dismisses); assertEquals(0, confirms)
    }

    @Test fun explicitConfirmationIsReachableByKeyboardAndTouch() {
        var visible by mutableStateOf(true)
        var kind by mutableIntStateOf(0)
        var dismisses = 0; var confirms = 0
        compose.setContent { WasteNotTheme {
            if (visible) Dialog(kind, { dismisses++ }, { confirms++; visible = false })
        } }
        for (state in 0..2) {
            compose.runOnIdle { kind = state; visible = true }
            waitForCancelFocus(state)
            compose.onNodeWithText(cancel(state)).assertIsFocused()
            for (attempt in 0 until 3) {
                key(KeyEvent.KEYCODE_TAB)
                if (compose.onAllNodes(hasText(this.confirms[state]) and isFocused()).fetchSemanticsNodes().isNotEmpty()) break
            }
            compose.onNodeWithText(this.confirms[state]).assertIsFocused().assertIsDisplayed()
            capture("exit-$state-confirm-focus")
            key(KeyEvent.KEYCODE_ENTER)
            compose.onNode(isDialog()).assertDoesNotExist()
            compose.runOnIdle { visible = true }
            compose.onNodeWithText(this.confirms[state]).performScrollTo().performClick()
            compose.onNode(isDialog()).assertDoesNotExist()
        }
        assertEquals(6, confirms); assertEquals(0, dismisses)
    }

    private fun assertReadable(text: String) {
        val node = compose.onNodeWithText(text, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertFalse("$text: box=${layouts.single().size}, paragraph=${layouts.single().multiParagraph.width}, intrinsic=${layouts.single().multiParagraph.intrinsics.maxIntrinsicWidth}", layouts.single().didOverflowWidth)
        assertFalse(text, layouts.single().didOverflowHeight)
    }

    private fun key(code: Int) {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(code)
        compose.waitForIdle()
    }

    private fun waitForCancelFocus(kind: Int) {
        if (compose.onAllNodes(hasText(cancel(kind)) and isFocused()).fetchSemanticsNodes().isEmpty()) key(KeyEvent.KEYCODE_TAB)
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText(cancel(kind)) and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(64)
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        instrumentation.uiAutomation.waitForIdle(500, 5_000)
        instrumentation.uiAutomation.takeScreenshot()?.recycle()
        android.os.SystemClock.sleep(150)
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "exit-qa").apply { mkdirs() }
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
