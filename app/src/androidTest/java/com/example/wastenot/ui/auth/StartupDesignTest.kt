package com.example.wastenot.ui.auth

import android.graphics.Bitmap
import android.view.KeyEvent
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.theme.WasteNotTheme
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class StartupDesignTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun loadingAndRecoveryUseTheKitInBothThemesWithoutSimulationControls() {
        var dark by mutableStateOf(false)
        var error by mutableStateOf(false)
        var retries = 0; var logins = 0
        compose.setContent { WasteNotTheme(dark) {
            StartupScreen(error, { retries++ }, { logins++ }, Modifier.fillMaxSize())
        } }
        val appName = compose.activity.getString(com.example.wastenot.R.string.app_name)
        for (theme in listOf(false, true)) for (failed in listOf(false, true)) {
            compose.runOnIdle { dark = theme; error = failed }
            val heading = if (failed) "Belum bisa memeriksa sesi" else "Menyiapkan catatanmu…"
            assertReadable(appName)
            assertReadable(heading)
            compose.onNodeWithText(appName).performScrollTo()
            capture("startup-${if (theme) "dark" else "light"}-${if (failed) "error" else "loading"}")
            compose.onNodeWithText("Simulasikan", substring = true).assertDoesNotExist()
            compose.onNodeWithText("Pratinjau", substring = true).assertDoesNotExist()
            if (failed) {
                listOf("Coba lagi", "Masuk ulang").forEach { assertAction(it) }
                capture("startup-${if (theme) "dark" else "light"}-actions")
                compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate)).assertDoesNotExist()
            } else {
                compose.onNodeWithText("Coba lagi").assertDoesNotExist()
                compose.onNodeWithText("Masuk ulang").assertDoesNotExist()
                compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate)).performScrollTo().assertIsDisplayed()
            }
        }
        assertEquals(0, retries); assertEquals(0, logins)
    }

    @Test fun recoveryRequiresAnExplicitActionAndSupportsKeyboardFocus() {
        var retries = 0; var logins = 0
        compose.setContent { WasteNotTheme {
            StartupScreen(true, { retries++ }, { logins++ }, Modifier.fillMaxSize())
        } }
        for (attempt in 0 until 3) {
            key(KeyEvent.KEYCODE_TAB)
            if (compose.onAllNodes(hasText("Coba lagi") and isFocused()).fetchSemanticsNodes().isNotEmpty()) break
        }
        compose.onNodeWithText("Coba lagi").assertIsFocused().assertIsDisplayed()
        capture("startup-keyboard-focus")
        key(KeyEvent.KEYCODE_ENTER)
        assertEquals(1, retries); assertEquals(0, logins)
        key(KeyEvent.KEYCODE_TAB)
        compose.onNodeWithText("Masuk ulang").assertIsFocused().assertIsDisplayed()
        key(KeyEvent.KEYCODE_ENTER)
        assertEquals(1, retries); assertEquals(1, logins)
        compose.onNodeWithText("Coba lagi").performScrollTo().performClick()
        compose.onNodeWithText("Masuk ulang").performScrollTo().performClick()
        assertEquals(2, retries); assertEquals(2, logins)
    }

    private fun assertReadable(text: String) {
        val node = compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertFalse("$text: box=${layout.size}, paragraph=${layout.multiParagraph.width}, intrinsic=${layout.multiParagraph.intrinsics.maxIntrinsicWidth}", layout.didOverflowWidth)
        assertFalse(text, layouts.single().didOverflowHeight)
    }

    private fun key(code: Int) {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(code)
        compose.waitForIdle()
    }

    private fun assertAction(text: String) {
        val node = compose.onNodeWithText(text).performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        val bounds = node.getUnclippedBoundsInRoot(); val root = compose.onRoot().getUnclippedBoundsInRoot()
        assertTrue(text, bounds.top >= root.top && bounds.bottom <= root.bottom)
    }

    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(64)
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        compose.waitUntil(5_000) { compose.activity.hasWindowFocus() }
        val drawn = CountDownLatch(1)
        instrumentation.runOnMainSync {
            val decor = compose.activity.window.decorView
            val listener = object : ViewTreeObserver.OnDrawListener {
                override fun onDraw() { decor.post { decor.viewTreeObserver.removeOnDrawListener(this); drawn.countDown() } }
            }
            decor.viewTreeObserver.addOnDrawListener(listener)
            decor.invalidate()
        }
        assertTrue("The owned startup window must draw before capture", drawn.await(5, TimeUnit.SECONDS))
        instrumentation.uiAutomation.waitForIdle(500, 5_000)
        instrumentation.uiAutomation.takeScreenshot()?.recycle()
        android.os.SystemClock.sleep(150)
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "startup-qa").apply { mkdirs() }
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
