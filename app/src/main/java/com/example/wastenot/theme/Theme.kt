package com.example.wastenot.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private fun createColorScheme(dark: Boolean): ColorScheme {
    val background = if (dark) BackgroundDark else Background
    val surface = if (dark) SurfaceDark else Surface
    val soft = if (dark) SurfaceVariantDark else SurfaceVariant
    val ink = if (dark) OnSurfaceDark else OnSurface
    val muted = if (dark) OnSurfaceVariantDark else OnSurfaceVariant
    val primary = if (dark) PrimaryDark else Primary
    val onPrimary = if (dark) OnPrimaryDark else OnPrimary
    val primaryContainer = if (dark) PrimaryContainerDark else PrimaryContainer
    val onPrimaryContainer = if (dark) OnPrimaryContainerDark else OnPrimaryContainer
    val error = if (dark) ErrorDark else Error
    val onError = if (dark) OnErrorDark else OnError
    val errorContainer = if (dark) ErrorContainerDark else ErrorContainer
    val warning = if (dark) WarningDark else Warning
    val warningContainer = if (dark) WarningContainerDark else WarningContainer
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        inversePrimary = if (dark) Primary else PrimaryDark,
        secondary = warning,
        onSecondary = onPrimary,
        secondaryContainer = warningContainer,
        onSecondaryContainer = warning,
        tertiary = Accent,
        onTertiary = OnAccent,
        tertiaryContainer = Accent,
        onTertiaryContainer = OnAccent,
        background = background,
        onBackground = ink,
        surface = surface,
        onSurface = ink,
        surfaceVariant = soft,
        onSurfaceVariant = muted,
        surfaceTint = primary,
        inverseSurface = ink,
        inverseOnSurface = surface,
        error = error,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = if (dark) ErrorDark else OnErrorContainer,
        outline = if (dark) OutlineDark else Outline,
        outlineVariant = if (dark) OutlineVariantDark else OutlineVariant,
        scrim = Color.Black,
        surfaceBright = if (dark) soft else surface,
        surfaceDim = background,
        surfaceContainerLowest = if (dark) background else surface,
        surfaceContainerLow = surface,
        surfaceContainer = soft,
        surfaceContainerHigh = soft,
        surfaceContainerHighest = soft,
        primaryFixed = PrimaryContainer,
        primaryFixedDim = PrimaryDark,
        onPrimaryFixed = OnPrimaryContainer,
        onPrimaryFixedVariant = OnPrimaryContainer,
        secondaryFixed = WarningContainer,
        secondaryFixedDim = WarningContainer,
        onSecondaryFixed = Warning,
        onSecondaryFixedVariant = Warning,
        tertiaryFixed = Accent,
        tertiaryFixedDim = LimeEnd,
        onTertiaryFixed = OnAccent,
        onTertiaryFixedVariant = OnAccent,
    )
}

internal val LightColorScheme = createColorScheme(dark = false)
internal val DarkColorScheme = createColorScheme(dark = true)

@Composable
fun WasteNotTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    CompositionLocalProvider(LocalWasteNotGradients provides if (darkTheme) DarkGradients else LightGradients) {
        MaterialTheme(colorScheme = colorScheme, typography = Typography, shapes = WasteNotShapes, content = content)
    }
}
