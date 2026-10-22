package com.example.wastenot.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

data class WasteNotGradients(val summary: Brush, val accent: Brush, val action: Brush, val canvas: Brush)

internal val LightGradients = WasteNotGradients(
    summary = Brush.linearGradient(listOf(Pine900, Pine700)),
    accent = Brush.linearGradient(listOf(LimeStart, LimeEnd)),
    action = Brush.linearGradient(listOf(ActionStart, ActionEnd)),
    canvas = Brush.linearGradient(listOf(CanvasStart, CanvasEnd)),
)

internal val DarkGradients = LightGradients.copy(
    action = Brush.linearGradient(listOf(ActionStartDark, ActionEndDark)),
    canvas = Brush.linearGradient(listOf(CanvasStartDark, CanvasEndDark)),
)

val LocalWasteNotGradients = staticCompositionLocalOf { LightGradients }
val LocalWasteNotDockEdge = staticCompositionLocalOf<Color> { DockEdge }
