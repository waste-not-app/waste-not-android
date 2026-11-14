package com.example.wastenot.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.wastenot.BuildConfig
import com.example.wastenot.R
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.model.User
import com.example.wastenot.model.UserProgress
import com.example.wastenot.model.WasteFormat
import com.example.wastenot.theme.*
import com.example.wastenot.ui.auth.MessageBanner
import com.example.wastenot.ui.components.ActionButton
import com.example.wastenot.ui.components.GroupSectionLabel
import com.example.wastenot.ui.components.GroupedNavRow
import com.example.wastenot.ui.components.InsetGroupCard
import com.example.wastenot.ui.components.MetricTile
import com.example.wastenot.ui.components.SecondaryActionButton
import com.example.wastenot.ui.components.TextActionButton
import java.util.Locale

@Composable
fun ProfileScreen(
    user: User,
    progress: UserProgress?,
    loading: Boolean,
    error: ApiFailureKind?,
    padding: PaddingValues,
    onRetry: () -> Unit,
    onRecord: () -> Unit,
    onSettings: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    achievementsEnabled: Boolean = false,
    onAchievements: () -> Unit = {},
    onSync: (() -> Unit)? = null,
) {
    ScrollPage(padding, modifier, maxWidth = 560.dp) {
        InsetGroupCard(contentPadding = true) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val initials = user.name.split(' ').filter(String::isNotBlank).take(2)
                    .joinToString("") { String(Character.toChars(it.codePointAt(0))).uppercase(Locale.forLanguageTag("id-ID")) }
                Box(
                    Modifier.size(54.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(LocalWasteNotGradients.current.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(initials, color = OnAccent, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(user.name, style = MaterialTheme.typography.titleLarge)
                    Text(user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(user.timezone.id, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (progress != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ProgressMeter(progress, Modifier.fillMaxWidth())
                Text(
                    stringResource(
                        R.string.profile_xp_count,
                        WasteFormat.integer(progress.xp.toLong()),
                        WasteFormat.integer(progress.activeLogCount.toLong()),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (progress.activeLogCount == 0) {
                    Text(stringResource(R.string.profile_empty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ActionButton(stringResource(R.string.record_activity), onRecord, Modifier.fillMaxWidth())
                }
            } else if (!loading && error == null) {
                MessageBanner(stringResource(R.string.profile_progress_unavailable))
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)))
        if (error != null) {
            MessageBanner(stringResource(R.string.profile_refresh_failed), isError = true)
            TextActionButton(stringResource(R.string.retry), onRetry)
        }
        if (progress != null) {
            val hasStreak = progress.currentStreak != null || progress.bestStreak != null
            val streakLabel = progress.currentStreak?.let {
                stringResource(R.string.profile_current_streak, WasteFormat.integer(it.toLong()))
            } ?: "—"
            val streakSubtitle = if (progress.currentStreak != null && progress.currentStreak > 0) {
                stringResource(if (progress.hasLoggedToday) R.string.profile_streak_today_logged else R.string.profile_streak_today_pending)
            } else null
            val bestStreakSubtitle = progress.bestStreak?.let {
                stringResource(R.string.profile_best_streak, WasteFormat.integer(it.toLong()))
            }
            if (!hasStreak) {
                MetricTile(stringResource(R.string.level_label), progress.level.toString(), R.drawable.ic_check, Modifier.fillMaxWidth())
            } else {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val stacked = maxWidth < 280.dp || LocalDensity.current.fontScale >= 1.5f
                    if (stacked) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            MetricTile(stringResource(R.string.level_label), progress.level.toString(), R.drawable.ic_check, Modifier.fillMaxWidth())
                            MetricTile(stringResource(R.string.streak_short_title), streakLabel, R.drawable.ic_clock, Modifier.fillMaxWidth(), limeHighlight = true, subtitle = streakSubtitle, secondarySubtitle = bestStreakSubtitle)
                        }
                    } else {
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MetricTile(stringResource(R.string.level_label), progress.level.toString(), R.drawable.ic_check, Modifier.weight(1f).fillMaxHeight())
                            MetricTile(stringResource(R.string.streak_short_title), streakLabel, R.drawable.ic_clock, Modifier.weight(1f).fillMaxHeight(), limeHighlight = true, subtitle = streakSubtitle, secondarySubtitle = bestStreakSubtitle)
                        }
                    }
                }
            }
        }
        InsetGroupCard {
            if (achievementsEnabled) {
                GroupedNavRow(
                    label = stringResource(R.string.achievements_title),
                    icon = R.drawable.ic_check,
                    onClick = onAchievements,
                    showDivider = true,
                )
            }
            if (onSync != null) {
                GroupedNavRow(
                    label = stringResource(R.string.sync_title),
                    icon = R.drawable.ic_clock,
                    onClick = onSync,
                    showDivider = true,
                )
            }
            GroupedNavRow(
                label = stringResource(R.string.settings_title),
                icon = R.drawable.ic_settings,
                onClick = onSettings,
                showDivider = false,
            )
        }
        SecondaryActionButton(
            stringResource(R.string.logout_action),
            onLogout,
            Modifier.fillMaxWidth(),
            icon = R.drawable.ic_logout,
        )
        Text(
            stringResource(R.string.profile_xp_basis),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun SettingsScreen(
    user: User,
    theme: ThemeMode,
    padding: PaddingValues,
    onTheme: () -> Unit,
    modifier: Modifier = Modifier,
    onReminderSettings: () -> Unit = {},
) {
    val tokens = LocalWasteNotSurfaceTokens.current
    ScrollPage(padding, modifier, maxWidth = 560.dp) {
        GroupSectionLabel(stringResource(R.string.theme_title))
        InsetGroupCard {
            val themeLabel = stringResource(R.string.theme_application)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable(onClick = onTheme)
                    .semantics { contentDescription = themeLabel }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_device), null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Column(Modifier.weight(1f)) {
                    Text(themeLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(theme.label), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                }
                Icon(painterResource(R.drawable.ic_next), null, Modifier.size(20.dp).rotate(90f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(Modifier.padding(start = 60.dp), color = MaterialTheme.colorScheme.outlineVariant)
            GroupedNavRow(
                label = stringResource(R.string.reminder_title),
                icon = R.drawable.ic_clock,
                onClick = onReminderSettings,
                showDivider = false,
            )
        }
        GroupSectionLabel(stringResource(R.string.personal_data))
        InsetGroupCard {
            SettingsMetaRow(stringResource(R.string.timezone_label), user.timezone.id, showDivider = true)
            Text(
                stringResource(R.string.timezone_read_only),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            SettingsMetaRow(stringResource(R.string.app_version, BuildConfig.VERSION_NAME), "", showDivider = false)
        }
        InsetGroupCard(contentPadding = true) {
            Text(stringResource(R.string.privacy_account), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.privacy_optional_photo), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.privacy_policy_pending), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsMetaRow(label: String, value: String, showDivider: Boolean) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (value.isNotEmpty()) Text(value, style = MaterialTheme.typography.titleSmall)
        }
        if (showDivider) HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}
