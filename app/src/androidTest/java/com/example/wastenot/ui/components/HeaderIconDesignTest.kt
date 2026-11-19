package com.example.wastenot.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.data.repository.SignOutReason
import com.example.wastenot.model.*
import com.example.wastenot.theme.*
import com.example.wastenot.ui.auth.AuthScreen
import com.example.wastenot.ui.auth.AuthUiState
import com.example.wastenot.ui.main.OverviewScreen
import com.example.wastenot.ui.main.WorkspaceUiState
import java.io.File
import androidx.core.graphics.ColorUtils
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class HeaderIconDesignTest {
    @get:Rule val compose = createComposeRule()

    @Test fun accountHeaderUsesTheKitForThemeAndBackWithoutChangingTheirActions() {
        var dark by mutableStateOf(false)
        var themes = 0; var backs = 0
        compose.setContent { WasteNotTheme(dark) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                AuthScreen(true, AuthUiState(), SignOutReason.NONE, { _, _ -> }, {}, {},
                    { themes++ }, Modifier.fillMaxSize(), onBack = { backs++ })
            }
        } }
        for (theme in listOf(false, true)) {
            compose.runOnIdle { dark = theme }
            inspect("Display", theme, "auth-theme")
            inspect("Back", theme, "auth-back")
            compose.onNodeWithContentDescription("Display").performTouchInput { click() }
            compose.onNodeWithContentDescription("Back").performTouchInput { click() }
            compose.mainClock.advanceTimeBy(1_000)
            // API 24 draws the native ripple on the render-thread clock.
            android.os.SystemClock.sleep(500)
        }
        compose.runOnIdle { assertEquals(2, themes); assertEquals(2, backs) }
    }

    @Test fun homeProfileUsesTheKitCornerAndKeepsItsAccessibleAction() {
        val date = LocalDate.of(2026, 10, 26)
        val zone = ZoneId.of("Asia/Jakarta")
        val user = User("owner", "Pengguna", "pengguna@example.com", zone, null)
        val log = WasteLog("log", WasteCategory.METAL, DisposalMethod.RECYCLE, Quantity.Weight.of(250), date, zone, "", 1, Instant.EPOCH, Instant.EPOCH)
        var dark by mutableStateOf(false)
        var profiles = 0
        compose.setContent { WasteNotTheme(dark) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                OverviewScreen(user, com.example.wastenot.ui.main.SnapshotState(
                    com.example.wastenot.data.dummy.dummyDashboard(listOf(log), date, zone), loading = false),
                    PaddingValues(), {}, {}, {}, {}, onProfile = { profiles++ })
            }
        } }
        for (theme in listOf(false, true)) {
            compose.runOnIdle { dark = theme }
            inspect("Open profile", theme, "home-profile")
            compose.onNodeWithContentDescription("Open profile").performTouchInput { click() }
            compose.mainClock.advanceTimeBy(1_000)
            // API 24 draws the native ripple on the render-thread clock.
            android.os.SystemClock.sleep(500)
        }
        compose.runOnIdle { assertEquals(2, profiles) }
    }

    private fun inspect(label: String, dark: Boolean, name: String) {
        val scheme = if (dark) DarkColorScheme else LightColorScheme
        val theme = if (dark) "dark" else "light"
        val node = compose.onNodeWithContentDescription(label).assertIsDisplayed().assertIsEnabled()
            .assertHasClickAction().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        val bounds = node.fetchSemanticsNode().boundsInWindow
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        compose.waitUntil(5_000) {
            val frame = automation.takeScreenshot() ?: return@waitUntil false
            val ready = frame.getPixel(1, frame.height / 2) == scheme.background.toArgb()
            frame.recycle(); ready
        }
        automation.waitForIdle(300, 5_000)
        automation.takeScreenshot()?.recycle()
        android.os.SystemClock.sleep(150)
        val bitmap = checkNotNull(automation.takeScreenshot())
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "icon-action-qa").apply { mkdirs() }
        File(directory, "$name-$theme.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        val pixels = JSONArray(); val violations = mutableListOf<String>()
        val tokens = if (dark) DarkSurfaceTokens else LightSurfaceTokens
        val expectedSurface = if (name == "home-profile") tokens.glassTintStrong else scheme.surfaceContainer
        val expectedInk = if (name == "home-profile") scheme.onPrimaryContainer else scheme.onPrimaryContainer
        val sampleOffsets = if (name == "home-profile") listOf(24f to 6f, 24f to 42f) else listOf(4f to 4f, 24f to 4f, 44f to 4f)
        for ((xDp, yDp) in sampleOffsets) {
            val actual = bitmap.getPixel((bounds.left + with(compose.density) { xDp.dp.toPx() }).roundToInt(),
                (bounds.top + with(compose.density) { yDp.dp.toPx() }).roundToInt())
            val ratio = ColorUtils.calculateContrast(expectedInk.toArgb(), actual)
            if (ratio < 3.0) violations.add("$label: low icon contrast $ratio at ($xDp,$yDp)")
            pixels.put(actual)
        }
        var ink = 0
        val left = (bounds.center.x - with(compose.density) { 12.dp.toPx() }).roundToInt()
        val top = (bounds.center.y - with(compose.density) { 12.dp.toPx() }).roundToInt()
        val side = with(compose.density) { 24.dp.toPx() }.roundToInt()
        for (x in left until left + side) for (y in top until top + side) {
            if (near(bitmap.getPixel(x, y), expectedInk.toArgb())) ink++
        }
        if (ink < 20) violations.add("$label: expected ink glyph, found $ink matching pixels")
        bitmap.recycle()
        File(directory, "$name-$theme.json").writeText(JSONObject().put("label", label).put("theme", theme)
            .put("font_scale", InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration.fontScale)
            .put("width_px", bounds.width).put("height_px", bounds.height).put("surface_samples", pixels)
            .put("expected_surface", expectedSurface.toArgb()).put("ink_pixels", ink)
            .put("violations", JSONArray(violations)).toString(2))
        assertTrue(violations.joinToString("\n"), violations.isEmpty())
    }

    private fun near(actual: Int, expected: Int) = listOf(24, 16, 8, 0).all { shift ->
        abs((actual shr shift and 255) - (expected shr shift and 255)) <= 4
    }
}
