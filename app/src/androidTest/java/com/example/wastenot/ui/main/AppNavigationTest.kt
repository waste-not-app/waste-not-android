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
import org.junit.Rule
import org.junit.Test
import java.io.File

class AppNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val tabs = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    private fun preview() {
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Lihat data contoh").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Lihat data contoh").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onAllNodes(tabs).assertCountEquals(5)
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
        tab("Profil")
        compose.onNodeWithText("Tampilan:", substring = true).performScrollTo().performClick()
        compose.onNodeWithText("Terang").performClick()
        compose.onNodeWithText("Tutup").performClick()
        tab("Beranda")
        compose.onNodeWithText("650 g").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("2 benda dihindari").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("1 dari 2 aktivitas penanganan", substring = true).performScrollTo().assertIsDisplayed()
        capture("home")
        listOf("Beranda", "Riwayat", "Panduan", "Statistik", "Profil").forEach { label ->
            tab(label)
            compose.onAllNodes(tabs).fetchSemanticsNodes().forEach { node ->
                compose.onNode(hasText(node.config[SemanticsProperties.Text].joinToString { it.text }) and tabs)
                    .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
            }
            compose.onNodeWithText("Catat").performClick()
            compose.onAllNodes(tabs).assertCountEquals(0)
            compose.onNodeWithText("Catat aktivitas").assertIsDisplayed()
            compose.onNodeWithContentDescription("Kembali").performClick()
            compose.onNode(tabs and hasText(label)).assertIsSelected()
        }
        tab("Statistik")
        compose.onNodeWithText("1,4 kg").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("2 dari 4 aktivitas penanganan", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("30 hari").performScrollTo().performClick()
        tab("Profil"); tab("Statistik")
        compose.onNodeWithText("30 hari").assertIsSelected()
        tab("Profil")
        compose.onNodeWithText("Tampilan:", substring = true).performScrollTo().performClick()
        compose.onNodeWithText("Gelap").performClick().assertIsSelected()
        compose.onNodeWithText("Tutup").performClick()
        tab("Beranda")
        compose.onNodeWithText("650 g").performScrollTo().assertIsDisplayed()
        capture("home-dark")
    }

    @Test fun backAtHomeExitsWithoutOpeningAuth() {
        preview()
        compose.onNode(tabs and hasText("Beranda")).assertIsSelected()
        val activity = compose.activity
        compose.runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(5_000) { activity.isFinishing }
    }

    @Test fun invalidPasswordStaysFocusedAboveKeyboardWithoutCallingAccountService() {
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Lihat data contoh").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText("Email") and hasSetTextAction()).performClick().performTextReplacement("fixture@example.invalid")
        val password = compose.onNode(hasText("Kata sandi") and hasSetTextAction())
        password.performScrollTo().performClick().performTextReplacement("short")
        compose.waitUntil(8_000) { compose.runOnUiThread {
            ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
        } }
        password.performImeAction()
        password.assertIsFocused().assertIsDisplayed()
        compose.onNodeWithText("Kata sandi perlu 12–128 karakter, termasuk spasi.").assertExists()
        compose.onNodeWithText("Masuk", substring = false).performScrollTo().assertIsDisplayed()
        compose.onAllNodes(tabs).assertCountEquals(0)
        capture("login-keyboard-error")
        val visible = Rect()
        compose.runOnUiThread { compose.activity.window.decorView.getWindowVisibleDisplayFrame(visible) }
        val button = compose.onNodeWithText("Masuk", substring = false).fetchSemanticsNode().boundsInWindow
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
        preview(); tab("Riwayat")
        compose.onNodeWithText("Kategori").performScrollTo().performClick()
        compose.onNode(hasText("Plastik") and hasAnyAncestor(isPopup())).performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("250 g"))
        compose.onNodeWithText("250 g").performScrollTo().performClick()
        capture("history-detail")
        compose.onNodeWithText("Detail catatan").assertIsDisplayed()
        compose.onNodeWithContentDescription("Kembali").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Hapus filter"))
        compose.onNodeWithText("Hapus filter").assertIsDisplayed()
        tab("Panduan"); tab("Riwayat")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Hapus filter"))
        compose.onNodeWithText("Hapus filter").assertIsDisplayed()
        tab("Statistik")
        compose.onNodeWithText("30 hari").performScrollTo().performClick()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithText("30 hari").assertIsSelected()
        tab("Riwayat")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Hapus filter"))
        compose.onNodeWithText("Hapus filter").assertIsDisplayed()
        back()
        compose.onNode(tabs and hasText("Beranda")).assertIsSelected()
    }

    @Test fun dirtyBackCreateAndEditKeepExclusiveQuantityAndDoNotAddEditXp() {
        preview()
        compose.onNodeWithText("Catat").performClick()
        compose.onNodeWithText("Recycle").performScrollTo().performClick()
        back()
        compose.onNodeWithText("Buang perubahan?").assertIsDisplayed()
        compose.onNodeWithText("Lanjut mengisi").performClick()
        compose.onNodeWithText("Recycle").assertIsSelected()
        compose.onNodeWithContentDescription("Kembali").performClick()
        compose.onNodeWithText("Buang perubahan").performClick()
        compose.onNodeWithText("Catat").performClick()
        compose.onNodeWithText("Recycle").performScrollTo().performClick()
        compose.onNodeWithText("Kategori").performScrollTo().performClick()
        compose.onNode(hasText("Plastik") and hasAnyAncestor(isPopup())).performClick()
        compose.onNodeWithText("kg").performScrollTo().performClick()
        compose.onNode(hasText("Berat") and hasSetTextAction()).performTextReplacement("0,25")
        compose.onNode(hasText("Catatan opsional") and hasSetTextAction()).performScrollTo().performTextReplacement("QA contoh pribadi")
        compose.onNodeWithText("21 Sep 2026").performScrollTo().performClick()
        compose.onNodeWithText("Batal").performClick()
        compose.onNodeWithText("Simpan contoh").performScrollTo().performClick()
        compose.onNodeWithText("Detail catatan").assertIsDisplayed()
        compose.onNodeWithText("QA contoh pribadi").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Ubah catatan").performScrollTo().performClick()
        capture("editor-edit")
        compose.onNodeWithText("benda").performScrollTo().performClick()
        val quantity = compose.onNode(hasText("Jumlah benda") and hasSetTextAction())
        assertEquals("", quantity.fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        quantity.performTextReplacement("2")
        compose.onNodeWithText("Simpan perubahan").performScrollTo().performClick()
        compose.onNodeWithText("2 benda").performScrollTo().assertIsDisplayed()
        tab("Profil")
        compose.onNodeWithText("Level 1 · 60/100 XP").performScrollTo().assertIsDisplayed()
        tab("Beranda")
        compose.onNodeWithText("Detail catatan").assertIsDisplayed()
        compose.onNodeWithContentDescription("Kembali").performClick()
        compose.onNodeWithText("650 g").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("2 benda ditangani").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("2 benda dihindari").performScrollTo().assertIsDisplayed()
        tab("Profil")
        compose.onNodeWithText("Keluar").performScrollTo().performClick()
        compose.onNodeWithText("Batal").performClick()
        compose.onNodeWithText("Level 1 · 60/100 XP").assertExists()
        compose.onNodeWithText("Keluar").performClick()
        compose.onNode(hasText("Keluar") and hasAnyAncestor(isDialog())).performClick()
        compose.waitForIdle()
        compose.onAllNodes(tabs).assertCountEquals(0)
        compose.onNodeWithText("Masuk ke demo").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("QA contoh pribadi").assertDoesNotExist()
        val activity = compose.activity
        compose.runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(5_000) { activity.isFinishing }
    }
}
