package com.example.wastenot

import android.os.Bundle
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.wastenot.theme.AppearanceProblemBanner
import com.example.wastenot.theme.ThemeMode
import com.example.wastenot.theme.ThemeViewModel
import com.example.wastenot.theme.WasteNotTheme

class MainActivity : ComponentActivity() {
    private var appearanceReady = false
    private var firstDraw: ViewTreeObserver.OnPreDrawListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as WasteNotApplication).container
        val factory = viewModelFactory { initializer { ThemeViewModel(container.appearance) } }
        val appearance = ViewModelProvider.create(this, factory)[ThemeViewModel::class]
        appearance.load()
        firstDraw = ViewTreeObserver.OnPreDrawListener { appearanceReady }
        window.decorView.viewTreeObserver.addOnPreDrawListener(firstDraw)
        setContent {
            val preference by appearance.state.collectAsStateWithLifecycle()
            val mode = preference.mode
            val dark = (mode ?: ThemeMode.SYSTEM).isDark(isSystemInDarkTheme())
            SideEffect {
                appearanceReady = mode != null
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.argb(230, 255, 255, 255), android.graphics.Color.argb(128, 0, 0, 0)) { dark },
                )
                window.isNavigationBarContrastEnforced = false
            }
            WasteNotTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (mode != null) Column(Modifier.fillMaxSize()) {
                        preference.failure?.let { AppearanceProblemBanner(it, appearance::retry) }
                        MainNavigation(container.accountSession, container.core, container.guides, container.ai,
                            mode, appearance::choose, Modifier.weight(1f),
                            activityStoreFactory = container::activityStore, scanRepositoryFactory = container::scanRepository)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        val observer = window.decorView.viewTreeObserver
        firstDraw?.let { if (observer.isAlive) observer.removeOnPreDrawListener(it) }
        super.onDestroy()
    }
}
