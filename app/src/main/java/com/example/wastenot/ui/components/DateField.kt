package com.example.wastenot.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.wastenot.R
import com.example.wastenot.model.WasteFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    date: LocalDate,
    minimum: LocalDate,
    maximum: LocalDate,
    onChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    error: String? = null,
) {
    var choosing by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.activity_date), style = MaterialTheme.typography.titleSmall)
        OutlinedButton(
            onClick = { choosing = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) { Text(WasteFormat.activityDate(date)) }
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
    }
    if (choosing) {
        val dates = remember(minimum, maximum) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate() in minimum..maximum
                override fun isSelectableYear(year: Int): Boolean = year in minimum.year..maximum.year
            }
        }
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            yearRange = minOf(date.year, minimum.year)..maxOf(date.year, maximum.year),
            selectableDates = dates,
        )
        DatePickerDialog(
            onDismissRequest = { choosing = false },
            confirmButton = {
                TextButton(
                    enabled = picker.selectedDateMillis?.let(dates::isSelectableDate) == true,
                    onClick = {
                        picker.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                        choosing = false
                    },
                ) { Text(stringResource(R.string.apply)) }
            },
            dismissButton = { TextButton(onClick = { choosing = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(picker) }
    }
}
