package com.example.wastenot.theme

import android.graphics.Paint
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.R
import com.example.wastenot.data.local.ThemePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ThemeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun bundledFontsLoadNativelyAndLicenseIsPackaged() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        listOf(
            R.font.plus_jakarta_sans_regular, R.font.plus_jakarta_sans_medium,
            R.font.plus_jakarta_sans_semibold, R.font.plus_jakarta_sans_bold,
            R.font.plus_jakarta_sans_extrabold,
        ).forEach { resource ->
            val font = ResourcesCompat.getFont(context, resource)
            assertNotNull(font)
            val paint = Paint().apply { typeface = font; textSize = 32f }
            assertTrue(paint.measureText("650 g · 2 benda") > 0)
        }
        val license = context.assets.open("licenses/OFL-PlusJakartaSans.txt").bufferedReader().use { it.readText() }
        assertTrue(license.contains("SIL OPEN FONT LICENSE Version 1.1"))
    }

    @Test fun semanticRolesAndAllGradientStopsMeetContrast() {
        listOf(LightColorScheme, DarkColorScheme).forEach { c ->
            listOf(
                c.onPrimary to c.primary, c.onPrimaryContainer to c.primaryContainer,
                c.onSecondary to c.secondary, c.onSecondaryContainer to c.secondaryContainer,
                c.onTertiary to c.tertiary, c.onTertiaryContainer to c.tertiaryContainer,
                c.onError to c.error, c.onErrorContainer to c.errorContainer,
                c.onSurface to c.surface, c.onBackground to c.background,
                c.onSurfaceVariant to c.surface, c.onSurfaceVariant to c.surfaceVariant,
                c.inverseOnSurface to c.inverseSurface,
                c.onPrimaryFixed to c.primaryFixed, c.onPrimaryFixedVariant to c.primaryFixedDim,
                c.onSecondaryFixed to c.secondaryFixed, c.onTertiaryFixed to c.tertiaryFixedDim,
            ).forEach { (text, background) ->
                val ratio = ColorUtils.calculateContrast(text.toArgb(), background.toArgb())
                assertTrue("$text on $background: $ratio", ratio >= 4.5)
            }
            assertTrue(ColorUtils.calculateContrast(c.outline.toArgb(), c.surface.toArgb()) >= 3.0)
        }
        listOf(
            Triple(Pine900, Pine700, SummaryText), Triple(Pine900, Pine700, SummarySecondaryText),
            Triple(LimeStart, LimeEnd, OnAccent), Triple(ActionStart, ActionEnd, OnPrimary),
            Triple(ActionStartDark, ActionEndDark, OnPrimaryDark),
            Triple(CanvasStart, CanvasEnd, OnSurface), Triple(CanvasStartDark, CanvasEndDark, OnSurfaceDark),
        ).forEach { (start, end, text) ->
            (0..100).forEach { step ->
                // blendARGB can truncate an opaque alpha to 254 on API 24.
                val background = ColorUtils.setAlphaComponent(ColorUtils.blendARGB(start.toArgb(), end.toArgb(), step / 100f), 255)
                assertTrue(ColorUtils.calculateContrast(text.toArgb(), background) >= 4.5)
            }
        }
    }

    @Test fun selectionChangesImmediatelyAndPersistsToDataStore() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "theme-${UUID.randomUUID()}.preferences_pb")
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.IO)
        val data = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        val store = ThemePreferences(data)
        val selected = mutableStateOf(store.modes.first())
        assertEquals(ThemeMode.SYSTEM, selected.value)
        try {
        compose.setContent {
            WasteNotTheme(darkTheme = selected.value.isDark(false)) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                    ThemePicker(selected.value, { mode -> selected.value = mode; scope.launch { store.write(mode) } }, {})
                }
            }
        }
        listOf("Gelap" to ThemeMode.DARK, "Terang" to ThemeMode.LIGHT, "Ikuti sistem" to ThemeMode.SYSTEM).forEach { (label, mode) ->
            compose.onNodeWithText(label).assertIsDisplayed().performClick().assertIsSelected()
            assertEquals(mode, store.modes.first { it == mode })
        }
        } finally { job.cancelAndJoin(); file.delete() }
    }
}
