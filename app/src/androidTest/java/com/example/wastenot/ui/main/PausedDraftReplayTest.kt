package com.example.wastenot.ui.main

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.example.wastenot.LogEditor
import com.example.wastenot.MainActivity
import com.example.wastenot.data.dummy.DummyActivityStore
import com.example.wastenot.data.local.LocalEditorDraftStore
import com.example.wastenot.data.network.*
import com.example.wastenot.model.*
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource

class PausedDraftReplayTest {
    private val requestId = UUID.randomUUID().toString()
    @get:Rule(order = -1) val navigationData = com.example.wastenot.NavigationFixtureRule()
    @get:Rule(order = 0) val fixture = object : ExternalResource() {
        override fun before() {
            resetDraftAccountFixture()
            val today = LocalDate.now(ZoneId.of("Asia/Jakarta"))
            val form = EditorDraft.from(null, today).apply {
                change(EditorField.QUANTITY, values.copy(category = WasteCategory.PLASTIC, method = DisposalMethod.RECYCLE,
                    quantity = QuantityInput("250"), notes = "Permintaan sebelum restart"))
            }
            val input = checkNotNull(form.prepareSubmission(today))
            form.submissionFailed(ApiFailureKind.NETWORK)
            form.submissionFailed(ApiFailureKind.RATE_LIMITED)
            runTest {
                val source = DummyActivityStore(draftTestContext.getSharedPreferences("dummy_logs_dummy-owner", Context.MODE_PRIVATE), ZoneId.of("Asia/Jakarta"))
                assertTrue(source.create(input, requestId) is ApiResult.Success)
                assertTrue(LocalEditorDraftStore.create(draftTestContext, "dummy-owner")
                    .write(form.stored(LogEditor(null, requestId), MainTab.GUIDE).copy(paused = true)) is ApiResult.Success)
            }
        }
    }
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    private fun waitFor(text: String) { compose.waitUntil(30_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }

    @Test fun closedUnknownDraftResumesAndAnAlreadyCommittedRequestDoesNotCreateADuplicate() {
        waitFor("Continue draft")
        compose.onNodeWithText("How you handled it").assertDoesNotExist()
        compose.onNodeWithContentDescription("Log or scan waste").performClick()
        compose.onNodeWithText("You have an unfinished draft").assertIsDisplayed()
        compose.onNodeWithTag("capture-sheet").assertDoesNotExist()
        compose.onNode(hasText("Cancel") and hasAnyAncestor(isDialog())).performScrollTo().performClick()
        compose.onNodeWithText("Continue draft").performScrollTo().performClick()
        waitFor("Draft restored. Check your entries before saving.")
        compose.onNodeWithContentDescription("Weight").performScrollTo().assertIsNotEnabled().assertTextContains("250")
        compose.onNodeWithContentDescription("Notes (optional)").performScrollTo().assertIsNotEnabled().assertTextContains("Permintaan sebelum restart")
        assertEquals(requestId, readDraftFixture()!!.requestId)
        captureDraftFixture("draft-paused-resumed")
        compose.onNodeWithText("Try again").performScrollTo().performClick()
        waitFor("Your log was already saved. No extra XP.")
        assertNull(readDraftFixture())
        runTest {
            val source = DummyActivityStore(draftTestContext.getSharedPreferences("dummy_logs_dummy-owner", Context.MODE_PRIVATE), ZoneId.of("Asia/Jakarta"))
            assertEquals(6, (source.load() as ApiResult.Success).value.size)
        }
        captureDraftFixture("draft-replayed-once")
    }

    @Test fun logoutWarnsThenClearsTheDraftWithoutDeletingStoredRecords() {
        waitFor("Continue draft")
        compose.onNode(hasText("Profile") and hasClickAction()).performClick()
        compose.onNodeWithText("Sign out").performScrollTo().performClick()
        compose.onNodeWithText("Unsaved drafts on this device", substring = true).assertExists()
        compose.onNode(hasText("Sign out") and hasClickAction() and hasAnyAncestor(isDialog())).performScrollTo().performClick()
        waitFor("Sign in")
        assertNull(readDraftFixture())
        runTest {
            val source = DummyActivityStore(draftTestContext.getSharedPreferences("dummy_logs_dummy-owner", Context.MODE_PRIVATE), ZoneId.of("Asia/Jakarta"))
            assertEquals(6, (source.load() as ApiResult.Success).value.size)
        }
        captureDraftFixture("draft-logout-cleared")
    }

    @Test fun closingAResumedFollowupFailureKeepsTheDraftForReviewAgain() {
        waitFor("Continue draft")
        compose.onNodeWithText("Continue draft").performScrollTo().performClick()
        waitFor("Draft restored. Check your entries before saving.")
        compose.onNodeWithText("Last attempt: Too many requests.", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Close the form?").assertIsDisplayed()
        compose.onNodeWithText("Closing the form doesn't cancel a request", substring = true).assertExists()
        compose.onNodeWithText("Discard changes").assertDoesNotExist()
        captureDraftFixture("draft-followup-close-dialog")
        compose.onNode(hasText("Close form") and hasClickAction()).performScrollTo().performClick()
        compose.waitUntil(10_000) { readDraftFixture()?.paused == true }
        compose.onNode(hasText("Guide") and hasClickAction()).assertIsSelected()
        val saved = checkNotNull(readDraftFixture())
        assertEquals(requestId, saved.requestId)
        assertTrue(saved.paused)
        assertTrue(saved.restoreForm().outcomeUnknown)
        assertEquals(ApiFailureKind.RATE_LIMITED, saved.restoreForm().submissionError)
        compose.onNode(hasText("Home") and hasClickAction()).performClick()
        waitFor("Continue draft")
    }
}
