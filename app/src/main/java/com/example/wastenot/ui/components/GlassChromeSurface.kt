package com.example.wastenot.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
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
        Brush.linearGradient(listOf(tokens.glassEdge, tokens.glassEdge.copy(alpha = 0.08f), tokens.glassEdge))
    }
    Surface(
        modifier = modifier
            .shadow(6.dp, shape, clip = false)
            .clip(shape)
            .hazeEffect(state = hazeState, style = style),
        shape = shape,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, rim),
        content = content,
    )
}
