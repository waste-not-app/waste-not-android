package com.example.wastenot.ui.main

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver
import com.example.wastenot.R
import com.example.wastenot.data.repository.ActivityInput
import com.example.wastenot.model.ActivityDates
import com.example.wastenot.model.DisposalMethod
import com.example.wastenot.model.Quantity
import com.example.wastenot.model.QuantityInput
import com.example.wastenot.model.QuantityParseResult
import com.example.wastenot.model.QuantityUnit
import com.example.wastenot.model.WasteCategory
import com.example.wastenot.model.WasteLog
import com.example.wastenot.model.parseQuantity
import java.time.LocalDate

enum class EditorField { METHOD, CATEGORY, QUANTITY, DATE, NOTES }

data class EditorValues(
    val category: WasteCategory?, val method: DisposalMethod?, val quantity: QuantityInput,
    val date: LocalDate, val notes: String, val version: Int?,
)

class EditorDraft(val original: EditorValues) {
    var values by mutableStateOf(original)
        private set
    var errors by mutableStateOf<Map<EditorField, Int>>(emptyMap())
        private set
    var focusRequest by mutableStateOf(0)
        private set
    val dirty: Boolean get() = values != original

    fun change(field: EditorField, updated: EditorValues) { values = updated; errors = errors - field }

    fun validateField(field: EditorField, referenceDate: LocalDate) {
        errors = errors - field + validationErrors(referenceDate).filterKeys { it == field }
    }

    private fun validationErrors(referenceDate: LocalDate): Map<EditorField, Int> {
        val current = values
        val notes = current.notes.trim()
        val quantity = parseQuantity(current.quantity.rawText, current.quantity.unit)
        return buildMap {
            if (current.method == null) put(EditorField.METHOD, R.string.error_choose_method)
            if (current.category == null) put(EditorField.CATEGORY, R.string.error_choose_category)
            if (quantity is QuantityParseResult.Invalid) put(EditorField.QUANTITY, when (quantity) {
                QuantityParseResult.Invalid.EMPTY -> R.string.error_quantity_empty
                QuantityParseResult.Invalid.FORMAT -> R.string.error_quantity_format
                QuantityParseResult.Invalid.PRECISION -> R.string.error_quantity_precision
                QuantityParseResult.Invalid.OUT_OF_RANGE -> R.string.error_quantity_range
            })
            if (!ActivityDates.canSave(current.date, referenceDate, if (original.version != null) original.date else null)) put(EditorField.DATE, R.string.error_activity_date)
            if (notes.length > 500) put(EditorField.NOTES, R.string.error_notes_length)
        }
    }

    fun validate(referenceDate: LocalDate): ActivityInput? {
        val current = values
        val quantity = parseQuantity(current.quantity.rawText, current.quantity.unit)
        val found = validationErrors(referenceDate)
        errors = found
        if (found.isNotEmpty()) { focusRequest++; return null }
        return ActivityInput(checkNotNull(current.category), checkNotNull(current.method),
            (quantity as QuantityParseResult.Valid).quantity, current.date, current.notes.trim())
    }

    companion object {
        fun from(log: WasteLog?, date: LocalDate) = EditorDraft(EditorValues(log?.category, log?.method,
            when (val quantity = log?.quantity) {
                is Quantity.Weight -> QuantityInput(quantity.grams.toString(), QuantityUnit.GRAM)
                is Quantity.Count -> QuantityInput(quantity.items.toString(), QuantityUnit.ITEM)
                null -> QuantityInput()
            }, log?.activityDate ?: date, log?.notes.orEmpty(), log?.version))

        private fun encode(value: EditorValues) = listOf(value.category?.name.orEmpty(), value.method?.name.orEmpty(),
            value.quantity.rawText, value.quantity.unit.name, value.date.toString(), value.notes, value.version?.toString().orEmpty())
        private fun decode(value: List<String>) = EditorValues(value[0].takeIf(String::isNotEmpty)?.let(WasteCategory::valueOf),
            value[1].takeIf(String::isNotEmpty)?.let(DisposalMethod::valueOf), QuantityInput(value[2], QuantityUnit.valueOf(value[3])),
            LocalDate.parse(value[4]), value[5], value[6].toIntOrNull())
        val Saver = listSaver<EditorDraft, String>(
            save = { encode(it.original) + encode(it.values) },
            restore = { EditorDraft(decode(it.take(7))).apply { values = decode(it.drop(7)) } },
        )
    }
}
