package com.example.wastenot.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wastenot.R
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.data.repository.ActivityStore
import com.example.wastenot.data.repository.SyncIssue
import com.example.wastenot.model.ActivitySyncState
import com.example.wastenot.model.WasteFormat
import com.example.wastenot.model.WasteLog
import com.example.wastenot.ui.auth.MessageBanner
import com.example.wastenot.ui.components.SecondaryActionButton
import com.example.wastenot.ui.components.ActionButton
import com.example.wastenot.ui.components.labelResource
import com.example.wastenot.theme.LocalWasteNotSurfaceTokens
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

internal fun syncLabel(state: ActivitySyncState): Int = when (state) {
    ActivitySyncState.SYNCED -> R.string.sync_confirmed
    ActivitySyncState.PENDING -> R.string.sync_pending
    ActivitySyncState.CONFLICT -> R.string.sync_conflict
    ActivitySyncState.FAILED -> R.string.sync_failed
}

internal class ActivitySyncViewModel(private val store: ActivityStore) : ViewModel() {
    val issues = store.pending.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val historyReady = store.historyReady.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private val mutableError = MutableStateFlow(false)
    val error = mutableError.asStateFlow()

    fun retry() = store.requestSync()
    fun resolve(id: String, keepLocal: Boolean) {
        if (mutableBusy.value) return
        mutableBusy.value = true
        mutableError.value = false
        viewModelScope.launch {
            try { mutableError.value = store.resolveSync(id, keepLocal) is ApiResult.Failure }
            finally { mutableBusy.value = false }
        }
    }
}

@Composable
internal fun ActivitySyncToast(
    count: Int,
    ready: Boolean,
    dismissedKey: String?,
    onDismissedKeyChange: (String?) -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    durationMillis: Int = 6_000,
) {
    val activeKey = if (!ready || count > 0) "$ready:$count" else null
    val visible = activeKey != null && dismissedKey != activeKey
    val timer = remember { Animatable(1f) }
    val latestOnDismiss by rememberUpdatedState(onDismissedKeyChange)

    LaunchedEffect(visible, activeKey) {
        if (visible) {
            timer.snapTo(1f)
            timer.animateTo(0f, tween(durationMillis = durationMillis, easing = LinearEasing))
            latestOnDismiss(activeKey)
        }
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { -it / 2 },
        exit = fadeOut(tween(160)) + slideOutVertically(tween(180)) { -it / 2 },
    ) {
        val tokens = LocalWasteNotSurfaceTokens.current
        val shape = RoundedCornerShape(18.dp)
        Surface(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("sync-toast"),
            shape = shape,
            color = tokens.glassTintStrong,
            border = BorderStroke(1.dp, tokens.glassEdge),
            shadowElevation = if (tokens.isDark) 0.dp else 4.dp,
        ) {
            Column(Modifier.fillMaxWidth().clip(shape)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(if (ready) R.string.sync_status else R.string.sync_initial, count),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(role = Role.Button) {
                                if (activeKey != null) latestOnDismiss(activeKey)
                                onOpen()
                            }
                            .padding(vertical = 8.dp),
                    )
                    IconButton(
                        onClick = { if (activeKey != null) latestOnDismiss(activeKey) },
                        modifier = Modifier.size(48.dp).testTag("sync-toast-close"),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(R.string.close),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                LinearProgressIndicator(
                    progress = { timer.value },
                    modifier = Modifier.fillMaxWidth().height(3.dp).testTag("sync-toast-progress"),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ActivitySyncSheet(issues: List<SyncIssue>, busy: Boolean, error: Boolean,
    onRetry: () -> Unit, onResolve: (String, Boolean) -> Unit, onDismiss: () -> Unit) {
    var decision by remember { mutableStateOf<Pair<SyncIssue, Boolean>?>(null) }
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        val syncScroll = rememberScrollState()
        Column(Modifier.fillMaxWidth().sheetVerticalScroll(syncScroll).navigationBarsPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.sync_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.sync_explanation), style = MaterialTheme.typography.bodyMedium)
            if (error) MessageBanner(stringResource(R.string.sync_resolution_failed), isError = true)
            if (issues.isEmpty()) Text(stringResource(R.string.sync_queue_empty))
            issues.forEach { issue ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(syncLabel(issue.local.syncState)), style = MaterialTheme.typography.titleSmall)
                    SyncRecord(issue.local, stringResource(if (issue.deleting) R.string.sync_local_delete else R.string.sync_local))
                    if (issue.local.syncState in setOf(ActivitySyncState.CONFLICT, ActivitySyncState.FAILED)) {
                        issue.remote?.let { SyncRecord(it, stringResource(R.string.sync_remote)) }
                            ?: Text(stringResource(R.string.sync_remote_unavailable))
                        if (issue.remote != null) SecondaryActionButton(
                            stringResource(if (issue.deleting) R.string.sync_retry_delete else R.string.sync_keep_local),
                            { decision = issue to true }, Modifier.fillMaxWidth(), enabled = !busy)
                        else if (!issue.deleting && issue.error in setOf(ApiFailureKind.AI_EXPIRED, ApiFailureKind.AI_REFERENCE_INVALID)) {
                            SecondaryActionButton(stringResource(R.string.sync_save_manual), { decision = issue to true },
                                Modifier.fillMaxWidth(), enabled = !busy)
                        }
                        if (issue.error == ApiFailureKind.AI_ALREADY_LINKED) Text(stringResource(R.string.sync_already_linked))
                        SecondaryActionButton(stringResource(R.string.sync_use_remote),
                            { decision = issue to false }, Modifier.fillMaxWidth(), enabled = !busy)
                    }
                    HorizontalDivider()
                }
            }
            ActionButton(stringResource(R.string.sync_retry), onRetry, Modifier.fillMaxWidth(), enabled = !busy)
        }
    }
    decision?.let { (issue, keep) -> AlertDialog(
        onDismissRequest = { decision = null },
        title = { Text(stringResource(R.string.sync_decision_title)) },
        text = { Text(stringResource(when {
            keep && issue.remote == null -> R.string.sync_manual_confirmation
            keep -> R.string.sync_keep_confirmation
            else -> R.string.sync_discard_confirmation
        })) },
        confirmButton = { TextButton(onClick = { onResolve(issue.localId, keep); decision = null }) { Text(stringResource(R.string.sync_confirm_decision)) } },
        dismissButton = { TextButton(onClick = { decision = null }) { Text(stringResource(R.string.cancel)) } },
    ) }
}

@Composable
private fun SyncRecord(log: WasteLog, label: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(stringResource(R.string.save_confirm_category_method, stringResource(log.category.labelResource), stringResource(log.method.labelResource)))
        val quantity = WasteFormat.quantity(log.quantity)
        Text(stringResource(R.string.save_confirm_quantity_date, quantity.amount, stringResource(quantity.unit.labelResource), WasteFormat.activityDate(log.activityDate)))
        Text(log.notes.ifBlank { stringResource(R.string.no_notes) })
    }
}
