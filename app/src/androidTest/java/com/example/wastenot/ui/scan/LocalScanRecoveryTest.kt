package com.example.wastenot.ui.scan

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.BuildConfig
import com.example.wastenot.MainActivity
import com.example.wastenot.ThemeFixtureRule
import com.example.wastenot.WasteNotApplication
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.repository.SessionState
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

    private fun capturePhoto() {
        compose.onNodeWithContentDescription("Catat atau scan sampah").performClick()
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
