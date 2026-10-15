package com.example.wastenot.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.wastenot.R
import com.example.wastenot.model.QuantityInput
import com.example.wastenot.model.QuantityUnit

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
        Text(stringResource(R.string.quantity_unit), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuantityUnit.entries.forEach { unit ->
                FilterChip(
                    selected = input.unit == unit,
                    onClick = { onChange(input.changeUnit(unit)) },
                    label = { Text(stringResource(unit.labelResource)) },
                    enabled = enabled,
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
        WasteTextField(
            value = input.rawText,
            onValueChange = { onChange(input.copy(rawText = it)) },
            label = stringResource(if (avoided) R.string.quantity_avoided else if (input.unit == QuantityUnit.ITEM) R.string.quantity_count else R.string.quantity_weight),
            modifier = inputModifier.fillMaxWidth(),
            helper = stringResource(if (avoided) R.string.reduce_separate else if (input.unit == QuantityUnit.ITEM) R.string.count_helper else R.string.weight_helper),
            errorText = error,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = if (input.unit == QuantityUnit.KILOGRAM) KeyboardType.Decimal else KeyboardType.Number),
        )
    }
}

val QuantityUnit.labelResource: Int get() = when (this) {
    QuantityUnit.GRAM -> R.string.unit_gram
    QuantityUnit.KILOGRAM -> R.string.unit_kilogram
    QuantityUnit.ITEM -> R.string.unit_item
}
