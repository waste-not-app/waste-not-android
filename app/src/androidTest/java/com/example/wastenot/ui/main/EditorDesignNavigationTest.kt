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
    @get:Rule(order = 0) val fixtures = object : ExternalResource() {
        override fun before() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
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
        compose.onNodeWithContentDescription("Catat atau scan sampah").performClick()
        compose.onNode(hasText("Input manual") and hasAnyAncestor(hasTestTag("capture-sheet"))).assertIsDisplayed().performClick()
        compose.onNodeWithText("Recycle").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Kategori").performScrollTo().performClick()
        compose.onNode(hasText("Plastik") and hasAnyAncestor(isPopup())).performClick()
        compose.onNodeWithContentDescription("Berat").performScrollTo().performTextReplacement("250")
        compose.onNodeWithContentDescription("Catatan (opsional)").performScrollTo().performTextReplacement("Isian gagal tersimpan")
        val preferences = InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("dummy_logs_dummy-owner", Context.MODE_PRIVATE)
        val original = checkNotNull(preferences.getString("records", null))
        try {
            compose.runOnIdle { check(preferences.edit().putString("records", "{").commit()) }
            compose.onNodeWithText("Simpan catatan").performScrollTo().performClick()
            val failure = "Catatan belum bisa disimpan di perangkat. Isianmu tetap tersedia; coba lagi."
            waitFor(failure)
            compose.onNodeWithContentDescription("Catatan (opsional)").assertIsEnabled().assertTextContains("Isian gagal tersimpan")
            compose.activityRule.scenario.recreate()
            compose.waitForIdle()
            compose.onNodeWithText(failure).assertExists()
            compose.onNodeWithContentDescription("Berat").assertTextContains("250")
            compose.onNodeWithContentDescription("Catatan (opsional)").assertIsEnabled().assertTextContains("Isian gagal tersimpan")
            compose.runOnIdle {
                assertEquals("{", preferences.getString("records", null))
                check(preferences.edit().putString("records", original).commit())
            }
            compose.onNodeWithContentDescription("Catatan (opsional)").performScrollTo().performTextReplacement("Koreksi eksplisit")
            compose.onNodeWithText("Simpan catatan").performScrollTo().performClick()
            waitFor("+10 XP untuk aktivitas baru ini.")
            compose.runOnIdle { assertEquals(6, org.json.JSONArray(preferences.getString("records", null)).length()) }
        } finally {
            if (preferences.getString("records", null) == "{") check(preferences.edit().putString("records", original).commit())
        }
    }

    private fun journey(dark: Boolean) {
        waitFor("650 g")
        compose.onNode(tabs and hasText("Profil")).performClick()
        compose.onNodeWithText("Pengaturan").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Tema aplikasi").performScrollTo().performClick()
        compose.onNode(hasText(if (dark) "Gelap" else "Terang") and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithText("Tutup").performClick()
        compose.onNodeWithContentDescription("Kembali").performClick()
        compose.onNode(tabs and hasText("Beranda")).performClick()
        compose.onNodeWithContentDescription("Catat atau scan sampah").performClick()
        compose.onNode(hasText("Input manual") and hasAnyAncestor(hasTestTag("capture-sheet"))).assertIsDisplayed().performClick()
        val mode = if (dark) "dark" else "light"
        capture("editor-create-$mode")
        compose.onNodeWithText("Pilih satu metode.").assertDoesNotExist()
        compose.onNodeWithText("Isi berat atau jumlah benda.").assertDoesNotExist()
        val quantity = compose.onNodeWithContentDescription("Berat")
        quantity.performScrollTo().performTouchInput { click() }
        quantity.performTextReplacement("1.5")
        compose.onNodeWithContentDescription("Catatan (opsional)").performScrollTo().performClick()
        compose.onNodeWithText("Gunakan angka sesuai satuan; benda dan gram tidak memakai desimal.").assertExists()
        compose.onNodeWithText("Pilih kategori.").assertDoesNotExist()
        compose.onNodeWithText("Simpan catatan").performScrollTo().performClick()
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
                compose.onNodeWithContentDescription("Jumlah yang dihindari").assertExists()
                compose.onNodeWithText("Dicatat terpisah dari sampah yang ditangani.").assertExists()
            }
        }
        compose.onNodeWithText("Recycle").performScrollTo().performClick()
        compose.onNodeWithText("Simpan catatan").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasContentDescription("Kategori") and isFocused()).fetchSemanticsNodes().size == 1 }
        compose.onNodeWithContentDescription("Kategori").assertIsFocused().assertIsDisplayed()
        compose.onNodeWithContentDescription("Kategori").performScrollTo().performClick()
        compose.onNode(hasText("Plastik") and hasAnyAncestor(isPopup())).performClick()
        compose.onNodeWithText("Simpan catatan").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasContentDescription("Berat") and isFocused()).fetchSemanticsNodes().size == 1 }
        quantity.assertIsFocused().assertIsDisplayed()
        quantity.performScrollTo().performTextReplacement("250")
        compose.onNodeWithContentDescription("Catatan (opsional)").performScrollTo().performTextReplacement("Catatan pribadi")
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithText("Recycle").performScrollTo().assertIsSelected()
        compose.onNodeWithContentDescription("Berat").performScrollTo().assertTextContains("250")
        compose.onNodeWithContentDescription("Catatan (opsional)").performScrollTo().assertTextContains("Catatan pribadi")
        compose.onNodeWithText("Asia/Jakarta · maksimum 30 hari sebelumnya.").assertExists()
        compose.onNodeWithText("Simpan catatan").performScrollTo().assertIsDisplayed()
        capture("editor-create-end-$mode")
        compose.onNodeWithText("Simpan catatan").performClick()
        waitFor("+10 XP untuk aktivitas baru ini.")
        compose.onNodeWithText("Lihat catatan").performScrollTo().performClick()
        compose.waitUntil(30_000) { compose.onAllNodes(hasText("Ubah catatan") and hasClickAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Ubah catatan").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("Simpan perubahan") and hasClickAction()).fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithText("Simpan perubahan").assertIsNotEnabled()
        capture("editor-edit-$mode")
        compose.onNodeWithContentDescription("Satuan").performScrollTo().performClick()
        capture("editor-unit-$mode")
        compose.onNodeWithText("Benda").performClick()
        val count = compose.onNodeWithContentDescription("Jumlah benda")
        assertEquals("", count.fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        count.performScrollTo().performTextReplacement("2")
        compose.runOnUiThread {
            compose.activity.getSystemService(InputMethodManager::class.java)
                .hideSoftInputFromWindow(compose.activity.window.decorView.windowToken, 0)
        }
        compose.onNodeWithText("Simpan perubahan").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText("Perubahan memperbarui statistik, tanpa XP tambahan.").assertExists()
        capture("editor-edit-end-$mode")
        compose.onNodeWithText("Simpan perubahan").performClick()
        waitFor("Catatanmu sudah tersimpan. Tidak ada XP tambahan.")
        compose.onNodeWithText("+10 XP", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Lihat catatan").performScrollTo().performClick()
        compose.onNodeWithText("2 benda").performScrollTo().assertIsDisplayed()
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
