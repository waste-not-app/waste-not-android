package com.example.wastenot.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    ExposedDropdownMenuBox(expanded, onExpandedChange = { if (enabled) expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = category?.let { stringResource(it.labelResource) }.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.category)) },
            placeholder = { Text(stringResource(R.string.choose_category)) },
            enabled = enabled,
            isError = error != null,
            supportingText = if (error != null) ({ Text(error) }) else null,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            WasteCategory.entries.forEach { option ->
                DropdownMenuItem(text = { Text(stringResource(option.labelResource)) }, onClick = { onChange(option); expanded = false })
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
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.method), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DisposalMethod.entries.forEach { option ->
                FilterChip(
                    selected = method == option,
                    onClick = { onChange(option) },
                    label = { Text(stringResource(option.labelResource)) },
                    enabled = enabled,
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
        if (method != null) Text(stringResource(method.descriptionResource), style = MaterialTheme.typography.bodySmall)
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
fun RangeSelector(days: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(7, 30).forEach { range ->
            FilterChip(
                days == range, { onChange(range) },
                label = { Text(stringResource(R.string.day_range, range)) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
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
