package com.example.wastenot.ui.main

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.wastenot.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import java.io.File

class AppNavigationTest {
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

    private fun preview() {
        compose.waitUntil(30_000) { compose.onAllNodes(tabs).fetchSemanticsNodes().size == 5 }
        compose.waitUntil(30_000) { compose.onAllNodesWithText("650 g").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        compose.onAllNodes(tabs).assertCountEquals(5)
    }

    private fun openEditor() {
        compose.onNodeWithContentDescription("Log or scan waste").performClick()
        compose.onNode(hasText("Log manually") and hasAnyAncestor(hasTestTag("capture-sheet"))).assertIsDisplayed().performClick()
    }

    private fun tab(label: String) {
        compose.onNode(tabs and hasText(label)).performClick().assertIsSelected()
    }

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.waitForIdle(300, 5_000)
        val file = File(instrumentation.targetContext.cacheDir, "qa/$name.png")
        file.parentFile!!.mkdirs()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }

    @Test fun datasetFiveTabsManualActionAndThemeWorkTogether() {
        preview()
        tab("Profile")
        compose.onNodeWithText("Settings").performScrollTo().performClick()
        compose.onNodeWithContentDescription("App theme").performScrollTo().performClick()
        compose.onNode(hasText("Light") and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        tab("Home")
        compose.onNodeWithText("650 g").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("2 items avoided").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("1 of 2 handling activities", substring = true).performScrollTo().assertIsDisplayed()
        capture("home")
        listOf("Home", "History", "Guide", "Stats", "Profile").forEach { label ->
            tab(label)
            compose.onAllNodes(tabs).fetchSemanticsNodes().forEach { node ->
                compose.onNode(hasText(node.config[SemanticsProperties.Text].joinToString { it.text }) and tabs)
                    .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
            }
            openEditor()
            compose.onAllNodes(tabs).assertCountEquals(0)
            compose.onNodeWithText("Log activity").assertIsDisplayed()
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNode(tabs and hasText(label)).assertIsSelected()
        }
        tab("Stats")
        compose.onNodeWithText("1.4 kg").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("2 of 4 handling activities", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("30 days").performScrollTo().performClick()
        tab("Profile"); tab("Stats")
        compose.onNodeWithText("30 days").assertIsSelected()
        tab("Profile")
        compose.onNodeWithText("Settings").performScrollTo().performClick()
        compose.onNodeWithContentDescription("App theme").performScrollTo().performClick()
        compose.onNode(hasText("Dark") and hasAnyAncestor(isDialog())).performClick().assertIsSelected()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        tab("Home")
        compose.onNodeWithText("650 g").performScrollTo().assertIsDisplayed()
        capture("home-dark")
    }

    @Test fun backAtHomeExitsWithoutOpeningAuth() {
        preview()
        compose.onNode(tabs and hasText("Home")).assertIsSelected()
        val activity = compose.activity
        compose.runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(5_000) { activity.isFinishing }
    }

    @Test fun edgeBackGestureUsesDirtyDraftPolicy() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("predictiveGesture") == "true")
        assumeTrue(android.os.Build.VERSION.SDK_INT >= 36)
        assertEquals(2, android.provider.Settings.Secure.getInt(compose.activity.contentResolver, "navigation_mode"))
        preview()
        openEditor()
        compose.onNodeWithContentDescription("Notes (optional)")
            .performScrollTo().performTextReplacement("Gesture draft")
        fun imeVisible() = compose.runOnUiThread {
            ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        compose.waitUntil(8_000) { imeVisible() }
        capture("gesture-keyboard")
        val height = compose.runOnUiThread { compose.activity.window.decorView.height }
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun gestureBack() {
            automation.executeShellCommand("input touchscreen swipe 1 ${height / 2} 600 ${height / 2} 500").use {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
            }
        }
        gestureBack()
        compose.waitUntil(5_000) { !imeVisible() }
        compose.onNodeWithText("Gesture draft").assertExists()
        compose.onNodeWithText("Discard changes?").assertDoesNotExist()
        compose.onAllNodes(tabs).assertCountEquals(0)
        capture("gesture-ime-hidden")
        gestureBack()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Discard changes?").fetchSemanticsNodes().isNotEmpty() }
        capture("gesture-dirty-dialog")
        compose.onNodeWithText("Keep editing").performClick()
        compose.onNodeWithText("Gesture draft").assertExists()
        compose.onAllNodes(tabs).assertCountEquals(0)
    }

    @Test fun invalidPasswordStaysFocusedAboveKeyboardWithoutCallingAccountService() {
        preview(); tab("Profile")
        compose.onNodeWithText("Sign out").performScrollTo().performClick()
        compose.onNode(hasText("Sign out") and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("New here? Create an account").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasContentDescription("Email") and hasSetTextAction()).performScrollTo().performClick().performTextReplacement("fixture@example.invalid")
        val password = compose.onNode(hasContentDescription("Password") and hasSetTextAction())
        password.performScrollTo().performClick().performTextReplacement("short")
        compose.waitUntil(8_000) { compose.runOnUiThread {
            ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
        } }
        password.performImeAction()
        password.assertIsFocused().assertIsDisplayed()
        compose.onNodeWithText("Passwords need 12 to 128 characters, including spaces.").assertExists()
        compose.onNodeWithText("Sign in", substring = false).performScrollTo().assertIsDisplayed()
        compose.onAllNodes(tabs).assertCountEquals(0)
        capture("login-keyboard-error")
        val visible = Rect()
        compose.runOnUiThread { compose.activity.window.decorView.getWindowVisibleDisplayFrame(visible) }
        val button = compose.onNodeWithText("Sign in", substring = false).fetchSemanticsNode().boundsInWindow
        val viewport = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).fetchSemanticsNode()
        val range = viewport.config[SemanticsProperties.VerticalScrollAxisRange]
        val window = compose.runOnUiThread {
            val view = compose.activity.window.decorView
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            "decor=${view.width}x${view.height} at ${location.toList()}, ime=${ViewCompat.getRootWindowInsets(view)?.getInsets(WindowInsetsCompat.Type.ime())}, softInput=${compose.activity.window.attributes.softInputMode}"
        }
        assertTrue("Submit $button must stay above IME $visible; viewport=${viewport.boundsInWindow}, scroll=${range.value()}/${range.maxValue()}, $window", button.bottom <= visible.bottom + 1f)
    }

    @Test fun historyFilterAndRangeSurviveDetailTabsAndRotation() {
        preview(); tab("History")
        capture("history-native")
        compose.onNodeWithContentDescription("Filter history").performClick()
        capture("history-filter-native")
        compose.onNodeWithContentDescription("Category").performScrollTo().performClick()
        compose.onNode(hasText("Plastic") and hasAnyAncestor(isPopup())).performClick()
        compose.onNodeWithText("Apply filters").performScrollTo().performClick()
        capture("history-filtered-native")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("250 g"))
        compose.onNodeWithText("250 g").performScrollTo().performClick()
        capture("history-detail")
        compose.onNodeWithText("Log details").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Clear filters"))
        compose.onNodeWithText("Clear filters").assertIsDisplayed()
        tab("Guide"); tab("History")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Clear filters"))
        compose.onNodeWithText("Clear filters").assertIsDisplayed()
        tab("Stats")
        compose.onNodeWithText("30 days").performScrollTo().performClick()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithText("30 days").assertIsSelected()
        tab("History")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Clear filters"))
        compose.onNodeWithText("Clear filters").assertIsDisplayed()
        back()
        compose.onNode(tabs and hasText("Home")).assertIsSelected()
    }

    @Test fun dirtyBackCreateAndEditKeepExclusiveQuantityAndDoNotAddEditXp() {
        preview()
        openEditor()
        compose.onNodeWithText("Recycle").performScrollTo().performClick()
        back()
        compose.onNodeWithText("Discard changes?").assertIsDisplayed()
        compose.onNodeWithText("Keep editing").performClick()
        compose.onNodeWithText("Recycle").assertIsSelected()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Discard changes").performClick()
        openEditor()
        compose.onNodeWithText("Recycle").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Category").performScrollTo().performClick()
        compose.onNode(hasText("Plastic") and hasAnyAncestor(isPopup())).performClick()
        compose.onNodeWithContentDescription("Unit").performScrollTo().performClick()
        compose.onNodeWithText("kg").performClick()
        compose.onNodeWithContentDescription("Weight").performTextReplacement("0,25")
        compose.onNodeWithContentDescription("Notes (optional)").performScrollTo().performTextReplacement("QA private sample")
        compose.onNodeWithText(com.example.wastenot.model.WasteFormat.activityDate(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Jakarta")))).performScrollTo().performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Save log").performScrollTo().performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithText("+10 XP for this new activity.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("View log").performScrollTo().performClick()
        compose.onNodeWithText("Log details").assertIsDisplayed()
        compose.onNodeWithText("QA private sample").performScrollTo().assertIsDisplayed()
        compose.waitUntil(30_000) { compose.onAllNodes(hasText("Edit log") and hasClickAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Edit log").performScrollTo().performClick()
        capture("editor-edit")
        compose.onNodeWithContentDescription("Unit").performScrollTo().performClick()
        compose.onNodeWithText("Items").performClick()
        val quantity = compose.onNodeWithContentDescription("Item count")
        assertEquals("", quantity.fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        quantity.performTextReplacement("2")
        compose.onNodeWithText("Save changes").performScrollTo().performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Your log was already saved. No extra XP.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("+10 XP", substring = true).assertDoesNotExist()
        compose.onNodeWithText("View log").performScrollTo().performClick()
        compose.onNodeWithText("2 items").performScrollTo().assertIsDisplayed()
        val retainedDetail = compose.onAllNodes(tabs).fetchSemanticsNodes().isNotEmpty()
        if (!retainedDetail) compose.onNodeWithContentDescription("Back").performClick()
        tab("Profile")
        compose.onNodeWithText("60 XP total").performScrollTo().assertIsDisplayed()
        tab("Home")
        if (retainedDetail) {
            compose.onNodeWithText("Log details").assertIsDisplayed()
            compose.onNodeWithContentDescription("Back").performClick()
        }
        compose.onNodeWithText("650 g").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("2 items handled").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("2 items avoided").performScrollTo().assertIsDisplayed()
        tab("Profile")
        compose.onNodeWithText("Sign out").performScrollTo().performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("60 XP total").assertExists()
        compose.onNodeWithText("Sign out").performClick()
        compose.onNode(hasText("Sign out") and hasAnyAncestor(isDialog())).performClick()
        compose.waitForIdle()
        compose.onAllNodes(tabs).assertCountEquals(0)
        compose.onNodeWithText("Sign in").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("QA private sample").assertDoesNotExist()
        val activity = compose.activity
        compose.runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(5_000) { activity.isFinishing }
    }
}
