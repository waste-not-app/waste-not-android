package com.example.wastenot.ui.main

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.MainActivity
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource

class NavigationTransitionTest {
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

    @Test fun confirmedCreateDoesNotFlashALoadErrorWhileTheEditorExits() {
        create(false)
    }

    @Test fun darkConfirmedCreateKeepsItsFormDuringExit() = create(true)

    private fun create(dark: Boolean) {
        openEditor(dark)
        compose.onNodeWithText("Recycle").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Category").performScrollTo().performClick()
        compose.onNode(hasText("Plastic") and hasAnyAncestor(isPopup())).performClick()
        compose.onNodeWithContentDescription("Weight").performScrollTo().performTextReplacement("250")
        compose.onNodeWithText("Save log").performScrollTo()
        traceExit(if (dark) "create-dark" else "create", "+10 XP for this new activity.") {
            compose.onNodeWithText("Save log").performClick()
        }
    }

    @Test fun confirmedEditKeepsItsFormUntilTheUpdatedReceiptAppears() {
        home()
        compose.onNodeWithText("Recent activity").performScrollTo()
        val bodyScroll = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and !hasAnyDescendant(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)))
        val scrollMax = bodyScroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].maxValue()
        bodyScroll.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, scrollMax) }
        compose.waitForIdle()
        compose.onNodeWithText("250 g").performClick()
        compose.waitUntil(30_000) {
            compose.onAllNodes(hasText("Edit log") and hasClickAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasText("Edit log") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithContentDescription("Notes (optional)").performScrollTo().performTextReplacement("Perubahan saat audit")
        compose.onNodeWithText("Save changes").performScrollTo()
        traceExit("edit", "Your log was already saved. No extra XP.") {
            compose.onNodeWithText("Save changes").performClick()
        }
    }

    @Test fun cleanBackKeepsItsFormUntilHomeReturns() {
        openEditor()
        traceExit("clean-back", "650 g") { compose.onNodeWithContentDescription("Back").performClick() }
    }

    @Test fun dirtyEditorHeaderBackUsesTheCurrentDraft() {
        openEditor()
        compose.onNodeWithContentDescription("Notes (optional)").performScrollTo().performTextReplacement("Unsaved draft")
        compose.onNodeWithContentDescription("Back").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.onNodeWithText("Discard changes?").assertIsDisplayed()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.onNodeWithText("Discard changes?").assertDoesNotExist()
        compose.onNodeWithContentDescription("Notes (optional)").performScrollTo().assertTextContains("Unsaved draft")
    }

    @Test fun discardKeepsItsFormDuringExitAndTheNextDraftStartsEmpty() {
        openEditor()
        compose.onNodeWithContentDescription("Notes (optional)").performScrollTo().performTextReplacement("Unsaved draft")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Keep editing").performClick()
        compose.onNodeWithContentDescription("Notes (optional)").assertTextContains("Unsaved draft")
        compose.onNodeWithContentDescription("Back").performClick()
        traceExit("discard-back", "650 g") {
            compose.onNode(hasText("Discard changes") and hasAnyAncestor(isDialog())).performClick()
        }
        openEditor()
        assertEquals("", compose.onNodeWithContentDescription("Notes (optional)").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        compose.onNodeWithText("Recycle").assertIsNotSelected()
        compose.onNodeWithText("Choose a category").assertExists()
    }

    private fun home() {
        compose.waitUntil(30_000) { compose.onAllNodesWithText("650 g").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun openEditor(dark: Boolean = false) {
        home()
        if (dark) {
            val tab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
            compose.onNode(tab and hasText("Profile")).performClick()
            compose.onNodeWithText("Settings").performScrollTo().performClick()
            compose.onNodeWithContentDescription("App theme").performScrollTo().performClick()
            compose.onNode(hasText("Dark") and hasAnyAncestor(isDialog())).performClick()
            compose.onNodeWithText("Close").performClick()
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNode(tab and hasText("Home")).performClick()
        }
        compose.onNodeWithContentDescription("Log or scan waste").performClick()
        compose.onNode(hasText("Log manually") and hasAnyAncestor(hasTestTag("capture-sheet"))).assertIsDisplayed().performClick()
    }

    private fun traceExit(phase: String, target: String, action: () -> Unit) {
        compose.onNodeWithText("Couldn't load your logs.").assertDoesNotExist()
        val flashes = mutableListOf<Int>()
        val captures = mutableListOf<String>()
        var confirmationAt: Int? = null
        var lastFrame = -1
        val clockStart = compose.mainClock.currentTime
        compose.mainClock.autoAdvance = false
        try {
            action()
            for (frame in 0 until 120) {
                lastFrame = frame
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
                if (compose.onAllNodesWithText("Couldn't load your logs.").fetchSemanticsNodes().isNotEmpty()) {
                    flashes.add(frame)
                    capture("$phase-exit-error-$frame")
                }
                if (confirmationAt == null && compose.onAllNodesWithText(target).fetchSemanticsNodes().isNotEmpty()) {
                    confirmationAt = frame
                    captures.add(capture("$phase-frame-$frame"))
                }
                if (confirmationAt != null && frame - confirmationAt in listOf(3, 7, 12, 16)) {
                    captures.add(capture("$phase-frame-$frame"))
                }
                if (confirmationAt != null && frame - confirmationAt >= 16) break
            }
            assertTrue("$phase must reach its destination: $target", confirmationAt != null)
            assertEquals("Successful navigation must never render a load-error fallback; frames=$flashes", 0, flashes.size)
        } finally {
            val report = JSONObject().put("phase", phase).put("frames_observed", lastFrame + 1)
                .put("target_frame", confirmationAt).put("error_frames", JSONArray(flashes))
                .put("clock_elapsed_ms", compose.mainClock.currentTime - clockStart).put("captures", JSONArray(captures))
            compose.mainClock.autoAdvance = true
            val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "qa/iteration-2/transitions/$phase-result.json")
            file.parentFile!!.mkdirs()
            file.writeText(report.toString(2))
        }
        compose.waitForIdle()
    }

    private fun capture(name: String): String {
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "qa/iteration-2/transitions/$name.png")
        file.parentFile!!.mkdirs()
        val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
        return file.name
    }
}
