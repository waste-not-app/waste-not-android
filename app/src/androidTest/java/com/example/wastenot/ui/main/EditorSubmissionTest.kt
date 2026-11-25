package com.example.wastenot.ui.main

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
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
            quantity = QuantityInput("0,25", QuantityUnit.KILOGRAM), notes = "  Original entry  "))
    }

    @Test fun uncertainLightSubmissionRestoresAndRetriesTheSameInput() = uncertain(false)
    @Test fun uncertainDarkSubmissionRestoresAndRetriesTheSameInput() = uncertain(true)
    @Test fun followupRejectionKeepsTheLightDraftFrozenAcrossRestoration() = followupRejection(false)
    @Test fun followupRejectionKeepsTheDarkDraftFrozenAcrossRestoration() = followupRejection(true)

    @Test fun normalAiEditorKeepsAttributionWithoutRedundantManualAction() {
        val form = aiDraft()
        compose.setContent { EditorHost(false) {
            LogEditorScreen(form, today, "Asia/Jakarta", false, PaddingValues(), {}, {}, {},
                scanPrefilled = true)
        } }
        compose.onNodeWithText("Continue as a manual log").assertDoesNotExist()
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(
            com.example.wastenot.R.string.ai_provenance_attached)).assertIsDisplayed()
        val save = compose.onNodeWithText("Save log")
        save.assertIsDisplayed().assertHeightIsAtLeast(52.dp)
        compose.onNodeWithText("Date and notes").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Notes (optional)").performScrollTo().assertIsDisplayed()
        save.assertIsDisplayed()
        compose.runOnIdle {
            assertNotNull(form.values.aiProvenance)
            form.change(EditorField.METHOD, form.values.copy(method = DisposalMethod.REDUCE))
        }
        compose.onNodeWithText("Continue as a manual log").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertNull(form.values.aiProvenance)
            assertEquals(DisposalMethod.REDUCE, form.values.method)
        }
        compose.onNodeWithText("Continue as a manual log").assertDoesNotExist()
    }

    @Test fun expiredAiEditorOffersExplicitManualRecoveryWithoutChangingFields() {
        val form = aiDraft().apply {
            change(EditorField.QUANTITY, values.copy(quantity = QuantityInput("250", QuantityUnit.GRAM)))
            prepareSubmission(today)
            submissionFailed(ApiFailureKind.AI_EXPIRED)
        }
        val before = form.values
        compose.setContent { EditorHost(true) {
            LogEditorScreen(form, today, "Asia/Jakarta", false, PaddingValues(), {}, {}, {},
                scanPrefilled = true)
        } }
        compose.onNodeWithText("Continue as a manual log").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(before.copy(aiProvenance = null), form.values)
            assertNull(form.prepareSubmission(today)!!.aiProvenance)
        }
    }

    @Test fun scanStartsAtAnEmptyAmountAndShortEditorStillRequiresAnExplicitSave() {
        val form = aiDraft()
        val writes = mutableListOf<ActivityInput>()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                EditorHost(false) {
                    Box(Modifier.size(320.dp, 300.dp)) {
                        LogEditorScreen(form, today, "Asia/Jakarta", false, PaddingValues(), { writes.add(it) },
                            {}, {}, scanPrefilled = true)
                    }
                }
            }
        }
        val weight = compose.onNodeWithContentDescription("Weight")
        weight.assertIsDisplayed().assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithText("Reduce").assertDoesNotExist()
        val save = compose.onNodeWithText("Save log")
        save.performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        weight.assertIsFocused().assertIsDisplayed().performTextReplacement("42")
        compose.runOnIdle { assertTrue(writes.isEmpty()) }
        save.performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(Quantity.Weight.of(42), writes.single().quantity)
            assertEquals(DisposalMethod.RECYCLE, writes.single().method)
            assertEquals(WasteCategory.PLASTIC, writes.single().category)
            assertEquals(form.values.aiProvenance, writes.single().aiProvenance)
        }
    }

    @Test fun rejectedCategoryReopensCollapsedScanChoicesAndFocusesTheField() {
        val form = aiDraft().apply {
            change(EditorField.QUANTITY, values.copy(quantity = QuantityInput("250", QuantityUnit.GRAM)))
        }
        compose.setContent { EditorHost(false) {
            LogEditorScreen(form, today, "Asia/Jakarta", false, PaddingValues(),
                { fail("Changing the scan category must not save with provenance") }, {}, {}, scanPrefilled = true)
        } }
        val choices = compose.onNodeWithText("Method and category")
        val category = compose.onNodeWithContentDescription("Category")
        choices.assertTextContains("Recycle · Plastic").performScrollTo().performClick()
        category.performScrollTo().performClick()
        compose.onNodeWithText("Glass").performClick()
        choices.performScrollTo().performClick()
        category.assertDoesNotExist()
        val save = compose.onNodeWithText("Save log")
        if (!save.isDisplayed()) save.performScrollTo()
        save.assertIsDisplayed().performClick()
        category.assertIsFocused().assertIsDisplayed().assertTextContains("Glass")
    }

    private fun aiDraft() = EditorDraft.from(null, today).apply {
        applyScan(com.example.wastenot.LogEditor(null, "ai-editor-ui", scanCategory = "PLASTIC",
            scanMethod = "RECYCLE", scanRecommendationId = "00000000-0000-4000-8000-000000000002"))
    }

    private fun followupRejection(dark: Boolean) {
        val restoration = StateRestorationTester(compose)
        val received = mutableListOf<ActivityInput>()
        lateinit var current: EditorDraft
        restoration.setContent {
            current = rememberSaveable(saver = EditorDraft.Saver) { draft() }
            EditorHost(dark) {
                LogEditorScreen(current, today, "Asia/Jakarta", false, PaddingValues(), { input ->
                    received.add(input)
                    current.submissionFailed(when (received.size) {
                        1 -> ApiFailureKind.TIMEOUT
                        2 -> ApiFailureKind.RATE_LIMITED
                        else -> ApiFailureKind.VALIDATION
                    })
                }, { fail("An unresolved write must retain its original request") }, {}, Modifier.fillMaxSize())
            }
        }
        compose.onNodeWithText("Save log").performScrollTo().performClick()
        compose.onNodeWithText("Try again").performScrollTo().performClick()
        assertReadableBanner("Last attempt: Too many requests.",
            "submission-followup-banner-${if (dark) "dark" else "light"}")
        assertFrozen()
        assertActionInViewport("Try again")
        capture("submission-followup-action-${if (dark) "dark" else "light"}")
        restoration.emulateSavedInstanceStateRestore()
        assertFrozen()
        compose.onNodeWithText("Try again").performScrollTo().performClick()
        assertReadableBanner("Last attempt: Some entries weren't accepted.",
            "submission-followup-validation-${if (dark) "dark" else "light"}")
        assertFrozen()
        assertActionInViewport("Try again")
        compose.runOnIdle {
            assertEquals(3, received.size)
            assertTrue(received.all { it == received.first() })
            assertTrue(current.outcomeUnknown)
            assertEquals(ApiFailureKind.VALIDATION, current.submissionError)
        }
    }

    @Test fun confirmedSaveOffersCleanupWithoutClaimingTheWriteIsStillUnknown() {
        val form = draft().apply {
            prepareSubmission(today)
            submissionFailed(ApiFailureKind.NETWORK)
            submissionFailed(ApiFailureKind.RATE_LIMITED)
        }
        var writes = 0
        var cleanups = 0
        compose.setContent { EditorHost(false) {
            LogEditorScreen(form, today, "Asia/Jakarta", false, PaddingValues(), { writes++ }, {}, {},
                Modifier.fillMaxSize(), persistence = EditorPersistenceState(loading = false, ready = true, failure = DraftFailure.FINISH),
                saveConfirmed = true, onRetryPersistence = { cleanups++ })
        } }
        compose.onNodeWithText("We don't know whether this was saved.", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Last attempt:", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Try again").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, cleanups); assertEquals(0, writes) }
    }

    private fun uncertain(dark: Boolean) {
        val restoration = StateRestorationTester(compose)
        val busy = mutableStateOf(false)
        val received = mutableListOf<ActivityInput>()
        lateinit var current: EditorDraft
        restoration.setContent {
            current = rememberSaveable(saver = EditorDraft.Saver) { draft() }
            EditorHost(dark) {
                LogEditorScreen(current, today, "Asia/Jakarta", busy.value, PaddingValues(),
                    { received.add(it); busy.value = true }, { error("Unknown result must offer an explicit retry") }, {}, Modifier.fillMaxSize())
            }
        }
        compose.onNodeWithText("Save log").performScrollTo().performClick()
        compose.onNodeWithText("Saving…").assertIsNotEnabled()
        compose.onNodeWithText("Waiting for the save result.").assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        assertFrozen()
        compose.runOnIdle {
            assertEquals(1, received.size)
            assertEquals(Quantity.Weight.of(250), received.single().quantity)
            assertEquals("Original entry", received.single().notes)
            current.submissionFailed(ApiFailureKind.TIMEOUT)
            busy.value = false
        }
        compose.onNodeWithText("We don't know whether this was saved.", substring = true).performScrollTo().assertIsDisplayed()
        assertActionInViewport("Try again")
        capture("submission-unknown-${if (dark) "dark" else "light"}")
        restoration.emulateSavedInstanceStateRestore()
        assertFrozen()
        compose.onNodeWithContentDescription("Weight").assertTextContains("0,25")
        compose.onNodeWithContentDescription("Notes (optional)").assertTextContains("  Original entry  ")
        compose.runOnIdle { assertEquals(1, received.size) }
        compose.onNodeWithText("Try again").performScrollTo().performClick()
        compose.onNodeWithText("Saving…").assertIsNotEnabled()
        compose.onNodeWithText("Waiting for the save result.").assertIsDisplayed()
        compose.onNodeWithText("We don't know whether this was saved.", substring = true).assertDoesNotExist()
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
            LogEditorScreen(draft, today, "Asia/Jakarta", false, PaddingValues(),
                { fail("A rejected request must not be retried") }, { history++ }, {}, Modifier.fillMaxSize())
        } }
        assertFrozen()
        compose.onNodeWithText(if (error == ApiFailureKind.DELETED) "The log from this request was deleted" else "This request was already used for different entries.",
            substring = true).performScrollTo().assertIsDisplayed()
        assertActionInViewport("See history")
        capture("submission-${if (error == ApiFailureKind.DELETED) "deleted-light" else "conflict-dark"}")
        compose.onNodeWithText("Try again").assertDoesNotExist()
        compose.onNodeWithText("See history").performClick()
        compose.runOnIdle { assertEquals(1, history) }
    }

    @Test fun rejectedSubmissionKeepsAnEditableDraft() {
        val draft = draft()
        draft.prepareSubmission(today)
        draft.submissionFailed(ApiFailureKind.VALIDATION)
        compose.setContent { EditorHost(false) {
            LogEditorScreen(draft, today, "Asia/Jakarta", false, PaddingValues(), {}, {}, {}, Modifier.fillMaxSize())
        } }
        compose.onNodeWithContentDescription("Notes (optional)").performScrollTo().assertIsEnabled().performTextReplacement("Koreksi")
        compose.onNodeWithText("Save log").performScrollTo().assertIsEnabled()
        compose.runOnIdle { assertEquals("Koreksi", draft.prepareSubmission(today)!!.notes) }
    }

    private fun assertFrozen() {
        listOf("Reduce", "Reuse", "Recycle", "Trash").forEach { compose.onNodeWithText(it).assertIsNotEnabled() }
        listOf("Category", "Weight", "Unit", "Notes (optional)").forEach {
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

    private fun assertReadableBanner(text: String, name: String) {
        val node = compose.onNodeWithText(text, substring = true).performScrollTo().assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.isNotEmpty())
        assertFalse(layouts.any { it.hasVisualOverflow })
        capture(name)
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
