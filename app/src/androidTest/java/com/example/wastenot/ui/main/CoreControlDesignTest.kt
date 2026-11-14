package com.example.wastenot.ui.main

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.model.User
import com.example.wastenot.model.UserProgress
import com.example.wastenot.theme.ThemeMode
import com.example.wastenot.theme.WasteNotTheme
import androidx.compose.ui.graphics.toArgb
import com.example.wastenot.theme.DarkColorScheme
import com.example.wastenot.theme.LightColorScheme
import java.io.File
import java.time.ZoneId
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CoreControlDesignTest {
    @get:Rule val compose = createComposeRule()
    private val density get() = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density

    @Test fun xpUsesAnEightDpContinuousTrackInBothThemes() {
        var dark by mutableStateOf(false)
        compose.setContent { WasteNotTheme(dark) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    ProgressMeter(UserProgress(5, 50, 1, 50, 50, null, null, false))
                }
            }
        } }
        val errors = mutableListOf<String>()
        for (theme in listOf(false, true)) {
            compose.runOnIdle { dark = theme }
            val name = if (theme) "dark" else "light"
            val scheme = if (theme) DarkColorScheme else LightColorScheme
            val primary = scheme.primary.toArgb()
            val background = scheme.background.toArgb()
            val bar = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            val image = capture(bar, "xp-$name")
            val rows = (0 until image.height).filter { image.getPixel(image.width / 4, it) == primary }
            if (abs(rows.size - 8 * density) > 1) errors.add("$name: XP paint height ${rows.size}px, expected ${8 * density}px")
            if (rows.isEmpty()) errors.add("$name: primary paint missing") else {
                val y = rows[rows.size / 2]
                val inset = (8 * density).toInt()
                val gaps = (inset until image.width - inset).count { image.getPixel(it, y) == background }
                if (gaps != 0) errors.add("$name: $gaps pixels interrupt the XP track")
            }
            image.recycle()
            captureUiPreview(compose, "xp-fixture-$name", "core-control-review")
        }
        assertTrue(errors.joinToString("; "), errors.isEmpty())
    }

    @Test fun themeChoiceUsesTheInputBorderTypeAndSizeAndKeepsItsAction() {
        var dark by mutableStateOf(false)
        var opened = 0
        val user = User("fixture", "Pengguna", "fixture@example.invalid", ZoneId.of("Asia/Jakarta"), null)
        compose.setContent { WasteNotTheme(dark) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                SettingsScreen(user, if (dark) ThemeMode.DARK else ThemeMode.LIGHT, PaddingValues(), { opened++ })
            }
        } }
        val errors = mutableListOf<String>()
        for (theme in listOf(false, true)) {
            compose.runOnIdle { dark = theme }
            val name = if (theme) "dark" else "light"
            val label = if (theme) "Gelap" else "Terang"
            val control = compose.onNodeWithContentDescription("Tema aplikasi").assertIsDisplayed()
            val image = capture(control, "theme-$name")
            if (image.height + 1 < 56 * density) errors.add("$name: theme height ${image.height}px, expected >= ${56 * density}px")
            image.recycle()
            val layout = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layout) }
            if (layout.single().layoutInput.style.fontSize.value != 16f) errors.add("$name: theme value is not 16sp")
            captureUiPreview(compose, "settings-fixture-$name", "core-control-review")
            control.performClick()
        }
        compose.runOnIdle { assertEquals(2, opened) }
        assertTrue(errors.joinToString("; "), errors.isEmpty())
    }

    private fun capture(node: SemanticsNodeInteraction, name: String): Bitmap {
        val image = node.captureToImage().asAndroidBitmap()
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "core-control-review").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        return image
    }
}
