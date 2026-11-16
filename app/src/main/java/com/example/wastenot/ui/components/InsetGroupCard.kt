package com.example.wastenot.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.wastenot.R
import com.example.wastenot.model.WasteCategory
import com.example.wastenot.theme.Accent
import com.example.wastenot.theme.LimeEnd
import com.example.wastenot.theme.LimeStart
import com.example.wastenot.theme.LocalWasteNotGradients
import com.example.wastenot.theme.LocalWasteNotSurfaceTokens
import com.example.wastenot.theme.OnAccent
import com.example.wastenot.theme.Primary
import com.example.wastenot.theme.PrimaryDark

@Composable
internal fun Modifier.ambientCanvasBackground(): Modifier {
    val gradients = LocalWasteNotGradients.current
    val tokens = LocalWasteNotSurfaceTokens.current
    val topGlow = if (tokens.isDark) PrimaryDark.copy(alpha = 0.14f) else Accent.copy(alpha = 0.22f)
    val bottomGlow = if (tokens.isDark) Accent.copy(alpha = 0.06f) else Primary.copy(alpha = 0.10f)
    return this
        .background(gradients.canvas)
        .drawWithCache {
            val topCenter = Offset(size.width * 0.86f, size.height * 0.12f)
            val topRadius = size.maxDimension * 0.44f
            val topBrush = Brush.radialGradient(listOf(topGlow, Color.Transparent), topCenter, topRadius)
            val bottomCenter = Offset(size.width * 0.16f, size.height * 0.80f)
            val bottomRadius = size.maxDimension * 0.48f
            val bottomBrush = Brush.radialGradient(listOf(bottomGlow, Color.Transparent), bottomCenter, bottomRadius)
            onDrawBehind {
                drawCircle(topBrush, radius = topRadius, center = topCenter)
                drawCircle(bottomBrush, radius = bottomRadius, center = bottomCenter)
            }
        }
}

@Composable
fun InsetGroupCard(
    modifier: Modifier = Modifier,
    contentPadding: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = LocalWasteNotSurfaceTokens.current
    val shape = RoundedCornerShape(22.dp)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, tokens.cardEdge),
    ) {
        Column(
            modifier = if (contentPadding) Modifier.fillMaxWidth().padding(16.dp) else Modifier.fillMaxWidth(),
            verticalArrangement = if (contentPadding) Arrangement.spacedBy(12.dp) else Arrangement.Top,
            content = content,
        )
    }
}

@Composable
fun GroupSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 4.dp).semantics { heading() },
    )
}

@Composable
fun MetricTile(
    label: String,
    value: String,
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    limeHighlight: Boolean = false,
    subtitle: String? = null,
    secondarySubtitle: String? = null,
) {
    val tokens = LocalWasteNotSurfaceTokens.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, tokens.cardEdge),
        shadowElevation = if (tokens.isDark) 0.dp else 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .then(
                            if (limeHighlight) Modifier.background(Brush.linearGradient(listOf(LimeStart, LimeEnd)))
                            else Modifier.background(MaterialTheme.colorScheme.surfaceVariant),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(icon),
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = if (limeHighlight) OnAccent else MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (secondarySubtitle != null) {
                Text(
                    secondarySubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun CategoryIconBadge(
    category: WasteCategory,
    @DrawableRes iconRes: Int,
    modifier: Modifier = Modifier,
    sizeDp: Int = 42,
) {
    val isDark = LocalWasteNotSurfaceTokens.current.isDark
    val (bg, fg) = categoryBadgeColors(category, isDark)
    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size((sizeDp * 0.54f).dp),
            tint = fg,
        )
    }
}

@Composable
fun GroupedNavRow(
    label: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Text(
                label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Icon(
                painterResource(R.drawable.ic_next),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 60.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

internal fun categoryBadgeColors(category: WasteCategory, isDark: Boolean): Pair<Color, Color> = when (category) {
    WasteCategory.ORGANIC -> if (isDark) Color(0xFF1D3827) to Color(0xFF80DF9F) else Color(0xFFE3F3E6) to Color(0xFF18643B)
    WasteCategory.PLASTIC -> if (isDark) Color(0xFF1B372D) to Color(0xFF6EE2B2) else Color(0xFFE2F1EC) to Color(0xFF146342)
    WasteCategory.GLASS -> if (isDark) Color(0xFF1D323E) to Color(0xFF82C9E5) else Color(0xFFE4EFF4) to Color(0xFF1F5A73)
    WasteCategory.PAPER -> if (isDark) Color(0xFF342E1D) to Color(0xFFE5C882) else Color(0xFFF4EFE2) to Color(0xFF745417)
    WasteCategory.METAL -> if (isDark) Color(0xFF253139) to Color(0xFFB3C7D4) else Color(0xFFEAEFF2) to Color(0xFF41525D)
    WasteCategory.ELECTRONIC -> if (isDark) Color(0xFF3C2126) to Color(0xFFF4A7AE) else Color(0xFFF6EAEB) to Color(0xFF932A33)
    WasteCategory.OTHER -> if (isDark) Color(0xFF1E3529) to Color(0xFF52D69A) else Color(0xFFE5F2EA) to Color(0xFF146342)
}
