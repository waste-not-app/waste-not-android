package com.example.wastenot.ui.main

import androidx.compose.foundation.BorderStroke
import com.example.wastenot.ui.components.InsetGroupCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.example.wastenot.ui.components.focusOutline
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
    var expandedIds by rememberSaveable(
        stateSaver = listSaver<Set<String>, String>(save = { it.toList() }, restore = { it.toSet() }),
    ) { mutableStateOf(emptySet()) }
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

    ScrollPage(padding, modifier, maxWidth = 560.dp) {
        Text(
            stringResource(R.string.achievements_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.loading) {
            Text(stringResource(R.string.achievements_loading), style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (state.error != null) {
            MessageBanner(stringResource(R.string.refresh_failed), isError = true)
            TextActionButton(stringResource(R.string.retry), onRetry)
        }
        if (items.isEmpty()) {
            AsyncStatePanel(
                title = stringResource(R.string.achievements_empty_title),
                icon = R.drawable.ic_trophy,
                modifier = Modifier.fillMaxWidth(),
                description = stringResource(R.string.achievements_empty_message),
            )
        } else {
            InsetGroupCard {
                items.forEachIndexed { index, item ->
                    val isOpen = item.id in expandedIds
                    AchievementRow(
                        item = item,
                        accountTimezone = accountTimezone,
                        expanded = isOpen,
                        onToggle = {
                            expandedIds = if (item.id in expandedIds) expandedIds - item.id else expandedIds + item.id
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (index < items.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun AchievementRow(
    item: Achievement,
    accountTimezone: ZoneId,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusLabel = stringResource(if (item.isUnlocked) R.string.achievement_unlocked else R.string.achievement_locked)
    val toggleHint = stringResource(if (expanded) R.string.achievement_hide_details else R.string.achievement_toggle_details)
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clickable(
                interactionSource = interactions,
                indication = LocalIndication.current,
                role = Role.Button,
                onClickLabel = toggleHint,
                onClick = onToggle,
            )
            .focusOutline(focused, RoundedCornerShape(12.dp), colors.primary, colors.surface)
            .semantics { stateDescription = "$statusLabel, $toggleHint" }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (item.isUnlocked) colors.primaryContainer else colors.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(if (item.isUnlocked) R.drawable.ic_check else R.drawable.ic_trophy),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (item.isUnlocked) colors.onPrimaryContainer else colors.onSurfaceVariant,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    item.title,
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                )
                Text(
                    stringResource(
                        R.string.achievement_progress,
                        WasteFormat.integer(item.currentValue),
                        WasteFormat.integer(item.targetValue),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (item.isUnlocked) colors.primaryContainer else colors.surface,
                contentColor = if (item.isUnlocked) colors.onPrimaryContainer else colors.onSurfaceVariant,
                border = if (item.isUnlocked) null else BorderStroke(1.dp, colors.outlineVariant),
            ) {
                Text(
                    statusLabel,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        val fraction = (item.currentValue.toFloat() / item.targetValue.toFloat()).coerceIn(0f, 1f)
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(8.dp)),
            color = if (item.isUnlocked) colors.primary else colors.outline,
            trackColor = colors.surfaceVariant,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                toggleHint,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = colors.primary,
            )
            Icon(
                painterResource(R.drawable.ic_chevron_down),
                contentDescription = null,
                modifier = Modifier.size(16.dp).rotate(if (expanded) 180f else 0f),
                tint = colors.primary,
            )
        }
        if (expanded) {
            Text(
                item.description,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface,
            )
            item.unlockedAt?.let { unlockedAt ->
                Text(
                    stringResource(
                        R.string.achievement_unlocked_at,
                        WasteFormat.timestamp(unlockedAt, accountTimezone),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
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
                        title = "First step",
                        description = "Log at least 1 active waste activity.",
                        requirementType = AchievementRequirement.ACTIVE_LOG_COUNT,
                        currentValue = 1,
                        targetValue = 1,
                        isUnlocked = true,
                        unlockedAt = Instant.parse("2026-11-13T07:00:00Z"),
                    ),
                    Achievement(
                        id = "00000000-0000-4000-8000-000000000102",
                        code = "GETTING_USED",
                        title = "Getting into the habit",
                        description = "Log at least 5 active waste activities.",
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
