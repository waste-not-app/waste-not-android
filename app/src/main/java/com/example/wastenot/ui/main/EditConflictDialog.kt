package com.example.wastenot.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.example.wastenot.theme.WasteNotDialogHeading
import com.example.wastenot.ui.components.ActionButton
import com.example.wastenot.ui.components.labelResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditConflictDialog(draft: EditorValues, read: ConflictReadState?, onDismiss: () -> Unit,
    onLoad: () -> Unit, onUse: (WasteLog, Boolean) -> Unit, modifier: Modifier = Modifier) {
    val cancel = remember { FocusRequester() }
    LaunchedEffect(Unit) { cancel.requestFocus() }
    val latest = read?.latest
    val missing = read?.error in setOf(ApiFailureKind.NOT_FOUND, ApiFailureKind.DELETED)
    BasicAlertDialog(onDismissRequest = onDismiss, modifier = modifier) {
        Surface(Modifier.widthIn(max = 400.dp).fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), shadowElevation = 6.dp) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                    Box(Modifier.size(64.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_history), null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Text(stringResource(if (missing) R.string.log_unavailable else R.string.log_conflict_title),
                    Modifier.semantics { heading() }, style = WasteNotDialogHeading)
                Text(stringResource(when {
                    missing -> R.string.conflict_missing_draft
                    latest != null -> R.string.conflict_compare_message
                    else -> R.string.conflict_preserve_message
                }), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                if (read?.loading == true) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                if (read?.error != null && !missing) Text(stringResource(
                    if (read.error == ApiFailureKind.PRECONDITION_REQUIRED) R.string.conflict_latest_stale else R.string.conflict_reload_failed),
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                if (latest != null) {
                    ConflictValues(stringResource(R.string.conflict_latest, latest.version), EditorDraft.from(latest, latest.activityDate).values)
                    ConflictValues(stringResource(R.string.conflict_my_draft), draft)
                }
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (latest != null) {
                        ActionButton(stringResource(R.string.conflict_use_draft), { onUse(latest, true) }, Modifier.fillMaxWidth())
                        ConflictSecondary(R.string.conflict_use_latest, { onUse(latest, false) })
                    } else if (!missing) ActionButton(stringResource(R.string.load_latest_log), onLoad,
                        Modifier.fillMaxWidth(), loading = read?.loading == true, loadingLabel = stringResource(R.string.conflict_loading))
                    ConflictSecondary(R.string.conflict_keep_draft, onDismiss, Modifier.focusRequester(cancel))
                }
            }
        }
    }
}

@Composable
private fun ConflictValues(title: String, value: EditorValues) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
            CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyMedium) {
                Text(value.category?.let { stringResource(it.labelResource) }.orEmpty() + " · " + value.method?.let { stringResource(it.labelResource) }.orEmpty())
                Text(value.quantity.rawText + " " + stringResource(value.quantity.unit.labelResource))
                Text(WasteFormat.activityDate(value.date))
                Text(value.notes.ifBlank { stringResource(R.string.conflict_no_notes) })
            }
        }
    }
}

@Composable
private fun ConflictSecondary(label: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick, modifier.fillMaxWidth().heightIn(min = 52.dp), shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) { Text(stringResource(label), color = MaterialTheme.colorScheme.onSurface) }
}
