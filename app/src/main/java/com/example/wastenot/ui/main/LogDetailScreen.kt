package com.example.wastenot.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import com.example.wastenot.R
import com.example.wastenot.model.DisposalMethod
import com.example.wastenot.model.WasteFormat
import com.example.wastenot.model.WasteLog
import com.example.wastenot.ui.auth.MessageBanner
import com.example.wastenot.ui.components.ActionButton
import com.example.wastenot.ui.components.AsyncStatePanel
import com.example.wastenot.ui.components.SecondaryActionButton
import com.example.wastenot.ui.components.TextActionButton
import com.example.wastenot.ui.components.labelResource

@Composable
fun LogDetailScreen(
    log: WasteLog?,
    loading: Boolean,
    loadError: Boolean,
    padding: PaddingValues,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onHistory: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScrollPage(padding, modifier) {
        if (log == null) {
            when {
                loading -> AsyncStatePanel(stringResource(R.string.logs_loading), R.drawable.ic_clock,
                    Modifier.fillMaxWidth(), loading = true)
                loadError -> {
                    AsyncStatePanel(stringResource(R.string.logs_load_failed), R.drawable.ic_alert, Modifier.fillMaxWidth(),
                        description = stringResource(R.string.async_retry_hint)) {
                        ActionButton(stringResource(R.string.retry), onRetry, Modifier.fillMaxWidth())
                    }
                }
                else -> {
                    AsyncStatePanel(stringResource(R.string.log_unavailable), R.drawable.ic_history, Modifier.fillMaxWidth(),
                        description = stringResource(R.string.log_unavailable_message)) {
                        ActionButton(stringResource(R.string.see_history), onHistory, Modifier.fillMaxWidth())
                    }
                }
            }
        } else {
            if (log.syncState != com.example.wastenot.model.ActivitySyncState.SYNCED) {
                MessageBanner(stringResource(syncLabel(log.syncState)))
            }
            if (loadError) {
                MessageBanner(stringResource(R.string.refresh_failed), isError = true)
                TextActionButton(stringResource(R.string.retry), onRetry)
            }
            val badgeColor = when (log.method) {
                DisposalMethod.REDUCE, DisposalMethod.RECYCLE -> MaterialTheme.colorScheme.primaryContainer
                DisposalMethod.REUSE -> MaterialTheme.colorScheme.secondaryContainer
                DisposalMethod.TRASH -> MaterialTheme.colorScheme.surfaceContainer
            }
            Surface(shape = RoundedCornerShape(8.dp), color = badgeColor, contentColor = contentColorFor(badgeColor)) {
                Text(stringResource(log.method.labelResource), Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(log.category.labelResource), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val quantity = WasteFormat.quantity(log.quantity)
                Text(buildAnnotatedString {
                    append(quantity.amount)
                    withStyle(SpanStyle(fontSize = MaterialTheme.typography.headlineSmall.fontSize, fontWeight = FontWeight.Medium)) {
                        append(" " + stringResource(quantity.unit.labelResource))
                    }
                }, style = MaterialTheme.typography.displayLarge)
                if (log.method == DisposalMethod.REDUCE) Text(stringResource(R.string.reduce_detail), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column {
                DetailMetadata(stringResource(R.string.activity_date), WasteFormat.activityDate(log.activityDate), Modifier.fillMaxWidth())
                DetailMetadata(stringResource(R.string.timezone_label), log.activityTimezone.id, Modifier.fillMaxWidth())
                DetailMetadata(stringResource(R.string.method), stringResource(log.method.labelResource), Modifier.fillMaxWidth())
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.notes_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                Text(log.notes.ifBlank { stringResource(R.string.no_notes) }, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            val canWrite = log.version > 0 && !loading && !loadError
            ActionButton(stringResource(R.string.log_edit), onEdit, Modifier.fillMaxWidth(), enabled = canWrite)
            SecondaryActionButton(stringResource(R.string.delete_log), onDelete, Modifier.fillMaxWidth(), enabled = canWrite)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.created_timestamp, WasteFormat.timestamp(log.createdAt, log.activityTimezone)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.updated_timestamp, WasteFormat.timestamp(log.updatedAt, log.activityTimezone)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DetailMetadata(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        if (LocalDensity.current.fontScale >= 1.5f) {
            Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                Text(value, style = MaterialTheme.typography.titleSmall)
            }
        } else {
            Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                Text(value, style = MaterialTheme.typography.titleSmall)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
