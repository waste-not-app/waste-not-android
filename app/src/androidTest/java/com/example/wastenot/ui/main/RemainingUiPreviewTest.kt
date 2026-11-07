package com.example.wastenot.ui.main

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.data.repository.SignOutReason
import com.example.wastenot.model.*
import com.example.wastenot.theme.LocalWasteNotGradients
import com.example.wastenot.theme.WasteNotTheme
import com.example.wastenot.ui.auth.AuthScreen
import com.example.wastenot.ui.auth.AuthUiState
import com.example.wastenot.ui.auth.StartupScreen
import com.example.wastenot.ui.scan.*
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Renders isolated session/fallback states; route navigation is captured in ScreenKitReviewTest. */
class RemainingUiPreviewTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sessionFallbackAndConflictViewports() {
        var dark by mutableStateOf(false)
        var screen by mutableStateOf("S01")
        val date = LocalDate.of(2026, 11, 6)
        val log = WasteLog("preview", WasteCategory.PLASTIC, DisposalMethod.RECYCLE, Quantity.Weight.of(250),
            date, ZoneId.of("Asia/Jakarta"), "Botol sudah dibilas", 2, Instant.EPOCH, Instant.EPOCH)
        compose.setContent { WasteNotTheme(dark) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            key(screen, dark) {
                Box(Modifier.fillMaxSize().background(LocalWasteNotGradients.current.canvas)) {
                    when (screen) {
                        "S01", "S01-error" -> StartupScreen(screen.endsWith("error"), {}, {}, Modifier.fillMaxSize())
                        "S02", "S03" -> AuthScreen(screen == "S03", AuthUiState(timezone = ZoneId.of("Asia/Jakarta")),
                            false, SignOutReason.NONE, { _, _ -> }, {}, {}, {}, Modifier.fillMaxSize().safeDrawingPadding())
                        "S25" -> ScanSheet(ScanState(phase = ScanPhase.FAILURE, problem = ScanProblem.DETECTION),
                            {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, camera = {})
                        "D04" -> EditConflictDialog(EditorDraft.from(log, date).values, null, {}, {}, { _, _ -> })
                        "D04-compare" -> EditConflictDialog(EditorDraft.from(log.copy(version = 1, notes = "Draf milikku"), date).values,
                            ConflictReadState("request", log.id, latest = log), {}, {}, { _, _ -> })
                    }
                }
            }
            }
        } }
        for (theme in listOf(false, true)) {
            for (id in listOf("S01", "S01-error", "S02", "S03", "S25", "D04", "D04-compare")) {
                compose.runOnIdle { dark = theme; screen = id }
                val heading = when (id) {
                    "S01" -> "Menyiapkan catatanmu…"
                    "S01-error" -> "Belum bisa memeriksa sesi"
                    "S02" -> "Catat kebiasaanmu lagi."
                    "S03" -> "Mulai dari kebiasaan kecil."
                    "S25" -> "Scan belum tersedia"
                    else -> "Catatan telah berubah"
                }
                compose.onNodeWithText(heading).assertIsDisplayed()
                captureUiPreview(compose, "$id-${if (theme) "dark" else "light"}")
                if (id == "S03" || id == "D04-compare") {
                    compose.onNodeWithText(if (id == "S03") "Buat akun" else "Tetap di draf").performScrollTo().assertIsDisplayed()
                    captureUiPreview(compose, "$id-end-${if (theme) "dark" else "light"}")
                }
            }
        }
    }
}

internal fun captureUiPreview(compose: ComposeTestRule, name: String, directoryName: String = "remaining-ui-preview", capturePanel: Boolean = true) {
    compose.mainClock.advanceTimeByFrame()
    compose.waitForIdle()
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val directory = File(instrumentation.targetContext.getExternalFilesDir(null), directoryName).apply { mkdirs() }
    if (capturePanel) {
        val sheet = compose.onAllNodesWithTag("capture-sheet").fetchSemanticsNodes().isNotEmpty()
        val target = when {
            sheet -> compose.onNodeWithTag("capture-sheet")
            compose.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty() -> compose.onNode(isDialog())
            else -> compose.onAllNodes(isRoot()).onLast()
        }
        val panel = target.captureToImage().asAndroidBitmap()
        if (sheet) File(directory, "$name-panel.png").outputStream().use { check(panel.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        panel.recycle()
    }
    instrumentation.waitForIdleSync()
    instrumentation.uiAutomation.waitForIdle(300, 5_000)
    assertEquals(instrumentation.targetContext.packageName,
        instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString())
    // API 37 UiAutomation can retain an older display buffer across dialog/configuration changes.
    val image = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("screencap -p"))
        .use { checkNotNull(BitmapFactory.decodeStream(it)) }
    File(directory, "$name.png").outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    image.recycle()
}
