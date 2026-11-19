package com.example.wastenot.ui.main

import android.graphics.Bitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.Assume.assumeTrue
import java.io.File

class LogDetailNavigationTest {
    @get:Rule(order = -1) val navigationData = com.example.wastenot.NavigationFixtureRule()
    @get:Rule(order = 0) val fixtures = object : ExternalResource() {
        override fun before() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            com.example.wastenot.clearEditorDraftFixtures(context)
            listOf("dummy_session", "dummy_logs_dummy-owner", "appearance").forEach {
                context.getSharedPreferences(it, android.content.Context.MODE_PRIVATE).edit().clear().commit()
            }
        }
    }
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    private val tabs = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
    private fun tab(label: String) { compose.onNode(tabs and hasText(label)).performClick() }
    private fun ready() { compose.waitUntil(30_000) { compose.onAllNodesWithText("650 g").fetchSemanticsNodes().isNotEmpty() } }
    private fun open() {
        ready()
        compose.onNodeWithText("250 g").performScrollTo().performClick()
        compose.onNodeWithText("Log details").assertIsDisplayed()
        detailReady()
    }
    private fun detailReady() {
        compose.waitUntil(30_000) { compose.onAllNodes(hasText("Edit log") and hasClickAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun delete() { compose.onNodeWithText("Delete log", substring = false).performScrollTo().performClick() }

    @Test fun cancelThenConfirmedDeleteRefreshesHistoryTotalsAndXpAcrossRecreation() {
        open()
        capture("detail-light")
        compose.onNodeWithText("Dibuat ", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Diperbarui ", substring = true).performScrollTo().assertIsDisplayed()
        delete()
        compose.onNodeWithText("Your statistics and XP will be recalculated.", substring = true).assertIsDisplayed()
        capture("delete-light")
        compose.onNodeWithText("Cancel").performScrollTo().performClick()
        compose.onNodeWithText("Log details").assertIsDisplayed()
        delete()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Delete this log?").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Clear", substring = false).performScrollTo().performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Log deleted.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(tabs and hasText("History")).assertIsSelected()
        capture("history-after-delete")
        tab("Profile")
        compose.onNodeWithText("40 XP total").performScrollTo().assertIsDisplayed()
        tab("Stats")
        compose.onNodeWithText("1,15 kg").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("1 of 3 handling activities", substring = true).performScrollTo().assertIsDisplayed()
        tab("Home")
        compose.onNode(hasText("400 g") and !hasClickAction()).performScrollTo().assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(30_000) { compose.onAllNodes(hasText("400 g") and !hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
        tab("Profile")
        compose.onNodeWithText("40 XP total").performScrollTo().assertIsDisplayed()
    }

    @Test fun darkDetailAndDeleteCancelKeepTheRecordAvailable() {
        ready(); tab("Profile")
        compose.onNodeWithText("Settings").performScrollTo().performClick()
        compose.onNodeWithContentDescription("App theme").performScrollTo().performClick()
        compose.onNode(hasText("Dark") and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        tab("Home"); open()
        capture("detail-dark")
        delete(); capture("delete-dark")
        compose.onNodeWithText("Cancel").performScrollTo().performClick()
        compose.onNodeWithText("250 g").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Edit log").performScrollTo().assertIsEnabled()
        if (compose.onAllNodes(tabs).fetchSemanticsNodes().isEmpty()) compose.onNodeWithContentDescription("Back").performClick()
        tab("Profile")
        compose.onNodeWithText("50 XP total").performScrollTo().assertIsDisplayed()
    }

    @Test fun sameRecordAcrossTwoTabStacksAndDoubleTapKeepBackNavigationCorrect() {
        ready()
        compose.onNodeWithText("250 g").performScrollTo().performTouchInput { doubleClick() }
        detailReady()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("650 g").performScrollTo().assertIsDisplayed()
        open()
        val retainedInHome = compose.onAllNodes(tabs).fetchSemanticsNodes().isNotEmpty()
        if (!retainedInHome) compose.onNodeWithContentDescription("Back").performClick()
        tab("History")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("250 g"))
        compose.onNodeWithText("250 g").performClick()
        detailReady()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNode(tabs and hasText("History")).assertIsSelected()
        tab("Home")
        if (retainedInHome) {
            compose.onNodeWithText("Log details").assertIsDisplayed()
            compose.onNodeWithContentDescription("Back").performClick()
        }
        compose.onNodeWithText("650 g").performScrollTo().assertIsDisplayed()
    }

    @Test fun wideDeleteReturnsToHistoryListWhenHistoryRetainsAnotherDetail() {
        assumeTrue(compose.activity.resources.configuration.screenWidthDp >= 600)
        ready()
        tab("History")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("400 g"))
        compose.onNodeWithText("400 g").performClick()
        detailReady()
        tab("Home")
        open()
        delete()
        compose.onNodeWithText("Clear", substring = false).performScrollTo().performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Log deleted.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(tabs and hasText("History")).assertIsSelected()
        compose.onNodeWithText("Log details").assertDoesNotExist()
        compose.onNodeWithText("Log deleted.").assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("400 g"))
        compose.onNodeWithText("400 g").assertIsDisplayed()
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
