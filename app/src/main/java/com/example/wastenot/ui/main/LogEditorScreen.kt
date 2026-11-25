package com.example.wastenot.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import android.net.Uri
import coil3.compose.AsyncImage
import com.example.wastenot.ui.components.InsetGroupCard
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.wastenot.R
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.data.repository.ActivityInput
import com.example.wastenot.model.DisposalMethod
import com.example.wastenot.ui.auth.MessageBanner
import com.example.wastenot.ui.components.ActionButton
import com.example.wastenot.ui.components.SecondaryActionButton
import com.example.wastenot.ui.components.CategoryPicker
import com.example.wastenot.ui.components.DateField
import com.example.wastenot.ui.components.MethodSelector
import com.example.wastenot.ui.components.QuantityField
import com.example.wastenot.ui.components.WasteTextField
import com.example.wastenot.ui.components.TextActionButton
import com.example.wastenot.ui.components.focusOutline
import com.example.wastenot.ui.components.labelResource
import java.time.LocalDate

@Composable
fun LogEditorScreen(
    draft: EditorDraft,
    referenceDate: LocalDate,
    accountTimezone: String,
    saving: Boolean,
    padding: PaddingValues,
    onSave: (ActivityInput) -> Unit,
    onHistory: () -> Unit,
    onResolveConflict: () -> Unit,
    modifier: Modifier = Modifier,
    persistence: EditorPersistenceState? = null,
    onRetryPersistence: () -> Unit = {},
    saveConfirmed: Boolean = false,
    restored: Boolean = false,
    photosEnabled: Boolean = false,
    photoPreviewBytes: ByteArray? = null,
    photoBusy: Boolean = false,
    photoError: Int? = null,
    onPickPhoto: (Uri) -> Unit = {},
    onRemovePhoto: () -> Unit = {},
    onDismissPhotoError: () -> Unit = {},
    scanPrefilled: Boolean = false,
) {
    val focus = remember { EditorField.entries.associateWith { FocusRequester() } }
    val reveal = remember { EditorField.entries.associateWith { BringIntoViewRequester() } }
    val focused = remember(draft) { mutableSetOf<EditorField>() }
    val value = draft.values
    val submissionPending = !saveConfirmed && draft.locked && (saving || draft.submissionError == null)
    val submissionError = draft.submissionError?.takeUnless { saveConfirmed || submissionPending }
    val enabled = !saving && !draft.locked && !saveConfirmed
    val focusManager = LocalFocusManager.current
    val submissionReveal = remember(draft) { BringIntoViewRequester() }
    var choicesExpanded by rememberSaveable { mutableStateOf(false) }
    var detailsExpanded by rememberSaveable { mutableStateOf(false) }
    fun firstError(): EditorField? =
        if (scanPrefilled && EditorField.QUANTITY in draft.errors) EditorField.QUANTITY else draft.errors.keys.firstOrNull()
    LaunchedEffect(scanPrefilled, photoError) {
        if (scanPrefilled && photoError != null) detailsExpanded = true
    }
    fun inputModifier(field: EditorField) = Modifier
        .bringIntoViewRequester(reveal.getValue(field))
        .focusRequester(focus.getValue(field))
    fun fieldModifier(field: EditorField) = Modifier
        .fillMaxWidth()
        .then(if (field == EditorField.METHOD || field == EditorField.QUANTITY) Modifier else inputModifier(field))
        .onFocusChanged {
            if (it.hasFocus) focused.add(field)
            else if (focused.remove(field)) draft.validateField(field, referenceDate)
        }
        .then(if (field == EditorField.METHOD || field == EditorField.QUANTITY) Modifier.focusGroup() else Modifier)
    val method: @Composable () -> Unit = {
        MethodSelector(value.method, { draft.change(EditorField.METHOD, value.copy(method = it)) }, fieldModifier(EditorField.METHOD),
            draft.errors[EditorField.METHOD]?.let { stringResource(it) }, enabled = enabled,
            initialFocusModifier = inputModifier(EditorField.METHOD))
    }
    val category: @Composable () -> Unit = {
        CategoryPicker(value.category, { draft.change(EditorField.CATEGORY, value.copy(category = it)) }, fieldModifier(EditorField.CATEGORY),
            draft.errors[EditorField.CATEGORY]?.let { stringResource(it) }, enabled = enabled)
    }
    val amount: @Composable () -> Unit = {
        QuantityField(value.quantity, { draft.change(EditorField.QUANTITY, value.copy(quantity = it)) },
            fieldModifier(EditorField.QUANTITY),
            error = draft.errors[EditorField.QUANTITY]?.let { stringResource(it) }, avoided = value.method == DisposalMethod.REDUCE, enabled = enabled,
            inputModifier = inputModifier(EditorField.QUANTITY),
        )
    }
    val date: @Composable () -> Unit = {
        DateField(value.date, referenceDate.minusDays(30), referenceDate,
            { draft.change(EditorField.DATE, value.copy(date = it)) }, fieldModifier(EditorField.DATE), enabled = enabled,
            error = draft.errors[EditorField.DATE]?.let { stringResource(it) },
            preservedDate = draft.original.date.takeIf { draft.original.version != null })
    }
    val dateHelp: @Composable () -> Unit = {
        Text(if (value.version != null) stringResource(R.string.edit_progress_message) else stringResource(R.string.editor_date_helper, accountTimezone),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val picker = if (photosEnabled) rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPickPhoto(uri)
    } else null
    val save: @Composable (Modifier) -> Unit = { placement ->
        ActionButton(
            stringResource(when {
                saveConfirmed -> R.string.draft_record_saved
                draft.needsConflictResolution -> R.string.load_latest_log
                draft.retryBlocked -> R.string.see_history
                draft.locked -> R.string.retry
                value.version != null -> R.string.save_changes
                else -> R.string.save_activity
            }),
            {
                when {
                    saving || saveConfirmed || (draft.locked && draft.submissionError == null) -> Unit
                    draft.needsConflictResolution -> onResolveConflict()
                    draft.retryBlocked -> onHistory()
                    else -> {
                        val input = draft.prepareSubmission(referenceDate)
                        if (input != null) {
                            focusManager.clearFocus()
                            onSave(input)
                        } else if (scanPrefilled) {
                            when (firstError()) {
                                EditorField.METHOD, EditorField.CATEGORY -> choicesExpanded = true
                                EditorField.DATE, EditorField.NOTES -> detailsExpanded = true
                                else -> Unit
                            }
                        }
                    }
                }
            },
            placement, loading = (saving || submissionPending) && !saveConfirmed,
            loadingLabel = stringResource(R.string.saving), enabled = !saveConfirmed &&
                (draft.needsConflictResolution || draft.retryBlocked || draft.locked || value.version == null || draft.dirty),
        )
    }
    val form: @Composable (Modifier, Boolean) -> Unit = { placement, pinSave ->
        Column(
            modifier = placement
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LaunchedEffect(submissionError) {
                if (submissionError != null) submissionReveal.bringIntoView()
            }
            LaunchedEffect(draft, draft.focusRequest) {
                firstError()?.let { field ->
                    val fieldVisible = !scanPrefilled || when (field) {
                        EditorField.METHOD, EditorField.CATEGORY -> choicesExpanded
                        EditorField.DATE, EditorField.NOTES -> detailsExpanded
                        EditorField.QUANTITY -> true
                    }
                    if (!fieldVisible) return@LaunchedEffect
                    reveal.getValue(field).bringIntoView()
                    focus.getValue(field).requestFocus()
                }
            }
            if (scanPrefilled) {
                InsetGroupCard(contentPadding = true) {
                    Text(stringResource(R.string.editor_required_amount), Modifier.semantics { heading() },
                        style = MaterialTheme.typography.titleMedium)
                    amount()
                }
                val methodLabel = value.method?.let { stringResource(it.labelResource) }
                val categoryLabel = value.category?.let { stringResource(it.labelResource) }
                EditorDisclosure(
                    stringResource(R.string.editor_scan_choices),
                    choicesExpanded,
                    { choicesExpanded = !choicesExpanded },
                    Modifier.fillMaxWidth(),
                    summary = if (methodLabel != null && categoryLabel != null)
                        stringResource(R.string.editor_scan_choice_summary, methodLabel, categoryLabel)
                    else methodLabel ?: categoryLabel,
                )
            }
            if (restored) Text(stringResource(R.string.draft_restored), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (value.aiProvenance != null) {
                Text(stringResource(R.string.ai_provenance_attached), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            draft.manualConversionReason?.let { reason ->
                MessageBanner(stringResource(reason), isError = true)
                TextActionButton(stringResource(R.string.ai_use_manual), draft::useManualEntry, enabled = enabled)
            }
            if (!scanPrefilled || choicesExpanded) {
                method()
                InsetGroupCard(contentPadding = true) {
                    category()
                    if (!scanPrefilled) {
                        amount()
                        date()
                    }
                }
            }
            if (scanPrefilled) {
                EditorDisclosure(
                    stringResource(if (photosEnabled) R.string.editor_extra_details_with_photo else R.string.editor_extra_details),
                    detailsExpanded,
                    { detailsExpanded = !detailsExpanded },
                    Modifier.fillMaxWidth(),
                )
            }
            if (!scanPrefilled || detailsExpanded) InsetGroupCard(contentPadding = true) {
                if (scanPrefilled) {
                    date()
                    dateHelp()
                }
                WasteTextField(value.notes, { draft.change(EditorField.NOTES, value.copy(notes = it)) }, stringResource(R.string.notes_optional),
                    fieldModifier(EditorField.NOTES), helper = stringResource(R.string.notes_helper), singleLine = false,
                    errorText = draft.errors[EditorField.NOTES]?.let { stringResource(it) }, enabled = enabled, labelAbove = true,
                    placeholder = stringResource(R.string.notes_placeholder))
                if (picker != null) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(stringResource(R.string.photo_section_title), style = MaterialTheme.typography.titleSmall)
                        if (photoBusy) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text(
                                stringResource(R.string.photo_uploading),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        photoError?.let { errRes ->
                            MessageBanner(stringResource(errRes), isError = true)
                            TextActionButton(stringResource(R.string.photo_continue_without), onDismissPhotoError)
                        }
                        if (value.photoId != null) {
                            if (photoPreviewBytes != null) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                ) {
                                    AsyncImage(
                                        model = photoPreviewBytes,
                                        contentDescription = stringResource(R.string.photo_preview_description),
                                        modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp)
                                            .clip(RoundedCornerShape(14.dp)),
                                        contentScale = ContentScale.Crop,
                                    )
                                }
                            } else {
                                Text(
                                    stringResource(R.string.photo_saved_summary),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SecondaryActionButton(
                                    stringResource(R.string.photo_replace),
                                    { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                    modifier = Modifier.weight(1f),
                                    enabled = enabled && !photoBusy,
                                )
                                TextActionButton(
                                    stringResource(R.string.photo_remove),
                                    onRemovePhoto,
                                    enabled = enabled && !photoBusy,
                                )
                            }
                        } else {
                            SecondaryActionButton(
                                stringResource(R.string.photo_choose),
                                { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = enabled && !photoBusy,
                            )
                        }
                    }
                }
            }
            if (!scanPrefilled) dateHelp()
            submissionError?.let { error ->
                val latestError = stringResource(when {
                    draft.needsConflictResolution -> R.string.conflict_form_message
                    error.saveOutcomeUnknown() -> R.string.save_outcome_unknown
                    error == ApiFailureKind.IDEMPOTENCY_CONFLICT -> R.string.save_request_conflict
                    error == ApiFailureKind.DELETED -> R.string.save_record_deleted
                    error in setOf(ApiFailureKind.AI_EXPIRED, ApiFailureKind.AI_REFERENCE_INVALID,
                        ApiFailureKind.AI_ALREADY_LINKED) -> R.string.ai_reference_rejected
                    error == ApiFailureKind.VALIDATION || error == ApiFailureKind.INVALID_REQUEST -> R.string.save_rejected
                    error == ApiFailureKind.RATE_LIMITED -> R.string.save_rate_limited
                    error == ApiFailureKind.STORAGE -> R.string.save_storage_failed
                    else -> R.string.save_failed_message
                })
                val message = if (draft.outcomeUnknown && !error.saveOutcomeUnknown()) {
                    stringResource(R.string.save_outcome_retry_error, stringResource(R.string.save_outcome_unknown), latestError)
                } else latestError
                MessageBanner(message, Modifier.bringIntoViewRequester(submissionReveal), isError = true)
            }
            if (persistence != null) {
                if (persistence.failure == DraftFailure.WRITE || persistence.failure == DraftFailure.FINISH) {
                    MessageBanner(stringResource(when {
                        saveConfirmed -> R.string.draft_saved_cleanup_failed
                        persistence.failure == DraftFailure.FINISH -> R.string.draft_cleanup_failed
                        else -> R.string.draft_write_failed
                    }), isError = true)
                    SecondaryActionButton(stringResource(R.string.retry), onRetryPersistence,
                        Modifier.fillMaxWidth(), enabled = !persistence.busy)
                } else if (!submissionPending) Text(stringResource(when {
                    saveConfirmed -> R.string.draft_completing
                    persistence.persisted?.fields == draft.snapshot() && !persistence.writing -> R.string.draft_saved
                    else -> R.string.draft_saving
                }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!pinSave) save(Modifier.fillMaxWidth())
        }
    }
    BoxWithConstraints(
        modifier
            .padding(padding)
            .consumeWindowInsets(padding)
            .imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        val pinSave = scanPrefilled && constraints.hasBoundedHeight && maxHeight >= 380.dp
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .then(if (pinSave) Modifier.fillMaxHeight() else Modifier),
        ) {
            if (submissionPending && !pinSave) {
                Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
                    Text(
                        stringResource(R.string.save_waiting_for_result),
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            form(if (pinSave) Modifier.weight(1f) else Modifier, pinSave)
            if (pinSave) {
                Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
                    Column {
                        HorizontalDivider()
                        save(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorDisclosure(
    label: String,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
) {
    val expansionState = stringResource(if (expanded) R.string.daily_expanded else R.string.daily_collapsed)
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier
            .heightIn(min = 48.dp)
            .focusOutline(focused, shape, colors.primary, colors.surface)
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.outline, shape)
            .clickable(interactionSource = interactions, indication = ripple(), role = Role.Button, onClick = onClick)
            .semantics { stateDescription = expansionState }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
            if (summary != null) Text(summary, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        Icon(painterResource(R.drawable.ic_chevron_down), null,
            Modifier.size(20.dp).rotate(if (expanded) 180f else 0f), tint = colors.onSurfaceVariant)
    }
}
