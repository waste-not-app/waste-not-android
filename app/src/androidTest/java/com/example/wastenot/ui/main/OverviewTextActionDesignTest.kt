package com.example.wastenot.ui.main

import android.graphics.Bitmap
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.model.*
import com.example.wastenot.theme.WasteNotTheme
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class OverviewTextActionDesignTest {
    @get:Rule val compose = createComposeRule()

    @Test fun recentActivitiesAndItsHistoryActionKeepReadableSpaceInBothThemes() {
        val date = LocalDate.of(2026, 10, 26)
        val zone = ZoneId.of("Asia/Jakarta")
        val user = User("owner", "Pengguna", "pengguna@example.com", zone, null)
        val log = WasteLog("log", WasteCategory.METAL, DisposalMethod.RECYCLE, Quantity.Weight.of(250), date, zone, "", 1, Instant.EPOCH, Instant.EPOCH)
        var dark by mutableStateOf(false)
        var histories = 0
        compose.setContent { WasteNotTheme(dark) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                OverviewScreen(user, WorkspaceUiState(records = listOf(log), loading = false), true, date, PaddingValues(),
                    {}, { histories++ }, {}, {})
            }
        } }
        for (theme in listOf(false, true)) {
            compose.runOnIdle { dark = theme }
            val action = compose.onNodeWithText("Lihat riwayat").performScrollTo().assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText("Aktivitas terbaru", useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.uiAutomation.waitForIdle(300, 5_000)
            instrumentation.uiAutomation.takeScreenshot()?.recycle()
            android.os.SystemClock.sleep(150)
            val image = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
            val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "text-action-qa").apply { mkdirs() }
            File(directory, "overview-text-${if (theme) "dark" else "light"}.png").outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            image.recycle()
            val title = layouts.single()
            assertTrue("History action must not squeeze the heading into ${title.lineCount} lines", title.lineCount <= 2)
            assertFalse(title.hasVisualOverflow)
            action.performTouchInput { click() }
        }
        compose.runOnIdle { assertEquals(2, histories) }
    }
}
