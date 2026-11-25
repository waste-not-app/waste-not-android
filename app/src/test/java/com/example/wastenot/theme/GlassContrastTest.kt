package com.example.wastenot.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassContrastTest {
    @Test fun normalTextStaysReadableAcrossBothThemesAndWorstGlassBackdrops() {
        for ((scheme, tokens) in listOf(LightColorScheme to LightSurfaceTokens, DarkColorScheme to DarkSurfaceTokens)) {
            for (backdrop in listOf(Color.Black, Color.White)) {
                val glass = tokens.glassTintStrong.compositeOver(backdrop)
                assertContrast(scheme.onSurface, glass, 4.5f)
                assertContrast(scheme.onSurfaceVariant, glass, 4.5f)
            }
            val fallback = tokens.glassTintStrong.compositeOver(scheme.background)
            assertContrast(scheme.onSurface, fallback, 4.5f)
            assertContrast(scheme.onSurfaceVariant, fallback, 4.5f)
            assertContrast(scheme.onPrimaryContainer, scheme.primaryContainer, 4.5f)
            assertContrast(scheme.primary, scheme.primaryContainer, 3f)
            assertContrast(scheme.onSurface, tokens.fieldWell, 4.5f)
            assertContrast(scheme.onSurfaceVariant, tokens.fieldWell, 4.5f)
            assertContrast(scheme.onTertiary, scheme.tertiary, 4.5f)
        }
    }

    @Test fun fieldEdgesAndFocusIndicatorsRemainDistinguishable() {
        for ((scheme, tokens) in listOf(LightColorScheme to LightSurfaceTokens, DarkColorScheme to DarkSurfaceTokens)) {
            assertContrast(tokens.fieldEdge, tokens.fieldWell, 3f)
            assertContrast(scheme.primary, tokens.fieldWellFocus, 3f)
        }
    }

    private fun assertContrast(foreground: Color, background: Color, minimum: Float) {
        val a = foreground.compositeOver(background).luminance()
        val b = background.luminance()
        val ratio = (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
        assertTrue("Contrast $ratio is below $minimum ($foreground on $background)", ratio >= minimum)
    }
}
