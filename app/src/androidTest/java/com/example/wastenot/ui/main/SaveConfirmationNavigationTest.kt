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
        compose.waitUntil(30_000) { compose.onAllNodes(hasText("Ubah catatan") and hasClickAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        if (compose.onAllNodes(tabs).fetchSemanticsNodes().isEmpty()) {
            compose.onNodeWithContentDescription("Kembali").performClick()
        }
        tab("Riwayat")
        compose.onNodeWithContentDescription("Catat atau scan sampah").performClick()
        compose.onNode(hasText("Input manual") and hasAnyAncestor(hasTestTag("capture-sheet"))).assertIsDisplayed().performClick()
        compose.onNodeWithText("Recycle").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Kategori").performScrollTo().performClick()
        compose.onNode(hasText("Logam") and hasAnyAncestor(isPopup())).performClick()
        compose.onNodeWithContentDescription("Satuan").performScrollTo().performClick()
        compose.onNodeWithText("Benda").performClick()
        compose.onNodeWithContentDescription("Jumlah benda").performTextReplacement("3")
        compose.onNodeWithText("Simpan catatan").performScrollTo().performClick()
        waitFor("Catatan tersimpan")
        capture("save-light-start")
        compose.onNodeWithText("+10 XP untuk aktivitas baru ini.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Logam · Recycle").performScrollTo().assertIsDisplayed()
        capture("save-light")
        compose.activityRule.scenario.recreate()
        waitFor("Catatan tersimpan")
        compose.onNodeWithText("+10 XP untuk aktivitas baru ini.").performScrollTo().assertIsDisplayed()
        capture("save-restored")
        compose.onNodeWithText("Kembali ke beranda").performScrollTo().performClick()
        compose.onNode(tabs and hasText("Beranda")).assertIsSelected()
        compose.onNodeWithText("Detail catatan").assertDoesNotExist()
        compose.onNodeWithText("Catatan tersimpan").assertDoesNotExist()
        tab("Profil")
        compose.onNodeWithText("60 XP total").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Pengaturan").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Tema aplikasi").performScrollTo().performClick()
        compose.onNode(hasText("Gelap") and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithText("Tutup").performClick()
        compose.onNodeWithContentDescription("Kembali").performClick()
        tab("Riwayat")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("3 benda"))
        compose.onNodeWithText("3 benda").performClick()
        compose.waitUntil(30_000) { compose.onAllNodes(hasText("Ubah catatan") and hasClickAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Ubah catatan").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Catatan (opsional)").performScrollTo().performTextReplacement("Diperbaiki")
        compose.onNodeWithText("Simpan perubahan").performScrollTo().performClick()
        waitFor("Catatan tersimpan")
        capture("save-edit-dark-start")
        compose.onNodeWithText("+10 XP", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Catatanmu sudah tersimpan. Tidak ada XP tambahan.").performScrollTo().assertIsDisplayed()
        capture("save-edit-dark")
        compose.onNodeWithText("Lihat catatan").performScrollTo().performClick()
        compose.onNodeWithText("Detail catatan").assertIsDisplayed()
        compose.onNodeWithText("Diperbaiki").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Kembali").performClick()
        tab("Profil")
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
