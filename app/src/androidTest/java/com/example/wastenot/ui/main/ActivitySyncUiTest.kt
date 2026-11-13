package com.example.wastenot.ui.main

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.data.repository.SyncIssue
import com.example.wastenot.model.*
import com.example.wastenot.theme.WasteNotTheme
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ActivitySyncUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val log = WasteLog("00000000-0000-4000-8000-000000000001", WasteCategory.PLASTIC,
        DisposalMethod.RECYCLE, Quantity.Count.of(2), LocalDate.of(2026, 11, 13), ZoneId.of("Asia/Jakarta"),
        "Botol sudah dipilah", 2, Instant.parse("2026-11-13T01:00:00Z"), Instant.parse("2026-11-13T01:00:00Z"), ActivitySyncState.PENDING)

    @Test fun localSaveConfirmationDoesNotAwardServerXp() {
        compose.setContent { WasteNotTheme { SaveConfirmationScreen(log, false, PaddingValues(), {}, {}) } }
        awaitContent()
        compose.onNodeWithText("Tersimpan di HP ini.", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("+10 XP", substring = true).assertDoesNotExist()
        capture("local-save")
    }

    @Test fun conflictShowsBothVersionsAndRequiresConfirmationBeforeSendingLocalFields() {
        var decision: Pair<String, Boolean>? = null
        val issue = SyncIssue(log.id, log.copy(syncState = ActivitySyncState.CONFLICT),
            log.copy(notes = "Catatan dari server", version = 3, syncState = ActivitySyncState.SYNCED), false, ApiFailureKind.VERSION_CONFLICT)
        compose.setContent { WasteNotTheme { ActivitySyncSheet(listOf(issue), false, false, {},
            { id, keep -> decision = id to keep }, {}) } }
        awaitContent()
        compose.onNodeWithText("Botol sudah dipilah").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Catatan dari server").performScrollTo().assertIsDisplayed()
        capture("conflict-review", dialog = true)
        compose.onNodeWithText("Kirim isian lokal setelah ditinjau").performScrollTo().performClick()
        compose.runOnIdle { assertNull(decision) }
        compose.onNode(hasText("Konfirmasi") and hasAnyAncestor(isDialog())).performClick()
        compose.runOnIdle { assertEquals(log.id to true, decision) }
    }

    @Test fun synchronizedQueuedSaveShowsServerConfirmationWithoutDenyingItsXp() {
        compose.setContent { WasteNotTheme { SaveConfirmationScreen(log.copy(syncState = ActivitySyncState.SYNCED),
            false, PaddingValues(), {}, {}, wasQueued = true) } }
        awaitContent()
        compose.onNodeWithText("Catatan sudah tersinkron.", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Tidak ada XP tambahan.", substring = true).assertDoesNotExist()
        capture("confirmed-save")
    }

    @Test fun expiredAiConversionExplainsManualInputWithoutClaimingAServerReview() {
        val issue = SyncIssue(log.id, log.copy(syncState = ActivitySyncState.FAILED), null, false, ApiFailureKind.AI_EXPIRED)
        compose.setContent { WasteNotTheme { ActivitySyncSheet(listOf(issue), false, false, {}, { _, _ -> }, {}) } }
        awaitContent()
        compose.onNodeWithText("Simpan sebagai catatan manual").performScrollTo().performClick()
        compose.onNode(hasText("Simpan isian ini sebagai catatan manual?", substring = true) and hasAnyAncestor(isDialog())).assertIsDisplayed()
    }

    private fun awaitContent() {
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    private fun capture(name: String, dialog: Boolean = false) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val image = (if (dialog) compose.onNode(isDialog()) else compose.onRoot()).captureToImage().asAndroidBitmap()
        val file = File(instrumentation.targetContext.cacheDir, "qa/offline-sync/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }
}
