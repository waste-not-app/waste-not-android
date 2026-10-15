package com.example.wastenot.ui.main

import androidx.compose.runtime.saveable.SaverScope
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

    private fun log() = WasteLog("00000000-0000-4000-8000-000000000001", WasteCategory.PLASTIC, DisposalMethod.RECYCLE,
        Quantity.Weight.of(250), today, ZoneId.of("Asia/Jakarta"), "", 1, Instant.EPOCH, Instant.EPOCH)
}
