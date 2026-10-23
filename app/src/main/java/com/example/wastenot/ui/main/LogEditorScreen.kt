package com.example.wastenot.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.wastenot.R
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.data.repository.ActivityInput
import com.example.wastenot.model.DisposalMethod
import com.example.wastenot.ui.auth.MessageBanner
import com.example.wastenot.ui.components.ActionButton
import com.example.wastenot.ui.components.CategoryPicker
import com.example.wastenot.ui.components.DateField
import com.example.wastenot.ui.components.MethodSelector
import com.example.wastenot.ui.components.QuantityField
import com.example.wastenot.ui.components.WasteTextField
import java.time.LocalDate

@Composable
fun LogEditorScreen(
    draft: EditorDraft,
    referenceDate: LocalDate,
    accountTimezone: String,
    demo: Boolean,
    saving: Boolean,
    padding: PaddingValues,
    onSave: (ActivityInput) -> Unit,
    onHistory: () -> Unit,
    onResolveConflict: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = remember { EditorField.entries.associateWith { FocusRequester() } }
    val reveal = remember { EditorField.entries.associateWith { BringIntoViewRequester() } }
    val focused = remember(draft) { mutableSetOf<EditorField>() }
    val value = draft.values
    val enabled = !saving && !draft.locked
    val focusManager = LocalFocusManager.current
    val submissionReveal = remember(draft) { BringIntoViewRequester() }
    LaunchedEffect(draft.submissionError) {
        if (draft.submissionError != null) submissionReveal.bringIntoView()
    }
    LaunchedEffect(draft, draft.focusRequest) {
        draft.errors.keys.firstOrNull()?.let { field ->
            reveal.getValue(field).bringIntoView()
            focus.getValue(field).requestFocus()
        }
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
    Box(modifier, contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!demo) MessageBanner(stringResource(R.string.remote_write_message))
            MethodSelector(value.method, { draft.change(EditorField.METHOD, value.copy(method = it)) }, fieldModifier(EditorField.METHOD),
                draft.errors[EditorField.METHOD]?.let { stringResource(it) }, enabled = enabled,
                initialFocusModifier = inputModifier(EditorField.METHOD))
            CategoryPicker(value.category, { draft.change(EditorField.CATEGORY, value.copy(category = it)) }, fieldModifier(EditorField.CATEGORY),
                draft.errors[EditorField.CATEGORY]?.let { stringResource(it) }, enabled = enabled)
            QuantityField(value.quantity, { draft.change(EditorField.QUANTITY, value.copy(quantity = it)) },
                fieldModifier(EditorField.QUANTITY),
                error = draft.errors[EditorField.QUANTITY]?.let { stringResource(it) }, avoided = value.method == DisposalMethod.REDUCE, enabled = enabled,
                inputModifier = inputModifier(EditorField.QUANTITY),
            )
            DateField(value.date, referenceDate.minusDays(30), referenceDate,
                { draft.change(EditorField.DATE, value.copy(date = it)) }, fieldModifier(EditorField.DATE), enabled = enabled,
                error = draft.errors[EditorField.DATE]?.let { stringResource(it) },
                preservedDate = draft.original.date.takeIf { draft.original.version != null })
            WasteTextField(value.notes, { draft.change(EditorField.NOTES, value.copy(notes = it)) }, stringResource(R.string.notes_optional),
                fieldModifier(EditorField.NOTES), helper = stringResource(R.string.notes_helper), singleLine = false,
                errorText = draft.errors[EditorField.NOTES]?.let { stringResource(it) }, enabled = enabled, labelAbove = true,
                placeholder = stringResource(R.string.notes_placeholder))
            Text(if (value.version != null) stringResource(R.string.edit_progress_message) else stringResource(R.string.editor_date_helper, accountTimezone),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            draft.submissionError?.let { error ->
                MessageBanner(stringResource(when {
                    draft.needsConflictResolution -> R.string.conflict_form_message
                    error.saveOutcomeUnknown() -> R.string.save_outcome_unknown
                    error == ApiFailureKind.IDEMPOTENCY_CONFLICT -> R.string.save_request_conflict
                    error == ApiFailureKind.DELETED -> R.string.save_record_deleted
                    error == ApiFailureKind.VALIDATION || error == ApiFailureKind.INVALID_REQUEST -> R.string.save_rejected
                    error == ApiFailureKind.RATE_LIMITED -> R.string.save_rate_limited
                    error == ApiFailureKind.STORAGE -> R.string.save_storage_failed
                    else -> R.string.save_failed_message
                }), Modifier.bringIntoViewRequester(submissionReveal), isError = true)
            }
            ActionButton(
                stringResource(when {
                    draft.needsConflictResolution -> R.string.load_latest_log
                    draft.retryBlocked -> R.string.see_history
                    draft.locked && !saving -> R.string.retry
                    value.version != null -> R.string.save_changes
                    else -> R.string.save_activity
                }),
                { if (draft.needsConflictResolution) onResolveConflict() else if (draft.retryBlocked) onHistory() else draft.prepareSubmission(referenceDate)?.let {
                    focusManager.clearFocus()
                    onSave(it)
                } }, Modifier.fillMaxWidth(), loading = saving,
                loadingLabel = stringResource(R.string.saving), enabled = draft.needsConflictResolution || draft.retryBlocked || draft.locked || value.version == null || draft.dirty,
            )
        }
    }
}
