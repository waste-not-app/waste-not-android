package com.example.wastenot.ui.scan

import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
import android.app.UiAutomation
import android.view.WindowInsets
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.R
import com.example.wastenot.data.dummy.DummyActivityStore
import com.example.wastenot.data.dummy.DummyGuideStore
import com.example.wastenot.data.dummy.DummyScanRepository
import com.example.wastenot.data.local.ScanPhotoCache
import com.example.wastenot.data.network.ApiFailure
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.repository.ActivityCreation
import com.example.wastenot.data.repository.ActivityInput
import com.example.wastenot.data.repository.ActivityStore
import com.example.wastenot.data.repository.ScanRepository
import com.example.wastenot.model.*
import com.example.wastenot.theme.ThemeMode
import com.example.wastenot.theme.WasteNotTheme
import com.example.wastenot.ui.main.AccountNavigation
import com.example.wastenot.ui.main.captureUiPreview
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SlowScanJourneyTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun slowAdviceAndLostSaveResponseKeepOneScanLogInHistory() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        val context = instrumentation.targetContext
        val user = User(UUID.randomUUID().toString(), "WasteNot QA", "scan@example.invalid", ZoneId.of("Asia/Jakarta"), null)
        val preferences = "scan-journey-${user.id}"
        val fixtureStore = DummyActivityStore(context.getSharedPreferences(preferences, 0), user.timezone, seedInitialRecords = false)
        val fixtureScan = DummyScanRepository()
        val guides = DummyGuideStore()
        val detectionCanFinish = CompletableDeferred<Unit>()
        val adviceCanFinish = CompletableDeferred<Unit>()
        val saveCanFinish = CompletableDeferred<Unit>()
        val requests = mutableListOf<Pair<String, ActivityInput>>()
        val store = object : ActivityStore by fixtureStore {
            override suspend fun create(input: ActivityInput, clientRequestId: String): ApiResult<ActivityCreation> {
                requests += clientRequestId to input
                val wasFirst = requests.size == 1
                if (wasFirst) saveCanFinish.await()
                val result = fixtureStore.create(input, clientRequestId)
                return if (wasFirst && result is ApiResult.Success) ApiResult.Failure(ApiFailure(ApiFailureKind.TIMEOUT)) else result
            }
        }
        val scan = object : ScanRepository by fixtureScan {
            override suspend fun detect(photo: ScanPhoto, requestId: String): ApiResult<AiDetection> {
                detectionCanFinish.await()
                return fixtureScan.detect(photo, requestId)
            }

            override suspend fun recommend(input: AiRecommendationRequest): ApiResult<AiRecommendation> {
                adviceCanFinish.await()
                val advice = (fixtureScan.recommend(input) as ApiResult.Success).value
                return ApiResult.Success(advice.copy(
                    prototypeOnly = false,
                    basis = AiRecommendationBasis.CONDITIONAL,
                    conditionsToVerify = listOf(AiContextField.CLEANLINESS),
                ))
            }
        }
        val reader = if (arguments.getString("verifyTalkBackScanJourney") == "true") {
            instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).also {
                it.serviceInfo = it.serviceInfo.apply { flags = flags or FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
                val manager = context.getSystemService(AccessibilityManager::class.java)
                assertTrue("TalkBack must be active", manager.isTouchExplorationEnabled &&
                    manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_SPOKEN)
                        .any { service -> service.resolveInfo.serviceInfo.packageName == "com.google.android.marvin.talkback" })
            }
        } else null
        fun preview(name: String) {
            if (reader == null && arguments.getString("captureScanJourney") == "true") {
                captureUiPreview(compose, name, "slow-scan-journey", capturePanel = false)
            }
        }
        val cache = ScanPhotoCache.create(context, user.id)
        val photo = instrumentation.context.assets.open("ai/pet-bottle.jpg").use { cache.save(it.readBytes(), rotation = 0) }

        try {
            compose.setContent {
                WasteNotTheme {
                    AccountNavigation(user, store, guides, { scan }, ThemeMode.SYSTEM, {}, {}, Modifier.fillMaxSize())
                }
            }
            val trigger = text(R.string.scan_action_description)
            compose.waitUntil(20_000) { compose.onAllNodesWithContentDescription(trigger).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription(trigger).performClick()
            waitFor(R.string.scan_title)
            // Use bundled test artwork, not whatever the phone camera happens to see.
            compose.runOnUiThread {
                val model = ViewModelProvider.create(compose.activity)["scan-${user.id}", ScanViewModel::class]
                assertTrue(model.state.value.session.isNotEmpty())
                model.photo(model.state.value.session, photo)
            }
            action(R.string.scan_send).performScrollTo().performClick()
            waitFor(R.string.scan_detecting)
            action(R.string.scan_detecting).assertIsDisplayed()
            action(R.string.scan_use).assertDoesNotExist()
            detectionCanFinish.complete(Unit)
            waitFor(R.string.scan_review_object)
            action(R.string.scan_choose_material).performScrollTo().performClick()
            compose.onNodeWithText("Plastic").performClick()
            action(R.string.scan_choose_object_type).performScrollTo().performClick()
            compose.onNodeWithText("Plastic bottle (sample)").performClick()
            action(R.string.scan_confirm_object).performScrollTo().performClick()
            action(R.string.scan_get_recommendation).performScrollTo().performClick()
            waitFor(R.string.scan_recommending)
            action(R.string.scan_recommending).assertIsDisplayed()
            preview("waiting-for-advice")
            action(R.string.scan_use).assertDoesNotExist()
            adviceCanFinish.complete(Unit)
            waitFor(R.string.scan_review_recommendation)
            action(R.string.scan_use).assertIsNotEnabled()
            action(R.string.scan_confirm_conditions).performScrollTo().performClick()
            action(R.string.scan_use).reveal().assertIsEnabled()
            preview("confirmed-advice")
            if (reader == null) action(R.string.scan_use).performClick()
            else focusAndActivateAdvice(reader, text(R.string.scan_use), context.packageName)
            waitFor(R.string.editor_scan_choices)
            assertFalse("Handoff must remove the scan JPEG", cache.exists(photo))
            val amount = compose.onNodeWithContentDescription("Weight").performScrollTo()
            amount.assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
                .performTextReplacement("247")
            compose.runOnUiThread { compose.activity.window.insetsController?.hide(WindowInsets.Type.ime()) }
            action(R.string.save_activity).reveal().performClick()
            waitFor(R.string.saving)
            action(R.string.saving).assertIsNotEnabled()
            action(R.string.save_confirm_title).assertDoesNotExist()
            preview("waiting-for-save")
            saveCanFinish.complete(Unit)
            waitFor(R.string.save_outcome_unknown)
            action(R.string.save_outcome_unknown).performScrollTo().assertIsDisplayed()
            preview("save-timeout")
            action(R.string.retry).reveal().performClick()
            waitFor(R.string.save_confirm_existing)
            action(R.string.save_confirm_created).assertDoesNotExist()
            action(R.string.save_confirm_home).performScrollTo().performClick()
            val tab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
            compose.onNode(hasText(text(R.string.tab_history)) and tab).performClick().assertIsSelected()
            compose.waitUntil(20_000) { compose.onAllNodesWithText("247 g", substring = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("247 g", substring = true).performScrollTo().assertIsDisplayed()
            compose.onAllNodesWithText("247 g", substring = true).assertCountEquals(1)
            preview("history")
            compose.runOnIdle {
                assertEquals(2, requests.size)
                assertEquals(requests.first(), requests.last())
                assertEquals(Quantity.Weight.of(247), requests.last().second.quantity)
                assertEquals(WasteCategory.PLASTIC, requests.last().second.aiProvenance?.category)
            }
        } finally {
            detectionCanFinish.cancel()
            adviceCanFinish.cancel()
            saveCanFinish.cancel()
            cache.delete(photo)
            context.deleteSharedPreferences(preferences)
        }
    }

    private fun text(id: Int) = compose.activity.getString(id)
    private fun action(id: Int) = compose.onNodeWithText(text(id))
    private fun SemanticsNodeInteraction.reveal() = apply { if (!isDisplayed()) performScrollTo() }
    private fun waitFor(id: Int) {
        compose.waitUntil(20_000) { compose.onAllNodesWithText(text(id)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun focusAndActivateAdvice(automation: UiAutomation, label: String, packageName: String) {
        fun matches(node: AccessibilityNodeInfo) =
            node.text?.toString()?.contains(label) == true || node.contentDescription?.toString()?.contains(label) == true

        fun find(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            node ?: return null
            if (node.packageName == packageName && matches(node)) {
                return generateSequence(node) { it.parent }.firstOrNull { it.isVisibleToUser && it.isEnabled && it.isClickable }
            }
            for (i in 0 until node.childCount) find(node.getChild(i))?.let { return it }
            return null
        }
        fun candidate() = find(automation.rootInActiveWindow)
            ?: automation.windows.firstNotNullOfOrNull { find(it.root) }

        compose.waitUntil(8_000) { candidate() != null }
        val button = checkNotNull(candidate()) { "Advice button was not exposed in the scan window" }
        assertTrue("TalkBack target must support accessibility focus", button.isAccessibilityFocused ||
            button.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS))
        compose.waitUntil(8_000) { candidate()?.isAccessibilityFocused == true }
        assertTrue("TalkBack target must be actionable", checkNotNull(candidate()).performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }
}
