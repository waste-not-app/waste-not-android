package com.example.wastenot.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.wastenot.R
import com.example.wastenot.model.DisposalMethod
import com.example.wastenot.model.WasteCategory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPicker(
    category: WasteCategory?,
    onChange: (WasteCategory) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = stringResource(R.string.category)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        ExposedDropdownMenuBox(expanded, onExpandedChange = { if (enabled) expanded = it }) {
            OutlinedTextField(
                value = category?.let { stringResource(it.labelResource) }.orEmpty(),
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.choose_category)) },
                enabled = enabled,
                isError = error != null,
                supportingText = if (error != null) ({ Text(error) }) else null,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                shape = MaterialTheme.shapes.small,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    errorContainerColor = MaterialTheme.colorScheme.surface,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .semantics { contentDescription = label }
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                WasteCategory.entries.forEach { option ->
                    DropdownMenuItem(text = { Text(stringResource(option.labelResource)) }, onClick = { onChange(option); expanded = false })
                }
            }
        }
    }
}

@Composable
fun MethodSelector(
    method: DisposalMethod?,
    onChange: (DisposalMethod) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    enabled: Boolean = true,
    initialFocusModifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.method_editor), style = MaterialTheme.typography.titleSmall)
        BoxWithConstraints(Modifier.fillMaxWidth().selectableGroup()) {
            val columns = if (maxWidth < 300.dp || LocalDensity.current.fontScale >= 1.5f) 1 else 2
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DisposalMethod.entries.chunked(columns).forEach { row ->
                    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { option ->
                            val selected = method == option
                            Surface(
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                shape = MaterialTheme.shapes.small,
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .then(if (option == DisposalMethod.entries.first()) initialFocusModifier else Modifier)
                                        .focusProperties { canFocus = enabled }
                                        .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = { onChange(option) })
                                        .heightIn(min = 56.dp)
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    RadioButton(selected, onClick = null, enabled = enabled, modifier = Modifier.size(20.dp),
                                        colors = RadioButtonDefaults.colors(
                                            unselectedColor = MaterialTheme.colorScheme.outline,
                                            disabledSelectedColor = MaterialTheme.colorScheme.primary,
                                            disabledUnselectedColor = MaterialTheme.colorScheme.outline,
                                        ))
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(stringResource(option.labelResource), style = MaterialTheme.typography.titleSmall)
                                        Text(stringResource(option.descriptionResource), style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
fun RangeSelector(days: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Row(Modifier.height(IntrinsicSize.Min).selectableGroup()) {
            listOf(7, 30).forEach { range ->
                val selected = days == range
                Box(Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp)
                    .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                    .selectable(selected, role = Role.RadioButton, onClick = { onChange(range) })
                    .padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.day_range, range), style = MaterialTheme.typography.labelLarge,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

val WasteCategory.labelResource: Int get() = when (this) {
    WasteCategory.ORGANIC -> R.string.category_organic
    WasteCategory.PLASTIC -> R.string.category_plastic
    WasteCategory.PAPER -> R.string.category_paper
    WasteCategory.GLASS -> R.string.category_glass
    WasteCategory.METAL -> R.string.category_metal
    WasteCategory.ELECTRONIC -> R.string.category_electronic
    WasteCategory.OTHER -> R.string.category_other
}

val DisposalMethod.labelResource: Int get() = when (this) {
    DisposalMethod.REDUCE -> R.string.method_reduce
    DisposalMethod.REUSE -> R.string.method_reuse
    DisposalMethod.RECYCLE -> R.string.method_recycle
    DisposalMethod.TRASH -> R.string.method_trash
}

val DisposalMethod.descriptionResource: Int get() = when (this) {
    DisposalMethod.REDUCE -> R.string.method_reduce_description
    DisposalMethod.REUSE -> R.string.method_reuse_description
    DisposalMethod.RECYCLE -> R.string.method_recycle_description
    DisposalMethod.TRASH -> R.string.method_trash_description
}
