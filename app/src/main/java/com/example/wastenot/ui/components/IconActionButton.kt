package com.example.wastenot.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.wastenot.theme.LocalWasteNotSurfaceTokens

@Composable
fun IconActionButton(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    circle: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val tokens = LocalWasteNotSurfaceTokens.current
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val shape = if (circle) CircleShape else RoundedCornerShape(14.dp)
    val container = if (circle) tokens.glassTintStrong else colors.surfaceVariant
    val content = if (enabled) colors.onPrimaryContainer else colors.onSurfaceVariant
    val edge = if (circle) tokens.glassEdge else tokens.cardEdge
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactions,
        shape = shape,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container,
            disabledContentColor = colors.onSurfaceVariant,
        ),
        modifier = modifier
            .size(48.dp)
            .then(if (circle && !tokens.isDark) Modifier.shadow(2.dp, shape) else Modifier)
            .border(BorderStroke(1.dp, edge), shape)
            .focusOutline(enabled && focused, shape, colors.primary, colors.surface),
    ) {
        Icon(painterResource(icon), label, Modifier.size(22.dp))
    }
}
