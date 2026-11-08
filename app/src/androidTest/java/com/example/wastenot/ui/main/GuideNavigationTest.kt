package com.example.wastenot.ui.main

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.wastenot.MainActivity
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource

class GuideNavigationTest {
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
    private val tabs = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    @Test fun lightGuideSearchBackAndPrefillKeepInputExplicit() = journey(false)
    @Test fun darkGuideSearchBackAndPrefillKeepInputExplicit() = journey(true)
    @Test fun searchBackAndPrefillWithTheNaturalAnimationClock() = journey(false, trace = false)

    private fun journey(dark: Boolean, trace: Boolean = true) {
        waitFor("650 g")
        compose.onNode(tabs and hasText("Profil")).performClick()
        compose.onNodeWithText("Pengaturan").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Tema aplikasi").performScrollTo().performClick()
        compose.onNode(hasText(if (dark) "Gelap" else "Terang") and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithText("Tutup").performClick()
        compose.onNodeWithContentDescription("Kembali").performClick()
        compose.onNode(tabs and hasText("Panduan")).performClick()
        waitFor("Kenali materialnya, tentukan langkah berikutnya.")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Botol plastik"))
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        val mode = if (dark) "dark" else if (trace) "light" else "natural"
        capture("guide-list-$mode")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Botol plastik"))
        compose.onNodeWithText("Botol plastik").performScrollTo().performClick()
        waitFor("Detail panduan")
        capture("guide-detail-$mode")
        compose.onNodeWithText("Catat aktivitas ini").performScrollTo()
        capture("guide-detail-end-$mode")
        compose.onNodeWithContentDescription("Kembali").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.onNodeWithContentDescription("Cari panduan").performScrollTo().performTextReplacement(" KARTON ")
        compose.onNodeWithContentDescription("Cari panduan").performImeAction()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Kertas dan kardus"))
        compose.onNodeWithText("Botol plastik").assertDoesNotExist()
        compose.onNodeWithText("Kertas dan kardus").performScrollTo()
        val paperTop = compose.onNodeWithText("Kertas dan kardus").fetchSemanticsNode().boundsInWindow.top
        val before = geometry()
        val outgoing = JSONArray()
        if (trace) {
            compose.mainClock.autoAdvance = false
            try {
                compose.onNodeWithText("Kertas dan kardus").performClick()
                repeat(4) {
                    compose.mainClock.advanceTimeByFrame()
                    compose.waitForIdle()
                    if (compose.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().isNotEmpty()) outgoing.put(geometry())
                }
            } finally { compose.mainClock.autoAdvance = true }
        } else {
            compose.onNodeWithText("Kertas dan kardus").performClick()
        }
        waitFor("Detail panduan")
        compose.onNodeWithText("Langkah yang perlu diperiksa").performScrollTo().assertIsDisplayed()
        compose.onAllNodes(tabs).assertCountEquals(if (compose.activity.resources.configuration.screenWidthDp >= 600) 5 else 0)
        compose.onNodeWithContentDescription("Kembali").performClick()
        compose.mainClock.advanceTimeBy(200)
        compose.waitForIdle()
        val after = geometry()
        val report = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "qa/iteration-2/guide-scroll-$mode.json")
        report.parentFile!!.mkdirs()
        report.writeText(JSONObject().put("sampled_paper_top", paperTop).put("before", before).put("outgoing", outgoing).put("after", after).toString(2))
        capture("guide-return-$mode")
        assertEquals(paperTop, compose.onNodeWithText("Kertas dan kardus").fetchSemanticsNode().boundsInWindow.top, 1f)
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.onNodeWithContentDescription("Cari panduan").assertTextContains(" KARTON ")
        compose.onNodeWithText("Semua kategori").performScrollTo().performClick()
        compose.onNode(hasText("Kaca") and hasAnyAncestor(isPopup())).performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Panduan belum ditemukan"))
        capture("guide-empty-$mode")
        compose.onNodeWithText("Hapus pencarian").performScrollTo().performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Wadah kaca"))
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        assertEquals("", compose.onNodeWithContentDescription("Cari panduan").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Wadah kaca"))
        compose.onNodeWithText("Wadah kaca").performScrollTo().performClick()
        waitFor("Detail panduan")
        compose.onNodeWithText("Catat aktivitas ini").performScrollTo()
        assertRecordCount(5)
        compose.onNodeWithText("Catat aktivitas ini").performClick()
        waitFor("Catat aktivitas")
        compose.onNodeWithText("Reuse").assertIsSelected()
        compose.onNodeWithContentDescription("Kategori").assertTextContains("Kaca")
        assertEquals("", compose.onNodeWithContentDescription("Berat").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        assertRecordCount(5)
        capture("guide-prefill-initial-$mode")
        compose.activityRule.scenario.recreate()
        waitFor("Catat aktivitas")
        compose.onNodeWithText("Reuse").assertIsSelected()
        compose.onNodeWithContentDescription("Kategori").assertTextContains("Kaca")
        compose.onNodeWithText("Simpan catatan").performScrollTo().performClick()
        compose.onNodeWithText("Isi berat atau jumlah benda.").performScrollTo().assertIsDisplayed()
        assertRecordCount(5)
        compose.onNodeWithText("Recycle").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Satuan").performScrollTo().performClick()
        compose.onNode(hasText("Benda") and hasAnyAncestor(isPopup())).performClick()
        compose.onNodeWithContentDescription("Jumlah benda").performScrollTo().performTextReplacement("2")
        compose.onNodeWithText("Simpan catatan").performScrollTo()
        capture("guide-prefill-$mode")
        compose.onNodeWithText("Simpan catatan").performClick()
        waitFor("+10 XP untuk aktivitas baru ini.")
        assertRecordCount(6)
        compose.onNodeWithText("Kembali ke beranda").performScrollTo().performClick()
        compose.onNode(tabs and hasText("Beranda")).assertIsSelected()
    }

    private fun waitFor(text: String) {
        compose.waitUntil(30_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun geometry(): JSONObject {
        var ime = false
        val window = android.graphics.Rect()
        compose.runOnUiThread {
            ime = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
            compose.activity.window.decorView.getWindowVisibleDisplayFrame(window)
        }
        val list = compose.onNode(hasScrollToIndexAction()).fetchSemanticsNode()
        val viewport = list.boundsInWindow
        val paper = compose.onAllNodes(hasText("Kertas dan kardus") and hasAnyAncestor(hasScrollToIndexAction())).fetchSemanticsNodes().firstOrNull()?.boundsInWindow
        val field = compose.onAllNodes(hasContentDescription("Cari panduan")).fetchSemanticsNodes().firstOrNull()
        return JSONObject().put("ime_visible", ime).put("viewport_top", viewport.top).put("viewport_bottom", viewport.bottom)
            .put("window_visible_bottom", window.bottom).put("search_present", field != null)
            .put("search_focused", field?.config?.getOrNull(SemanticsProperties.Focused) ?: false)
            .put("paper_top", paper?.top ?: JSONObject.NULL).put("paper_bottom", paper?.bottom ?: JSONObject.NULL)
            .put("scroll_value", list.config[SemanticsProperties.VerticalScrollAxisRange].value())
    }

    private fun assertRecordCount(expected: Int) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.runOnIdle { assertEquals(expected, JSONArray(context.getSharedPreferences("dummy_logs_dummy-owner", Context.MODE_PRIVATE).getString("records", null)).length()) }
    }

    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(200)
        compose.waitForIdle()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.waitForIdle(300, 5_000)
        val image = checkNotNull(automation.takeScreenshot())
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "qa/iteration-2/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        image.recycle()
    }
}
