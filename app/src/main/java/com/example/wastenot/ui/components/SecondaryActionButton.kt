package com.example.wastenot.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.wastenot.theme.LocalWasteNotSurfaceTokens

@Composable
fun SecondaryActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
) {
    val colors = MaterialTheme.colorScheme
    val tokens = LocalWasteNotSurfaceTokens.current
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val shape = RoundedCornerShape(15.dp)
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp)
            .focusOutline(enabled && focused, shape, colors.primary, colors.surface),
        enabled = enabled,
        interactionSource = interactions,
        shape = shape,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (enabled && focused) colors.primaryContainer else colors.surfaceVariant,
            contentColor = colors.onPrimaryContainer,
            disabledContainerColor = colors.surfaceVariant.copy(alpha = 0.6f),
            disabledContentColor = colors.onSurfaceVariant,
        ),
        border = BorderStroke(
            1.dp,
            when {
                !enabled -> colors.outlineVariant
                focused -> colors.primary
                else -> tokens.cardEdge
            },
        ),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) Icon(painterResource(icon), null, Modifier.size(20.dp))
            Text(label, Modifier.width(IntrinsicSize.Max), textAlign = TextAlign.Center)
        }
    }
}
