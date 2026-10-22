package com.example.wastenot.ui.main

import android.graphics.Bitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.MainActivity
import java.io.File
import java.util.UUID
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource

class ProfileNavigationTest {
    @get:Rule(order = 0) val fixtures = object : ExternalResource() {
        override fun before() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val freshId = UUID.nameUUIDFromBytes("profile.zero@example.invalid".toByteArray(Charsets.UTF_8))
            listOf("dummy_session", "dummy_logs_dummy-owner", "dummy_logs_$freshId", "appearance").forEach {
                context.getSharedPreferences(it, android.content.Context.MODE_PRIVATE).edit().clear().commit()
            }
        }
    }
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    private val tabs = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    @Test fun registeredAccountStartsAtZeroAndTheOriginalAccountsLogsStayAvailable() {
        compose.waitUntil(30_000) { compose.onAllNodesWithText("650 g").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(tabs and hasText("Profil")).performClick()
        logout()
        compose.onNodeWithText("Belum punya akun? Daftar").performScrollTo().performClick()
        compose.onNode(hasText("Nama") and hasSetTextAction()).performScrollTo().performTextReplacement("Wulan Wijaya")
        compose.onNode(hasText("Email") and hasSetTextAction()).performScrollTo().performTextReplacement("profile.zero@example.invalid")
        compose.onNode(hasText("Kata sandi") and hasSetTextAction()).performScrollTo().performTextReplacement("fixture-password-12")
        compose.onNode(hasText("Konfirmasi kata sandi") and hasSetTextAction()).performScrollTo().performTextReplacement("fixture-password-12")
        compose.onNodeWithText("Buat akun").performScrollTo().performClick()
        compose.waitUntil(30_000) { compose.onAllNodes(tabs).fetchSemanticsNodes().size == 5 }
        compose.onNode(tabs and hasText("Profil")).performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("XP total", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("0 XP total").performScrollTo().assertIsDisplayed()
        capture("profile-new-account")
        compose.onNodeWithText("Catat aktivitas").performScrollTo().performClick()
        compose.onNodeWithText("Cara pengelolaan").assertExists()
        compose.onNodeWithContentDescription("Kembali").performClick()
        compose.onNodeWithText("0 XP total").assertExists()
        logout()
        compose.onNode(hasText("Email") and hasSetTextAction()).performScrollTo().performTextReplacement("pengguna@example.invalid")
        compose.onNode(hasText("Kata sandi") and hasSetTextAction()).performScrollTo().performTextReplacement("fixture-password-12")
        compose.onNodeWithText("Masuk").performScrollTo().performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithText("650 g").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(tabs and hasText("Profil")).performClick()
        compose.onNodeWithText("50 XP dari 5 catatan aktif").assertExists()
        logout()
        compose.onNode(hasText("Email") and hasSetTextAction()).performScrollTo().performTextReplacement("profile.zero@example.invalid")
        compose.onNode(hasText("Kata sandi") and hasSetTextAction()).performScrollTo().performTextReplacement("fixture-password-12")
        compose.onNodeWithText("Masuk").performScrollTo().performClick()
        compose.waitUntil(30_000) { compose.onAllNodes(tabs).fetchSemanticsNodes().size == 5 }
        compose.onNode(tabs and hasText("Profil")).performClick()
        compose.onNodeWithText("Wulan Wijaya").assertIsDisplayed()
        compose.onNodeWithText("Asia/Jakarta").assertExists()
        compose.onNodeWithText("0 XP total").assertExists()
        capture("profile-returning-account")
    }

    @Test fun settingsBackThemePersistenceAndLogoutCancelKeepTheAccount() {
        compose.waitUntil(30_000) { compose.onAllNodesWithText("650 g").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(tabs and hasText("Profil")).performClick()
        compose.onNodeWithText("50 XP dari 5 catatan aktif").assertExists()
        capture("profile-app-light")
        compose.onNodeWithText("Pengaturan").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Kembali").assertExists()
        compose.onNodeWithContentDescription("Tema aplikasi").performScrollTo().performClick()
        compose.onNode(hasText("Gelap") and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithText("Tutup").performClick()
        capture("settings-app-dark")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Gelap").assertExists()
        compose.onNodeWithContentDescription("Kembali").performClick()
        compose.onNode(tabs and hasText("Profil")).assertIsSelected()
        compose.onNodeWithText("50 XP total").performScrollTo().assertIsDisplayed()
        capture("profile-app-dark")
        compose.onNodeWithText("Keluar").performScrollTo().performClick()
        compose.onNodeWithText("Keluar?").assertIsDisplayed()
        compose.onNodeWithText("Batal").performClick()
        compose.onNodeWithText("50 XP total").assertExists()
        compose.onNode(tabs and hasText("Beranda")).performClick()
        compose.onNodeWithText("650 g").assertExists()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "profile-qa").apply { mkdirs() }
        val dark = name.endsWith("dark")
        val minima = if (dark) intArrayOf(20, 35, 26) else intArrayOf(220, 235, 216)
        compose.waitUntil(10_000) {
            val frame = instrumentation.uiAutomation.takeScreenshot()
            val pixel = frame.getPixel(1, frame.height / 2)
            frame.recycle()
            listOf(16, 8, 0).mapIndexed { i, shift -> (pixel shr shift and 255) >= minima[i] }.all { it }
        }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun logout() {
        compose.onNodeWithText("Keluar").performScrollTo().performClick()
        compose.onNode(hasText("Keluar") and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Belum punya akun? Daftar").fetchSemanticsNodes().isNotEmpty() }
    }
}
