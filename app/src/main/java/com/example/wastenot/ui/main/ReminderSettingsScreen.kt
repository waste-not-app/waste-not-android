package com.example.wastenot.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.wastenot.R
import com.example.wastenot.ReminderSettings
import com.example.wastenot.data.local.ReminderSaveResult
import com.example.wastenot.data.local.ReminderSetting
import com.example.wastenot.data.local.ReminderPermissionPreferences
import com.example.wastenot.data.local.ReminderPermissionAction
import com.example.wastenot.data.local.reminderPermissionAction
import com.example.wastenot.theme.LocalWasteNotSurfaceTokens
import com.example.wastenot.theme.WasteNotTheme
import com.example.wastenot.ui.auth.MessageBanner
import com.example.wastenot.ui.components.ActionButton
import com.example.wastenot.ui.components.SecondaryActionButton
import com.example.wastenot.ui.components.TextActionButton
import com.github.skydoves.navgraph.annotations.NavDestination
import com.github.skydoves.navgraph.annotations.NavPreview
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@NavDestination(route = ReminderSettings::class)
@Composable
fun ReminderSettingsScreen(
    setting: ReminderSetting?,
    padding: PaddingValues,
    onSave: suspend (ReminderSetting) -> ReminderSaveResult,
    modifier: Modifier = Modifier,
    accountSave: ReminderSaveProgress? = null,
) {
    if (setting == null) {
        ScrollPage(padding, modifier, maxWidth = 560.dp) {
            Text(stringResource(R.string.reminder_loading), style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        return
    }

    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val recovery = accountSave?.takeUnless { it.result == ReminderSaveResult.SAVED }
    val initialDraft = recovery?.setting ?: setting
    var enabled by rememberSaveable { mutableStateOf(initialDraft.enabled) }
    var hour by rememberSaveable { mutableIntStateOf(initialDraft.hour) }
    var minute by rememberSaveable { mutableIntStateOf(initialDraft.minute) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    val permissionPreferences = remember(context) { ReminderPermissionPreferences(context) }
    var permissionDenied by rememberSaveable { mutableStateOf(permissionPreferences.denied) }
    var pendingEnable by rememberSaveable { mutableStateOf(false) }
    var settingsFailed by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saveResult by rememberSaveable { mutableStateOf<ReminderSaveResult?>(null) }
    var followAccountSave by rememberSaveable { mutableStateOf(recovery != null) }
    val accountWorking = accountSave != null && accountSave.result == null
    val working = saving || accountWorking

    fun clearSaveFeedback() {
        saveResult = null
        followAccountSave = false
    }

    fun notificationsAllowed(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        settingsFailed = runCatching { context.startActivity(intent) }.isFailure
    }

    val reconcilePermission by rememberUpdatedState {
        if (notificationsAllowed()) {
            if (permissionPreferences.denied) permissionPreferences.recordResult(granted = true)
            permissionDenied = false
            if (pendingEnable && !enabled) {
                enabled = true
                clearSaveFeedback()
            }
            pendingEnable = false
            settingsFailed = false
        } else {
            permissionDenied = permissionPreferences.denied || enabled
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, permissionPreferences) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            reconcilePermission()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionPreferences.recordResult(granted)
        pendingEnable = false
        if (granted) {
            permissionDenied = false
            enabled = true
            clearSaveFeedback()
        } else {
            permissionDenied = true
            enabled = false
        }
    }

    fun formatTime(h: Int, m: Int): String = String.format(Locale.getDefault(), "%02d:%02d", h, m)

    if (pickingTime) {
        val pickerState = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickingTime = false },
            title = { Text(stringResource(R.string.reminder_choose_time)) },
            text = {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val compact = maxWidth < 300.dp || maxHeight < 420.dp ||
                        LocalDensity.current.fontScale >= 1.5f
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        if (compact) TimeInput(state = pickerState)
                        else TimePicker(state = pickerState)
                    }
                }
            },
            confirmButton = {
                TextActionButton(stringResource(R.string.apply), {
                    hour = pickerState.hour
                    minute = pickerState.minute
                    clearSaveFeedback()
                    pickingTime = false
                })
            },
            dismissButton = {
                TextActionButton(stringResource(R.string.cancel), { pickingTime = false })
            },
        )
    }

    val changeEnabled: (Boolean) -> Unit = change@{ checked ->
        if (saving || accountWorking) return@change
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        clearSaveFeedback()
        if (!checked) {
            enabled = false
            pendingEnable = false
        } else when (reminderPermissionAction(
            notificationsAllowed = notificationsAllowed(),
            deniedBefore = permissionPreferences.denied,
            runtimePermissionRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
        )) {
            ReminderPermissionAction.ENABLE -> {
                permissionPreferences.recordResult(granted = true)
                permissionDenied = false
                pendingEnable = false
                settingsFailed = false
                enabled = true
            }
            ReminderPermissionAction.SETTINGS -> {
                permissionDenied = true
                pendingEnable = true
                openNotificationSettings()
            }
            ReminderPermissionAction.REQUEST -> {
                pendingEnable = true
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    ScrollPage(padding, modifier, maxWidth = 560.dp) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .toggleable(enabled, enabled = !working, role = Role.Switch, onValueChange = changeEnabled)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.reminder_switch_label),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        stringResource(R.string.reminder_helper),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = null,
                    enabled = !working,
                )
            }
        }

        if (permissionDenied) {
            MessageBanner(stringResource(R.string.reminder_permission_denied), isError = true)
            SecondaryActionButton(
                label = stringResource(R.string.reminder_open_settings),
                onClick = ::openNotificationSettings,
                modifier = Modifier.fillMaxWidth(),
                enabled = !working,
            )
        }
        if (settingsFailed) {
            MessageBanner(stringResource(R.string.reminder_settings_unavailable), isError = true)
        }

        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val tokens = LocalWasteNotSurfaceTokens.current
            Text(stringResource(R.string.reminder_time_label), style = MaterialTheme.typography.titleSmall)
            OutlinedButton(
                onClick = { if (!saving && !accountWorking) pickingTime = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                enabled = enabled && !working,
                shape = MaterialTheme.shapes.small,
                border = BorderStroke(
                    1.5.dp,
                    if (enabled && !working) tokens.fieldEdge else MaterialTheme.colorScheme.outlineVariant,
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = tokens.fieldWell,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    stringResource(R.string.reminder_time_value, formatTime(hour, minute)),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Icon(painterResource(R.drawable.ic_clock), null)
            }
        }

        val outcome = if (working) null else saveResult ?: accountSave?.takeIf {
            followAccountSave && it.setting.enabled == enabled && it.setting.hour == hour && it.setting.minute == minute
        }?.result
        when (outcome) {
            ReminderSaveResult.SAVED -> MessageBanner(
                if (enabled) stringResource(R.string.reminder_saved, formatTime(hour, minute))
                else stringResource(R.string.reminder_disabled_saved),
            )
            ReminderSaveResult.PERSISTENCE_FAILED ->
                MessageBanner(stringResource(R.string.reminder_persistence_failed), isError = true)
            ReminderSaveResult.SCHEDULING_FAILED -> MessageBanner(
                stringResource(if (enabled) R.string.reminder_schedule_failed else R.string.reminder_cancel_failed),
                isError = true,
            )
            null -> Unit
        }

        val failed = outcome != null && outcome != ReminderSaveResult.SAVED
        ActionButton(
            label = stringResource(if (failed) R.string.retry else R.string.reminder_save),
            onClick = save@{
                if (saving || accountWorking) return@save
                if (enabled && !setting.enabled && !notificationsAllowed()) {
                    permissionDenied = true
                    return@save
                }
                val next = ReminderSetting(enabled = enabled, hour = hour, minute = minute)
                pendingEnable = false
                saveResult = null
                followAccountSave = true
                saving = true
                scope.launch {
                    try {
                        saveResult = onSave(next)
                    } finally {
                        saving = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !enabled || setting.enabled || !permissionDenied,
            loading = working,
            loadingLabel = stringResource(R.string.reminder_saving),
        )
    }
}

@NavPreview(route = ReminderSettings::class)
@Preview(showBackground = true)
@Composable
private fun ReminderSettingsScreenPreview() {
    WasteNotTheme {
        ReminderSettingsScreen(
            setting = ReminderSetting(enabled = true, hour = 20, minute = 0),
            padding = PaddingValues(),
            onSave = { ReminderSaveResult.SAVED },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
