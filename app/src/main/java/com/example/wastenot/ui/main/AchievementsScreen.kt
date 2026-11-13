package com.example.wastenot.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.wastenot.Achievements
import com.example.wastenot.R
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.model.Achievement
import com.example.wastenot.model.AchievementRequirement
import com.example.wastenot.model.WasteFormat
import com.example.wastenot.theme.WasteNotTheme
import com.example.wastenot.ui.auth.MessageBanner
import com.example.wastenot.ui.components.ActionButton
import com.example.wastenot.ui.components.AsyncStatePanel
import com.example.wastenot.ui.components.TextActionButton
import com.github.skydoves.navgraph.annotations.NavDestination
import com.github.skydoves.navgraph.annotations.NavPreview
import java.time.Instant
import java.time.ZoneId

@NavDestination(route = Achievements::class)
@Composable
fun AchievementsScreen(
    state: AchievementsUiState,
    accountTimezone: ZoneId,
    padding: PaddingValues,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = state.items
    if (items == null) {
        ScrollPage(padding, modifier, maxWidth = 560.dp) {
            AsyncStatePanel(
                title = stringResource(if (state.loading) R.string.achievements_loading else R.string.achievements_error),
                icon = if (state.loading) R.drawable.ic_clock else R.drawable.ic_alert,
                modifier = Modifier.fillMaxWidth(),
                description = if (state.loading) null else loadFailureHint(state.error),
                loading = state.loading,
            ) {
                if (!state.loading) ActionButton(stringResource(R.string.retry), onRetry, Modifier.fillMaxWidth())
            }
        }
        return
    }

    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    ScrollPage(padding, modifier, maxWidth = 560.dp) {
        Text(
            stringResource(R.string.achievements_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.error != null) {
            MessageBanner(stringResource(R.string.refresh_failed), isError = true)
            TextActionButton(stringResource(R.string.retry), onRetry)
        }
        if (items.isEmpty()) {
            AsyncStatePanel(
                title = stringResource(R.string.achievements_empty_title),
                icon = R.drawable.ic_check,
                modifier = Modifier.fillMaxWidth(),
                description = stringResource(R.string.achievements_empty_message),
            )
        } else {
            items.forEach { item ->
                val isOpen = expanded[item.id] == true
                AchievementCard(
                    item = item,
                    accountTimezone = accountTimezone,
                    expanded = isOpen,
                    onToggle = { expanded[item.id] = !isOpen },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun AchievementCard(
    item: Achievement,
    accountTimezone: ZoneId,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusLabel = stringResource(if (item.isUnlocked) R.string.achievement_unlocked else R.string.achievement_locked)
    val toggleHint = stringResource(if (expanded) R.string.achievement_hide_details else R.string.achievement_toggle_details)
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (item.isUnlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .clickable(
                    role = Role.Button,
                    onClickLabel = toggleHint,
                    onClick = onToggle,
                )
                .semantics { stateDescription = "$statusLabel, $toggleHint" }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    item.title,
                    modifier = Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (item.isUnlocked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (item.isUnlocked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Text(
                        statusLabel,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            val fraction = (item.currentValue.toFloat() / item.targetValue.toFloat()).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(8.dp)),
                color = if (item.isUnlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                gapSize = 0.dp,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(
                        R.string.achievement_progress,
                        WasteFormat.integer(item.currentValue),
                        WasteFormat.integer(item.targetValue),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        toggleHint,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        painterResource(R.drawable.ic_chevron_down),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp).rotate(if (expanded) 180f else 0f),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (expanded) {
                Text(
                    item.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                item.unlockedAt?.let { unlockedAt ->
                    Text(
                        stringResource(
                            R.string.achievement_unlocked_at,
                            WasteFormat.timestamp(unlockedAt, accountTimezone),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@NavPreview(route = Achievements::class)
@Preview(showBackground = true)
@Composable
private fun AchievementsScreenPreview() {
    WasteNotTheme {
        AchievementsScreen(
            state = AchievementsUiState(
                items = listOf(
                    Achievement(
                        id = "00000000-0000-4000-8000-000000000101",
                        code = "FIRST_STEP",
                        title = "Langkah pertama",
                        description = "Catat minimal 1 aktivitas pengelolaan sampah aktif.",
                        requirementType = AchievementRequirement.ACTIVE_LOG_COUNT,
                        currentValue = 1,
                        targetValue = 1,
                        isUnlocked = true,
                        unlockedAt = Instant.parse("2026-11-13T07:00:00Z"),
                    ),
                    Achievement(
                        id = "00000000-0000-4000-8000-000000000102",
                        code = "GETTING_USED",
                        title = "Mulai terbiasa",
                        description = "Catat minimal 5 aktivitas pengelolaan sampah aktif.",
                        requirementType = AchievementRequirement.ACTIVE_LOG_COUNT,
                        currentValue = 3,
                        targetValue = 5,
                        isUnlocked = false,
                        unlockedAt = null,
                    ),
                ),
            ),
            accountTimezone = ZoneId.of("Asia/Jakarta"),
            padding = PaddingValues(),
            onRetry = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}
