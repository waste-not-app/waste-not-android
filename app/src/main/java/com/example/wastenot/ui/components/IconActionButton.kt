package com.example.wastenot.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactions,
        shape = shape,
        color = if (circle) tokens.glassTintStrong else colors.surfaceVariant,
        contentColor = if (enabled) colors.onPrimaryContainer else colors.onSurfaceVariant,
        border = BorderStroke(1.dp, if (circle) tokens.glassEdge else tokens.cardEdge),
        shadowElevation = if (circle && !tokens.isDark) 2.dp else 0.dp,
        modifier = modifier
            .size(48.dp)
            .focusOutline(enabled && focused, shape, colors.primary, colors.surface)
            .semantics {
                contentDescription = label
                role = Role.Button
            },
    ) {
        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), null, Modifier.size(22.dp))
        }
    }
}
