package com.example.wastenot.ui.main

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver
import com.example.wastenot.R
import com.example.wastenot.LogEditor
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.data.repository.ActivityInput
import com.example.wastenot.model.ActivityDates
import com.example.wastenot.model.AiLogProvenance
import com.example.wastenot.model.DisposalMethod
import com.example.wastenot.model.Quantity
import com.example.wastenot.model.QuantityInput
import com.example.wastenot.model.QuantityParseResult
import com.example.wastenot.model.QuantityUnit
import com.example.wastenot.model.WasteCategory
import com.example.wastenot.model.WasteLog
import com.example.wastenot.model.WasteGuide
import com.example.wastenot.model.parseQuantity
import java.time.LocalDate

enum class EditorField { METHOD, CATEGORY, QUANTITY, DATE, NOTES }

data class EditorValues(
    val category: WasteCategory?, val method: DisposalMethod?, val quantity: QuantityInput,
    val date: LocalDate, val notes: String, val version: Int?,
    val aiProvenance: AiLogProvenance? = null,
    val photoId: String? = null,
    val clearPhoto: Boolean = false,
)

class EditorDraft(original: EditorValues) {
    var original by mutableStateOf(original)
        private set
    var values by mutableStateOf(original)
        private set
    var errors by mutableStateOf<Map<EditorField, Int>>(emptyMap())
        private set
    var focusRequest by mutableStateOf(0)
        private set
    var submittedValues by mutableStateOf<EditorValues?>(null)
        private set
    var submissionError by mutableStateOf<ApiFailureKind?>(null)
        private set
    val dirty: Boolean get() = values != original
    val locked: Boolean get() = submittedValues != null
    val retryBlocked: Boolean get() = submissionError == ApiFailureKind.IDEMPOTENCY_CONFLICT || submissionError == ApiFailureKind.DELETED
    val needsConflictResolution: Boolean get() = original.version != null &&
        submissionError in setOf(ApiFailureKind.VERSION_CONFLICT, ApiFailureKind.PRECONDITION_REQUIRED)
    val outcomeUnknown: Boolean get() = locked && submissionError != null && !retryBlocked && !needsConflictResolution
    private val aiReferenceRejected: Boolean get() = when (submissionError) {
        ApiFailureKind.AI_EXPIRED, ApiFailureKind.AI_REFERENCE_INVALID, ApiFailureKind.AI_ALREADY_LINKED -> true
        else -> false
    }
    val manualConversionReason: Int? get() {
        val source = values.aiProvenance ?: return null
        if (locked) return null
        return when {
            aiReferenceRejected -> R.string.ai_reference_rejected
            values.category != source.category -> R.string.ai_category_changed
            values.method == DisposalMethod.REDUCE -> R.string.ai_reduce_manual
            else -> null
        }
    }

    fun resolveConflict(latest: WasteLog, keepDraft: Boolean): Boolean {
        if (!needsConflictResolution || locked || latest.version <= (original.version ?: 0)) return false
        val mine = values
        val fresh = valuesFrom(latest, mine.date)
        original = fresh
        values = if (keepDraft) mine.copy(version = latest.version) else fresh
        errors = emptyMap()
        submittedValues = null
        submissionError = null
        return true
    }

    fun applyScan(key: LogEditor): EditorDraft {
        if (key.logId != null || key.guideId != null || key.scanCategory == null) return this
        val category = WasteCategory.entries.find { it.name == key.scanCategory } ?: return this
        val method = DisposalMethod.entries.find { it.name == key.scanMethod && it != DisposalMethod.REDUCE }
        val source = key.scanRecommendationId?.let { AiLogProvenance(it, category) }
        require(source == null || method != null)
        change(EditorField.CATEGORY, values.copy(category = category, method = method, aiProvenance = source))
        return this
    }

    fun useManualEntry() {
        if (locked) return
        values = values.copy(aiProvenance = null)
        errors = errors - EditorField.CATEGORY - EditorField.METHOD
        submissionError = null
    }

    fun change(field: EditorField, updated: EditorValues) {
        if (locked) return
        values = updated
        errors = errors - field
        if (!needsConflictResolution && !aiReferenceRejected) submissionError = null
    }

    fun attachPhoto(photoId: String) {
        if (locked) return
        values = values.copy(photoId = photoId, clearPhoto = false)
        if (!aiReferenceRejected) submissionError = null
    }

    fun removePhoto() {
        if (locked) return
        values = values.copy(
            photoId = null,
            clearPhoto = original.photoId != null,
        )
        if (!aiReferenceRejected) submissionError = null
    }

    fun validateField(field: EditorField, referenceDate: LocalDate) {
        if (locked) return
        errors = errors - field + validationErrors(referenceDate).filterKeys { it == field }
    }

    fun prepareSubmission(referenceDate: LocalDate): ActivityInput? {
        if (retryBlocked || needsConflictResolution) return null
        val submitted = submittedValues
        if (submitted != null) return input(submitted)
        return validate(referenceDate)?.also {
            submittedValues = values
            submissionError = null
        }
    }

    fun submissionFailed(error: ApiFailureKind) {
        val wasUnknown = outcomeUnknown
        submissionError = error
        if (!error.saveOutcomeUnknown() && !retryBlocked && (!wasUnknown || needsConflictResolution)) submittedValues = null
    }

    private fun validationErrors(referenceDate: LocalDate): Map<EditorField, Int> {
        val current = values
        val notes = current.notes.trim()
        val quantity = parseQuantity(current.quantity.rawText, current.quantity.unit)
        return buildMap {
            if (current.method == null) put(EditorField.METHOD, R.string.error_choose_method)
            if (current.category == null) put(EditorField.CATEGORY, R.string.error_choose_category)
            current.aiProvenance?.let { source ->
                if (current.category != source.category) put(EditorField.CATEGORY, R.string.ai_category_changed)
                if (current.method == DisposalMethod.REDUCE) put(EditorField.METHOD, R.string.ai_reduce_manual)
            }
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
        val found = validationErrors(referenceDate)
        errors = found
        if (found.isNotEmpty()) { focusRequest++; return null }
        return input(current)
    }

    fun snapshot(): List<String> {
        val base = encode(original) + encode(values) +
            (submittedValues?.let(::encode) ?: List(7) { "" }) + submissionError?.name.orEmpty()
        val sources = listOf(original, values, submittedValues).flatMap { value ->
            listOf(value?.aiProvenance?.recommendationId.orEmpty(), value?.aiProvenance?.category?.name.orEmpty())
        }
        val photos = listOf(original, values, submittedValues).flatMap { value ->
            listOf(value?.photoId.orEmpty(), if (value?.clearPhoto == true) "1" else "")
        }
        return when {
            photos.any(String::isNotEmpty) -> base + sources + photos
            sources.any(String::isNotEmpty) -> base + sources
            else -> base
        }
    }

    companion object {
        fun forGuide(guide: WasteGuide, date: LocalDate) = EditorDraft(EditorValues(
            guide.category, guide.method, QuantityInput(), date, "", null,
        ))

        private fun input(value: EditorValues) = ActivityInput(checkNotNull(value.category), checkNotNull(value.method),
            (parseQuantity(value.quantity.rawText, value.quantity.unit) as QuantityParseResult.Valid).quantity,
            value.date, value.notes.trim(), value.aiProvenance, photoId = value.photoId, clearPhoto = value.clearPhoto)

        fun from(log: WasteLog?, date: LocalDate) = EditorDraft(valuesFrom(log, date))
        private fun valuesFrom(log: WasteLog?, date: LocalDate) = EditorValues(log?.category, log?.method,
            when (val quantity = log?.quantity) {
                is Quantity.Weight -> QuantityInput(quantity.grams.toString(), QuantityUnit.GRAM)
                is Quantity.Count -> QuantityInput(quantity.items.toString(), QuantityUnit.ITEM)
                null -> QuantityInput()
            }, log?.activityDate ?: date, log?.notes.orEmpty(), log?.version, photoId = log?.photoId)
        private fun encode(value: EditorValues) = listOf(value.category?.name.orEmpty(), value.method?.name.orEmpty(),
            value.quantity.rawText, value.quantity.unit.name, value.date.toString(), value.notes, value.version?.toString().orEmpty())
        private fun decode(value: List<String>) = EditorValues(value[0].takeIf(String::isNotEmpty)?.let(WasteCategory::valueOf),
            value[1].takeIf(String::isNotEmpty)?.let(DisposalMethod::valueOf), QuantityInput(value[2], QuantityUnit.valueOf(value[3])),
            LocalDate.parse(value[4]), value[5], value[6].takeIf(String::isNotEmpty)?.toInt()?.also { require(it > 0) })

        fun fromSnapshot(snapshot: List<String>): EditorDraft {
            require(snapshot.size in setOf(14, 21, 22, 28, 34))
            fun source(index: Int): AiLogProvenance? {
                if (snapshot.size < 28) return null
                val id = snapshot[22 + index * 2]
                val category = snapshot[23 + index * 2]
                require(id.isEmpty() == category.isEmpty())
                return id.takeIf(String::isNotEmpty)?.let { AiLogProvenance(it, WasteCategory.valueOf(category)) }
            }
            fun photo(index: Int): Pair<String?, Boolean> {
                if (snapshot.size < 34) return null to false
                val id = snapshot[28 + index * 2].takeIf(String::isNotEmpty)
                val clear = snapshot[29 + index * 2] == "1"
                require(!(id != null && clear))
                return id to clear
            }
            val origPhoto = photo(0)
            val valPhoto = photo(1)
            val subPhoto = photo(2)
            return EditorDraft(decode(snapshot.take(7)).copy(aiProvenance = source(0), photoId = origPhoto.first, clearPhoto = origPhoto.second)).apply {
                values = decode(snapshot.drop(7).take(7)).copy(aiProvenance = source(1), photoId = valPhoto.first, clearPhoto = valPhoto.second)
                require(values.version == original.version)
                require(original.aiProvenance == null && (values.version == null || values.aiProvenance == null))
                if (snapshot.getOrNull(14)?.isNotEmpty() == true) submittedValues =
                    decode(snapshot.drop(14).take(7)).copy(aiProvenance = source(2), photoId = subPhoto.first, clearPhoto = subPhoto.second)
                else require(snapshot.drop(14).take(7).all(String::isEmpty) && source(2) == null && subPhoto.first == null && !subPhoto.second)
                submittedValues?.let { submitted ->
                    require(values == submitted && submitted.category != null && submitted.method != null)
                    require(parseQuantity(submitted.quantity.rawText, submitted.quantity.unit) is QuantityParseResult.Valid)
                    require(submitted.notes.trim().length <= 500)
                    require(submitted.aiProvenance == null ||
                        (submitted.category == submitted.aiProvenance.category && submitted.method != DisposalMethod.REDUCE))
                }
                submissionError = snapshot.getOrNull(21)?.takeIf(String::isNotEmpty)?.let(ApiFailureKind::valueOf)
                    ?: ApiFailureKind.TIMEOUT.takeIf { submittedValues != null }
            }
        }
        val Saver = listSaver<EditorDraft, String>(
            save = { it.snapshot() },
            restore = ::fromSnapshot,
        )
        val NullableSaver = androidx.compose.runtime.saveable.Saver<EditorDraft?, Any>(
            save = { draft -> draft?.let { with(Saver) { save(it) } } },
            restore = { Saver.restore(it) },
        )
    }
}
