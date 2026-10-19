package com.example.wastenot.ui.main

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.wastenot.R
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.model.WasteFormat
import com.example.wastenot.model.WasteLog
import com.example.wastenot.ui.components.ActionButton
import com.example.wastenot.ui.components.labelResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeleteLogDialog(
    log: WasteLog,
    busy: Boolean,
    error: ApiFailureKind?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onReload: () -> Unit,
    onUnavailable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val conflict = error == ApiFailureKind.VERSION_CONFLICT
    val needsVersion = conflict || error == ApiFailureKind.PRECONDITION_REQUIRED
    val unavailable = error == ApiFailureKind.NOT_FOUND || error == ApiFailureKind.DELETED
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { cancelFocus.requestFocus() }
    BasicAlertDialog(
        modifier = modifier,
        onDismissRequest = { if (!busy) onDismiss() },
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 400.dp).fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.extraLarge,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 6.dp,
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
                    Icon(painterResource(R.drawable.ic_delete), null,
                        Modifier.padding(16.dp).size(24.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Text(stringResource(when {
                    unavailable -> R.string.log_unavailable
                    conflict -> R.string.log_conflict_title
                    needsVersion -> R.string.log_version_required
                    else -> R.string.delete_log_title
                }), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                Text(when {
                    unavailable -> stringResource(R.string.log_unavailable_message)
                    needsVersion -> stringResource(R.string.log_conflict_message)
                    else -> stringResource(R.string.delete_log_message, stringResource(log.category.labelResource), WasteFormat.activityDate(log.activityDate))
                }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (error != null && !needsVersion && !unavailable) Text(stringResource(R.string.delete_failed), color = MaterialTheme.colorScheme.error)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when {
                        unavailable -> ActionButton(stringResource(R.string.see_history), onUnavailable, Modifier.fillMaxWidth(), enabled = !busy)
                        needsVersion -> ActionButton(stringResource(R.string.load_latest_log), onReload, Modifier.fillMaxWidth(), enabled = !busy)
                        else -> ActionButton(stringResource(R.string.delete_action), onConfirm, Modifier.fillMaxWidth(), loading = busy,
                            loadingLabel = stringResource(R.string.deleting_log), destructive = true)
                    }
                    OutlinedButton(onClick = onDismiss, enabled = !busy, shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).focusRequester(cancelFocus)) {
                        Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}
