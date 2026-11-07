package com.example.wastenot.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp

internal fun Modifier.focusOutline(focused: Boolean, shape: Shape, color: Color, backing: Color): Modifier =
    if (!focused) this else drawWithContent {
        drawContent()
        val stroke = 3.dp.toPx()
        val outset = 4.dp.toPx() + stroke / 2
        val outline = shape.createOutline(Size(size.width + 2 * outset, size.height + 2 * outset), layoutDirection, this)
        translate(-outset, -outset) {
            // A surface halo keeps the focus outline distinct over the pine and lime backgrounds.
            drawOutline(outline, backing, style = Stroke(stroke + 2.dp.toPx()))
            drawOutline(outline, color, style = Stroke(stroke))
        }
    }
