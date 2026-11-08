package com.example.wastenot.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
fun IconActionButton(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier.size(48.dp)
            .focusOutline(enabled && focused, MaterialTheme.shapes.small, colors.primary, colors.surface),
        enabled = enabled,
        interactionSource = interactions,
        shape = MaterialTheme.shapes.small,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = colors.surfaceContainer,
            contentColor = colors.onSurface,
            disabledContainerColor = colors.surfaceContainer,
            disabledContentColor = colors.onSurfaceVariant,
        ),
    ) {
        Icon(painterResource(icon), label, Modifier.size(24.dp))
    }
}
