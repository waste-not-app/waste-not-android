package com.example.wastenot.ui.main

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.BuildConfig
import com.example.wastenot.MainActivity
import com.example.wastenot.ThemeFixtureRule
import com.example.wastenot.WasteNotApplication
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.repository.SessionState
import java.time.ZoneId
import java.util.UUID
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class LocalBackendGuideScanTest {
    @get:Rule(order = 0) val appearance = ThemeFixtureRule()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun publishedGuidePrefillsAndDisabledScanKeepsManualEntryAvailable() = runTest(timeout = 3.minutes) {
        assumeTrue(InstrumentationRegistry.getArguments().getString("verifyLocalGuideScan") == "true")
        assertTrue(BuildConfig.DEBUG)
        assertEquals("http://10.0.2.2:18089/v1/", BuildConfig.API_BASE_URL)
        val container = (compose.activity.application as WasteNotApplication).container
        waitFor("Belum punya akun? Daftar")
        assertTrue(container.sessions.state.value is SessionState.SignedOut)
        try {
            val id = UUID.randomUUID()
            success(container.sessions.register("Android #112 local probe", "guide-$id@example.test",
                UUID.randomUUID().toString(), ZoneId.of("Asia/Jakarta")))
            assertEquals(false, success(container.core.metadata()).capabilities["ai_scan"])
            waitFor("Input manual")
            compose.onNode(hasText("Panduan") and isSelectable()).performClick()
            waitFor("Karton QA 112")
            compose.onNodeWithText("Karton QA draf").assertDoesNotExist()
            val search = compose.onNodeWithContentDescription("Cari panduan")
            search.performScrollTo().performTextReplacement("tidak-ada-112")
            search.performImeAction()
            waitFor("Panduan belum ditemukan")
            search.performScrollTo().performTextReplacement(" KARTON ")
            search.performImeAction()
            waitFor("Karton QA 112")
            compose.onNodeWithText("Karton QA 112").performScrollTo().performClick()
            waitFor("Detail panduan")
            compose.onNodeWithText("Pisahkan karton kering.", substring = true).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("example.com", substring = true).performScrollTo().assertIsDisplayed()
            captureUiPreview(compose, "guide-source", "local-backend-guide", capturePanel = false)
            compose.onNodeWithText("Catat aktivitas ini").performScrollTo().performClick()
            waitFor("Catat aktivitas")
            compose.onNodeWithText("Recycle").assertIsSelected()
            compose.onNodeWithContentDescription("Kategori").performScrollTo().assertTextContains("Kertas")
            val weight = compose.onNodeWithContentDescription("Berat").performScrollTo()
            assertEquals("", weight.fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
            assertTrue(success(container.core.wasteLogs()).isEmpty())
            captureUiPreview(compose, "guide-prefill", "local-backend-guide", capturePanel = false)
            compose.onNodeWithContentDescription("Kembali").performClick()
            waitFor("Detail panduan")
            compose.onNodeWithContentDescription("Kembali").performClick()
            waitFor("Karton QA 112")
            compose.onNode(hasText("Riwayat") and isSelectable()).performClick()
            compose.onNodeWithContentDescription("Catat atau scan sampah").performClick()
            waitFor("Scan foto dinonaktifkan. Kamu tetap bisa mencatat secara manual.")
            compose.onNodeWithText("Aktifkan kamera").assertDoesNotExist()
            captureUiPreview(compose, "scan-disabled", "local-backend-guide", capturePanel = false)
            compose.onNode(hasText("Input manual") and hasAnyAncestor(hasTestTag("capture-sheet"))).performClick()
            waitFor("Catat aktivitas")
            compose.onNodeWithContentDescription("Berat").performScrollTo().assertIsDisplayed()
            assertTrue(success(container.core.wasteLogs()).isEmpty())
        } finally {
            if (container.sessions.state.value is SessionState.Authenticated) container.sessions.logout()
        }
    }

    private fun waitFor(text: String) {
        compose.waitUntil(30_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun <T> success(result: ApiResult<T>): T {
        check(result is ApiResult.Success) { "Local backend returned ${(result as? ApiResult.Failure)?.error?.kind}" }
        return result.value
    }
}
