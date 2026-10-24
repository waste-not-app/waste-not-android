package com.example.wastenot.ui.main

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.MainActivity
import com.example.wastenot.data.dummy.DummyActivityStore
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.repository.StoredEditorDraft
import java.io.File
import java.time.ZoneId
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
    @get:Rule(order = 0) val fixture = object : ExternalResource() {
        override fun before() {
            assumeTrue("Requires the host-driven process-death protocol", stage in listOf("prepare", "restore"))
            if (stage == "prepare") resetDraftAccountFixture()
        }
    }
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    private fun waitFor(text: String) { compose.waitUntil(30_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
    private fun expectedFile() = File(draftTestContext.getExternalFilesDir(null), "draft-qa/process-$mode-before.json").apply { parentFile!!.mkdirs() }
    private fun recordProcess() {
        File(expectedFile().parentFile, "process-$mode-$stage.pid").writeText(android.os.Process.myPid().toString())
    }

    @Test fun prepareDraftForHostProcessTermination() {
        assumeTrue(stage == "prepare")
        recordProcess()
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
            assertEquals(5, records.size)
            if (expected.logId != null) assertEquals(1, records.single { it.id == expected.logId }.version)
        }
        captureDraftFixture("process-$mode-restored")
    }
}
