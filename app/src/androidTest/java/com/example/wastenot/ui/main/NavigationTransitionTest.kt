package com.example.wastenot.ui.main

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.MainActivity
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource

class NavigationTransitionTest {
    @get:Rule(order = 0) val fixtures = object : ExternalResource() {
        override fun before() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            listOf("dummy_session", "dummy_logs_dummy-owner", "appearance").forEach {
                check(context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit())
            }
        }
    }
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun confirmedCreateDoesNotFlashALoadErrorWhileTheEditorExits() {
        create(false)
    }

    @Test fun darkConfirmedCreateKeepsItsFormDuringExit() = create(true)

    private fun create(dark: Boolean) {
        openEditor(dark)
        compose.onNodeWithText("Recycle").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Kategori").performScrollTo().performClick()
        compose.onNode(hasText("Plastik") and hasAnyAncestor(isPopup())).performClick()
        compose.onNodeWithContentDescription("Berat").performScrollTo().performTextReplacement("250")
        compose.onNodeWithText("Simpan catatan").performScrollTo()
        traceExit(if (dark) "create-dark" else "create", "+10 XP untuk aktivitas baru ini.") {
            compose.onNodeWithText("Simpan catatan").performClick()
        }
    }

    @Test fun confirmedEditKeepsItsFormUntilTheUpdatedReceiptAppears() {
        home()
        compose.onNodeWithText("250 g").performScrollTo().performClick()
        compose.waitUntil(30_000) {
            compose.onAllNodes(hasText("Ubah catatan") and hasClickAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasText("Ubah catatan") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithContentDescription("Catatan (opsional)").performScrollTo().performTextReplacement("Perubahan saat audit")
        compose.onNodeWithText("Simpan perubahan").performScrollTo()
        traceExit("edit", "Catatanmu sudah tersimpan. Tidak ada XP tambahan.") {
            compose.onNodeWithText("Simpan perubahan").performClick()
        }
    }

    @Test fun cleanBackKeepsItsFormUntilHomeReturns() {
        openEditor()
        traceExit("clean-back", "650 g") { compose.onNodeWithContentDescription("Kembali").performClick() }
    }

    @Test fun dirtyEditorHeaderBackUsesTheCurrentDraft() {
        openEditor()
        compose.onNodeWithContentDescription("Catatan (opsional)").performScrollTo().performTextReplacement("Draf belum disimpan")
        compose.onNodeWithContentDescription("Kembali").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.onNodeWithText("Buang perubahan?").assertIsDisplayed()
    }

    @Test fun discardKeepsItsFormDuringExitAndTheNextDraftStartsEmpty() {
        openEditor()
        compose.onNodeWithContentDescription("Catatan (opsional)").performScrollTo().performTextReplacement("Draf belum disimpan")
        compose.onNodeWithContentDescription("Kembali").performClick()
        compose.onNodeWithText("Lanjut mengisi").performClick()
        compose.onNodeWithContentDescription("Catatan (opsional)").assertTextContains("Draf belum disimpan")
        compose.onNodeWithContentDescription("Kembali").performClick()
        traceExit("discard-back", "650 g") {
            compose.onNode(hasText("Buang perubahan") and hasAnyAncestor(isDialog())).performClick()
        }
        openEditor()
        assertEquals("", compose.onNodeWithContentDescription("Catatan (opsional)").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        compose.onNodeWithText("Recycle").assertIsNotSelected()
        compose.onNodeWithText("Pilih kategori").assertExists()
    }

    private fun home() {
        compose.waitUntil(30_000) { compose.onAllNodesWithText("650 g").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun openEditor(dark: Boolean = false) {
        home()
        if (dark) {
            val tab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
            compose.onNode(tab and hasText("Profil")).performClick()
            compose.onNodeWithText("Tampilan:", substring = true).performScrollTo().performClick()
            compose.onNodeWithText("Gelap").performClick()
            compose.onNodeWithText("Tutup").performClick()
            compose.onNode(tab and hasText("Beranda")).performClick()
        }
        compose.onNodeWithContentDescription("Catat atau scan sampah").performClick()
        compose.onNode(hasText("Input manual") and hasAnyAncestor(hasTestTag("capture-sheet"))).performScrollTo().performClick()
    }

    private fun traceExit(phase: String, target: String, action: () -> Unit) {
        compose.onNodeWithText("Catatan belum bisa dimuat.").assertDoesNotExist()
        val flashes = mutableListOf<Int>()
        val captures = mutableListOf<String>()
        var confirmationAt: Int? = null
        var lastFrame = -1
        val clockStart = compose.mainClock.currentTime
        compose.mainClock.autoAdvance = false
        try {
            action()
            for (frame in 0 until 120) {
                lastFrame = frame
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
                if (compose.onAllNodesWithText("Catatan belum bisa dimuat.").fetchSemanticsNodes().isNotEmpty()) {
                    flashes.add(frame)
                    capture("$phase-exit-error-$frame")
                }
                if (confirmationAt == null && compose.onAllNodesWithText(target).fetchSemanticsNodes().isNotEmpty()) {
                    confirmationAt = frame
                    captures.add(capture("$phase-frame-$frame"))
                }
                if (confirmationAt != null && frame - confirmationAt in listOf(3, 7, 12, 16)) {
                    captures.add(capture("$phase-frame-$frame"))
                }
                if (confirmationAt != null && frame - confirmationAt >= 16) break
            }
            assertTrue("$phase must reach its destination: $target", confirmationAt != null)
            assertEquals("Successful navigation must never render a load-error fallback; frames=$flashes", 0, flashes.size)
        } finally {
            val report = JSONObject().put("phase", phase).put("frames_observed", lastFrame + 1)
                .put("target_frame", confirmationAt).put("error_frames", JSONArray(flashes))
                .put("clock_elapsed_ms", compose.mainClock.currentTime - clockStart).put("captures", JSONArray(captures))
            compose.mainClock.autoAdvance = true
            val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "qa/iteration-2/transitions/$phase-result.json")
            file.parentFile!!.mkdirs()
            file.writeText(report.toString(2))
        }
        compose.waitForIdle()
    }

    private fun capture(name: String): String {
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "qa/iteration-2/transitions/$name.png")
        file.parentFile!!.mkdirs()
        val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
        return file.name
    }
}
