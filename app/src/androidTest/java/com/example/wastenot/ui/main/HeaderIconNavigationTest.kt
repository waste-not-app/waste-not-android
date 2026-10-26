package com.example.wastenot.ui.main

import android.content.Context
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource

class HeaderIconNavigationTest {
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

    @Test fun profileFilterBackAndScanCloseKeepTheirOriginalDestinations() {
        compose.waitUntil(30_000) { compose.onAllNodes(tabs).fetchSemanticsNodes().size == 5 }
        compose.onNodeWithContentDescription("Buka profil").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            .performTouchInput { click() }
        compose.onNode(tabs and hasText("Profil")).assertIsSelected()
        compose.onNodeWithText("Pengaturan").performScrollTo().performTouchInput { click() }
        compose.onNodeWithContentDescription("Kembali").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            .performTouchInput { click() }
        compose.onNode(tabs and hasText("Profil")).assertIsSelected()

        compose.onNode(tabs and hasText("Riwayat")).performClick()
        compose.onNodeWithContentDescription("Filter riwayat").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            .performTouchInput { click() }
        compose.onNodeWithTag("history-filter-sheet").assertExists()
        compose.onNode(hasText("Batal") and hasAnyAncestor(hasTestTag("history-filter-sheet"))).performTouchInput { click() }
        compose.onNodeWithTag("history-filter-sheet").assertDoesNotExist()
        compose.onNode(tabs and hasText("Riwayat")).assertIsSelected()

        compose.onNodeWithContentDescription("Catat atau scan sampah").performTouchInput { click() }
        compose.onNodeWithTag("capture-sheet").assertExists()
        compose.onNodeWithContentDescription("Tutup scan").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            .performTouchInput { click() }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("capture-sheet").fetchSemanticsNodes().isEmpty() }
        compose.onNode(tabs and hasText("Riwayat")).assertIsSelected()
        compose.onNodeWithText("Simpan catatan").assertDoesNotExist()
    }
}
