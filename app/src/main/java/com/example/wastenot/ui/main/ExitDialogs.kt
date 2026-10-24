package com.example.wastenot.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.wastenot.R
import com.example.wastenot.theme.WasteNotDialogHeading
import com.example.wastenot.ui.components.ActionButton

@Composable
internal fun LogoutDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    ExitConfirmationDialog(
        title = stringResource(R.string.logout_title),
        message = stringResource(R.string.logout_message),
        confirmLabel = stringResource(R.string.logout_action),
        dismissLabel = stringResource(R.string.cancel),
        icon = R.drawable.ic_logout,
        destructive = false,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}

@Composable
internal fun DiscardChangesDialog(pendingOutcome: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    ExitConfirmationDialog(
        title = stringResource(if (pendingOutcome) R.string.close_editor_title else R.string.discard_title),
        message = stringResource(if (pendingOutcome) R.string.discard_pending_message else R.string.discard_message),
        confirmLabel = stringResource(if (pendingOutcome) R.string.close_editor else R.string.discard_action),
        dismissLabel = stringResource(R.string.keep_editing),
        icon = R.drawable.ic_alert,
        destructive = !pendingOutcome,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExitConfirmationDialog(title: String, message: String, confirmLabel: String, dismissLabel: String,
    icon: Int, destructive: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val cancel = remember { FocusRequester() }
    BasicAlertDialog(onDismissRequest = onDismiss) {
        val focused = LocalWindowInfo.current.isWindowFocused
        val inputMode = LocalInputModeManager.current.inputMode
        LaunchedEffect(focused, inputMode) {
            if (focused && inputMode == InputMode.Keyboard) cancel.requestFocus()
        }
        Surface(Modifier.widthIn(max = 400.dp).fillMaxWidth().onPreviewKeyEvent {
            if (it.key == Key.Escape) {
                if (it.type == KeyEventType.KeyUp) onDismiss()
                true
            } else false
        }, shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), shadowElevation = 6.dp) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                        Icon(painterResource(icon), null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Text(title, Modifier.fillMaxWidth().semantics { heading() }, style = WasteNotDialogHeading)
                Text(message, Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium)
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionButton(confirmLabel, onConfirm, Modifier.fillMaxWidth(), destructive = destructive)
                    OutlinedButton(onDismiss, Modifier.fillMaxWidth().heightIn(min = 52.dp).focusRequester(cancel),
                        shape = MaterialTheme.shapes.small, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                        Text(dismissLabel, Modifier.width(IntrinsicSize.Max), color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}
