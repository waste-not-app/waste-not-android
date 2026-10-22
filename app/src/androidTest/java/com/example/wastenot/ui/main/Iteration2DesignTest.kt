package com.example.wastenot.ui.main

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.MainActivity
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.rules.ExternalResource

class Iteration2DesignTest {
    @get:Rule(order = 0) val fixtures = object : ExternalResource() {
        override fun before() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            listOf("dummy_session", "dummy_logs_dummy-owner", "appearance").forEach {
                context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
            }
        }
    }
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    private val tabs = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    @Test fun directDummyHomeDockAndManualFallbackRetainAllFiveStacks() {
        compose.waitUntil(30_000) { compose.onAllNodes(tabs).fetchSemanticsNodes().size == 5 }
        compose.onNodeWithText("Lihat data contoh").assertDoesNotExist()
        compose.onNodeWithText("Data contoh", substring = true).assertDoesNotExist()
        waitForHome()
        val label = "Catat / Scan"
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(label, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        for (line in 0 until layout.lineCount - 1) {
            val end = layout.getLineEnd(line, visibleEnd = true)
            assertTrue("Scan action must wrap between words, not inside them", end == 0 || end >= label.length ||
                !label[end - 1].isLetter() || !label[end].isLetter())
        }
        capture("home-light")
        compose.onNodeWithContentDescription("Buka profil").performClick()
        compose.onNode(tabs and hasText("Profil")).assertIsSelected()
        compose.onNode(tabs and hasText("Beranda")).performClick()
        compose.onNodeWithText("Input manual").performScrollTo().performClick()
        compose.onNodeWithText("Catat aktivitas").assertIsDisplayed()
        compose.onNodeWithContentDescription("Kembali").performClick()
        compose.onNodeWithText("Lihat riwayat").performScrollTo().performClick()
        compose.onNode(tabs and hasText("Riwayat")).assertIsSelected()
        compose.onNode(tabs and hasText("Beranda")).performClick()
        compose.onNodeWithText("250 g").performScrollTo().performClick()
        compose.onNodeWithText("Detail catatan").assertIsDisplayed()
        compose.onNodeWithContentDescription("Kembali").performClick()
        listOf("Beranda", "Riwayat", "Panduan", "Statistik", "Profil").forEach { label ->
            compose.onNode(tabs and hasText(label)).performClick().assertIsSelected()
            compose.onAllNodes(tabs).fetchSemanticsNodes().forEach { node ->
                val text = node.config[SemanticsProperties.Text].joinToString { it.text }
                compose.onNode(tabs and hasText(text)).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
                val labelLayouts = mutableListOf<TextLayoutResult>()
                compose.onNode(hasText(text) and hasAnyAncestor(tabs), useUnmergedTree = true)
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(labelLayouts) }
                val labelLayout = labelLayouts.single()
                assertTrue("Navigation label must remain whole and visible: $text; lines=${labelLayout.lineCount}, " +
                    "size=${labelLayout.size}, paragraph=${labelLayout.multiParagraph.width}x${labelLayout.multiParagraph.height}, " +
                    "overflowWidth=${labelLayout.didOverflowWidth}, overflowHeight=${labelLayout.didOverflowHeight}, " +
                    "constraints=${labelLayout.layoutInput.constraints}",
                    labelLayout.lineCount == 1 && !labelLayout.hasVisualOverflow)
            }
            compose.onNodeWithContentDescription("Catat atau scan sampah").performClick()
            compose.onNodeWithText("Catat sampahmu").assertIsDisplayed()
            if (label == "Beranda") capture("capture-sheet")
            compose.onNode(hasText("Input manual") and hasAnyAncestor(hasTestTag("capture-sheet"))).performScrollTo().performClick()
            compose.onAllNodes(tabs).assertCountEquals(0)
            compose.onNodeWithText("Catat aktivitas").assertIsDisplayed()
            compose.onNodeWithText("Simpan contoh").assertDoesNotExist()
            compose.onNodeWithText("Simpan catatan").performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription("Kembali").performClick()
            compose.onNode(tabs and hasText(label)).assertIsSelected()
        }
        compose.onNode(tabs and hasText("Profil")).performClick()
        compose.onNodeWithText("Pengaturan").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Tema aplikasi").performScrollTo().performClick()
        compose.onNode(hasText("Gelap") and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithText("Tutup").performClick()
        compose.onNodeWithContentDescription("Kembali").performClick()
        compose.onNode(tabs and hasText("Beranda")).performClick()
        waitForHome()
        capture("home-dark")
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        waitForHome()
        compose.onNodeWithText("Lihat data contoh").assertDoesNotExist()
    }

    private fun waitForHome() {
        compose.waitUntil(30_000) { compose.onAllNodesWithText("650 g").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("650 g").performScrollTo().assertIsDisplayed()
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
