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
    @get:Rule(order = 0) val fixtures = object : ExternalResource() {
        override fun before() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
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
        compose.onNodeWithText("Detail catatan").assertIsDisplayed()
        detailReady()
    }
    private fun detailReady() {
        compose.waitUntil(30_000) { compose.onAllNodes(hasText("Ubah catatan") and hasClickAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun delete() { compose.onNodeWithText("Hapus catatan", substring = false).performScrollTo().performClick() }

    @Test fun cancelThenConfirmedDeleteRefreshesHistoryTotalsAndXpAcrossRecreation() {
        open()
        capture("detail-light")
        compose.onNodeWithText("Dibuat ", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Diperbarui ", substring = true).performScrollTo().assertIsDisplayed()
        delete()
        compose.onNodeWithText("Statistik dan XP akan dihitung ulang.", substring = true).assertIsDisplayed()
        capture("delete-light")
        compose.onNodeWithText("Batal").performScrollTo().performClick()
        compose.onNodeWithText("Detail catatan").assertIsDisplayed()
        delete()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Hapus catatan?").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Hapus", substring = false).performScrollTo().performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Catatan dihapus.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(tabs and hasText("Riwayat")).assertIsSelected()
        capture("history-after-delete")
        tab("Profil")
        compose.onNodeWithText("40 XP total").performScrollTo().assertIsDisplayed()
        tab("Statistik")
        compose.onNodeWithText("1,15 kg").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("1 dari 3 aktivitas penanganan", substring = true).performScrollTo().assertIsDisplayed()
        tab("Beranda")
        compose.onNode(hasText("400 g") and !hasClickAction()).performScrollTo().assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(30_000) { compose.onAllNodes(hasText("400 g") and !hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
        tab("Profil")
        compose.onNodeWithText("40 XP total").performScrollTo().assertIsDisplayed()
    }

    @Test fun darkDetailAndDeleteCancelKeepTheRecordAvailable() {
        ready(); tab("Profil")
        compose.onNodeWithText("Pengaturan").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Tema aplikasi").performScrollTo().performClick()
        compose.onNode(hasText("Gelap") and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithText("Tutup").performClick()
        compose.onNodeWithContentDescription("Kembali").performClick()
        tab("Beranda"); open()
        capture("detail-dark")
        delete(); capture("delete-dark")
        compose.onNodeWithText("Batal").performScrollTo().performClick()
        compose.onNodeWithText("250 g").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Ubah catatan").performScrollTo().assertIsEnabled()
        if (compose.onAllNodes(tabs).fetchSemanticsNodes().isEmpty()) compose.onNodeWithContentDescription("Kembali").performClick()
        tab("Profil")
        compose.onNodeWithText("50 XP total").performScrollTo().assertIsDisplayed()
    }

    @Test fun sameRecordAcrossTwoTabStacksAndDoubleTapKeepBackNavigationCorrect() {
        ready()
        compose.onNodeWithText("250 g").performScrollTo().performTouchInput { doubleClick() }
        detailReady()
        compose.onNodeWithContentDescription("Kembali").performClick()
        compose.onNodeWithText("650 g").performScrollTo().assertIsDisplayed()
        open()
        val retainedInHome = compose.onAllNodes(tabs).fetchSemanticsNodes().isNotEmpty()
        if (!retainedInHome) compose.onNodeWithContentDescription("Kembali").performClick()
        tab("Riwayat")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("250 g"))
        compose.onNodeWithText("250 g").performClick()
        detailReady()
        compose.onNodeWithContentDescription("Kembali").performClick()
        compose.onNode(tabs and hasText("Riwayat")).assertIsSelected()
        tab("Beranda")
        if (retainedInHome) {
            compose.onNodeWithText("Detail catatan").assertIsDisplayed()
            compose.onNodeWithContentDescription("Kembali").performClick()
        }
        compose.onNodeWithText("650 g").performScrollTo().assertIsDisplayed()
    }

    @Test fun wideDeleteReturnsToHistoryListWhenHistoryRetainsAnotherDetail() {
        assumeTrue(compose.activity.resources.configuration.screenWidthDp >= 600)
        ready()
        tab("Riwayat")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("400 g"))
        compose.onNodeWithText("400 g").performClick()
        detailReady()
        tab("Beranda")
        open()
        delete()
        compose.onNodeWithText("Hapus", substring = false).performScrollTo().performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Catatan dihapus.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(tabs and hasText("Riwayat")).assertIsSelected()
        compose.onNodeWithText("Detail catatan").assertDoesNotExist()
        compose.onNodeWithText("Catatan dihapus.").assertIsDisplayed()
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
