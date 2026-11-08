package com.example.wastenot.theme

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.AppContainer
import com.example.wastenot.MainActivity
import com.example.wastenot.TestWasteNotApplication
import com.example.wastenot.data.repository.ThemeStore
import com.example.wastenot.ui.main.captureUiPreview
import java.io.IOException
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource

class ThemeFailureStartupTest {
    private val durable = MutableStateFlow(ThemeMode.DARK)
    private var failRead = true
    private var failWrite = false
    private val store = object : ThemeStore {
        override val modes: Flow<ThemeMode> get() = flow {
            if (failRead) throw IOException("appearance unavailable")
            emitAll(durable)
        }
        override suspend fun write(mode: ThemeMode) {
            if (failWrite) throw IOException("appearance unavailable")
            durable.value = mode
        }
    }
    @get:Rule(order = 0) val source = object : ExternalResource() {
        private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as TestWasteNotApplication
        override fun before() {
            assertNull(app.fixtureContainer)
            app.fixtureContainer = object : AppContainer(app) { override val appearance = store }
        }
        override fun after() { app.fixtureContainer = null }
    }
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    private fun waitFor(text: String) {
        compose.waitUntil(15_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun preferenceFailuresKeepSignInUsableAndRetryRecoversTheRequestedChoice() {
        val readFailure = "Pilihan tampilan belum bisa dibaca. Coba lagi."
        val writeFailure = "Pilihan tampilan belum bisa dipastikan tersimpan. Coba lagi."
        waitFor(readFailure)
        compose.onNodeWithContentDescription("Email").performScrollTo().assertIsDisplayed()
        captureUiPreview(compose, "read-failure", "theme-datastore")
        compose.runOnIdle { failRead = false }
        compose.onNodeWithText("Coba lagi").performClick()
        compose.waitUntil { compose.onAllNodesWithText(readFailure).fetchSemanticsNodes().isEmpty() }
        val themeButton = compose.onNodeWithContentDescription("Tampilan")
        if (!themeButton.isDisplayed()) themeButton.performScrollTo()
        themeButton.performClick()
        compose.onNodeWithText("Gelap").assertIsSelected()
        captureUiPreview(compose, "read-recovered-dark", "theme-datastore")
        compose.runOnIdle { failWrite = true }
        compose.onNodeWithText("Terang").performClick().assertIsSelected()
        compose.onNodeWithText("Tutup").performClick()
        waitFor(writeFailure)
        assertEquals(ThemeMode.DARK, durable.value)
        captureUiPreview(compose, "write-failure", "theme-datastore")
        compose.runOnIdle { failWrite = false }
        compose.onNodeWithText("Coba lagi").performClick()
        compose.waitUntil { compose.onAllNodesWithText(writeFailure).fetchSemanticsNodes().isEmpty() }
        assertEquals(ThemeMode.LIGHT, durable.value)
        captureUiPreview(compose, "write-recovered-light", "theme-datastore")
    }
}
