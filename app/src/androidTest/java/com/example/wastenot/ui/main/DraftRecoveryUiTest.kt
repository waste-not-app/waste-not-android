package com.example.wastenot.ui.main

import android.view.KeyEvent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.model.*
import com.example.wastenot.theme.WasteNotTheme
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DraftRecoveryUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun recoveryStatesUseTheKitAndRemainReadableInBothThemes() {
        var dark by mutableStateOf(false)
        var loading by mutableStateOf(true)
        var retries = 0; var discards = 0
        compose.setContent { WasteNotTheme(dark) {
            Surface(color = MaterialTheme.colorScheme.background) {
                DraftRestoreScreen(loading, { retries++ }, { discards++ }, Modifier.fillMaxSize())
            }
        } }
        for (theme in listOf(false,true)) for (pending in listOf(true,false)) {
            compose.runOnIdle { dark = theme; loading = pending }
            readable(if (pending) "Checking for a draft on this device…" else "Couldn't open your draft")
            if (!pending) {
                listOf("Try again","Discard saved draft").forEach {
                    readable(it)
                    compose.onNodeWithText(it).assertHeightIsAtLeast(48.dp)
                }
            }
            captureDraftFixture("draft-recovery-${if(theme) "dark" else "light"}-${if(pending) "loading" else "error"}")
        }
        compose.onNodeWithText("Try again").performScrollTo().performClick()
        compose.onNodeWithText("Discard saved draft").performScrollTo().performClick()
        assertEquals(1,retries); assertEquals(1,discards)
    }

    @Test fun aConfirmedRecordOnlyOffersLocalCleanupRetryAndNeverAnotherSubmission() {
        val form = EditorDraft.from(null, LocalDate.of(2026,10,24)).apply {
            change(EditorField.QUANTITY, values.copy(category = WasteCategory.PLASTIC, method = DisposalMethod.RECYCLE, quantity = QuantityInput("250")))
            prepareSubmission(values.date)
        }
        var dark by mutableStateOf(false)
        var retries = 0; var writes = 0
        compose.setContent { WasteNotTheme(dark) {
            Surface { LogEditorScreen(form, form.values.date, "Asia/Jakarta", false, PaddingValues(0.dp),
                { writes++ }, {}, {}, Modifier.fillMaxSize(), persistence = EditorPersistenceState(loading = false, ready = true, failure = DraftFailure.FINISH),
                saveConfirmed = true, onRetryPersistence = { retries++ }) }
        } }
        for (theme in listOf(false,true)) {
            compose.runOnIdle { dark = theme }
            compose.onNodeWithText("Log saved").performScrollTo().assertIsNotEnabled()
            compose.onNodeWithText("Try again").performScrollTo().performClick()
            captureDraftFixture("draft-saved-cleanup-${if(theme) "dark" else "light"}")
        }
        assertEquals(0,writes); assertEquals(2,retries)
    }

    @Test fun busyCleanupRejectsBackAndEscapeAndFailureKeepsExplicitActions() {
        var busy by mutableStateOf(true)
        var dismisses = 0; var confirms = 0
        compose.setContent { WasteNotTheme {
            DiscardChangesDialog(false, { dismisses++ }, { confirms++ }, busy = busy, error = !busy)
        } }
        compose.onNodeWithText("Updating draft…").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Keep editing").assertIsNotEnabled()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_ESCAPE)
        compose.onNode(isDialog()).assertExists()
        assertEquals(0,dismisses); assertEquals(0,confirms)
        compose.runOnIdle { busy = false }
        compose.onNodeWithText("Discard changes").performScrollTo().performClick()
        compose.onNodeWithText("Keep editing").performScrollTo().performClick()
        assertEquals(1,dismisses); assertEquals(1,confirms)
        captureDraftFixture("draft-cleanup-failed")
    }

    private fun readable(text: String) {
        val node = compose.onNodeWithText(text,useUnmergedTree=true).performScrollTo().assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertFalse(text,layouts.single().didOverflowWidth)
        assertFalse(text,layouts.single().didOverflowHeight)
    }
}
