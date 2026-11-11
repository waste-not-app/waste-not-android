package com.example.wastenot.ui.scan

import android.view.WindowInsets
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.BuildConfig
import com.example.wastenot.MainActivity
import com.example.wastenot.R
import com.example.wastenot.ThemeFixtureRule
import com.example.wastenot.WasteNotApplication
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.repository.SessionState
import com.example.wastenot.model.DisposalMethod
import com.example.wastenot.model.Quantity
import com.example.wastenot.ui.main.captureUiPreview
import java.time.ZoneId
import java.util.UUID
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class LocalScanRecoveryTest {
    @get:Rule(order = 0) val appearance = ThemeFixtureRule()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun retriesAfterDroppedDetectionAndIgnoresLateResponseAfterClose() = runTest(timeout = 3.minutes) {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("verifyLocalScanRecovery") == "true")
        assertTrue(BuildConfig.DEBUG)
        assertEquals("http://10.0.2.2:18094/v1/", BuildConfig.API_BASE_URL)
        val runId = UUID.fromString(requireNotNull(arguments.getString("fixtureRunId")))
        val container = (compose.activity.application as WasteNotApplication).container
        waitFor("Belum punya akun? Daftar")
        assertTrue(container.sessions.state.value is SessionState.SignedOut)
        try {
            assertTrue(container.sessions.register("Android QA 112", "scan-recovery-$runId@example.test",
                UUID.randomUUID().toString(), ZoneId.of("Asia/Jakarta")) is ApiResult.Success)
            assertTrue((container.core.metadata() as ApiResult.Success).value.isEnabled("ai_scan"))
            waitFor("Input manual")
            capturePhoto()
            tap("Scan foto")
            waitFor("Scan belum tersedia")
            captureUiPreview(compose, "detection-network-error", "local-scan-recovery", capturePanel = false)
            tap("Coba lagi")
            waitFor("Tinjau foto")
            tap("Scan foto")
            waitFor("Periksa objek")
            captureUiPreview(compose, "detection-retry", "local-scan-recovery", capturePanel = false)
            dismissScan()

            capturePhoto()
            tap("Scan foto")
            waitFor("Mengenali objek…")
            dismissScan()
            withContext(Dispatchers.IO) { delay(9_000) }
            compose.onNodeWithText("Periksa objek").assertDoesNotExist()
            compose.onNodeWithContentDescription("Catat atau scan sampah").performClick()
            waitFor("Arahkan ke satu benda")
            compose.onNodeWithText("Periksa objek").assertDoesNotExist()
            dismissScan()

            val dashboard = (container.core.dashboard() as ApiResult.Success).value
            assertEquals(0L, dashboard.summary.activityCount)
            assertEquals(0, dashboard.progress.xp)
        } finally {
            if (container.sessions.state.value is SessionState.Authenticated) container.sessions.logout()
        }
    }

    @Test fun scanListRespectsServerConfidenceBandsAtBoundaries() = runTest(timeout = 3.minutes) {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("verifyLocalConfidenceReview") == "true")
        assertTrue(BuildConfig.DEBUG)
        assertEquals("http://10.0.2.2:18094/v1/", BuildConfig.API_BASE_URL)
        val runId = UUID.fromString(requireNotNull(arguments.getString("fixtureRunId")))
        val container = (compose.activity.application as WasteNotApplication).container
        waitFor("Belum punya akun? Daftar")
        assertTrue(container.sessions.state.value is SessionState.SignedOut)
        try {
            assertTrue(container.sessions.register("Android QA 112", "confidence-$runId@example.test",
                UUID.randomUUID().toString(), ZoneId.of("Asia/Jakarta")) is ApiResult.Success)
            waitFor("Input manual")
            val scanCache = compose.activity.cacheDir.resolve("scan")
            val beforeCapture = scanCache.listFiles().orEmpty().toSet()
            capturePhoto()
            val originalPhoto = (scanCache.listFiles().orEmpty().toSet() - beforeCapture).single()
            tap("Ambil ulang")
            waitFor("Arahkan ke satu benda")
            assertTrue(!originalPhoto.exists())
            captureInOpenSheet()
            val retake = (scanCache.listFiles().orEmpty().toSet() - beforeCapture).single()
            tap("Scan foto")
            waitFor("Periksa objek")
            val candidate = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
            val uncertain = hasText("Belum yakin. Periksa atau ambil ulang foto.")
            val atOfferedThreshold = candidate and hasText("Skor deteksi 70%") and !uncertain
            val belowOfferedThreshold = candidate and hasText("Skor deteksi 70%") and uncertain
            val atLowerThreshold = candidate and hasText("Skor deteksi 50%") and uncertain
            compose.onAllNodes(candidate).assertCountEquals(3)
            compose.onNode(atOfferedThreshold).assertIsSelected()
            compose.onNode(belowOfferedThreshold).assertIsNotSelected()
            compose.onNode(atLowerThreshold).performScrollTo().performClick().assertIsSelected()
            compose.onNode(atOfferedThreshold).assertIsNotSelected()
            compose.onNode(hasText("Konfirmasi objek") and hasClickAction()).assertIsNotEnabled()
            dismissScan()
            assertTrue(!retake.exists())
        } finally {
            if (container.sessions.state.value is SessionState.Authenticated) container.sessions.logout()
        }
    }

    @Test fun recoversRecommendationAndSavesReduceWithoutProvenance() = runTest(timeout = 3.minutes) {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("verifyLocalRecommendationRecovery") == "true")
        assertTrue(BuildConfig.DEBUG)
        assertEquals("http://10.0.2.2:18094/v1/", BuildConfig.API_BASE_URL)
        val runId = UUID.fromString(requireNotNull(arguments.getString("fixtureRunId")))
        val container = (compose.activity.application as WasteNotApplication).container
        waitFor("Belum punya akun? Daftar")
        assertTrue(container.sessions.state.value is SessionState.SignedOut)
        try {
            assertTrue(container.sessions.register("Android QA 112", "recommendation-$runId@example.test",
                UUID.randomUUID().toString(), ZoneId.of("Asia/Jakarta")) is ApiResult.Success)
            waitFor("Input manual")
            capturePhoto()
            tap("Scan foto")
            waitFor("Periksa objek")
            confirmGlassAndFillConditions()
            tap("Lihat saran")
            waitFor("Saran belum bisa dimuat. Kondisi yang sudah kamu isi tetap tersimpan.")
            tap("Coba lagi")
            waitFor("Kondisi objek")
            assertChoice(R.string.scan_cleanliness, "Bersih")
            assertChoice(R.string.scan_integrity, "Utuh")
            tap("Lihat saran")
            waitFor("Menyiapkan saran…")
            tap("Koreksi objek")
            waitFor("Periksa objek")
            tap("Konfirmasi objek")
            choose(R.string.scan_cleanliness, "Kotor")
            withContext(Dispatchers.IO) { delay(6_000) }
            compose.onNodeWithText("Periksa saran").assertDoesNotExist()
            assertChoice(R.string.scan_cleanliness, "Kotor")
            choose(R.string.scan_cleanliness, "Bersih")
            tap("Lihat saran")
            try {
                waitFor("Periksa saran")
            } catch (error: ComposeTimeoutException) {
                compose.onAllNodes(isRoot()).printToLog("RecommendationTimeout")
                throw error
            }
            tap("Gunakan pilihan, isi jumlah")
            val attached = compose.activity.getString(R.string.ai_provenance_attached)
            waitFor(attached)
            tap("Reduce")
            choose(R.string.quantity_unit, "Benda")
            compose.onNodeWithContentDescription("Jumlah yang dihindari").performScrollTo().performTextReplacement("2")
            tap("Simpan catatan")
            waitFor(compose.activity.getString(R.string.ai_reduce_manual))
            val before = (container.core.dashboard() as ApiResult.Success).value
            assertEquals(0L, before.summary.activityCount)
            tap(compose.activity.getString(R.string.ai_use_manual))
            compose.onNodeWithText(attached).assertDoesNotExist()
            tap("Simpan catatan")
            try {
                waitFor("Catatan tersimpan")
            } catch (error: ComposeTimeoutException) {
                compose.onAllNodes(isRoot()).printToLog("ReduceSaveTimeout")
                throw error
            }
            val dashboard = (container.core.dashboard() as ApiResult.Success).value
            assertEquals(1L, dashboard.summary.activityCount)
            assertEquals(10, dashboard.progress.xp)
            assertEquals(DisposalMethod.REDUCE, dashboard.recentLogs.single().method)
        } finally {
            if (container.sessions.state.value is SessionState.Authenticated) container.sessions.logout()
        }
    }

    @Test fun rapidDoubleTapSavesOneAiAssistedLog() = runTest(timeout = 3.minutes) {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("verifyLocalDoubleSave") == "true")
        assertTrue(BuildConfig.DEBUG)
        assertEquals("http://10.0.2.2:18094/v1/", BuildConfig.API_BASE_URL)
        val runId = UUID.fromString(requireNotNull(arguments.getString("fixtureRunId")))
        val container = (compose.activity.application as WasteNotApplication).container
        waitFor("Belum punya akun? Daftar")
        assertTrue(container.sessions.state.value is SessionState.SignedOut)
        try {
            assertTrue(container.sessions.register("Android QA 112", "double-save-$runId@example.test",
                UUID.randomUUID().toString(), ZoneId.of("Asia/Jakarta")) is ApiResult.Success)
            waitFor("Input manual")
            capturePhoto()
            tap("Scan foto")
            waitFor("Periksa objek")
            confirmGlassAndFillConditions()
            tap("Lihat saran")
            waitFor("Periksa saran")
            tap("Gunakan pilihan, isi jumlah")
            waitFor(compose.activity.getString(R.string.ai_provenance_attached))
            compose.onNodeWithContentDescription("Berat").performScrollTo().performTextReplacement("250")
            compose.runOnUiThread { compose.activity.window.insetsController?.hide(WindowInsets.Type.ime()) }
            compose.onNode(hasText("Simpan catatan") and hasClickAction()).performScrollTo().assertIsEnabled()
                .performTouchInput { doubleClick() }
            waitFor("Catatan tersimpan")
            val dashboard = (container.core.dashboard() as ApiResult.Success).value
            assertEquals(1L, dashboard.summary.activityCount)
            assertEquals(10, dashboard.progress.xp)
            val saved = dashboard.recentLogs.single()
            assertEquals(DisposalMethod.RECYCLE, saved.method)
            assertEquals(Quantity.Weight.of(250), saved.quantity)
        } finally {
            if (container.sessions.state.value is SessionState.Authenticated) container.sessions.logout()
        }
    }

    private fun confirmGlassAndFillConditions() {
        choose(R.string.scan_material, "Kaca")
        choose(R.string.scan_object_type, "Botol kaca")
        tap("Konfirmasi objek")
        waitFor("Kondisi objek")
        choose(R.string.scan_cleanliness, "Bersih")
        choose(R.string.scan_integrity, "Utuh")
        choose(R.string.scan_recycling_access, "Ya")
    }

    private fun choose(label: Int, value: String) {
        compose.onNodeWithContentDescription(compose.activity.getString(label)).performScrollTo().performClick()
        compose.onNode(hasText(value) and hasAnyAncestor(isPopup())).performScrollTo().performClick()
    }

    private fun assertChoice(label: Int, value: String) {
        compose.onNodeWithContentDescription(compose.activity.getString(label)).performScrollTo().assertTextContains(value)
    }

    private fun capturePhoto() {
        compose.onNodeWithContentDescription("Catat atau scan sampah").performClick()
        captureInOpenSheet()
    }

    private fun captureInOpenSheet() {
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText("Aktifkan kamera").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodes(hasText("Ambil foto") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        if (compose.onAllNodesWithText("Aktifkan kamera").fetchSemanticsNodes().isNotEmpty()) tap("Aktifkan kamera")
        compose.waitUntil(30_000) {
            compose.onAllNodes(hasText("Ambil foto") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        tap("Ambil foto")
        waitFor("Tinjau foto")
    }

    private fun dismissScan() {
        compose.onNodeWithContentDescription("Tutup scan").performClick()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("Tutup scan").fetchSemanticsNodes().isEmpty()
        }
    }

    private fun tap(text: String) {
        compose.onNode(hasText(text) and hasClickAction()).performScrollTo().assertIsEnabled().performClick()
    }

    private fun waitFor(text: String) {
        compose.waitUntil(30_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
}
