package com.example.wastenot.theme

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.MainActivity
import com.example.wastenot.ThemeFixtureRule
import com.example.wastenot.WasteNotApplication
import com.example.wastenot.ui.main.captureUiPreview
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ThemeNavigationTest {
    @get:Rule(order = 0) val appearance = ThemeFixtureRule()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    private fun openPicker() {
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Display").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Display").performClick()
    }
    @Test fun actualApplicationAppliesAndPersistsEachChoiceAcrossActivityRecreation() = runBlocking {
        val store = (compose.activity.application as WasteNotApplication).container.appearance
        for ((label, mode) in listOf("Dark" to ThemeMode.DARK, "Light" to ThemeMode.LIGHT, "Follow system" to ThemeMode.SYSTEM)) {
            openPicker()
            compose.onNodeWithText(label).performClick().assertIsSelected()
            assertEquals(mode, store.modes.first { it == mode })
            captureUiPreview(compose, "picker-${mode.name.lowercase()}", "theme-datastore")
            compose.onNodeWithText("Close").performClick()
            compose.activityRule.scenario.recreate()
            openPicker()
            compose.onNodeWithText(label).assertIsSelected()
            compose.onNodeWithText("Close").performClick()
        }
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun shell(command: String) = android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command))
            .bufferedReader().use { it.readText().trim() }
        val previous = shell("cmd uimode night").substringAfter(':').trim()
        require(previous in setOf("yes", "no", "auto", "custom"))
        try {
            for ((night, name) in listOf("yes" to "system-dark", "no" to "system-light")) {
                shell("cmd uimode night $night")
                val expected = if (night == "yes") android.content.res.Configuration.UI_MODE_NIGHT_YES else android.content.res.Configuration.UI_MODE_NIGHT_NO
                compose.waitUntil(15_000) {
                    (InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK) == expected
                }
                compose.waitForIdle()
                openPicker()
                compose.onNodeWithText("Follow system").assertIsSelected()
                assertEquals(ThemeMode.SYSTEM, store.modes.first())
                captureUiPreview(compose, name, "theme-datastore")
                compose.onNodeWithText("Close").performClick()
            }
        } finally { shell("cmd uimode night $previous") }
    }
}
