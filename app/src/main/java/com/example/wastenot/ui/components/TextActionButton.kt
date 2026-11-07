package com.example.wastenot.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun TextActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp)
            .focusOutline(enabled && focused, MaterialTheme.shapes.small, colors.primary, colors.surface),
        enabled = enabled,
        interactionSource = interactions,
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.textButtonColors(
            containerColor = Color.Transparent,
            contentColor = colors.primary,
            disabledContainerColor = colors.surfaceContainer,
            disabledContentColor = colors.onSurfaceVariant,
        ),
        border = if (enabled) null else BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Text(label, Modifier.width(IntrinsicSize.Max), textAlign = TextAlign.Center)
    }
}
