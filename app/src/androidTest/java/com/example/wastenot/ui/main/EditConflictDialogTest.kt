package com.example.wastenot.ui.main

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.model.*
import com.example.wastenot.theme.WasteNotTheme
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class EditConflictDialogTest {
    @get:Rule val compose = createComposeRule()
    private val latest = WasteLog("log", WasteCategory.GLASS, DisposalMethod.TRASH, Quantity.Count.of(2),
        LocalDate.of(2026, 10, 23), ZoneId.of("Asia/Jakarta"), "Perangkat lain", 2, Instant.EPOCH, Instant.EPOCH)
    private val mine = EditorDraft.from(latest.copy(version = 1, quantity = Quantity.Weight.of(300), notes = "Isian draf saya"), latest.activityDate).values

    @Test fun loadingErrorsMissingAndComparisonRemainReadableInBothThemes() {
        var dark by mutableStateOf(false)
        var read by mutableStateOf<ConflictReadState?>(null)
        var reloads = 0; var dismisses = 0
        compose.setContent { WasteNotTheme(dark) {
            EditConflictDialog(mine, read, { dismisses++ }, { reloads++ }, { _, _ -> fail("Review must require an explicit choice") })
        } }
        val states = listOf(null, ConflictReadState("entry", "log", loading = true),
            ConflictReadState("entry", "log", error = ApiFailureKind.NETWORK),
            ConflictReadState("entry", "log", error = ApiFailureKind.PRECONDITION_REQUIRED),
            ConflictReadState("entry", "log", error = ApiFailureKind.NOT_FOUND),
            ConflictReadState("entry", "log", latest = latest))
        for (theme in listOf(false, true)) {
            compose.runOnIdle { dark = theme }
            states.forEachIndexed { index, state ->
                compose.runOnIdle { read = state }
                compose.onNodeWithText("Tetap di draf").performScrollTo().assertIsDisplayed()
                if (state?.loading == true) compose.onNodeWithText("Memuat versi terbaru…").assertIsNotEnabled()
                if (state?.error == ApiFailureKind.NOT_FOUND) compose.onNodeWithText("Muat versi terbaru").assertDoesNotExist()
                if (state?.latest != null) {
                    compose.onNodeWithText("Versi terbaru · v2").performScrollTo().assertIsDisplayed()
                    compose.onNodeWithText("Drafmu").performScrollTo().assertIsDisplayed()
                }
                capture("conflict-${if (theme) "dark" else "light"}-$index")
                if (state?.latest != null) {
                    compose.onNodeWithText("Tetap di draf").performScrollTo().assertIsDisplayed()
                    capture("conflict-${if (theme) "dark" else "light"}-choices")
                }
            }
        }
        assertEquals(0, reloads); assertEquals(0, dismisses)
    }

    @Test fun choicesReturnTheReviewedSnapshotAndNeverRunOnLoadOrDismiss() {
        var read by mutableStateOf<ConflictReadState?>(null)
        val choices = mutableListOf<Pair<WasteLog, Boolean>>()
        var reloads = 0; var dismisses = 0
        compose.setContent { WasteNotTheme {
            EditConflictDialog(mine, read, { dismisses++ }, { reloads++ }, { log, keep -> choices.add(log to keep) })
        } }
        compose.onNodeWithText("Muat versi terbaru").performScrollTo().performClick()
        assertEquals(1, reloads); assertTrue(choices.isEmpty())
        compose.runOnIdle { read = ConflictReadState("entry", "log", latest = latest) }
        compose.onNodeWithText("Gunakan seluruh isian drafku").performScrollTo().performClick()
        compose.onNodeWithText("Gunakan isian versi terbaru").performScrollTo().performClick()
        compose.onNodeWithText("Tetap di draf").performScrollTo().performClick()
        assertEquals(listOf(latest to true, latest to false), choices); assertEquals(1, dismisses)
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.waitForIdle(300, 5_000)
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "conflict-qa").apply { mkdirs() }
        val image = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }; image.recycle()
    }
}
