package com.example.wastenot

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.wastenot.ui.home.HomeScreen
import com.github.skydoves.navgraph.annotations.NavGraphRoot

@NavGraphRoot
@Composable
fun MainNavigation() {
    val backStack = rememberNavBackStack(Main)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<Main> {
                HomeScreen(
                    userName = "Okta",
                    onRecordClick = { /* TODO: open record sheet */ },
                    modifier = Modifier.safeDrawingPadding(),
                )
            }
        },
    )
}
