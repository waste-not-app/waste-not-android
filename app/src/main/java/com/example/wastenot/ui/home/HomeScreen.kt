package com.example.wastenot.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wastenot.theme.Accent
import com.example.wastenot.theme.OnAccent
import com.example.wastenot.theme.OnSurfaceVariant
import com.example.wastenot.theme.Pine700
import com.example.wastenot.theme.Pine900
import com.example.wastenot.theme.WasteNotTheme

data class WasteActivity(
    val label: String,
    val method: String,
    val amount: String,
    val timeAgo: String,
)

private val pineBrush = Brush.linearGradient(
    colors = listOf(Pine900, Pine700),
    start = androidx.compose.ui.geometry.Offset(0f, 0f),
    end = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
)

@Composable
fun HomeScreen(
    userName: String = "Okta",
    totalToday: Float = 0.65f,
    unit: String = "kg",
    recyclingRate: Int = 72,
    recentActivities: List<WasteActivity> = emptyList(),
    onRecordClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Greeting
        item {
            Column {
                Text(
                    text = "Halo, $userName 👋",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Hari ini",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // Summary card — Pine gradient
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(pineBrush)
                    .padding(24.dp),
            ) {
                Column {
                    Text(
                        text = "Sampah ditangani",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFDBEBDC),
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = totalToday.toString(),
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontSize = 48.sp,
                                fontWeight = FontWeight.ExtraBold,
                            ),
                            color = Color.White,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = unit,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color(0xFFDBEBDC),
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    // Recycling rate badge
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Accent)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "♻ Recycling rate",
                            style = MaterialTheme.typography.labelMedium,
                            color = OnAccent,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "$recyclingRate%",
                            style = MaterialTheme.typography.labelLarge,
                            color = OnAccent,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        // Record button
        item {
            Button(
                onClick = onRecordClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(
                    text = "+",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Catat Aktivitas",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // Recent activity header
        if (recentActivities.isNotEmpty()) {
            item {
                Text(
                    text = "Aktivitas Terbaru",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            items(recentActivities) { activity ->
                ActivityRow(activity = activity)
            }
        } else {
            // Empty state
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "♻",
                            fontSize = 32.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Belum ada catatan hari ini.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityRow(
    activity: WasteActivity,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Method badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = activity.method,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = activity.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = activity.timeAgo,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant,
                )
            }
            Text(
                text = activity.amount,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

// ─── Previews ────────────────────────────────────────────────────────────────

private val sampleActivities = listOf(
    WasteActivity("Botol plastik", "Recycle", "0.2 kg", "2 jam lalu"),
    WasteActivity("Kardus", "Recycle", "0.3 kg", "4 jam lalu"),
    WasteActivity("Sisa makanan", "Reduce", "1 porsi", "Kemarin"),
)

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun HomeScreenPreview() {
    WasteNotTheme {
        HomeScreen(
            userName = "Okta",
            totalToday = 0.65f,
            recyclingRate = 72,
            recentActivities = sampleActivities,
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun HomeScreenEmptyPreview() {
    WasteNotTheme {
        HomeScreen(userName = "Okta", totalToday = 0f, recyclingRate = 0)
    }
}
