package com.example.wastenot.ui.main

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.MainActivity
import com.example.wastenot.data.dummy.DummyActivityStore
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.repository.ActivityInput
import com.example.wastenot.model.*
import java.io.File
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource

class EditConflictNavigationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @get:Rule(order = 0) val fixtures = object : ExternalResource() {
        override fun before() {
            listOf("dummy_session", "dummy_logs_dummy-owner", "appearance").forEach {
                check(context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit())
            }
        }
    }
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun conflictPreservesDraftAndRequiresReloadChoiceThenExplicitSave() {
        val source = DummyActivityStore(context.getSharedPreferences("dummy_logs_dummy-owner", Context.MODE_PRIVATE), ZoneId.of("Asia/Jakarta"))
        waitFor("650 g")
        lateinit var initial: WasteLog
        runTest { initial = (source.load() as ApiResult.Success).value.first {
            it.category == WasteCategory.PLASTIC && it.method == DisposalMethod.RECYCLE && it.quantity == Quantity.Weight.of(250)
        } }
        compose.onNodeWithText("250 g").performScrollTo().performClick()
        compose.onNodeWithText("Ubah catatan").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Berat").performScrollTo().performTextReplacement("300")
        compose.onNodeWithContentDescription("Catatan (opsional)").performScrollTo().performTextReplacement("Draf saya")
        val server = ActivityInput(WasteCategory.GLASS, DisposalMethod.TRASH, Quantity.Count.of(2), initial.activityDate, "Perangkat lain")
        runTest { assertTrue(source.update(initial.id, 1, server) is ApiResult.Success) }
        compose.onNodeWithText("Simpan perubahan").performScrollTo().performClick()
        waitFor("Catatan telah berubah")
        dialogText("Catatan telah berubah").performScrollTo().assertIsDisplayed()
        capture("edit-conflict-initial")
        dialogText("Muat versi terbaru").performScrollTo().performClick()
        waitFor("Versi terbaru · v2")
        dialogText("Perangkat lain").performScrollTo().assertIsDisplayed()
        dialogText("Drafmu").performScrollTo().assertIsDisplayed()
        dialogText("Draf saya").assertExists()
        capture("edit-conflict-compare")
        dialogText("Tetap di draf").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Catatan (opsional)").performScrollTo().assertTextContains("Draf saya").performTextReplacement("Draf diperiksa")
        compose.onNodeWithText("Simpan perubahan").assertDoesNotExist()
        compose.onNodeWithText("Muat versi terbaru").performScrollTo().performClick()
        dialogText("Muat versi terbaru").performScrollTo().performClick()
        waitFor("Versi terbaru · v2")
        dialogText("Gunakan seluruh isian drafku").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Kategori").performScrollTo().assertTextContains("Plastik")
        compose.onNodeWithContentDescription("Berat").performScrollTo().assertTextContains("300")
        compose.onNodeWithContentDescription("Catatan (opsional)").performScrollTo().assertTextContains("Draf diperiksa")
        runTest {
            val current = (source.get(initial.id) as ApiResult.Success).value
            assertEquals(2, current.version); assertEquals("Perangkat lain", current.notes)
            assertEquals(Quantity.Count.of(2), current.quantity)
        }
        capture("edit-conflict-draft-retained")
        compose.onNodeWithText("Simpan perubahan").performScrollTo().performClick()
        waitFor("Catatanmu sudah tersimpan. Tidak ada XP tambahan.")
        runTest {
            val current = (source.get(initial.id) as ApiResult.Success).value
            assertEquals(3, current.version); assertEquals("Draf diperiksa", current.notes)
            assertEquals(Quantity.Weight.of(300), current.quantity); assertEquals(WasteCategory.PLASTIC, current.category)
            assertEquals(5, (source.load() as ApiResult.Success).value.size)
        }
    }

    private fun waitFor(text: String) { compose.waitUntil(30_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
    private fun dialogText(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(isDialog()))
    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.waitForIdle(300, 5_000)
        val directory = File(context.getExternalFilesDir(null), "conflict-qa").apply { mkdirs() }
        val image = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }; image.recycle()
    }
}
