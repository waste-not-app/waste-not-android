package com.example.wastenot.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.compositionLocalOf
object WasteNotSpacing {
    val Space1 = 4.dp
    val Space2 = 8.dp
    val Space3 = 12.dp
    val Space4 = 16.dp
    val Space5 = 20.dp
    val Space6 = 24.dp
    val Space8 = 32.dp
    val Space12 = 48.dp
}

val WasteNotShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

data class WasteNotGradients(
    val summary: Brush,
    val accent: Brush,
    val action: Brush,
    val canvas: Brush,
    val progress: Brush,
)

data class WasteNotSurfaceTokens(
    val isDark: Boolean,
    val fieldWell: Color,
    val fieldWellFocus: Color,
    val fieldEdge: Color,
    val glassTint: Color,
    val glassTintStrong: Color,
    val glassEdge: Color,
    val cardEdge: Color,
    val glassHighlight: Color = GlassHighlight,
)

internal val LightSurfaceTokens = WasteNotSurfaceTokens(
    isDark = false,
    fieldWell = FieldWell,
    fieldWellFocus = FieldWellFocus,
    fieldEdge = FieldEdge,
    glassTint = GlassTint,
    glassTintStrong = GlassTintStrong,
    glassEdge = DockEdge,
    cardEdge = CardEdge,
    glassHighlight = GlassHighlight,
)

internal val DarkSurfaceTokens = WasteNotSurfaceTokens(
    isDark = true,
    fieldWell = FieldWellDark,
    fieldWellFocus = FieldWellFocusDark,
    fieldEdge = FieldEdgeDark,
    glassTint = GlassTintDark,
    glassTintStrong = GlassTintStrongDark,
    glassEdge = DockEdgeDark,
    cardEdge = CardEdgeDark,
    glassHighlight = GlassHighlightDark,
)

internal val LightGradients = WasteNotGradients(
    summary = Brush.linearGradient(listOf(Pine900, Pine700)),
    accent = Brush.linearGradient(listOf(LimeStart, LimeEnd)),
    action = Brush.linearGradient(listOf(ActionStart, ActionEnd)),
    canvas = Brush.linearGradient(listOf(CanvasStart, CanvasEnd)),
    progress = Brush.horizontalGradient(listOf(Primary, Color(0xFF8CE052))),
)

internal val DarkGradients = LightGradients.copy(
    action = Brush.linearGradient(listOf(ActionStartDark, ActionEndDark)),
    canvas = Brush.linearGradient(listOf(CanvasStartDark, CanvasEndDark)),
    progress = Brush.horizontalGradient(listOf(PrimaryDark, LimeEnd)),
)

val LocalWasteNotGradients = staticCompositionLocalOf { LightGradients }
val LocalWasteNotDockEdge = staticCompositionLocalOf<Color> { DockEdge }
val LocalWasteNotSurfaceTokens = staticCompositionLocalOf { LightSurfaceTokens }
val LocalWasteNotScaffoldPadding = compositionLocalOf { PaddingValues(0.dp) }
