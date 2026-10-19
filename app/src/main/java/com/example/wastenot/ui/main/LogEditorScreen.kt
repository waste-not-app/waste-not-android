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
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.wastenot.R
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
    demo: Boolean,
    saving: Boolean,
    saveError: Boolean,
    padding: PaddingValues,
    onSave: (ActivityInput) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = remember { EditorField.entries.associateWith { FocusRequester() } }
    val reveal = remember { EditorField.entries.associateWith { BringIntoViewRequester() } }
    val value = draft.values
    LaunchedEffect(draft.focusRequest) {
        draft.errors.keys.firstOrNull()?.let { field ->
            reveal.getValue(field).bringIntoView()
            focus.getValue(field).requestFocus()
        }
    }
    fun fieldModifier(field: EditorField) = Modifier
        .fillMaxWidth()
        .bringIntoViewRequester(reveal.getValue(field))
        .focusRequester(focus.getValue(field))
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
            if (value.version != null) MessageBanner(stringResource(R.string.edit_progress_message))
            MethodSelector(value.method, { draft.change(EditorField.METHOD, value.copy(method = it)) }, fieldModifier(EditorField.METHOD),
                draft.errors[EditorField.METHOD]?.let { stringResource(it) }, enabled = !saving)
            CategoryPicker(value.category, { draft.change(EditorField.CATEGORY, value.copy(category = it)) }, fieldModifier(EditorField.CATEGORY),
                draft.errors[EditorField.CATEGORY]?.let { stringResource(it) }, enabled = !saving)
            QuantityField(value.quantity, { draft.change(EditorField.QUANTITY, value.copy(quantity = it)) },
                Modifier.fillMaxWidth().bringIntoViewRequester(reveal.getValue(EditorField.QUANTITY)),
                error = draft.errors[EditorField.QUANTITY]?.let { stringResource(it) }, avoided = value.method == DisposalMethod.REDUCE, enabled = !saving,
                inputModifier = Modifier.focusRequester(focus.getValue(EditorField.QUANTITY)))
            DateField(value.date, referenceDate.minusDays(30), referenceDate,
                { draft.change(EditorField.DATE, value.copy(date = it)) }, fieldModifier(EditorField.DATE), enabled = !saving,
                error = draft.errors[EditorField.DATE]?.let { stringResource(it) })
            WasteTextField(value.notes, { draft.change(EditorField.NOTES, value.copy(notes = it)) }, stringResource(R.string.notes_optional),
                fieldModifier(EditorField.NOTES), helper = stringResource(R.string.notes_helper), singleLine = false,
                errorText = draft.errors[EditorField.NOTES]?.let { stringResource(it) }, enabled = !saving)
            if (saveError) MessageBanner(stringResource(R.string.save_failed_message), isError = true)
            ActionButton(
                stringResource(if (value.version != null) R.string.save_changes else R.string.save_activity),
                { draft.validate(referenceDate)?.let(onSave) }, Modifier.fillMaxWidth(), loading = saving,
                loadingLabel = stringResource(R.string.saving), enabled = value.version == null || draft.dirty,
            )
        }
    }
}
