package com.example.wastenot.ui.main

import android.content.Context
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
import java.io.File

class SaveConfirmationNavigationTest {
    @get:Rule(order = -1) val navigationData = com.example.wastenot.NavigationFixtureRule()
    @get:Rule(order = 0) val fixtures = object : ExternalResource() {
        override fun before() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            com.example.wastenot.clearEditorDraftFixtures(context)
            listOf("dummy_session", "dummy_logs_dummy-owner", "appearance").forEach {
                context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
            }
        }
    }
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    private val tabs = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
    private fun tab(label: String) { compose.onNode(tabs and hasText(label)).performClick() }
    private fun waitFor(text: String) {
        compose.waitUntil(30_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun confirmedCreateSurvivesRecreationAndEditNeverAnnouncesNewXp() {
        waitFor("650 g")
        compose.onNodeWithText("250 g").performScrollTo().performClick()
        compose.waitUntil(30_000) { compose.onAllNodes(hasText("Edit log") and hasClickAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        if (compose.onAllNodes(tabs).fetchSemanticsNodes().isEmpty()) {
            compose.onNodeWithContentDescription("Back").performClick()
        }
        tab("History")
        compose.onNodeWithContentDescription("Log or scan waste").performClick()
        compose.onNode(hasText("Log manually") and hasAnyAncestor(hasTestTag("capture-sheet"))).assertIsDisplayed().performClick()
        compose.onNodeWithText("Recycle").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Category").performScrollTo().performClick()
        compose.onNode(hasText("Metal") and hasAnyAncestor(isPopup())).performClick()
        compose.onNodeWithContentDescription("Unit").performScrollTo().performClick()
        compose.onNodeWithText("Items").performClick()
        compose.onNodeWithContentDescription("Item count").performTextReplacement("3")
        compose.onNodeWithText("Save log").performScrollTo().performClick()
        waitFor("Log saved")
        capture("save-light-start")
        compose.onNodeWithText("+10 XP for this new activity.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Metal · Recycle").performScrollTo().assertIsDisplayed()
        capture("save-light")
        compose.activityRule.scenario.recreate()
        waitFor("Log saved")
        compose.onNodeWithText("+10 XP for this new activity.").performScrollTo().assertIsDisplayed()
        capture("save-restored")
        compose.onNodeWithText("Back to home").performScrollTo().performClick()
        compose.onNode(tabs and hasText("Home")).assertIsSelected()
        compose.onNodeWithText("Log details").assertDoesNotExist()
        compose.onNodeWithText("Log saved").assertDoesNotExist()
        tab("Profile")
        compose.onNodeWithText("60 XP total").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Settings").performScrollTo().performClick()
        compose.onNodeWithContentDescription("App theme").performScrollTo().performClick()
        compose.onNode(hasText("Dark") and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        tab("History")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("3 items"))
        compose.onNodeWithText("3 items").performClick()
        compose.waitUntil(30_000) { compose.onAllNodes(hasText("Edit log") and hasClickAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Edit log").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Notes (optional)").performScrollTo().performTextReplacement("Diperbaiki")
        compose.onNodeWithText("Save changes").performScrollTo().performClick()
        waitFor("Log saved")
        capture("save-edit-dark-start")
        compose.onNodeWithText("+10 XP", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Your log was already saved. No extra XP.").performScrollTo().assertIsDisplayed()
        capture("save-edit-dark")
        compose.onNodeWithText("View log").performScrollTo().performClick()
        compose.onNodeWithText("Log details").assertIsDisplayed()
        compose.onNodeWithText("Diperbaiki").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        tab("Profile")
        compose.onNodeWithText("60 XP total").performScrollTo().assertIsDisplayed()
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
