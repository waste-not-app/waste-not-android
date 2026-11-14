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

        compose.onNodeWithText("Catat kebiasaan tanpa ribet.").assertIsDisplayed()
        compose.onNodeWithText("Lewati").assertIsDisplayed()
        compose.onNodeWithText("Lanjut").performClick()

        compose.onNodeWithText("Foto sampah, cek cara tangani.").assertIsDisplayed()
        compose.onNodeWithText("Lanjut").performClick()

        compose.onNodeWithText("Pantau tren dari waktu ke waktu.").assertIsDisplayed()
        compose.onNodeWithText("Mulai sekarang").assertIsDisplayed().performClick()
        compose.onNodeWithText("Sudah punya akun? Masuk").assertIsDisplayed().performClick()
        compose.onNodeWithText("Lewati").performClick()
        compose.onNodeWithContentDescription("Tampilan").performClick()

        compose.runOnIdle { dark = true }
        compose.onNodeWithText("Pantau tren dari waktu ke waktu.").assertIsDisplayed()

        assertEquals(1, starts)
        assertEquals(1, logins)
        assertEquals(1, skips)
        assertEquals(1, themes)
    }
}
