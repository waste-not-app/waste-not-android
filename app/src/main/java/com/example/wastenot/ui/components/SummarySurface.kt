package com.example.wastenot.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.example.wastenot.theme.LocalWasteNotGradients
import com.example.wastenot.theme.SummaryBorder
import com.example.wastenot.theme.SummaryDecoration
import com.example.wastenot.theme.SummaryHighlight
import com.example.wastenot.theme.SummaryShadow

@Composable
internal fun Modifier.summarySurface(): Modifier {
    val shape = MaterialTheme.shapes.large
    return this
        .dropShadow(shape, Shadow(20.dp, SummaryShadow, spread = (-14).dp, offset = DpOffset(0.dp, 8.dp)))
        .clip(shape)
        .background(LocalWasteNotGradients.current.summary)
        .border(1.dp, SummaryBorder, shape)
        .innerShadow(shape, Shadow(0.dp, SummaryHighlight, offset = DpOffset(0.dp, 1.dp)))
        .drawBehind {
            // Kit ring: 150 dp outer diameter, 24 dp stroke, top -80/right -94.
            drawCircle(SummaryDecoration, radius = 63.dp.toPx(),
                center = Offset(size.width + 19.dp.toPx(), -5.dp.toPx()), style = Stroke(width = 24.dp.toPx()))
        }
}
