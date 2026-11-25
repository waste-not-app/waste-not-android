package com.example.wastenot.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.example.wastenot.theme.LocalWasteNotSurfaceTokens
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect

@Composable
internal fun GlassChromeSurface(
    hazeState: HazeState,
    style: HazeStyle,
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val tokens = LocalWasteNotSurfaceTokens.current
    val rim = remember(tokens) {
        val midAlpha = if (tokens.isDark) 0.14f else 0.18f
        val bottomAlpha = if (tokens.isDark) 0.45f else 0.55f
        Brush.verticalGradient(
            listOf(
                tokens.glassEdge,
                tokens.glassEdge.copy(alpha = midAlpha),
                tokens.glassEdge.copy(alpha = bottomAlpha),
            ),
        )
    }
    Surface(
        modifier = modifier
            .shadow(4.dp, shape, clip = false)
            .clip(shape)
            .hazeEffect(state = hazeState, style = style)
            .innerShadow(shape, Shadow(radius = 2.dp, color = tokens.glassHighlight, offset = DpOffset(0.dp, 1.dp))),
        shape = shape,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, rim),
        content = content,
    )
}
