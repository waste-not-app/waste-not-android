package com.example.wastenot.ui.main

import android.os.Debug
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.data.demo.DemoActivityStore
import com.example.wastenot.data.dummy.fixtureLogPage
import com.example.wastenot.data.network.ApiFailure
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.repository.ActivityStore
import com.example.wastenot.model.*
import com.example.wastenot.theme.LocalWasteNotGradients
import com.example.wastenot.theme.WasteNotTheme
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.After
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HistoryDesignTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.of(2026, 9, 21)
    private val owner = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
    @After fun clear() { owner.viewModelStore.clear() }
    private val records = (1..25).map { index ->
        WasteLog("record-${index.toString().padStart(2, '0')}", if (index % 2 == 0) WasteCategory.PLASTIC else WasteCategory.PAPER,
            if (index % 3 == 0) DisposalMethod.REDUCE else DisposalMethod.RECYCLE, Quantity.Count.of(index),
            if (index <= 20) today else today.minusDays(1), ZoneId.of("Asia/Jakarta"), "Note $index", 1, Instant.EPOCH, Instant.EPOCH)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    private fun show(
        restoration: StateRestorationTester? = null,
        dark: State<Boolean>? = null,
        notice: MutableState<Boolean>? = null,
        dataset: List<WasteLog> = records,
        pageFailure: (String?) -> ApiFailure? = { null },
        onDetail: (String) -> Unit = {},
    ) {
        val source = object : ActivityStore by DemoActivityStore(ZoneId.of("Asia/Jakarta")) {
            override suspend fun page(category: WasteCategory?, method: DisposalMethod?, dates: LogDateRange?, cursor: String?): ApiResult<List<WasteLog>> {
                pageFailure(cursor)?.let { return ApiResult.Failure(it) }
                return fixtureLogPage(dataset, category, method, dates, cursor)
            }
        }
        val vm = ViewModelProvider.create(owner, viewModelFactory { initializer { HistoryViewModel(source, SavedStateHandle()) } })[HistoryViewModel::class]
        val content: @Composable () -> Unit = {
            val data by vm.state.collectAsState()
            LaunchedEffect(vm) { vm.load() }
            WasteNotTheme(darkTheme = dark?.value ?: false) {
                var open by rememberSaveable { mutableStateOf(false) }
                Column(Modifier.fillMaxSize().background(LocalWasteNotGradients.current.canvas).systemBarsPadding()) {
                    TopAppBar(title = { Text("History") }, actions = {
                        TextButton(onClick = { open = true }) { Text("Filter history") }
                    })
                    HistoryScreen(data, PaddingValues(), {}, onDetail, vm::refresh, vm::filter, vm::more,
                        Modifier.weight(1f).fillMaxWidth(), today, open, { open = true }, { open = false },
                        deletedNotice = notice?.value ?: false, onDismissNotice = { notice?.value = false })
                }
            }
        }
        if (restoration == null) compose.setContent(content) else restoration.setContent(content)
    }

    private fun open() { compose.onNodeWithText("Filter history").performClick() }
    private fun chooseCategory(label: String) {
        compose.onNodeWithContentDescription("Category").performScrollTo().performClick()
        compose.onNode(hasText(label) and hasAnyAncestor(isPopup())).performClick()
    }
    private fun apply() { compose.onNodeWithText("Apply filters").performScrollTo().performClick() }
    private fun chooseDate(index: Int, day: String) {
        compose.onAllNodesWithText("Choose a date")[index].performScrollTo().performClick()
        val label = if (day == "21") "Monday, September 21, 2026" else "Monday, September 14, 2026"
        compose.onNodeWithText(label).performScrollTo().performClick()
        compose.onNodeWithText("Apply", substring = false).performClick()
    }
    private fun listText(text: String) { compose.onNodeWithTag("history-list").performScrollToNode(hasText(text)) }

    @Test fun draftCancelResetApplyAndRotationKeepTheirSeparateMeaning() {
        val restoration = StateRestorationTester(compose)
        show(restoration)
        compose.onNodeWithText("Showing 20 logs").assertIsDisplayed()
        open(); chooseCategory("Plastic")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNode(hasText("Plastic") and hasAnyAncestor(hasTestTag("history-filter-sheet"))).assertExists()
        compose.onNodeWithText("Cancel").performScrollTo().performClick()
        compose.onNodeWithText("Showing 20 logs").assertIsDisplayed()
        open(); chooseCategory("Plastic"); apply()
        compose.onNodeWithText("Showing 12 matching logs").assertIsDisplayed()
        open(); compose.onNodeWithText("Reset").performScrollTo().performClick()
        compose.onNodeWithText("Cancel").performScrollTo().performClick()
        compose.onNodeWithText("Showing 12 matching logs").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Showing 12 matching logs").assertIsDisplayed()
        open(); compose.onNodeWithText("Reset").performScrollTo().performClick(); apply()
        compose.onNodeWithText("Showing 20 logs").assertIsDisplayed()
    }

    @Test fun incompleteAndReversedDatesStayInSheetUntilCorrected() {
        show()
        open(); chooseDate(0, "21"); apply()
        compose.onNodeWithText("Fill in both dates, or leave both empty.").performScrollTo().assertIsDisplayed()
        chooseDate(0, "14"); apply()
        compose.onNodeWithText("The start date can't be after the end date.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Reset").performScrollTo().performClick()
        chooseDate(0, "21"); chooseDate(0, "21"); apply()
        compose.onNodeWithText("Showing 20 matching logs").assertIsDisplayed()
        compose.onNodeWithText("Today").assertIsDisplayed()
        listText("Log manually")
        compose.onNodeWithText("Yesterday").assertDoesNotExist()
    }

    @Test fun paginationRestoresScrollAndShowsGroupedRowsInBothThemes() {
        val restoration = StateRestorationTester(compose)
        val dark = mutableStateOf(false)
        var selected: String? = null
        show(restoration, dark) { selected = it }
        capture("history-light")
        listText("Load more logs")
        compose.onNodeWithText("Load more logs").performScrollTo().performClick()
        listText("Note 21")
        compose.onNodeWithText("Note 21").performClick()
        assertEquals("record-21", selected)
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Note 21").assertIsDisplayed()
        listText("You've reached the end of your logs.")
        compose.onNodeWithText("Load more logs").assertDoesNotExist()
        listText("Yesterday")
        compose.onNodeWithText("Yesterday").assertIsDisplayed()
        listText("Showing 25 logs")
        compose.runOnIdle { dark.value = true }
        capture("history-dark")
        open(); capture("filter-dark")
        compose.onNodeWithText("Apply filters").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Cancel").performScrollTo().performClick()
        compose.runOnIdle { dark.value = false }
        open(); capture("filter-light")
    }

    @Test fun deletionNoticeStaysVisibleAboveAScrolledHistory() {
        val notice = mutableStateOf(false)
        show(notice = notice)
        listText("Log manually")
        compose.runOnIdle { notice.value = true }
        compose.onNodeWithText("Log deleted.").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Log deleted.").assertDoesNotExist()
        compose.onNodeWithText("Log manually").assertIsDisplayed()
    }

    @Test fun measuresNativeHistoryLoadingAndScrollingOneThousandRecords() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("historyBenchmark") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val directory = File(context.getExternalFilesDir(null), "history-1000").apply { mkdirs() }
        fun graphics(suffix: String = ""): String = ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand("dumpsys gfxinfo ${context.packageName}$suffix")
        ).bufferedReader().use { it.readText() }

        val dataset = (1..1_000).map { index ->
            records.first().copy(id = "record-${index.toString().padStart(4, '0')}", notes = "Note $index")
        }
        val requests = mutableListOf<String?>()
        show(dataset = dataset, pageFailure = { cursor ->
            requests += cursor
            if (requests.size == 2) ApiFailure(ApiFailureKind.NETWORK) else null
        })
        val list = compose.onNodeWithTag("history-list")
        compose.onNodeWithText("Showing 20 logs").assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf(null), requests) }

        graphics(" reset")
        val startPss = Debug.getPss()
        val startedAt = SystemClock.elapsedRealtime()
        list.performScrollToIndex(22)
        compose.onNodeWithText("Load more logs").performClick()
        compose.onNodeWithText("Couldn't load more logs.").assertIsDisplayed()
        list.performScrollToIndex(0)
        compose.onNodeWithText("Showing 20 logs").assertIsDisplayed()
        list.performScrollToIndex(22)
        compose.onNodeWithText("Try again").performClick()

        for (loaded in 40 until 1_000 step 20) {
            list.performScrollToIndex(loaded + 2)
            compose.onNodeWithText("Load more logs").performClick()
        }
        list.performScrollToIndex(1_002)
        compose.onNodeWithText("You've reached the end of your logs.").assertIsDisplayed()
        list.performScrollToIndex(1_001)
        compose.onNodeWithText("Note 1").assertIsDisplayed()
        list.performScrollToIndex(0)
        compose.onNodeWithText("Showing 1.000 logs").assertIsDisplayed()
        compose.onNodeWithText("Note 1000").assertIsDisplayed()
        repeat(4) { list.performTouchInput { swipeUp() } }
        repeat(4) { list.performTouchInput { swipeDown() } }
        list.performScrollToIndex(0)
        compose.runOnIdle {
            assertEquals(51, requests.size)
            assertEquals(requests[1], requests[2])
            assertEquals(50, requests.distinct().size)
        }
        val elapsedMs = SystemClock.elapsedRealtime() - startedAt
        val endPss = Debug.getPss()
        File(directory, "gfxinfo.txt").writeText(graphics())
        File(directory, "result.txt").writeText(
            "records=1000\nrequests=51\nunique_cursors=50\nelapsed_ms=$elapsedMs\n" +
                "start_pss_kib=$startPss\nend_pss_kib=$endPss\n"
        )
        captureUiPreview(compose, "history-1000", "history-1000", capturePanel = false)
    }

    private fun capture(name: String) {
        captureUiPreview(compose, name, "history-pagination", capturePanel = false)
    }
}
