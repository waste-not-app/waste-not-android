package com.example.wastenot.data.local

import com.example.wastenot.LogEditor
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.model.*
import com.example.wastenot.ui.main.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class DraftDiskCodecTest {
    private val date = LocalDate.of(2026, 10, 24)
    private fun draft() = EditorDraft.from(null, date).apply {
        change(EditorField.QUANTITY, values.copy(category = WasteCategory.PLASTIC, method = DisposalMethod.RECYCLE,
            quantity = QuantityInput("0,250", QuantityUnit.KILOGRAM), notes = "  Isian mentah\nberikutnya  "))
    }
    private val key = LogEditor(null, "7b20f6b4-278c-492c-bddf-8ef2daaf28e5")

    @Test fun aiReferenceIsAccountBoundAndSurvivesDiskWithoutMakingAnUnknownWriteEditable() {
        val aiKey = key.copy(scanCategory = "PLASTIC", scanMethod = "RECYCLE",
            scanRecommendationId = "00000000-0000-4000-8000-000000000002")
        val form = draft().apply { applyScan(aiKey) }
        val submitted = form.prepareSubmission(date)
        form.submissionFailed(ApiFailureKind.TIMEOUT)
        val raw = DraftDiskCodec.encode("account-A", form.stored(aiKey, MainTab.HOME))
        val stored = DraftDiskCodec.decode("account-A", raw)
        assertEquals(aiKey, stored.editorKey())
        val restored = stored.restoreForm()
        assertEquals(submitted, restored.prepareSubmission(date.plusDays(60)))
        assertTrue(restored.locked)
        assertNotNull(restored.values.aiProvenance)
        assertFalse(raw.contains("capture.jpg"))
        assertThrows(IllegalArgumentException::class.java) { DraftDiskCodec.decode("account-B", raw) }
    }

    @Test fun unfinishedFieldsKeepTheirExactRawInputAcrossDiskEncoding() {
        val original = draft().apply { change(EditorField.QUANTITY, values.copy(quantity = QuantityInput("0,", QuantityUnit.KILOGRAM))) }
        val restored = DraftDiskCodec.decode("account-A", DraftDiskCodec.encode("account-A", original.stored(key, MainTab.HOME))).restoreForm()
        assertEquals(original.original, restored.original)
        assertEquals(original.values, restored.values)
        assertFalse(restored.locked)
        assertNull(restored.prepareSubmission(date))
    }

    @Test fun pendingCreateKeepsTheOriginalRequestAndPayloadDespiteANewDateWindow() {
        val form = draft(); val submitted = form.prepareSubmission(date)
        form.submissionFailed(ApiFailureKind.NETWORK)
        val stored = DraftDiskCodec.decode("account-A", DraftDiskCodec.encode("account-A", form.stored(key, MainTab.GUIDE).copy(paused = true)))
        val restored = stored.restoreForm()
        restored.change(EditorField.NOTES, restored.values.copy(notes = "Do not adopt this edit"))
        assertEquals(key, stored.editorKey())
        assertEquals(submitted, restored.prepareSubmission(date.plusDays(60)))
        assertEquals(form.values, restored.values)
        assertEquals(ApiFailureKind.NETWORK, restored.submissionError)
    }

    @Test fun aCrashAfterCheckpointCannotTurnTheSubmissionIntoAnEditableNewPayload() {
        val form = draft(); val submitted = form.prepareSubmission(date)
        val restored = DraftDiskCodec.decode("account-A", DraftDiskCodec.encode("account-A", form.stored(key, MainTab.HOME))).restoreForm()
        assertEquals(ApiFailureKind.TIMEOUT, restored.submissionError)
        assertTrue(restored.locked)
        assertEquals(submitted, restored.prepareSubmission(date.plusDays(90)))
    }

    @Test fun anotherAccountAndUnknownSchemasCannotRestoreTheDraft() {
        val raw = DraftDiskCodec.encode("account-A", draft().stored(key, MainTab.HOME))
        assertThrows(IllegalArgumentException::class.java) { DraftDiskCodec.decode("account-B", raw) }
        assertThrows(IllegalArgumentException::class.java) { DraftDiskCodec.decode("account-A", raw.replace("\"schema\":1", "\"schema\":2")) }
        assertThrows(kotlinx.serialization.SerializationException::class.java) { DraftDiskCodec.decode("account-A", raw.dropLast(3)) }
    }

    @Test fun aCorruptSubmittedSnapshotAndInvalidVersionsAreRejected() {
        val fields = draft().apply { prepareSubmission(date) }.snapshot()
        assertThrows(IllegalArgumentException::class.java) { EditorDraft.fromSnapshot(fields.toMutableList().apply { this[16] = "bad quantity" }) }
        assertThrows(IllegalArgumentException::class.java) { EditorDraft.fromSnapshot(fields.toMutableList().apply { this[6] = "0" }) }
        assertThrows(IllegalArgumentException::class.java) { EditorDraft.fromSnapshot(fields.toMutableList().apply { this[14] = "" }) }
        assertThrows(IllegalArgumentException::class.java) { EditorDraft.fromSnapshot(fields.dropLast(2)) }
    }

    @Test fun activeAndPausedFormsHaveDifferentRestorationContracts() {
        val unfinished = draft().stored(key, MainTab.HOME)
        assertNotNull(unfinished.restoreForm())
        assertThrows(IllegalArgumentException::class.java) { unfinished.copy(paused = true).restoreForm() }
        assertThrows(IllegalArgumentException::class.java) { unfinished.copy(logId = "other-record").restoreForm() }
    }
}
