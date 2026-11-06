package com.example.wastenot.ui.scan

import android.graphics.Bitmap
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import com.example.wastenot.model.*
import com.example.wastenot.theme.WasteNotTheme
import java.io.File
import java.net.URI
import java.time.Instant
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ScanSheetTest {
    @get:Rule val compose = createComposeRule()
    private val candidate = AiDetectionObject("bottle", 0, "bottle", .94, ScanBounds(.2f, .1f, .8f, .9f),
        AiConfidenceBand.OFFERED, true, true, listOf("plastic_bottle"), emptyList(), emptyList(), null)
    private val catalog = AiCatalog("native-fixture", listOf(AiCatalogItem("plastic_bottle", "Botol plastik",
        WasteCategory.PLASTIC, false, AiGuideStatus.PUBLISHED)), "fixture", "fixture", "fixture",
        emptyMap(), emptySet(), mapOf("bottle" to "Botol"))
    private val objects = ScanState(session = "native-test", phase = ScanPhase.OBJECT, objects = listOf(candidate),
        selectedId = candidate.id, catalog = catalog)
    private val recommendation = AiRecommendation("00000000-0000-4000-8000-000000000002", Instant.now().plusSeconds(3600),
        AiRecommendationStatus.READY, "00000000-0000-4000-8000-000000000001", candidate.id, 1,
        "plastic_bottle", WasteCategory.PLASTIC, DisposalMethod.RECYCLE, AiRecommendationScope.HANDLING,
        AiRecommendationBasis.CONFIRMED, emptyList(), emptyList(), "Periksa penerimaan fasilitas setempat.", null,
        false, "Pisahkan dari sampah lain.", "native-fixture", false, "fixture", listOf("fixture-evidence"),
        listOf(URI("https://example.org/fixture")), listOf(AiSourceReference(URI("https://example.org/fixture"),
            "Sumber fixture", "Penerbit fixture", "Wilayah fixture", "2026-10-20", "Cakupan fixture, bukan panduan produksi.")),
        emptyList(), listOf("Periksa material kemasan."))

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
            Sheet(state, onCategory = { state = state.copy(category = it) }, onConfirm = { confirmed++ },
                onObjectKey = { state = state.copy(objectKey = it) })
        } }
        compose.onNodeWithText("Konfirmasi objek").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Pilih material").performScrollTo().performClick()
        compose.onNodeWithText("Plastik").performClick()
        compose.onNodeWithText("Konfirmasi objek").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Pilih jenis benda").performScrollTo().performClick()
        compose.onNodeWithText("Botol plastik").performClick()
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
            objectKey = "plastic_bottle", objectConfirmed = true, recommendation = recommendation, method = DisposalMethod.RECYCLE))
        var used = 0
        compose.setContent { WasteNotTheme {
            Sheet(state, onMethod = { state = state.copy(method = it) }, onUse = { used++ })
        } }
        compose.onNodeWithText("Pisahkan dari sampah lain.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Lihat dasar saran").performScrollTo().performClick()
        compose.onNodeWithText("Botol plastik").performScrollTo().assertIsDisplayed()
        compose.onNode(hasText("Recycle") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("Reduce").assertDoesNotExist()
        compose.onNodeWithText("Trash").performClick()
        assertEquals(DisposalMethod.TRASH, state.method); assertEquals(0, used)
        compose.onNodeWithText("Gunakan pilihan, isi jumlah").performScrollTo().performClick()
        assertEquals(1, used)
    }

    @Test fun conditionalAdviceRequiresAcknowledgementAndSourceMetadataOpensOnlyItsUri() {
        val urls = mutableListOf<String>()
        var state by mutableStateOf(objects.copy(phase = ScanPhase.RECOMMENDATION, category = WasteCategory.PLASTIC,
            objectKey = "plastic_bottle", objectConfirmed = true, method = DisposalMethod.RECYCLE,
            recommendation = recommendation.copy(basis = AiRecommendationBasis.CONDITIONAL,
                conditionsToVerify = listOf(AiContextField.CLEANLINESS), specialHandling = true)))
        compose.setContent { WasteNotTheme {
            CompositionLocalProvider(LocalUriHandler provides object : UriHandler {
                override fun openUri(uri: String) { urls.add(uri) }
            }) {
                Sheet(state, onConfirmConditions = { state = state.copy(conditionsConfirmed = it) })
            }
        } }
        compose.onNodeWithText("Gunakan pilihan, isi jumlah").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Aku sudah memeriksa syarat saran ini").performScrollTo().performClick()
        compose.onNodeWithText("Gunakan pilihan, isi jumlah").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("Sumber fixture").performScrollTo().performClick()
        assertEquals(listOf("https://example.org/fixture"), urls)
        compose.onNodeWithText("Referensi diperiksa: 2026-10-20").performScrollTo().assertIsDisplayed()
        capture("scan-conditional-source")
    }

    @Test fun needsContextAndPrototypeRemainSeparateFromAUsableLiveRecommendation() {
        var state by mutableStateOf(objects.copy(phase = ScanPhase.RECOMMENDATION, category = WasteCategory.PLASTIC,
            objectKey = "plastic_bottle", objectConfirmed = true, method = null,
            recommendation = recommendation.copy(status = AiRecommendationStatus.NEEDS_CONTEXT, method = null,
                scope = null, basis = null, missingContext = listOf(AiContextField.RISK_TYPE))))
        var reviews = 0
        compose.setContent { WasteNotTheme { Sheet(state, onReviewContext = { reviews++ }) } }
        compose.onNodeWithText("Lengkapi informasi benda").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Jenis risiko").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Gunakan pilihan, isi jumlah").assertDoesNotExist()
        compose.onNodeWithText("Periksa kondisi benda").performScrollTo().performClick()
        assertEquals(1, reviews)
        capture("scan-needs-context")
        compose.runOnIdle { state = state.copy(recommendation = recommendation.copy(prototypeOnly = true)) }
        compose.onNodeWithText("Hasil prototipe. Saran ini belum dapat digunakan untuk catatan AI.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Gunakan pilihan, isi jumlah").assertDoesNotExist()
        capture("scan-prototype-unusable")
    }

    @Composable
    private fun Sheet(state: ScanState, onManual: () -> Unit = {}, onCategory: (WasteCategory) -> Unit = {},
        onConfirm: () -> Unit = {}, onContext: (ScanContext) -> Unit = {}, onMethod: (DisposalMethod) -> Unit = {}, onUse: () -> Unit = {},
        onObjectKey: (String) -> Unit = {}, onReviewContext: () -> Unit = {}, onConfirmConditions: (Boolean) -> Unit = {}) {
        ScanSheet(state, {}, onManual, {}, {}, {}, onCategory, onConfirm, {}, onContext, {}, onMethod, onUse, {},
            onObjectKey = onObjectKey, onReviewContext = onReviewContext, onConfirmConditions = onConfirmConditions) {
            Text("Arahkan ke satu benda")
        }
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assertEquals("Capture must show the tested app, not a system dialog", instrumentation.targetContext.packageName,
            instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString())
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "scan-qa").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
