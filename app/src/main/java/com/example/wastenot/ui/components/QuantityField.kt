package com.example.wastenot.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.wastenot.R
import com.example.wastenot.model.QuantityInput
import com.example.wastenot.model.QuantityUnit
import com.example.wastenot.theme.LocalWasteNotSurfaceTokens

@Composable
fun QuantityField(
    input: QuantityInput,
    onChange: (QuantityInput) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    avoided: Boolean = false,
    enabled: Boolean = true,
    inputModifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val stacked = maxWidth < 280.dp || LocalDensity.current.fontScale >= 1.5f
            val field: @Composable (Modifier) -> Unit = { placement ->
                WasteTextField(
                    value = input.rawText,
                    onValueChange = { onChange(input.copy(rawText = it)) },
                    label = stringResource(if (avoided) R.string.quantity_avoided else if (input.unit == QuantityUnit.ITEM) R.string.quantity_count else R.string.quantity_weight),
                    modifier = placement.then(inputModifier),
                    errorText = error,
                    enabled = enabled,
                    labelAbove = true,
                    placeholder = stringResource(when (input.unit) {
                        QuantityUnit.GRAM -> R.string.quantity_example_weight
                        QuantityUnit.KILOGRAM -> R.string.quantity_example_kilogram
                        QuantityUnit.ITEM -> R.string.quantity_example_count
                    }),
                    keyboardOptions = KeyboardOptions(keyboardType = if (input.unit == QuantityUnit.KILOGRAM) KeyboardType.Decimal else KeyboardType.Number),
                )
            }
            if (stacked) Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                field(Modifier.fillMaxWidth())
                QuantityUnitPicker(input.unit, { onChange(input.changeUnit(it)) }, Modifier.fillMaxWidth(), enabled)
            } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                field(Modifier.weight(1f))
                QuantityUnitPicker(input.unit, { onChange(input.changeUnit(it)) }, Modifier.width(104.dp), enabled)
            }
        }
        Text(stringResource(if (avoided) R.string.reduce_separate else R.string.quantity_unit_helper),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuantityUnitPicker(unit: QuantityUnit, onChange: (QuantityUnit) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    var expanded by remember(enabled) { mutableStateOf(false) }
    val label = stringResource(R.string.quantity_unit)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Box {
            val tokens = LocalWasteNotSurfaceTokens.current
            OutlinedButton(
                onClick = { expanded = true }, enabled = enabled,
                shape = RoundedCornerShape(15.dp),
                border = BorderStroke(1.5.dp, if (enabled) tokens.fieldEdge else MaterialTheme.colorScheme.outlineVariant),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp)
                    .semantics { contentDescription = label }
                    .focusProperties { canFocus = enabled },
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(unit.optionLabelResource), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded)
                }
            }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                QuantityUnit.entries.forEach { option ->
                    DropdownMenuItem(text = { Text(stringResource(option.optionLabelResource)) },
                        enabled = enabled,
                        onClick = { onChange(option); expanded = false })
                }
            }
        }
    }
}

private val QuantityUnit.optionLabelResource: Int get() = if (this == QuantityUnit.ITEM) R.string.unit_item_option else labelResource

val QuantityUnit.labelResource: Int get() = when (this) {
    QuantityUnit.GRAM -> R.string.unit_gram
    QuantityUnit.KILOGRAM -> R.string.unit_kilogram
    QuantityUnit.ITEM -> R.string.unit_item
}
