package com.example.wastenot.ui.auth

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.example.wastenot.theme.WasteNotTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OnboardingDesignTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun slidesAdvanceThroughAllThreePagesAndExposeSkipStartAndLoginActions() {
        var dark by mutableStateOf(false)
        var skips = 0
        var starts = 0
        var logins = 0
        var themes = 0
        compose.setContent {
            WasteNotTheme(dark) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    OnboardingScreen(
                        onSkip = { skips++ },
                        onStartRegister = { starts++ },
                        onStartLogin = { logins++ },
                        onTheme = { themes++ },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        compose.onNodeWithText("Log your habits without the hassle.").assertIsDisplayed()
        compose.onNodeWithText("Skip").assertIsDisplayed()
        compose.onNodeWithText("Next").performClick()

        compose.onNodeWithText("Snap your waste, see how to handle it.").assertIsDisplayed()
        compose.onNodeWithText("Next").performClick()

        compose.onNodeWithText("Watch your trends over time.").assertIsDisplayed()
        compose.onNodeWithText("Get started").assertIsDisplayed().performClick()
        compose.onNodeWithText("Already have an account? Sign in").assertIsDisplayed().performClick()
        compose.onNodeWithText("Skip").performClick()
        compose.onNodeWithContentDescription("Display").performClick()

        compose.runOnIdle { dark = true }
        compose.onNodeWithText("Watch your trends over time.").assertIsDisplayed()

        assertEquals(1, starts)
        assertEquals(1, logins)
        assertEquals(1, skips)
        assertEquals(1, themes)
    }
}
