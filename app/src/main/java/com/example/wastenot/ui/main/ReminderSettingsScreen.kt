package com.example.wastenot.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import com.example.wastenot.data.local.ReminderSetting
import com.example.wastenot.data.local.ReminderPermissionPreferences
import com.example.wastenot.data.local.ReminderPermissionAction
import com.example.wastenot.data.local.reminderPermissionAction
import com.example.wastenot.theme.WasteNotTheme
import com.example.wastenot.ui.auth.MessageBanner
import com.example.wastenot.ui.components.ActionButton
import com.example.wastenot.ui.components.SecondaryActionButton
import com.example.wastenot.ui.components.TextActionButton
import com.github.skydoves.navgraph.annotations.NavDestination
import com.github.skydoves.navgraph.annotations.NavPreview
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@NavDestination(route = ReminderSettings::class)
@Composable
fun ReminderSettingsScreen(
    setting: ReminderSetting,
    padding: PaddingValues,
    onSave: (ReminderSetting) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var enabled by rememberSaveable(setting) { mutableStateOf(setting.enabled) }
    var hour by rememberSaveable(setting) { mutableIntStateOf(setting.hour) }
    var minute by rememberSaveable(setting) { mutableIntStateOf(setting.minute) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    val permissionPreferences = remember(context) { ReminderPermissionPreferences(context) }
    var permissionDenied by rememberSaveable { mutableStateOf(permissionPreferences.denied) }
    var pendingEnable by rememberSaveable { mutableStateOf(false) }
    var settingsFailed by remember { mutableStateOf(false) }
    var savedMessage by remember { mutableStateOf<String?>(null) }

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
            if (pendingEnable) enabled = true
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

    LaunchedEffect(setting) {
        enabled = setting.enabled
        hour = setting.hour
        minute = setting.minute
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionPreferences.recordResult(granted)
        pendingEnable = false
        if (granted) {
            permissionDenied = false
            enabled = true
        } else {
            permissionDenied = true
            enabled = false
        }
    }

    fun formatTime(h: Int, m: Int): String = String.format(Locale.forLanguageTag("id-ID"), "%02d.%02d", h, m)

    if (pickingTime) {
        val pickerState = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickingTime = false },
            title = { Text(stringResource(R.string.reminder_choose_time)) },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextActionButton(stringResource(R.string.apply), {
                    hour = pickerState.hour
                    minute = pickerState.minute
                    savedMessage = null
                    pickingTime = false
                })
            },
            dismissButton = {
                TextActionButton(stringResource(R.string.cancel), { pickingTime = false })
            },
        )
    }

    ScrollPage(padding, modifier, maxWidth = 560.dp) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
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
                    onCheckedChange = { checked ->
                        savedMessage = null
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
                    },
                )
            }
        }

        if (permissionDenied) {
            MessageBanner(stringResource(R.string.reminder_permission_denied), isError = true)
            SecondaryActionButton(
                label = stringResource(R.string.reminder_open_settings),
                onClick = ::openNotificationSettings,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (settingsFailed) {
            MessageBanner(stringResource(R.string.reminder_settings_unavailable), isError = true)
        }

        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.reminder_time_label), style = MaterialTheme.typography.titleSmall)
            OutlinedButton(
                onClick = { pickingTime = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                enabled = enabled,
                shape = MaterialTheme.shapes.small,
                border = BorderStroke(
                    1.dp,
                    if (enabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant,
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
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

        savedMessage?.let { MessageBanner(it) }

        val savedText = stringResource(R.string.reminder_saved, formatTime(hour, minute))
        val disabledText = stringResource(R.string.reminder_disabled_saved)
        ActionButton(
            label = stringResource(R.string.reminder_save),
            onClick = {
                val next = ReminderSetting(enabled = enabled, hour = hour, minute = minute)
                onSave(next)
                savedMessage = if (next.enabled) savedText else disabledText
            },
            modifier = Modifier.fillMaxWidth(),
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
            onSave = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}
