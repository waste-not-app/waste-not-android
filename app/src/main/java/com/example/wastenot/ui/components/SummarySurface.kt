package com.example.wastenot.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import com.example.wastenot.R
import com.example.wastenot.theme.LocalWasteNotGradients
import com.example.wastenot.theme.SummaryBorder
import com.example.wastenot.theme.SummaryDecoration
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.wastenot.theme.SummaryAmbientGlow
import com.example.wastenot.theme.SummaryHighlight
import com.example.wastenot.theme.SummaryShadow

@Composable
internal fun Modifier.summarySurface(): Modifier {
    val shape = MaterialTheme.shapes.large
    val brandMark = painterResource(R.drawable.wastenot_brand_mark)
    return this
        .dropShadow(shape, Shadow(20.dp, SummaryShadow, spread = (-14).dp, offset = DpOffset(0.dp, 8.dp)))
        .clip(shape)
        .background(LocalWasteNotGradients.current.summary)
        .border(1.dp, SummaryBorder, shape)
        .innerShadow(shape, Shadow(0.dp, SummaryHighlight, offset = DpOffset(0.dp, 1.dp)))
        .drawWithCache {
            val glowCenter = Offset(size.width * 0.92f, size.height * 0.14f)
            val glowRadius = size.maxDimension * 0.50f
            val glow = Brush.radialGradient(
                colors = listOf(SummaryAmbientGlow, Color.Transparent),
                center = glowCenter,
                radius = glowRadius,
            )
            val markRatio = 773f / 546f
            val markWidth = minOf(120.dp.toPx(), size.width * 0.34f, size.height * 0.72f * markRatio)
            val markSize = Size(markWidth, markWidth / markRatio)
            val markInset = minOf(16.dp.toPx(), size.height * 0.10f)
            val markTint = ColorFilter.tint(SummaryDecoration)
            onDrawBehind {
                drawCircle(glow, radius = glowRadius, center = glowCenter)
                // A pine tint darkens the surface, preserving contrast beneath light summary text.
                translate(left = size.width - markWidth - markInset, top = markInset) {
                    with(brandMark) {
                        draw(size = markSize, colorFilter = markTint)
                    }
                }
            }
        }
}
