package com.example.wastenot.ui.main

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.wastenot.model.*
import com.example.wastenot.theme.WasteNotTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class SaveConfirmationDesignTest {
    @get:Rule val compose = createComposeRule()

    @Test fun replayFeedbackHasNoNewXpAndBothActionsAreAvailable() {
        val log = WasteLog("saved", WasteCategory.METAL, DisposalMethod.RECYCLE, Quantity.Count.of(3),
            LocalDate.of(2026, 10, 20), ZoneId.of("Asia/Jakarta"), "", 1, Instant.EPOCH, Instant.EPOCH)
        var views = 0
        var homes = 0
        compose.setContent { WasteNotTheme {
            SaveConfirmationScreen(log, false, PaddingValues(), { views++ }, { homes++ }, Modifier.fillMaxSize())
        } }
        compose.onNodeWithText("+10 XP", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Your log was already saved. No extra XP.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("One more step logged.")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Log saved"))
        compose.onNodeWithText("View log").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithText("Back to home").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, views)
        assertEquals(1, homes)
    }
}
