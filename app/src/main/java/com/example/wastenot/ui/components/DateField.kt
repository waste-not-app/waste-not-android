package com.example.wastenot.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.material3.Icon
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.DisplayMode
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
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
import com.example.wastenot.theme.LocalWasteNotSurfaceTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    date: LocalDate?,
    minimum: LocalDate,
    maximum: LocalDate,
    onChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    error: String? = null,
    label: String = stringResource(R.string.activity_date),
    preservedDate: LocalDate? = null,
) {
    var choosing by rememberSaveable(enabled) { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        val tokens = LocalWasteNotSurfaceTokens.current
        OutlinedButton(
            onClick = { choosing = true },
            enabled = enabled,
            shape = RoundedCornerShape(15.dp),
            border = BorderStroke(
                1.5.dp,
                when {
                    error != null -> MaterialTheme.colorScheme.error
                    !enabled -> MaterialTheme.colorScheme.outlineVariant
                    else -> tokens.fieldEdge
                },
            ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = tokens.fieldWell,
                contentColor = MaterialTheme.colorScheme.onSurface,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .focusProperties { canFocus = enabled },
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(date?.let(WasteFormat::activityDate) ?: stringResource(R.string.choose_filter_date),
                    Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Box(
                    Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_calendar), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
    }
    if (choosing && enabled) {
        val compact = LocalConfiguration.current.screenWidthDp < 360 || LocalDensity.current.fontScale >= 1.5f
        val firstYear = minOf(minimum.year, preservedDate?.year ?: minimum.year)
        val lastYear = maxOf(maximum.year, preservedDate?.year ?: maximum.year)
        val dates = remember(minimum, maximum, preservedDate) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().let {
                        it == preservedDate || it in minimum..maximum
                    }
                override fun isSelectableYear(year: Int): Boolean = year in firstYear..lastYear
            }
        }
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = date?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            initialDisplayedMonthMillis = (date ?: maximum).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            yearRange = minOf(date?.year ?: firstYear, firstYear)..maxOf(date?.year ?: lastYear, lastYear),
            selectableDates = dates,
            initialDisplayMode = if (compact) DisplayMode.Input else DisplayMode.Picker,
        )
        LaunchedEffect(compact, picker) {
            if (compact) picker.displayMode = DisplayMode.Input
        }
        DatePickerDialog(
            onDismissRequest = { choosing = false },
            confirmButton = {
                TextActionButton(
                    label = stringResource(R.string.apply),
                    enabled = picker.selectedDateMillis?.let(dates::isSelectableDate) == true,
                    onClick = {
                        picker.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                        choosing = false
                    },
                )
            },
            dismissButton = { TextActionButton(stringResource(R.string.cancel), onClick = { choosing = false }) },
        ) {
            DatePicker(
                picker,
                modifier = Modifier.verticalScroll(rememberScrollState()),
                showModeToggle = !compact,
            )
        }
    }
}
