package com.example.wastenot.ui.main

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.model.*
import com.example.wastenot.theme.LocalWasteNotGradients
import com.example.wastenot.theme.WasteNotTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HistoryDesignTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.of(2026, 9, 21)
    private val records = (1..25).map { index ->
        WasteLog("record-${index.toString().padStart(2, '0')}", if (index % 2 == 0) WasteCategory.PLASTIC else WasteCategory.PAPER,
            if (index % 3 == 0) DisposalMethod.REDUCE else DisposalMethod.RECYCLE, Quantity.Count.of(index),
            if (index <= 20) today else today.minusDays(1), ZoneId.of("Asia/Jakarta"), "Catatan $index", 1, Instant.EPOCH, Instant.EPOCH)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    private fun show(restoration: StateRestorationTester? = null, dark: State<Boolean>? = null, notice: MutableState<Boolean>? = null, onDetail: (String) -> Unit = {}) {
        val content: @Composable () -> Unit = {
            WasteNotTheme(darkTheme = dark?.value ?: false) {
                var open by rememberSaveable { mutableStateOf(false) }
                Column(Modifier.fillMaxSize().background(LocalWasteNotGradients.current.canvas).systemBarsPadding()) {
                    TopAppBar(title = { Text("Riwayat") }, actions = {
                        TextButton(onClick = { open = true }) { Text("Filter riwayat") }
                    })
                    HistoryScreen(WorkspaceUiState(records, loading = false), PaddingValues(), {}, onDetail, {},
                        Modifier.weight(1f).fillMaxWidth(), today, open, { open = true }, { open = false },
                        deletedNotice = notice?.value ?: false, onDismissNotice = { notice?.value = false })
                }
            }
        }
        if (restoration == null) compose.setContent(content) else restoration.setContent(content)
    }

    private fun open() { compose.onNodeWithText("Filter riwayat").performClick() }
    private fun chooseCategory(label: String) {
        compose.onNodeWithContentDescription("Kategori").performScrollTo().performClick()
        compose.onNode(hasText(label) and hasAnyAncestor(isPopup())).performClick()
    }
    private fun apply() { compose.onNodeWithText("Terapkan filter").performScrollTo().performClick() }
    private fun chooseDate(index: Int, day: String) {
        compose.onAllNodesWithText("Pilih tanggal")[index].performScrollTo().performClick()
        val label = if (day == "21") "Monday, September 21, 2026" else "Monday, September 14, 2026"
        compose.onNodeWithText(label).performScrollTo().performClick()
        compose.onNodeWithText("Terapkan", substring = false).performClick()
    }
    private fun listText(text: String) { compose.onNodeWithTag("history-list").performScrollToNode(hasText(text)) }

    @Test fun draftCancelResetApplyAndRotationKeepTheirSeparateMeaning() {
        val restoration = StateRestorationTester(compose)
        show(restoration)
        compose.onNodeWithText("25 aktivitas tercatat.").assertIsDisplayed()
        open(); chooseCategory("Plastik")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNode(hasText("Plastik") and hasAnyAncestor(hasTestTag("history-filter-sheet"))).assertExists()
        compose.onNodeWithText("Batal").performScrollTo().performClick()
        compose.onNodeWithText("25 aktivitas tercatat.").assertIsDisplayed()
        open(); chooseCategory("Plastik"); apply()
        compose.onNodeWithText("12 aktivitas sesuai filter.").assertIsDisplayed()
        open(); compose.onNodeWithText("Reset pilihan").performScrollTo().performClick()
        compose.onNodeWithText("Batal").performScrollTo().performClick()
        compose.onNodeWithText("12 aktivitas sesuai filter.").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("12 aktivitas sesuai filter.").assertIsDisplayed()
        open(); compose.onNodeWithText("Reset pilihan").performScrollTo().performClick(); apply()
        compose.onNodeWithText("25 aktivitas tercatat.").assertIsDisplayed()
    }

    @Test fun incompleteAndReversedDatesStayInSheetUntilCorrected() {
        show()
        open(); chooseDate(0, "21"); apply()
        compose.onNodeWithText("Isi tanggal mulai dan akhir, atau kosongkan keduanya.").performScrollTo().assertIsDisplayed()
        chooseDate(0, "14"); apply()
        compose.onNodeWithText("Tanggal mulai tidak boleh melewati tanggal akhir.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Reset pilihan").performScrollTo().performClick()
        chooseDate(0, "21"); chooseDate(0, "21"); apply()
        compose.onNodeWithText("20 aktivitas sesuai filter.").assertIsDisplayed()
        compose.onNodeWithText("Hari ini").assertIsDisplayed()
        listText("Input manual")
        compose.onNodeWithText("Kemarin").assertDoesNotExist()
    }

    @Test fun paginationRestoresScrollAndShowsGroupedRowsInBothThemes() {
        val restoration = StateRestorationTester(compose)
        val dark = mutableStateOf(false)
        var selected: String? = null
        show(restoration, dark) { selected = it }
        capture("history-light")
        listText("20 dari 25 catatan ditampilkan")
        compose.onNodeWithText("Muat catatan berikutnya").performScrollTo().performClick()
        listText("Catatan 21")
        compose.onNodeWithText("Catatan 21").performClick()
        assertEquals("record-21", selected)
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Catatan 21").assertIsDisplayed()
        listText("25 dari 25 catatan ditampilkan")
        compose.onNodeWithText("Muat catatan berikutnya").assertDoesNotExist()
        listText("Kemarin")
        compose.onNodeWithText("Kemarin").assertIsDisplayed()
        listText("25 aktivitas tercatat.")
        compose.runOnIdle { dark.value = true }
        capture("history-dark")
        open(); capture("filter-dark")
        compose.onNodeWithText("Terapkan filter").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Batal").performScrollTo().performClick()
        compose.runOnIdle { dark.value = false }
        open(); capture("filter-light")
    }

    @Test fun deletionNoticeStaysVisibleAboveAScrolledHistory() {
        val notice = mutableStateOf(false)
        show(notice = notice)
        listText("Input manual")
        compose.runOnIdle { notice.value = true }
        compose.onNodeWithText("Catatan dihapus.").assertIsDisplayed()
        compose.onNodeWithText("Tutup").performClick()
        compose.onNodeWithText("Catatan dihapus.").assertDoesNotExist()
        compose.onNodeWithText("Input manual").assertIsDisplayed()
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
