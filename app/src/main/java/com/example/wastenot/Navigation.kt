package com.example.wastenot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.example.wastenot.data.repository.AccountSession
import com.example.wastenot.data.repository.CoreRepository
import com.example.wastenot.data.repository.SessionState
import com.example.wastenot.theme.ThemeMode
import com.example.wastenot.theme.ThemePicker
import com.example.wastenot.ui.auth.AuthScreen
import com.example.wastenot.ui.auth.SessionViewModel
import com.example.wastenot.ui.components.ActionButton
import com.example.wastenot.ui.main.AccountNavigation
import com.github.skydoves.navgraph.annotations.NavGraphRoot

@NavGraphRoot
@Composable
fun MainNavigation(
    accountSession: AccountSession,
    coreRepository: CoreRepository,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val factory = remember(accountSession) { viewModelFactory { initializer { SessionViewModel(accountSession) } } }
    val model: SessionViewModel = viewModel(factory = factory)
    val session by model.session.collectAsStateWithLifecycle()
    val bootstrapping by model.bootstrapping.collectAsStateWithLifecycle()
    val root = rememberNavBackStack(SessionRoot)
    var choosingTheme by remember { mutableStateOf(false) }
    val target: NavKey = when (val phase = session.phase) {
        is SessionState.Authenticated -> AccountRoot(phase.user.id, phase.epoch, session.isDemo)
        is SessionState.SignedOut -> AuthRoot
        else -> SessionRoot
    }
    LaunchedEffect(model) { model.bootstrap() }
    LaunchedEffect(target) {
        if (root.size != 1 || root.last() != target) { root.clear(); root.add(target) }
    }
    if (choosingTheme) ThemePicker(themeMode, onThemeModeChange, onDismiss = { choosingTheme = false })
    Box(modifier) {
      NavDisplay(
        backStack = root,
        modifier = Modifier.fillMaxSize(),
        onBack = {},
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
        entryProvider = entryProvider {
            entry<SessionRoot> {
                StartupScreen(session.phase is SessionState.ConnectionRequired && !bootstrapping,
                    { model.bootstrap(retry = true) }, model::logout, Modifier.fillMaxSize())
            }
            entry<AuthRoot> {
                val phase = session.phase as? SessionState.SignedOut
                if (phase != null) AuthNavigation(model, session.isDemo, phase, { choosingTheme = true })
            }
            entry<AccountRoot> { key ->
                val phase = session.phase as? SessionState.Authenticated
                if (phase != null && key == AccountRoot(phase.user.id, phase.epoch, session.isDemo)) {
                    AccountNavigation(phase.user, session.isDemo, coreRepository, themeMode,
                        { choosingTheme = true }, model::logout, Modifier.fillMaxSize())
                }
            }
        },
      )
      // Keep decorators mounted so removed account stores are cleared, while the entry guard and overlay protect identity changes.
      if (root.lastOrNull() != target) StartupScreen(false, {}, {}, Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthNavigation(model: SessionViewModel, demo: Boolean, phase: SessionState.SignedOut, onTheme: () -> Unit) {
    val stack = rememberNavBackStack(Login)
    val form by model.auth.collectAsStateWithLifecycle()
    val logout by model.logoutState.collectAsStateWithLifecycle()
    fun back() { model.cancelSubmission(); if (stack.size > 1) stack.removeLastOrNull() }
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(title = {}, navigationIcon = {
                if (stack.size > 1) IconButton(onClick = ::back) { Icon(painterResource(R.drawable.ic_back), stringResource(R.string.back)) }
            }, actions = { TextButton(onClick = onTheme) { Text(stringResource(R.string.theme_title)) } })
        },
    ) { padding ->
        NavDisplay(
            backStack = stack,
            onBack = ::back,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding),
            entryProvider = entryProvider {
                entry<Login> {
                    AuthScreen(false, form, demo, phase.reason, model::change, { model.submit(false) },
                        { model.clearPasswords(); if (stack.last() != Register) stack.add(Register) },
                        Modifier.fillMaxSize(), logout, model::logout)
                }
                entry<Register> {
                    AuthScreen(true, form, demo, phase.reason, model::change, { model.submit(true) }, ::back,
                        Modifier.fillMaxSize(), logout, model::logout)
                }
            },
        )
    }
}

@Composable
private fun StartupScreen(error: Boolean, onRetry: () -> Unit, onLoginAgain: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.safeDrawingPadding(), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
            if (error) {
                Text(stringResource(R.string.session_connection_required))
                ActionButton(stringResource(R.string.retry), onRetry)
                TextButton(onClick = onLoginAgain) { Text(stringResource(R.string.login_again)) }
            } else {
                CircularProgressIndicator()
                Text(stringResource(R.string.session_preparing))
            }
        }
    }
}
