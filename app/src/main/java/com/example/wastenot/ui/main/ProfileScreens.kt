package com.example.wastenot.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
) {
    ScrollPage(padding, modifier, maxWidth = 560.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            val initials = user.name.split(' ').filter(String::isNotBlank).take(2)
                .joinToString("") { String(Character.toChars(it.codePointAt(0))).uppercase(Locale.forLanguageTag("id-ID")) }
            Box(Modifier.sizeIn(minWidth = 56.dp, minHeight = 56.dp)
                .background(LocalWasteNotGradients.current.accent, MaterialTheme.shapes.medium).padding(12.dp),
                contentAlignment = Alignment.Center) {
                Text(initials, color = OnAccent, style = MaterialTheme.typography.titleLarge)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(user.name, style = MaterialTheme.typography.titleLarge)
                Text(user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(user.timezone.id, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error != null) {
            MessageBanner(stringResource(R.string.profile_refresh_failed), isError = true)
            TextActionButton(stringResource(R.string.retry), onRetry)
        }
        if (progress != null) {
            Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
                .background(LocalWasteNotGradients.current.summary).drawBehind {
                    drawCircle(Color.White.copy(alpha = 0.06f), radius = 82.dp.toPx(),
                        center = Offset(size.width, 0f), style = Stroke(width = 20.dp.toPx()))
                }.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.profile_progress), color = SummarySecondaryText)
                Text(stringResource(R.string.level_title, progress.level), color = SummaryText, style = MaterialTheme.typography.displayLarge)
                Text(stringResource(R.string.profile_xp_count, WasteFormat.integer(progress.xp.toLong()),
                    WasteFormat.integer(progress.activeLogCount.toLong())), color = SummarySecondaryText)
            }
            ProgressMeter(progress, Modifier.fillMaxWidth())
            if (progress.activeLogCount == 0) {
                Text(stringResource(R.string.profile_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                ActionButton(stringResource(R.string.record_activity), onRecord, Modifier.fillMaxWidth())
            }
        } else if (!loading && error == null) MessageBanner(stringResource(R.string.profile_progress_unavailable))
        AccountMenuRow(stringResource(R.string.settings_title), onSettings, Modifier.fillMaxWidth())
        SecondaryActionButton(stringResource(R.string.logout_action), onLogout,
            Modifier.fillMaxWidth(), icon = R.drawable.ic_logout)
        Text(stringResource(R.string.profile_xp_basis), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SettingsScreen(user: User, theme: ThemeMode, padding: PaddingValues, onTheme: () -> Unit, modifier: Modifier = Modifier) {
    ScrollPage(padding, modifier, maxWidth = 560.dp) {
        Text(stringResource(R.string.theme_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.theme_application), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        val themeLabel = stringResource(R.string.theme_application)
        OutlinedButton(onClick = onTheme, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            .semantics { contentDescription = themeLabel }, shape = MaterialTheme.shapes.small,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface,
                containerColor = MaterialTheme.colorScheme.surface)) {
            Text(stringResource(theme.label), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Icon(painterResource(R.drawable.ic_next), null, Modifier.size(20.dp).rotate(90f))
        }
        Text(stringResource(R.string.personal_data), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.privacy_account), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.privacy_optional_photo), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.privacy_policy_pending), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.account_timezone, user.timezone.id), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.timezone_read_only), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(stringResource(R.string.app_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AccountMenuRow(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
      Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), color = Color.Transparent) {
        Row(Modifier.padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_settings), null, Modifier.size(20.dp))
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Icon(painterResource(R.drawable.ic_next), null, Modifier.size(20.dp))
        }
      }
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
