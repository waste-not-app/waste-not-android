package com.example.wastenot.ui.main

import android.content.Context
import android.graphics.Bitmap
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import java.io.File

class EditorDesignNavigationTest {
    @get:Rule(order = -1) val navigationData = com.example.wastenot.NavigationFixtureRule()
    @get:Rule(order = 0) val fixtures = object : ExternalResource() {
        override fun before() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            com.example.wastenot.clearEditorDraftFixtures(context)
            listOf("dummy_session", "dummy_logs_dummy-owner", "appearance").forEach {
                check(context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit())
            }
        }
    }
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    private val tabs = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
    private fun waitFor(text: String) {
        compose.waitUntil(30_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun lightEditorValidatesOnBlurRestoresDraftAndSavesThroughDetail() = journey(false)
    @Test fun darkEditorValidatesOnBlurRestoresDraftAndSavesThroughDetail() = journey(true)

    @Test fun storageFailureKeepsTheDraftAcrossRecreationAndAllowsCorrection() {
        waitFor("650 g")
        compose.onNodeWithContentDescription("Log or scan waste").performClick()
        compose.onNode(hasText("Log manually") and hasAnyAncestor(hasTestTag("capture-sheet"))).assertIsDisplayed().performClick()
        compose.onNodeWithText("Recycle").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Category").performScrollTo().performClick()
        compose.onNode(hasText("Plastic") and hasAnyAncestor(isPopup())).performClick()
        compose.onNodeWithContentDescription("Weight").performScrollTo().performTextReplacement("250")
        compose.onNodeWithContentDescription("Notes (optional)").performScrollTo().performTextReplacement("Entry that failed to save")
        val preferences = InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("dummy_logs_dummy-owner", Context.MODE_PRIVATE)
        val original = checkNotNull(preferences.getString("records", null))
        try {
            compose.runOnIdle { check(preferences.edit().putString("records", "{").commit()) }
            compose.onNodeWithText("Save log").performScrollTo().performClick()
            val failure = "Couldn't save the log on this device. Your entries are still here; try again."
            waitFor(failure)
            compose.onNodeWithContentDescription("Notes (optional)").assertIsEnabled().assertTextContains("Entry that failed to save")
            compose.activityRule.scenario.recreate()
            compose.waitForIdle()
            compose.onNodeWithText(failure).assertExists()
            compose.onNodeWithContentDescription("Weight").assertTextContains("250")
            compose.onNodeWithContentDescription("Notes (optional)").assertIsEnabled().assertTextContains("Entry that failed to save")
            compose.runOnIdle {
                assertEquals("{", preferences.getString("records", null))
                check(preferences.edit().putString("records", original).commit())
            }
            compose.onNodeWithContentDescription("Notes (optional)").performScrollTo().performTextReplacement("Koreksi eksplisit")
            compose.onNodeWithText("Save log").performScrollTo().performClick()
            waitFor("+10 XP for this new activity.")
            compose.runOnIdle { assertEquals(6, org.json.JSONArray(preferences.getString("records", null)).length()) }
        } finally {
            if (preferences.getString("records", null) == "{") check(preferences.edit().putString("records", original).commit())
        }
    }

    private fun journey(dark: Boolean) {
        waitFor("650 g")
        compose.onNode(tabs and hasText("Profile")).performClick()
        compose.onNodeWithText("Settings").performScrollTo().performClick()
        compose.onNodeWithContentDescription("App theme").performScrollTo().performClick()
        compose.onNode(hasText(if (dark) "Dark" else "Light") and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNode(tabs and hasText("Home")).performClick()
        compose.onNodeWithContentDescription("Log or scan waste").performClick()
        compose.onNode(hasText("Log manually") and hasAnyAncestor(hasTestTag("capture-sheet"))).assertIsDisplayed().performClick()
        val mode = if (dark) "dark" else "light"
        capture("editor-create-$mode")
        compose.onNodeWithText("Choose a method.").assertDoesNotExist()
        compose.onNodeWithText("Enter a weight or an item count.").assertDoesNotExist()
        val quantity = compose.onNodeWithContentDescription("Weight")
        quantity.performScrollTo().performTouchInput { click() }
        quantity.performTextReplacement("1.5")
        compose.onNodeWithContentDescription("Notes (optional)").performScrollTo().performClick()
        compose.onNodeWithText("Use numbers that fit the unit; items and grams have no decimals.").assertExists()
        compose.onNodeWithText("Choose a category.").assertDoesNotExist()
        compose.onNodeWithText("Save log").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("Reduce") and isFocused()).fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithText("Reduce").assertIsFocused().assertIsDisplayed()
        compose.onAllNodes(isSelected() and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).assertCountEquals(0)
        compose.onNodeWithText("Reduce").performKeyInput { pressKey(Key.Tab) }
        compose.onNodeWithText("Reuse").assertIsFocused().assertIsDisplayed()
        listOf("Reduce", "Reuse", "Recycle", "Trash").forEach { label ->
            compose.onNodeWithText(label).performScrollTo().assertHeightIsAtLeast(48.dp).performClick().assertIsSelected()
            if (label == "Reduce") {
                compose.onNodeWithContentDescription("Amount avoided").assertExists()
                compose.onNodeWithText("Tracked separately from waste you handled.").assertExists()
            }
        }
        compose.onNodeWithText("Recycle").performScrollTo().performClick()
        compose.onNodeWithText("Save log").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasContentDescription("Category") and isFocused()).fetchSemanticsNodes().size == 1 }
        compose.onNodeWithContentDescription("Category").assertIsFocused().assertIsDisplayed()
        compose.onNodeWithContentDescription("Category").performScrollTo().performClick()
        compose.onNode(hasText("Plastic") and hasAnyAncestor(isPopup())).performClick()
        compose.onNodeWithText("Save log").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasContentDescription("Weight") and isFocused()).fetchSemanticsNodes().size == 1 }
        quantity.assertIsFocused().assertIsDisplayed()
        quantity.performScrollTo().performTextReplacement("250")
        compose.onNodeWithContentDescription("Notes (optional)").performScrollTo().performTextReplacement("Private note")
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithText("Recycle").performScrollTo().assertIsSelected()
        compose.onNodeWithContentDescription("Weight").performScrollTo().assertTextContains("250")
        compose.onNodeWithContentDescription("Notes (optional)").performScrollTo().assertTextContains("Private note")
        compose.onNodeWithText("Asia/Jakarta · up to 30 days ago.").assertExists()
        compose.onNodeWithText("Save log").performScrollTo().assertIsDisplayed()
        capture("editor-create-end-$mode")
        compose.onNodeWithText("Save log").performClick()
        waitFor("+10 XP for this new activity.")
        compose.onNodeWithText("View log").performScrollTo().performClick()
        compose.waitUntil(30_000) { compose.onAllNodes(hasText("Edit log") and hasClickAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Edit log").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("Save changes") and hasClickAction()).fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithText("Save changes").assertIsNotEnabled()
        capture("editor-edit-$mode")
        compose.onNodeWithContentDescription("Unit").performScrollTo().performClick()
        capture("editor-unit-$mode")
        compose.onNodeWithText("Items").performClick()
        val count = compose.onNodeWithContentDescription("Item count")
        assertEquals("", count.fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        count.performScrollTo().performTextReplacement("2")
        compose.runOnUiThread {
            compose.activity.getSystemService(InputMethodManager::class.java)
                .hideSoftInputFromWindow(compose.activity.window.decorView.windowToken, 0)
        }
        compose.onNodeWithText("Save changes").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText("Edits update your statistics but don't add XP.").assertExists()
        capture("editor-edit-end-$mode")
        compose.onNodeWithText("Save changes").performClick()
        waitFor("Your log was already saved. No extra XP.")
        compose.onNodeWithText("+10 XP", substring = true).assertDoesNotExist()
        compose.onNodeWithText("View log").performScrollTo().performClick()
        compose.onNodeWithText("2 items").performScrollTo().assertIsDisplayed()
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
