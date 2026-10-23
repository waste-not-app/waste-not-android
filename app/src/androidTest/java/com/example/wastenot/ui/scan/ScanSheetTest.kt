package com.example.wastenot.ui.scan

import android.graphics.Bitmap
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.data.repository.DummyScanRepository
import com.example.wastenot.model.*
import com.example.wastenot.theme.WasteNotTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ScanSheetTest {
    @get:Rule val compose = createComposeRule()
    private val candidate = ScanObject("bottle", "Botol", .94f, ScanBounds(.2f, .1f, .8f, .9f))
    private val objects = ScanState(session = "native-test", phase = ScanPhase.OBJECT, objects = listOf(candidate), selectedId = candidate.id)
    private val recommendation = ScanRecommendation(DisposalMethod.RECYCLE, "Periksa penerimaan fasilitas setempat.", "Botol plastik", "kit-v1.3",
        listOf("Periksa material kemasan."), listOf("Pisahkan dari sampah lain."))

    @Test fun everyPhaseKeepsManualVisibleAndThemesRenderWithoutSimulationControls() {
        var state by mutableStateOf(ScanState())
        var dark by mutableStateOf(false)
        var manual = 0
        compose.setContent { WasteNotTheme(dark) {
            Sheet(state, onManual = { manual++ })
        } }
        val states = listOf(
            ScanState(), objects, objects.copy(phase = ScanPhase.CONTEXT, category = WasteCategory.PLASTIC, objectConfirmed = true),
            objects.copy(phase = ScanPhase.RECOMMENDATION, category = WasteCategory.PLASTIC, recommendation = recommendation, method = DisposalMethod.RECYCLE),
            objects.copy(phase = ScanPhase.RECOMMENDATION, recommendation = recommendation.copy(method = null), method = null),
            ScanState(phase = ScanPhase.FAILURE, problem = ScanProblem.CAMERA),
            ScanState(phase = ScanPhase.DETECTING), ScanState(phase = ScanPhase.RECOMMENDING),
        )
        for (theme in listOf(false, true)) {
            compose.runOnIdle { dark = theme }
            states.forEachIndexed { index, next ->
                compose.runOnIdle { state = next }
                compose.onNodeWithText("Input manual").assertIsDisplayed().performClick()
                compose.onNodeWithText("Gunakan contoh simulasi").assertDoesNotExist()
                compose.onNodeWithText("Pengaturan simulasi").assertDoesNotExist()
                val markers = (0..3).map { compose.onNodeWithTag("scan-step-$it", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot }
                markers.forEach { marker ->
                    assertEquals("Step markers must remain circles at the user's text scale", marker.width, marker.height, 1f)
                    assertEquals("Completed and current steps must keep equal-sized markers", markers.first().height, marker.height, 1f)
                }
                capture("scan-${if (theme) "dark" else "light"}-$index")
            }
        }
        assertEquals(states.size * 2, manual)
    }

    @Test fun objectNeedsMaterialBeforeConfirmationAndOnlyExplicitChoiceUpdatesIt() {
        var state by mutableStateOf(objects)
        var confirmed = 0
        compose.setContent { WasteNotTheme {
            Sheet(state, onCategory = { state = state.copy(category = it) }, onConfirm = { confirmed++ })
        } }
        compose.onNodeWithText("Konfirmasi objek").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Pilih material").performScrollTo().performClick()
        compose.onNodeWithText("Plastik").performClick()
        compose.onNodeWithText("Konfirmasi objek").performScrollTo().assertIsEnabled().performClick()
        assertEquals(WasteCategory.PLASTIC, state.category); assertEquals(1, confirmed)
    }

    @Test fun contextFieldsAreIndependentAndUnknownDoesNotImplyNo() {
        var state by mutableStateOf(objects.copy(phase = ScanPhase.CONTEXT, category = WasteCategory.PLASTIC, objectConfirmed = true))
        compose.setContent { WasteNotTheme {
            Sheet(state, onContext = { state = state.copy(context = it) })
        } }
        compose.onAllNodesWithText("Belum tahu")[0].performScrollTo().performClick()
        compose.onNodeWithText("Bersih").performClick()
        assertEquals(Cleanliness.CLEAN, state.context.cleanliness)
        assertEquals(Integrity.UNKNOWN, state.context.integrity)
        assertEquals(Reusability.UNKNOWN, state.context.reusable); assertNull(state.context.reuseIntention)
        compose.onNodeWithText("Belum memutuskan").performScrollTo().performClick()
        compose.onNodeWithText("Akan dipakai ulang").performClick()
        assertEquals(true, state.context.reuseIntention)
        assertEquals(Integrity.UNKNOWN, state.context.integrity)
    }

    @Test fun recommendationAllowsThreeMethodsShowsBasisAndRequiresUseAction() {
        var state by mutableStateOf(objects.copy(phase = ScanPhase.RECOMMENDATION, category = WasteCategory.PLASTIC,
            objectConfirmed = true, recommendation = recommendation, method = DisposalMethod.RECYCLE))
        var used = 0
        compose.setContent { WasteNotTheme {
            Sheet(state, onMethod = { state = state.copy(method = it) }, onUse = { used++ })
        } }
        compose.onNodeWithText("Pisahkan dari sampah lain.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Lihat dasar saran").performScrollTo().performClick()
        compose.onNodeWithText("Botol plastik · kit-v1.3").performScrollTo().assertIsDisplayed()
        compose.onNode(hasText("Recycle") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("Reduce").assertDoesNotExist()
        compose.onNodeWithText("Trash").performClick()
        assertEquals(DisposalMethod.TRASH, state.method); assertEquals(0, used)
        compose.onNodeWithText("Gunakan pilihan").performScrollTo().performClick()
        assertEquals(1, used)
    }

    @Composable
    private fun Sheet(state: ScanState, onManual: () -> Unit = {}, onCategory: (WasteCategory) -> Unit = {},
        onConfirm: () -> Unit = {}, onContext: (ScanContext) -> Unit = {}, onMethod: (DisposalMethod) -> Unit = {}, onUse: () -> Unit = {}) {
        ScanSheet(state, {}, onManual, {}, {}, {}, onCategory, onConfirm, {}, onContext, {}, onMethod, onUse, {}) {
            Text("Arahkan ke satu benda")
        }
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "scan-qa").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
