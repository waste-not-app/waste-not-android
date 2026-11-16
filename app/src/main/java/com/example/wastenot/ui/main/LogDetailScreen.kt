package com.example.wastenot.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.wastenot.R
import com.example.wastenot.model.ActivitySyncState
import com.example.wastenot.model.DisposalMethod
import com.example.wastenot.model.WasteFormat
import com.example.wastenot.model.WasteLog
import com.example.wastenot.ui.auth.MessageBanner
import com.example.wastenot.ui.components.ActionButton
import com.example.wastenot.ui.components.AsyncStatePanel
import com.example.wastenot.ui.components.CategoryIconBadge
import com.example.wastenot.ui.components.GroupSectionLabel
import com.example.wastenot.ui.components.InsetGroupCard
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
    photosEnabled: Boolean = false,
    photoBytes: ByteArray? = null,
    photoLoading: Boolean = false,
    photoError: Boolean = false,
    onRetryPhoto: () -> Unit = {},
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
            if (log.syncState != ActivitySyncState.SYNCED) {
                MessageBanner(stringResource(syncLabel(log.syncState)))
            }
            if (loadError) {
                MessageBanner(stringResource(R.string.refresh_failed), isError = true)
                TextActionButton(stringResource(R.string.retry), onRetry)
            }
            val badgeColor = when (log.method) {
                DisposalMethod.REDUCE, DisposalMethod.RECYCLE -> MaterialTheme.colorScheme.primaryContainer
                DisposalMethod.REUSE -> MaterialTheme.colorScheme.secondaryContainer
                DisposalMethod.TRASH -> MaterialTheme.colorScheme.surfaceVariant
            }
            InsetGroupCard(contentPadding = true) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    CategoryIconBadge(log.category, log.category.iconResource, sizeDp = 46)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(log.category.labelResource), style = MaterialTheme.typography.titleMedium)
                        Surface(shape = RoundedCornerShape(999.dp), color = badgeColor, contentColor = contentColorFor(badgeColor)) {
                            Text(
                                stringResource(log.method.labelResource),
                                Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
                val quantity = WasteFormat.quantity(log.quantity)
                Text(buildAnnotatedString {
                    append(quantity.amount)
                    withStyle(SpanStyle(fontSize = MaterialTheme.typography.headlineSmall.fontSize, fontWeight = FontWeight.Medium)) {
                        append(" " + stringResource(quantity.unit.labelResource))
                    }
                }, style = MaterialTheme.typography.displayLarge)
                if (log.method == DisposalMethod.REDUCE) {
                    Text(stringResource(R.string.reduce_detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            InsetGroupCard {
                DetailMetadata(stringResource(R.string.activity_date), WasteFormat.activityDate(log.activityDate), showDivider = true)
                DetailMetadata(stringResource(R.string.timezone_label), log.activityTimezone.id, showDivider = true)
                DetailMetadata(stringResource(R.string.method), stringResource(log.method.labelResource), showDivider = false)
            }
            InsetGroupCard(contentPadding = true) {
                Text(stringResource(R.string.notes_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text(log.notes.ifBlank { stringResource(R.string.no_notes) }, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (photosEnabled && log.photoId != null) {
                GroupSectionLabel(stringResource(R.string.photo_detail_title))
                InsetGroupCard(contentPadding = true) {
                    when {
                        photoBytes != null -> {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            ) {
                                AsyncImage(
                                    model = photoBytes,
                                    contentDescription = stringResource(R.string.photo_preview_description),
                                    modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp)
                                        .clip(RoundedCornerShape(16.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                        }
                        photoLoading -> {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text(
                                stringResource(R.string.photo_detail_loading),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        photoError -> {
                            MessageBanner(stringResource(R.string.photo_detail_error), isError = true)
                            TextActionButton(stringResource(R.string.retry), onRetryPhoto)
                        }
                    }
                }
            }
            if (loading) {
                Text(stringResource(R.string.logs_loading), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
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
private fun DetailMetadata(label: String, value: String, showDivider: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.titleSmall)
        }
        if (showDivider) HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}
