package com.example.wastenot.ui.main

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.data.repository.ActivityInput
import com.example.wastenot.model.DisposalMethod
import com.example.wastenot.model.Quantity
import com.example.wastenot.model.QuantityInput
import com.example.wastenot.model.QuantityUnit
import com.example.wastenot.model.WasteCategory
import com.example.wastenot.theme.WasteNotTheme
import com.example.wastenot.theme.LocalWasteNotGradients
import java.io.File
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class EditorSubmissionTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.of(2026, 10, 20)

    private fun draft() = EditorDraft.from(null, today).apply {
        change(EditorField.QUANTITY, values.copy(category = WasteCategory.PLASTIC, method = DisposalMethod.RECYCLE,
            quantity = QuantityInput("0,25", QuantityUnit.KILOGRAM), notes = "  Isian awal  "))
    }

    @Test fun uncertainLightSubmissionRestoresAndRetriesTheSameInput() = uncertain(false)
    @Test fun uncertainDarkSubmissionRestoresAndRetriesTheSameInput() = uncertain(true)

    private fun uncertain(dark: Boolean) {
        val restoration = StateRestorationTester(compose)
        val busy = mutableStateOf(false)
        val received = mutableListOf<ActivityInput>()
        lateinit var current: EditorDraft
        restoration.setContent {
            current = rememberSaveable(saver = EditorDraft.Saver) { draft() }
            EditorHost(dark) {
                LogEditorScreen(current, today, "Asia/Jakarta", true, busy.value, PaddingValues(),
                    { received.add(it); busy.value = true }, { error("Unknown result must offer an explicit retry") }, Modifier.fillMaxSize())
            }
        }
        compose.onNodeWithText("Simpan catatan").performScrollTo().performClick()
        compose.onNodeWithText("Menyimpan…").assertIsNotEnabled()
        assertFrozen()
        compose.runOnIdle {
            assertEquals(1, received.size)
            assertEquals(Quantity.Weight.of(250), received.single().quantity)
            assertEquals("Isian awal", received.single().notes)
            current.submissionFailed(ApiFailureKind.TIMEOUT)
            busy.value = false
        }
        compose.onNodeWithText("Hasil penyimpanan belum diketahui.", substring = true).performScrollTo().assertIsDisplayed()
        assertActionInViewport("Coba lagi")
        capture("submission-unknown-${if (dark) "dark" else "light"}")
        restoration.emulateSavedInstanceStateRestore()
        assertFrozen()
        compose.onNodeWithContentDescription("Berat").assertTextContains("0,25")
        compose.onNodeWithContentDescription("Catatan (opsional)").assertTextContains("  Isian awal  ")
        compose.runOnIdle { assertEquals(1, received.size) }
        compose.onNodeWithText("Coba lagi").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(2, received.size)
            assertEquals(received[0], received[1])
        }
    }

    @Test fun deletedRequestOffersHistoryWithoutRetryingOrReplacingItsInput() = terminal(ApiFailureKind.DELETED, false)
    @Test fun conflictingRequestOffersHistoryWithoutRetryingOrReplacingItsInput() = terminal(ApiFailureKind.IDEMPOTENCY_CONFLICT, true)

    private fun terminal(error: ApiFailureKind, dark: Boolean) {
        val draft = draft()
        draft.prepareSubmission(today)
        draft.submissionFailed(error)
        var history = 0
        compose.setContent { EditorHost(dark) {
            LogEditorScreen(draft, today, "Asia/Jakarta", true, false, PaddingValues(),
                { fail("A rejected request must not be retried") }, { history++ }, Modifier.fillMaxSize())
        } }
        assertFrozen()
        compose.onNodeWithText(if (error == ApiFailureKind.DELETED) "Catatan dari permintaan ini sudah dihapus." else "Permintaan ini sudah dipakai untuk isian lain.",
            substring = true).performScrollTo().assertIsDisplayed()
        assertActionInViewport("Lihat riwayat")
        capture("submission-${if (error == ApiFailureKind.DELETED) "deleted-light" else "conflict-dark"}")
        compose.onNodeWithText("Coba lagi").assertDoesNotExist()
        compose.onNodeWithText("Lihat riwayat").performClick()
        compose.runOnIdle { assertEquals(1, history) }
    }

    @Test fun rejectedSubmissionKeepsAnEditableDraft() {
        val draft = draft()
        draft.prepareSubmission(today)
        draft.submissionFailed(ApiFailureKind.VALIDATION)
        compose.setContent { EditorHost(false) {
            LogEditorScreen(draft, today, "Asia/Jakarta", true, false, PaddingValues(), {}, {}, Modifier.fillMaxSize())
        } }
        compose.onNodeWithContentDescription("Catatan (opsional)").performScrollTo().assertIsEnabled().performTextReplacement("Koreksi")
        compose.onNodeWithText("Simpan catatan").performScrollTo().assertIsEnabled()
        compose.runOnIdle { assertEquals("Koreksi", draft.prepareSubmission(today)!!.notes) }
    }

    private fun assertFrozen() {
        listOf("Reduce", "Reuse", "Recycle", "Trash").forEach { compose.onNodeWithText(it).assertIsNotEnabled() }
        listOf("Kategori", "Berat", "Satuan", "Catatan (opsional)").forEach {
            compose.onNodeWithContentDescription(it).assertIsNotEnabled()
        }
    }

    @Composable
    private fun EditorHost(dark: Boolean, content: @Composable () -> Unit) {
        WasteNotTheme(darkTheme = dark) {
            Surface(Modifier.fillMaxSize(), color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface) {
                Box(Modifier.background(LocalWasteNotGradients.current.canvas).safeDrawingPadding().testTag("submission-viewport")) { content() }
            }
        }
    }

    private fun assertActionInViewport(label: String) {
        val action = compose.onNodeWithText(label).performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        val bounds = action.fetchSemanticsNode().boundsInWindow
        val viewport = compose.onNodeWithTag("submission-viewport").fetchSemanticsNode().boundsInWindow
        assertTrue("CTA must stay inside the inset viewport", bounds.top >= viewport.top && bounds.bottom <= viewport.bottom)
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.waitForIdle(300, 5_000)
        val file = File(instrumentation.targetContext.cacheDir, "qa/iteration-2/$name.png")
        file.parentFile!!.mkdirs()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
}
