package com.example.wastenot

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.wastenot.ui.home.HomeScreen
import com.example.wastenot.theme.ThemeMode
import com.example.wastenot.theme.ThemePicker
import com.github.skydoves.navgraph.annotations.NavGraphRoot

@NavGraphRoot
@Composable
fun MainNavigation(themeMode: ThemeMode, onThemeModeChange: (ThemeMode) -> Unit) {
    val backStack = rememberNavBackStack(Main)
    var choosingTheme by remember { mutableStateOf(false) }

    if (choosingTheme) ThemePicker(themeMode, onThemeModeChange, onDismiss = { choosingTheme = false })

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<Main> {
              Column(Modifier.safeDrawingPadding()) {
                TextButton(onClick = { choosingTheme = true }) { Text(stringResource(R.string.theme_title)) }
                HomeScreen(
                    userName = "Okta",
                    onRecordClick = { /* TODO: open record sheet */ },
                    modifier = Modifier.weight(1f),
                )
              }
            }
        },
    )
}
