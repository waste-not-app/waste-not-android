package com.example.wastenot.ui.main

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.example.wastenot.MainActivity
import com.example.wastenot.data.dummy.DummyActivityStore
import com.example.wastenot.data.local.LocalEditorDraftStore
import com.example.wastenot.data.network.ApiResult
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource

class CorruptDraftRecoveryTest {
    @get:Rule(order = -1) val navigationData = com.example.wastenot.NavigationFixtureRule()
    @get:Rule(order = 0) val fixture = object : ExternalResource() {
        override fun before() {
            resetDraftAccountFixture()
            check(draftTestContext.getSharedPreferences(LocalEditorDraftStore.preferencesName("dummy-owner"), Context.MODE_PRIVATE)
                .edit().putString("draft", "{").commit())
        }
    }
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    private fun waitFor(text: String) { compose.waitUntil(30_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }

    @Test fun corruptDraftShowsRecoveryBeforeAnyBlankEditorAndOnlyExplicitDiscardClearsIt() {
        waitFor("Couldn't open your draft")
        compose.onNodeWithText("How you handled it").assertDoesNotExist()
        compose.onNodeWithText("Home").assertDoesNotExist()
        captureDraftFixture("draft-corrupt-recovery")
        compose.onNodeWithText("Discard saved draft").performScrollTo().performClick()
        compose.onNodeWithText("Discard saved draft?").assertIsDisplayed()
        compose.onNode(hasText("Cancel") and hasAnyAncestor(isDialog())).performScrollTo().performClick()
        compose.onNodeWithText("Couldn't open your draft").assertExists()
        assertEquals("{", draftTestContext.getSharedPreferences(LocalEditorDraftStore.preferencesName("dummy-owner"), Context.MODE_PRIVATE).getString("draft", null))
        compose.onNodeWithText("Discard saved draft").performScrollTo().performClick()
        compose.onNode(hasText("Discard saved draft") and hasAnyAncestor(isDialog())).performScrollTo().performClick()
        waitFor("650 g")
        assertNull(readDraftFixture())
        runTest {
            val source = DummyActivityStore(draftTestContext.getSharedPreferences("dummy_logs_dummy-owner", Context.MODE_PRIVATE), ZoneId.of("Asia/Jakarta"))
            assertEquals(5, (source.load() as ApiResult.Success).value.size)
        }
        captureDraftFixture("draft-corrupt-cleared")
    }
}
