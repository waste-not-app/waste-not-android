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
        waitFor("New here? Create an account")
        assertTrue(container.sessions.state.value is SessionState.SignedOut)
        try {
            val id = UUID.randomUUID()
            success(container.sessions.register("Android #112 local probe", "guide-$id@example.test",
                UUID.randomUUID().toString(), ZoneId.of("Asia/Jakarta")))
            assertEquals(false, success(container.core.metadata()).capabilities["ai_scan"])
            waitFor("Log manually")
            compose.onNode(hasText("Guide") and isSelectable()).performClick()
            waitFor("Karton QA 112")
            compose.onNodeWithText("QA carton draft").assertDoesNotExist()
            val search = compose.onNodeWithContentDescription("Search guides")
            search.performScrollTo().performTextReplacement("missing-112")
            search.performImeAction()
            waitFor("No guides found")
            search.performScrollTo().performTextReplacement(" KARTON ")
            search.performImeAction()
            waitFor("Karton QA 112")
            compose.onNodeWithText("Karton QA 112").performScrollTo().performClick()
            waitFor("Guide details")
            compose.onNodeWithText("Pisahkan karton kering.", substring = true).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("example.com", substring = true).performScrollTo().assertIsDisplayed()
            captureUiPreview(compose, "guide-source", "local-backend-guide", capturePanel = false)
            compose.onNodeWithText("Log this activity").performScrollTo().performClick()
            waitFor("Log activity")
            compose.onNodeWithText("Recycle").assertIsSelected()
            compose.onNodeWithContentDescription("Category").performScrollTo().assertTextContains("Paper")
            val weight = compose.onNodeWithContentDescription("Weight").performScrollTo()
            assertEquals("", weight.fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
            assertTrue(success(container.core.wasteLogs()).isEmpty())
            captureUiPreview(compose, "guide-prefill", "local-backend-guide", capturePanel = false)
            compose.onNodeWithContentDescription("Back").performClick()
            waitFor("Guide details")
            compose.onNodeWithContentDescription("Back").performClick()
            waitFor("Karton QA 112")
            compose.onNode(hasText("History") and isSelectable()).performClick()
            compose.onNodeWithContentDescription("Log or scan waste").performClick()
            waitFor("Photo scanning is turned off. You can still log manually.")
            compose.onNodeWithText("Turn on camera").assertDoesNotExist()
            captureUiPreview(compose, "scan-disabled", "local-backend-guide", capturePanel = false)
            compose.onNode(hasText("Log manually") and hasAnyAncestor(hasTestTag("capture-sheet"))).performClick()
            waitFor("Log activity")
            compose.onNodeWithContentDescription("Weight").performScrollTo().assertIsDisplayed()
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
