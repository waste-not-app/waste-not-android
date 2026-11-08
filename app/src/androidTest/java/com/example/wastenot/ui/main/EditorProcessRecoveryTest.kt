package com.example.wastenot.ui.main

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.MainActivity
import com.example.wastenot.LogEditor
import com.example.wastenot.data.dummy.DummyActivityStore
import com.example.wastenot.data.local.LocalEditorDraftStore
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.repository.StoredEditorDraft
import com.example.wastenot.model.DisposalMethod
import com.example.wastenot.model.QuantityInput
import com.example.wastenot.model.QuantityUnit
import com.example.wastenot.model.WasteCategory
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource

class EditorProcessRecoveryTest {
    private val arguments get() = InstrumentationRegistry.getArguments()
    private val mode get() = arguments.getString("wastenot.draftMode", "create")!!
    private val stage get() = arguments.getString("wastenot.draftStage")
    @get:Rule(order = -1) val navigationData = com.example.wastenot.NavigationFixtureRule()
    @get:Rule(order = 0) val fixture = object : ExternalResource() {
        override fun before() {
            assumeTrue("Requires the host-driven process-death protocol", stage in listOf("prepare", "restore"))
            if (stage == "prepare") {
                resetDraftAccountFixture()
                if (mode == "pending") seedPendingReplay()
            }
        }
    }
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    private fun waitFor(text: String) { compose.waitUntil(30_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
    private fun expectedFile() = File(draftTestContext.getExternalFilesDir(null), "draft-qa/process-$mode-before.json").apply { parentFile!!.mkdirs() }
    private fun recordProcess() {
        File(expectedFile().parentFile, "process-$mode-$stage.pid").writeText(android.os.Process.myPid().toString())
    }

    private fun seedPendingReplay() = runTest {
        val zone = ZoneId.of("Asia/Jakarta")
        val date = LocalDate.now(zone)
        val form = EditorDraft.from(null, date).apply {
            change(EditorField.QUANTITY, values.copy(category = WasteCategory.PLASTIC, method = DisposalMethod.RECYCLE,
                quantity = QuantityInput("0,250", QuantityUnit.KILOGRAM), notes = "  Draf setelah proses mati  "))
        }
        val request = UUID.randomUUID().toString()
        val input = checkNotNull(form.prepareSubmission(date))
        val source = DummyActivityStore(draftTestContext.getSharedPreferences("dummy_logs_dummy-owner", Context.MODE_PRIVATE), zone)
        assertTrue(source.create(input, request) is ApiResult.Success)
        form.submissionFailed(ApiFailureKind.NETWORK)
        form.submissionFailed(ApiFailureKind.RATE_LIMITED)
        assertTrue(LocalEditorDraftStore.create(draftTestContext, "dummy-owner")
            .write(form.stored(LogEditor(null, request), MainTab.HOME)) is ApiResult.Success)
    }

    @Test fun prepareDraftForHostProcessTermination() {
        assumeTrue(stage == "prepare")
        recordProcess()
        if (mode == "pending") {
            waitFor("Draf dipulihkan. Periksa isian sebelum menyimpan.")
            compose.onNodeWithContentDescription("Berat").performScrollTo().assertIsNotEnabled().assertTextContains("0,250")
            val stored = checkNotNull(readDraftFixture())
            assertTrue(stored.restoreForm().outcomeUnknown)
            assertEquals(ApiFailureKind.RATE_LIMITED, stored.restoreForm().submissionError)
            expectedFile().writeText(Json.encodeToString(StoredEditorDraft.serializer(), stored))
            captureDraftFixture("process-pending-before")
            return
        }
        waitFor("650 g")
        if (mode == "edit") {
            compose.onNodeWithText("250 g").performScrollTo().performClick()
            compose.onNodeWithText("Ubah catatan").performScrollTo().performClick()
        } else {
            compose.onNodeWithText("Input manual").performScrollTo().performClick()
            compose.onNodeWithText("Recycle").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Kategori").performScrollTo().performClick()
            compose.onNode(hasText("Plastik") and hasAnyAncestor(isPopup())).performClick()
        }
        compose.onNodeWithContentDescription("Satuan").performScrollTo().performClick()
        compose.onNode(hasText("kg") and hasAnyAncestor(isPopup())).performClick()
        val raw = if (mode == "edit") "0,300" else "0,250"
        compose.onNodeWithContentDescription("Berat").performScrollTo().performTextReplacement(raw)
        compose.onNodeWithContentDescription("Catatan (opsional)").performScrollTo().performTextReplacement("  Draf setelah proses mati  ")
        compose.onNodeWithText("Draf tersimpan di perangkat ini.").performScrollTo()
        compose.waitUntil(10_000) { readDraftFixture()?.fields?.get(12) == "  Draf setelah proses mati  " }
        val stored = checkNotNull(readDraftFixture())
        assertEquals(raw, stored.fields[9]); assertEquals(mode == "edit", stored.logId != null)
        expectedFile().writeText(Json.encodeToString(StoredEditorDraft.serializer(), stored))
        captureDraftFixture("process-$mode-before")
    }

    @Test fun restoreDraftAfterHostProcessTermination() {
        assumeTrue(stage == "restore")
        recordProcess()
        val expected = Json.decodeFromString(StoredEditorDraft.serializer(), expectedFile().readText())
        waitFor("Draf dipulihkan. Periksa isian sebelum menyimpan.")
        compose.onNodeWithContentDescription("Berat").performScrollTo().assertTextContains(expected.fields[9])
        compose.onNodeWithContentDescription("Satuan").assertTextContains("kg")
        compose.onNodeWithContentDescription("Catatan (opsional)").performScrollTo().assertTextContains(expected.fields[12])
        val restored = checkNotNull(readDraftFixture())
        assertEquals(expected.requestId, restored.requestId)
        assertEquals(expected.logId, restored.logId)
        assertEquals(expected.fields, restored.fields)
        runTest {
            val source = DummyActivityStore(draftTestContext.getSharedPreferences("dummy_logs_dummy-owner", Context.MODE_PRIVATE), ZoneId.of("Asia/Jakarta"))
            val records = (source.load() as ApiResult.Success).value
            assertEquals(if (mode == "pending") 6 else 5, records.size)
            if (expected.logId != null) assertEquals(1, records.single { it.id == expected.logId }.version)
        }
        captureDraftFixture("process-$mode-restored")
        if (mode == "pending") {
            assertTrue(restored.restoreForm().outcomeUnknown)
            compose.onNodeWithContentDescription("Berat").assertIsNotEnabled()
            compose.onNodeWithText("Coba lagi").performScrollTo().performClick()
            waitFor("Catatanmu sudah tersimpan. Tidak ada XP tambahan.")
            assertNull(readDraftFixture())
            runTest {
                val source = DummyActivityStore(draftTestContext.getSharedPreferences("dummy_logs_dummy-owner", Context.MODE_PRIVATE), ZoneId.of("Asia/Jakarta"))
                assertEquals(6, (source.load() as ApiResult.Success).value.size)
            }
            captureDraftFixture("process-pending-replayed")
        }
    }
}
