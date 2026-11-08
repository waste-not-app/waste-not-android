package com.example.wastenot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.wastenot.theme.ThemePreferences
import com.example.wastenot.theme.WasteNotTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    enableEdgeToEdge()
    setContent {
      val preferences = remember { ThemePreferences(getSharedPreferences("appearance", MODE_PRIVATE)) }
      var mode by rememberSaveable { mutableStateOf(preferences.read()) }
      val dark = mode.isDark(isSystemInDarkTheme())
      SideEffect {
        enableEdgeToEdge(
          statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
          navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.argb(230, 255, 255, 255), android.graphics.Color.argb(128, 0, 0, 0)) { dark },
        )
        if (android.os.Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
      }
      WasteNotTheme(darkTheme = dark) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          val container = (application as WasteNotApplication).container
          MainNavigation(container.accountSession, container.core, container.guides, container.ai, mode, { mode = it; preferences.write(it) }, Modifier.fillMaxSize(),
              activityStoreFactory = container::activityStore, scanRepositoryFactory = container::scanRepository)
        }
      }
    }
  }
}
