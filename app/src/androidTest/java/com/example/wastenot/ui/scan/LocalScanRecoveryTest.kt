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
        waitFor("New here? Create an account")
        assertTrue(container.sessions.state.value is SessionState.SignedOut)
        try {
            assertTrue(container.sessions.register("Android QA 112", "scan-recovery-$runId@example.test",
                UUID.randomUUID().toString(), ZoneId.of("Asia/Jakarta")) is ApiResult.Success)
            assertTrue((container.core.metadata() as ApiResult.Success).value.isEnabled("ai_scan"))
            waitFor("Log manually")
            capturePhoto()
            tap("Scan photo")
            waitFor("Scan isn't available")
            captureUiPreview(compose, "detection-network-error", "local-scan-recovery", capturePanel = false)
            tap("Try again")
            waitFor("Review photo")
            tap("Scan photo")
            waitFor("Check the object")
            captureUiPreview(compose, "detection-retry", "local-scan-recovery", capturePanel = false)
            dismissScan()

            capturePhoto()
            tap("Scan photo")
            waitFor("Identifying the object…")
            dismissScan()
            withContext(Dispatchers.IO) { delay(9_000) }
            compose.onNodeWithText("Check the object").assertDoesNotExist()
            compose.onNodeWithContentDescription("Log or scan waste").performClick()
            waitFor("Point at one item")
            compose.onNodeWithText("Check the object").assertDoesNotExist()
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
        waitFor("New here? Create an account")
        assertTrue(container.sessions.state.value is SessionState.SignedOut)
        try {
            assertTrue(container.sessions.register("Android QA 112", "confidence-$runId@example.test",
                UUID.randomUUID().toString(), ZoneId.of("Asia/Jakarta")) is ApiResult.Success)
            waitFor("Log manually")
            val scanCache = compose.activity.cacheDir.resolve("scan")
            val beforeCapture = scanCache.listFiles().orEmpty().toSet()
            capturePhoto()
            val originalPhoto = (scanCache.listFiles().orEmpty().toSet() - beforeCapture).single()
            tap("Retake")
            waitFor("Point at one item")
            assertTrue(!originalPhoto.exists())
            captureInOpenSheet()
            val retake = (scanCache.listFiles().orEmpty().toSet() - beforeCapture).single()
            tap("Scan photo")
            waitFor("Check the object")
            val candidate = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
            val uncertain = hasText("Not sure yet. Check it or retake the photo.")
            val atOfferedThreshold = candidate and hasText("Detection score 70%") and !uncertain
            val belowOfferedThreshold = candidate and hasText("Detection score 70%") and uncertain
            val atLowerThreshold = candidate and hasText("Detection score 50%") and uncertain
            compose.onAllNodes(candidate).assertCountEquals(3)
            compose.onNode(atOfferedThreshold).assertIsSelected()
            compose.onNode(belowOfferedThreshold).assertIsNotSelected()
            compose.onNode(atLowerThreshold).performScrollTo().performClick().assertIsSelected()
            compose.onNode(atOfferedThreshold).assertIsNotSelected()
            compose.onNode(hasText("Confirm object") and hasClickAction()).assertIsNotEnabled()
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
        waitFor("New here? Create an account")
        assertTrue(container.sessions.state.value is SessionState.SignedOut)
        try {
            assertTrue(container.sessions.register("Android QA 112", "recommendation-$runId@example.test",
                UUID.randomUUID().toString(), ZoneId.of("Asia/Jakarta")) is ApiResult.Success)
            waitFor("Log manually")
            capturePhoto()
            tap("Scan photo")
            waitFor("Check the object")
            confirmGlassAndFillConditions()
            tap("See advice")
            waitFor("Couldn't load the advice. Your answers are still saved.")
            tap("Try again")
            waitFor("Item condition")
            assertChoice(R.string.scan_cleanliness, "Clean")
            assertChoice(R.string.scan_integrity, "Intact")
            tap("See advice")
            waitFor("Preparing advice…")
            tap("Correct the object")
            waitFor("Check the object")
            tap("Confirm object")
            choose(R.string.scan_cleanliness, "Dirty")
            withContext(Dispatchers.IO) { delay(6_000) }
            compose.onNodeWithText("Review the advice").assertDoesNotExist()
            assertChoice(R.string.scan_cleanliness, "Dirty")
            choose(R.string.scan_cleanliness, "Clean")
            tap("See advice")
            try {
                waitFor("Review the advice")
            } catch (error: ComposeTimeoutException) {
                compose.onAllNodes(isRoot()).printToLog("RecommendationTimeout")
                throw error
            }
            tap("Use this, add amount")
            val attached = compose.activity.getString(R.string.ai_provenance_attached)
            waitFor(attached)
            tap("Reduce")
            choose(R.string.quantity_unit, "Items")
            compose.onNodeWithContentDescription("Amount avoided").performScrollTo().performTextReplacement("2")
            tap("Save log")
            waitFor(compose.activity.getString(R.string.ai_reduce_manual))
            val before = (container.core.dashboard() as ApiResult.Success).value
            assertEquals(0L, before.summary.activityCount)
            tap(compose.activity.getString(R.string.ai_use_manual))
            compose.onNodeWithText(attached).assertDoesNotExist()
            tap("Save log")
            try {
                waitFor("Log saved")
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
        waitFor("New here? Create an account")
        assertTrue(container.sessions.state.value is SessionState.SignedOut)
        try {
            assertTrue(container.sessions.register("Android QA 112", "double-save-$runId@example.test",
                UUID.randomUUID().toString(), ZoneId.of("Asia/Jakarta")) is ApiResult.Success)
            waitFor("Log manually")
            capturePhoto()
            tap("Scan photo")
            waitFor("Check the object")
            confirmGlassAndFillConditions()
            tap("See advice")
            waitFor("Review the advice")
            tap("Use this, add amount")
            waitFor(compose.activity.getString(R.string.ai_provenance_attached))
            compose.onNodeWithContentDescription("Weight").performScrollTo().performTextReplacement("250")
            compose.runOnUiThread { compose.activity.window.insetsController?.hide(WindowInsets.Type.ime()) }
            compose.onNode(hasText("Save log") and hasClickAction()).performScrollTo().assertIsEnabled()
                .performTouchInput { doubleClick() }
            waitFor("Log saved")
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
        choose(R.string.scan_material, "Glass")
        choose(R.string.scan_object_type, "Glass bottle")
        tap("Confirm object")
        waitFor("Item condition")
        choose(R.string.scan_cleanliness, "Clean")
        choose(R.string.scan_integrity, "Intact")
        choose(R.string.scan_recycling_access, "Yes")
    }

    @Test fun rejectsInvalidAdviceAndKeepsSpecialHandlingManual() = runTest(timeout = 3.minutes) {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("verifyLocalPolicyReview") == "true")
        assertTrue(BuildConfig.DEBUG)
        assertEquals("http://10.0.2.2:18094/v1/", BuildConfig.API_BASE_URL)
        val runId = UUID.fromString(requireNotNull(arguments.getString("fixtureRunId")))
        val container = (compose.activity.application as WasteNotApplication).container
        waitFor("New here? Create an account")
        assertTrue(container.sessions.state.value is SessionState.SignedOut)
        try {
            assertTrue(container.sessions.register("Android QA 112", "scan-policy-$runId@example.test",
                UUID.randomUUID().toString(), ZoneId.of("Asia/Jakarta")) is ApiResult.Success)
            waitFor("Log manually")
            capturePhoto()
            tap("Scan photo")
            waitFor("Check the object")
            confirmGlassAndFillConditions()
            repeat(4) {
                tap("See advice")
                waitFor(compose.activity.getString(R.string.scan_recommendation_error))
                compose.onNodeWithText("Use this, add amount").assertDoesNotExist()
                tap("Try again")
                waitFor("Item condition")
                assertChoice(R.string.scan_cleanliness, "Clean")
            }
            tap(compose.activity.getString(R.string.scan_risk_flag))
            choose(R.string.scan_risk_type, compose.activity.getString(R.string.scan_risk_sharp))
            tap("See advice")
            waitFor("Specialist handling advice")
            compose.onNodeWithText("How Do I Recycle Common Recyclables").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Use this, add amount").assertDoesNotExist()
            compose.onNodeWithText(compose.activity.getString(R.string.scan_method_confirmation)).assertDoesNotExist()
            compose.onNode(hasText("Log manually") and hasAnyAncestor(hasTestTag("capture-sheet"))).performClick()
            waitFor("How you handled it")
            compose.onNodeWithText(compose.activity.getString(R.string.ai_provenance_attached)).assertDoesNotExist()
            val dashboard = (container.core.dashboard() as ApiResult.Success).value
            assertEquals(0L, dashboard.summary.activityCount)
            assertEquals(0, dashboard.progress.xp)
        } finally {
            if (container.sessions.state.value is SessionState.Authenticated) container.sessions.logout()
        }
    }

    private fun choose(label: Int, value: String) {
        compose.onNodeWithContentDescription(compose.activity.getString(label)).performScrollTo().performClick()
        compose.onNode(hasText(value) and hasAnyAncestor(isPopup())).performScrollTo().performClick()
    }

    private fun assertChoice(label: Int, value: String) {
        compose.onNodeWithContentDescription(compose.activity.getString(label)).performScrollTo().assertTextContains(value)
    }

    private fun capturePhoto() {
        compose.onNodeWithContentDescription("Log or scan waste").performClick()
        captureInOpenSheet()
    }

    private fun captureInOpenSheet() {
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText("Turn on camera").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodes(hasText("Take photo") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        if (compose.onAllNodesWithText("Turn on camera").fetchSemanticsNodes().isNotEmpty()) tap("Turn on camera")
        compose.waitUntil(30_000) {
            compose.onAllNodes(hasText("Take photo") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        tap("Take photo")
        waitFor("Review photo")
    }

    private fun dismissScan() {
        compose.onNodeWithContentDescription("Close scan").performClick()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("Close scan").fetchSemanticsNodes().isEmpty()
        }
    }

    private fun tap(text: String) {
        compose.onNode(hasText(text) and hasClickAction()).performScrollTo().assertIsEnabled().performClick()
    }

    private fun waitFor(text: String) {
        compose.waitUntil(30_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
}
