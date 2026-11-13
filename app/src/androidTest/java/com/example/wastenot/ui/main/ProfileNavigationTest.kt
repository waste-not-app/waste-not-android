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
import org.junit.Assert.assertEquals
import org.junit.rules.ExternalResource

class ProfileNavigationTest {
    @get:Rule(order = -1) val navigationData = com.example.wastenot.NavigationFixtureRule()
    @get:Rule(order = 0) val fixtures = object : ExternalResource() {
        override fun before() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val freshId = UUID.nameUUIDFromBytes("profile.zero@example.invalid".toByteArray(Charsets.UTF_8))
            com.example.wastenot.clearEditorDraftFixtures(context)
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
        chooseTheme("Terang")
        capture("auth-login-light")
        compose.onNodeWithText("Masuk").performScrollTo()
        capture("auth-login-form-light")
        chooseTheme("Gelap")
        capture("auth-login-dark")
        chooseTheme("Terang")
        compose.onNodeWithText("Belum punya akun? Daftar").performScrollTo().performClick()
        capture("auth-register-light")
        chooseTheme("Gelap")
        capture("auth-register-dark")
        chooseTheme("Terang")
        compose.onNodeWithText("Buat akun").performScrollTo()
        capture("auth-register-form-light")
        compose.onNode(hasContentDescription("Nama") and hasSetTextAction()).performScrollTo().performTextReplacement("Wulan Wijaya")
        compose.onNode(hasContentDescription("Email") and hasSetTextAction()).performScrollTo().performTextReplacement("profile.zero@example.invalid")
        compose.onNode(hasContentDescription("Kata sandi") and hasSetTextAction()).performScrollTo().performTextReplacement("fixture-password-12")
        compose.onNode(hasContentDescription("Konfirmasi kata sandi") and hasSetTextAction()).performScrollTo().performTextReplacement("fixture-password-12")
        compose.onNode(hasContentDescription("Nama") and hasSetTextAction()).assertTextContains("Wulan Wijaya")
        compose.onNode(hasContentDescription("Email") and hasSetTextAction()).assertTextContains("profile.zero@example.invalid")
        listOf("Kata sandi", "Konfirmasi kata sandi").forEach { label ->
            val rendered = compose.onNode(hasContentDescription(label) and hasSetTextAction()).fetchSemanticsNode()
                .config[SemanticsProperties.EditableText].text
            assertEquals("Masked password length for $label", 19, rendered.length)
        }
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.waitForIdle()
        compose.onNodeWithText("Buat akun").performScrollTo().performClick()
        capture("auth-register-submitted-light")
        listOf("Nama perlu", "Masukkan alamat email", "Kata sandi perlu", "Konfirmasi kata sandi belum sama", "Email ini sudah digunakan", "Permintaan belum berhasil").forEach {
            compose.onNodeWithText(it, substring = true).assertDoesNotExist()
        }
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
        compose.onNode(hasContentDescription("Email") and hasSetTextAction()).performScrollTo().performTextReplacement("pengguna@example.invalid")
        compose.onNode(hasContentDescription("Kata sandi") and hasSetTextAction()).performScrollTo().performTextReplacement("fixture-password-12")
        compose.onNodeWithText("Masuk").performScrollTo().performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithText("650 g").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(tabs and hasText("Profil")).performClick()
        compose.onNodeWithText("50 XP dari 5 catatan aktif").assertExists()
        logout()
        compose.onNode(hasContentDescription("Email") and hasSetTextAction()).performScrollTo().performTextReplacement("profile.zero@example.invalid")
        compose.onNode(hasContentDescription("Kata sandi") and hasSetTextAction()).performScrollTo().performTextReplacement("fixture-password-12")
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
        compose.onNodeWithText("Pengaturan").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Kembali").assertExists()
        compose.onNodeWithContentDescription("Tema aplikasi").performScrollTo().performClick()
        compose.onNode(hasText("Terang") and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithText("Tutup").performClick()
        compose.onNodeWithContentDescription("Kembali").performClick()
        capture("profile-app-light")
        compose.onNodeWithText("Pengaturan").performScrollTo().performClick()
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
        compose.onNodeWithText("Keluar dari akun?").assertIsDisplayed()
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
        val minima = if (dark) intArrayOf(15, 30, 20) else intArrayOf(210, 225, 210)
        var lastPixel = 0
        try {
            compose.waitUntil(10_000) {
                val frame = instrumentation.uiAutomation.takeScreenshot()
                lastPixel = frame.getPixel(20, frame.height / 2)
                frame.recycle()
                listOf(16, 8, 0).mapIndexed { i, shift -> (lastPixel shr shift and 255) >= minima[i] }.all { it }
            }
        } catch (t: Throwable) {
            val r = android.graphics.Color.red(lastPixel)
            val g = android.graphics.Color.green(lastPixel)
            val b = android.graphics.Color.blue(lastPixel)
            throw AssertionError("capture($name) failed pixel(1,h/2)=($r,$g,$b) vs minima=${minima.toList()}", t)
        }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun chooseTheme(label: String) {
        compose.onNodeWithContentDescription("Tampilan").performScrollTo().performClick()
        compose.onNode(hasText(label) and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithText("Tutup").performClick()
    }

    private fun logout() {
        compose.onNodeWithText("Keluar").performScrollTo().performClick()
        compose.onNode(hasText("Keluar") and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Belum punya akun? Daftar").fetchSemanticsNodes().isNotEmpty() }
    }
}
