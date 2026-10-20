package com.example.wastenot.ui.main

import androidx.compose.runtime.saveable.SaverScope
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.model.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class EditorDraftTest {
    private val today = LocalDate.of(2026, 9, 21)

    @Test fun blankDraftDoesNotInventMethodOrCategory() {
        val draft = EditorDraft.from(null, today)
        assertFalse(draft.dirty)
        assertNull(draft.validate(today))
        assertEquals(EditorField.METHOD, draft.errors.keys.first())
        assertTrue(EditorField.CATEGORY in draft.errors)
        assertTrue(EditorField.QUANTITY in draft.errors)
    }

    @Test fun createAndEditUseTheSameExactQuantityParser() {
        val create = EditorDraft.from(null, today)
        create.change(EditorField.QUANTITY, create.values.copy(category = WasteCategory.PLASTIC, method = DisposalMethod.RECYCLE,
            quantity = QuantityInput("0,25", QuantityUnit.KILOGRAM)))
        assertEquals(Quantity.Weight.of(250), create.validate(today)!!.quantity)
        val edit = EditorDraft.from(log(), today)
        edit.change(EditorField.QUANTITY, edit.values.copy(quantity = QuantityInput("0.25", QuantityUnit.KILOGRAM)))
        assertEquals(create.validate(today)!!.quantity, edit.validate(today)!!.quantity)
        edit.change(EditorField.QUANTITY, edit.values.copy(quantity = edit.values.quantity.changeUnit(QuantityUnit.ITEM)))
        assertEquals("", edit.values.quantity.rawText)
        assertNull(edit.validate(today))
        edit.change(EditorField.QUANTITY, edit.values.copy(quantity = QuantityInput("2", QuantityUnit.ITEM)))
        assertEquals(Quantity.Count.of(2), edit.validate(today)!!.quantity)
    }

    @Test fun blurValidatesOnlyTheVisitedFieldAndDoesNotRequestFocus() {
        val draft = EditorDraft.from(null, today)
        assertTrue(draft.errors.isEmpty())
        draft.validateField(EditorField.QUANTITY, today)
        assertEquals(setOf(EditorField.QUANTITY), draft.errors.keys)
        assertEquals(0, draft.focusRequest)
        draft.change(EditorField.QUANTITY, draft.values.copy(quantity = QuantityInput("250")))
        draft.validateField(EditorField.QUANTITY, today)
        assertTrue(draft.errors.isEmpty())
        draft.validateField(EditorField.NOTES, today)
        assertTrue(draft.errors.isEmpty())
        assertNull(draft.validate(today))
        assertEquals(EditorField.METHOD, draft.errors.keys.first())
        assertEquals(1, draft.focusRequest)
    }

    @Test fun excessPrecisionAndFractionalItemsAreRejectedWithoutRounding() {
        val draft = EditorDraft.from(log(), today)
        listOf(QuantityInput("0.0001", QuantityUnit.KILOGRAM), QuantityInput("2,5", QuantityUnit.ITEM)).forEach {
            draft.change(EditorField.QUANTITY, draft.values.copy(quantity = it))
            assertNull(draft.validate(today))
            assertTrue(EditorField.QUANTITY in draft.errors)
        }
    }

    @Test fun savedDraftRetainsOriginalVersionAndDirtyFieldsAcrossRecreation() {
        val draft = EditorDraft.from(log(), today)
        draft.change(EditorField.NOTES, draft.values.copy(notes = "draf percobaan"))
        val scope = object : SaverScope { override fun canBeSaved(value: Any) = true }
        val saved = with(EditorDraft.Saver) { scope.save(draft) }
        val restored = EditorDraft.Saver.restore(checkNotNull(saved))!!
        assertEquals(draft.values, restored.values)
        assertEquals(draft.original, restored.original)
        assertTrue(restored.dirty)
    }

    @Test fun oldDateMayStayButANewOutOfWindowDateFails() {
        val draft = EditorDraft.from(log().copy(activityDate = today.minusDays(90)), today)
        draft.change(EditorField.NOTES, draft.values.copy(notes = "koreksi"))
        assertNotNull(draft.validate(today))
        draft.change(EditorField.DATE, draft.values.copy(date = today.plusDays(1)))
        assertNull(draft.validate(today))
        assertTrue(EditorField.DATE in draft.errors)
    }

    @Test fun notesAreTrimmedBeforeTheLimitAndPayloadForCreateAndEdit() {
        val valid = EditorDraft.from(log(), today).values
        val note = "a".repeat(497) + "  b"
        listOf(EditorDraft.from(null, today), EditorDraft.from(log(), today)).forEach { draft ->
            val raw = "\t\n  $note  \n"
            draft.change(EditorField.NOTES, valid.copy(notes = raw, version = draft.original.version))
            assertEquals(note, draft.validate(today)!!.notes)
            assertEquals(raw, draft.values.notes)
            draft.change(EditorField.NOTES, draft.values.copy(notes = " \t\n "))
            assertEquals("", draft.validate(today)!!.notes)
            draft.change(EditorField.NOTES, draft.values.copy(notes = " $note! "))
            assertNull(draft.validate(today))
            assertTrue(EditorField.NOTES in draft.errors)
        }
    }

    @Test fun uncertainSubmissionFreezesRawValuesAndRetriesTheCanonicalSnapshotEvenAfterDateWindowMoves() {
        val draft = EditorDraft.from(null, today)
        draft.change(EditorField.QUANTITY, draft.values.copy(category = WasteCategory.PLASTIC, method = DisposalMethod.RECYCLE,
            quantity = QuantityInput("0,25", QuantityUnit.KILOGRAM), notes = "  awal  "))
        val submitted = draft.prepareSubmission(today)!!
        assertEquals(Quantity.Weight.of(250), submitted.quantity)
        assertEquals("awal", submitted.notes)
        assertTrue(draft.locked)
        val raw = draft.values
        draft.change(EditorField.NOTES, raw.copy(notes = "koreksi"))
        assertEquals(raw, draft.values)
        listOf(ApiFailureKind.TIMEOUT, ApiFailureKind.NETWORK, ApiFailureKind.SERVER,
            ApiFailureKind.HTTP, ApiFailureKind.MALFORMED_RESPONSE).forEach { error ->
            draft.submissionFailed(error)
            assertTrue(draft.locked)
            assertEquals(submitted, draft.prepareSubmission(today.plusDays(31)))
            assertEquals(raw, draft.values)
            assertTrue(draft.errors.isEmpty())
        }
    }

    @Test fun restoringAnUncertainSubmissionKeepsItsSnapshotAndError() {
        val draft = EditorDraft.from(log(), today)
        draft.change(EditorField.NOTES, draft.values.copy(notes = "  awal  "))
        val input = draft.prepareSubmission(today)!!
        draft.submissionFailed(ApiFailureKind.TIMEOUT)
        val scope = object : SaverScope { override fun canBeSaved(value: Any) = true }
        val saved = with(EditorDraft.Saver) { scope.save(draft) }!!
        val restored = EditorDraft.Saver.restore(saved)!!
        assertEquals(draft.values, restored.values)
        assertEquals(draft.submittedValues, restored.submittedValues)
        assertEquals(ApiFailureKind.TIMEOUT, restored.submissionError)
        assertEquals(input, restored.prepareSubmission(today.plusDays(31)))
        val oldFormat = EditorDraft.Saver.restore((saved as List<*>).take(14))!!
        assertFalse(oldFormat.locked)
        assertNull(oldFormat.submissionError)
    }

    @Test fun restoringAnInterruptedActiveSubmissionRequiresAnExplicitRetry() {
        val draft = EditorDraft.from(log(), today)
        val input = draft.prepareSubmission(today)!!
        val scope = object : SaverScope { override fun canBeSaved(value: Any) = true }
        val saved = with(EditorDraft.Saver) { scope.save(draft) }!!
        val restored = EditorDraft.Saver.restore(saved)!!
        assertTrue(restored.locked)
        assertEquals(ApiFailureKind.TIMEOUT, restored.submissionError)
        assertEquals(input, restored.prepareSubmission(today))
    }

    @Test fun definiteRejectionAllowsCorrectionsButConflictAndDeletedRequestsNeverRetry() {
        listOf(ApiFailureKind.VALIDATION, ApiFailureKind.NOT_CONFIGURED, ApiFailureKind.STORAGE,
            ApiFailureKind.RATE_LIMITED).forEach { error ->
            val draft = EditorDraft.from(log(), today)
            draft.prepareSubmission(today)
            draft.submissionFailed(error)
            assertFalse(draft.locked)
            draft.change(EditorField.NOTES, draft.values.copy(notes = "koreksi"))
            assertNull(draft.submissionError)
            assertEquals("koreksi", draft.prepareSubmission(today)!!.notes)
        }
        listOf(ApiFailureKind.IDEMPOTENCY_CONFLICT, ApiFailureKind.DELETED).forEach { error ->
            val draft = EditorDraft.from(log(), today)
            draft.prepareSubmission(today)
            draft.submissionFailed(error)
            assertTrue(draft.locked)
            assertTrue(draft.retryBlocked)
            assertNull(draft.prepareSubmission(today))
        }
    }

    private fun log() = WasteLog("00000000-0000-4000-8000-000000000001", WasteCategory.PLASTIC, DisposalMethod.RECYCLE,
        Quantity.Weight.of(250), today, ZoneId.of("Asia/Jakarta"), "", 1, Instant.EPOCH, Instant.EPOCH)
}
