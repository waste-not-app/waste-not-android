package com.example.wastenot.ui.main

import android.graphics.Bitmap
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.BuildConfig
import com.example.wastenot.data.dummy.dummyProgress
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.model.User
import com.example.wastenot.model.UserProgress
import com.example.wastenot.theme.*
import java.io.File
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ProfileDesignTest {
    @get:Rule val compose = createComposeRule()
    private val user = User("owner", "Wulan Wijaya", "wulan@example.com", ZoneId.of("Asia/Jakarta"), null)

    @Test fun emptyAccountOffersAWorkingRecordSettingsAndLogoutAction() {
        var records = 0; var settings = 0; var exits = 0
        show { ProfileScreen(user, dummyProgress(emptyList(), java.time.LocalDate.of(2026, 10, 22)), false, null, PaddingValues(), {}, { records++ }, { settings++ }, { exits++ }) }
        compose.onNodeWithText("0 XP total").assertExists()
        compose.onNodeWithText("100 XP lagi ke level 2").assertExists()
        compose.onNodeWithText("WW").assertExists()
        capture("profile-empty")
        compose.onNodeWithText("Catat aktivitas").performScrollTo().performClick()
        compose.onNodeWithText("Pengaturan").performScrollTo().performClick()
        compose.onNodeWithText("Keluar").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, records); assertEquals(1, settings); assertEquals(1, exits) }
        compose.onNodeWithText("Achievement").assertDoesNotExist()
    }

    @Test fun missingProgressDoesNotBecomeZeroOrReuseThePreviousIdentity() {
        var current by mutableStateOf(user)
        show { ProfileScreen(current, null, false, ApiFailureKind.NETWORK, PaddingValues(), {}, {}, {}, {}) }
        compose.onNodeWithText("0 XP total").assertDoesNotExist()
        compose.runOnIdle { current = user.copy(id = "other", name = "Nadia", email = "nadia@example.com") }
        compose.onNodeWithText("Wulan Wijaya").assertDoesNotExist()
        compose.onNodeWithText("wulan@example.com").assertDoesNotExist()
        compose.onNodeWithText("nadia@example.com").assertExists()
        capture("profile-error")
    }

    @Test fun reportedStreaksRenderInBothThemesAndMissingValuesStayUnknown() {
        var dark by mutableStateOf(false)
        var progress by mutableStateOf<UserProgress?>(UserProgress(9, 90, 1, 90, 10, 3, 7, true))
        compose.setContent { WasteNotTheme(dark) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                ProfileScreen(user, progress, false, null, PaddingValues(), {}, {}, {}, {})
            }
        } }
        for (theme in listOf(false, true)) {
            compose.runOnIdle { dark = theme }
            compose.onNodeWithText("3 hari beruntun").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Kamu sudah mencatat hari ini.").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Terbaik: 7 hari").performScrollTo().assertIsDisplayed()
            captureUiPreview(compose, "S14-streak-${if (theme) "dark" else "light"}", "backend-contract-preview")
        }
        compose.runOnIdle { progress = progress!!.copy(currentStreak = null) }
        compose.onNodeWithText("3 hari beruntun").assertDoesNotExist()
        compose.onNodeWithText("Kamu sudah mencatat hari ini.").assertDoesNotExist()
        compose.onNodeWithText("Terbaik: 7 hari").assertExists()
        compose.runOnIdle { progress = progress!!.copy(currentStreak = 3, bestStreak = null, hasLoggedToday = false) }
        compose.onNodeWithText("3 hari beruntun").assertExists()
        compose.onNodeWithText("Belum mencatat hari ini.").assertExists()
        compose.onNodeWithText("Terbaik:", substring = true).assertDoesNotExist()
        compose.runOnIdle { progress = UserProgress(0, 0, 1, 0, 100, 0, 0, false) }
        compose.onNodeWithText("0 hari beruntun").assertExists()
        compose.onNodeWithText("Terbaik: 0 hari").assertExists()
        compose.onNodeWithText("Belum mencatat hari ini.").assertDoesNotExist()
        captureUiPreview(compose, "S14-streak-zero", "backend-contract-preview")
        compose.runOnIdle { progress = progress!!.copy(currentStreak = null, bestStreak = null) }
        compose.onNodeWithText("Hari berturut-turut").assertDoesNotExist()
        compose.onAllNodesWithText("0 hari", substring = true).assertCountEquals(0)
        captureUiPreview(compose, "S14-streak-unknown", "backend-contract-preview")
    }

    @Test fun settingsHasTheRealVersionReadOnlyZoneAndNoInactiveFeatureLinks() {
        var calls = 0
        show { SettingsScreen(user, ThemeMode.DARK, PaddingValues(), { calls++ }) }
        compose.onNodeWithContentDescription("Tema aplikasi").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, calls) }
        capture("settings-theme")
        compose.onNodeWithText("Versi ${BuildConfig.VERSION_NAME}").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Zona akun ditetapkan saat pendaftaran.").assertExists()
        compose.onNodeWithText("Ringkasan ini bukan kebijakan privasi resmi.", substring = true).assertExists()
        compose.onNodeWithText("Pengingat harian").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Achievement").assertDoesNotExist()
        capture("settings-privacy")
    }

    private fun show(content: @Composable () -> Unit) {
        val dark = InstrumentationRegistry.getArguments().getString("profileDark") == "true"
        compose.setContent { WasteNotTheme(dark) { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() } } }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "profile-qa").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
