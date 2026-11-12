package com.example.wastenot

import android.content.Context
import com.example.wastenot.data.local.ScanPhotoCache
import com.example.wastenot.data.repository.RemoteScanRepository
import com.example.wastenot.data.repository.ScanRepository
import com.example.wastenot.data.repository.RemoteActivityStore
import com.example.wastenot.data.repository.ActivityStore
import com.example.wastenot.model.User
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
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
import com.example.wastenot.data.repository.AiRepository
import com.example.wastenot.data.repository.GuideStore
import com.example.wastenot.data.repository.SessionState
import com.example.wastenot.theme.ThemeMode
import com.example.wastenot.theme.ThemePicker
import com.example.wastenot.theme.LocalWasteNotGradients
import com.example.wastenot.ui.auth.AuthScreen
import com.example.wastenot.ui.auth.SessionViewModel
import com.example.wastenot.ui.auth.StartupScreen
import com.example.wastenot.ui.main.AccountNavigation
import com.github.skydoves.navgraph.annotations.NavGraphRoot

@NavGraphRoot
@Composable
fun MainNavigation(
    accountSession: AccountSession,
    coreRepository: CoreRepository,
    guideStore: GuideStore,
    aiRepository: AiRepository,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
    activityStoreFactory: (User) -> ActivityStore =
        { RemoteActivityStore(coreRepository) },
    scanRepositoryFactory: (ScanPhotoCache) -> ScanRepository =
        { RemoteScanRepository(aiRepository, it) },
) {
    val factory = remember(accountSession) { viewModelFactory { initializer { SessionViewModel(accountSession) } } }
    val model: SessionViewModel = viewModel(factory = factory)
    val session by model.session.collectAsStateWithLifecycle()
    val bootstrapping by model.bootstrapping.collectAsStateWithLifecycle()
    val logout by model.logoutState.collectAsStateWithLifecycle()
    val root = rememberNavBackStack(SessionRoot)
    var choosingTheme by remember { mutableStateOf(false) }
    val context = LocalContext.current.applicationContext
    val scanReview = remember(context) { context.getSharedPreferences("scan_review", Context.MODE_PRIVATE) }
    // A fresh task can restore the account without restoring any of its navigation state.
    var photoReviewOwner by remember(scanReview) { mutableStateOf(scanReview.getString("owner", null)) }
    fun setPhotoReviewOwner(owner: String?) {
        if (photoReviewOwner == owner) return
        photoReviewOwner = owner
        scanReview.edit { putString("owner", owner) }
    }
    val target: NavKey = when (val phase = session.phase) {
        is SessionState.Authenticated -> AccountRoot(phase.user.id, phase.epoch)
        is SessionState.SignedOut -> AuthRoot
        else -> SessionRoot
    }
    LaunchedEffect(model) { model.bootstrap() }
    LaunchedEffect(session.phase) {
        val phase = session.phase
        if (phase is SessionState.SignedOut || (phase is SessionState.Authenticated && photoReviewOwner != phase.user.id)) {
            setPhotoReviewOwner(null)
        }
    }
    LaunchedEffect(target) {
        if (root.size != 1 || root.last() != target) { root.clear(); root.add(target) }
    }
    if (choosingTheme) ThemePicker(themeMode, onThemeModeChange, onDismiss = { choosingTheme = false })
    Box(modifier.background(LocalWasteNotGradients.current.canvas)) {
      NavDisplay(
        backStack = root,
        modifier = Modifier.fillMaxSize(),
        onBack = {},
        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
        popTransitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
        entryProvider = entryProvider {
            entry<SessionRoot> {
                StartupScreen(session.phase is SessionState.ConnectionRequired && !bootstrapping,
                    { model.bootstrap(retry = true) }, model::logout, Modifier.fillMaxSize())
            }
            entry<AuthRoot> {
                val phase = session.phase as? SessionState.SignedOut
                if (phase != null) AuthNavigation(model, phase, { choosingTheme = true })
            }
            entry<AccountRoot> { key ->
                val phase = session.phase as? SessionState.Authenticated
                if (phase != null && key == AccountRoot(phase.user.id, phase.epoch)) {
                    val activities = remember(key, activityStoreFactory) { activityStoreFactory(phase.user) }
                    AccountNavigation(phase.user, activities, guideStore, scanRepositoryFactory, themeMode,
                        { choosingTheme = true }, model::logout, Modifier.fillMaxSize(), logoutState = logout,
                        restorePhotoReview = photoReviewOwner == phase.user.id,
                        onPhotoReviewChanged = { reviewing -> setPhotoReviewOwner(phase.user.id.takeIf { reviewing }) })
                }
            }
        },
      )
      // Keep decorators mounted so removed account stores are cleared, while the entry guard and overlay protect identity changes.
      if (root.lastOrNull() != target) StartupScreen(false, {}, {}, Modifier.fillMaxSize())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthNavigation(model: SessionViewModel, phase: SessionState.SignedOut, onTheme: () -> Unit) {
    val stack = rememberNavBackStack(Login)
    val form by model.auth.collectAsStateWithLifecycle()
    val logout by model.logoutState.collectAsStateWithLifecycle()
    fun back() { model.cancelSubmission(); if (stack.size > 1) stack.removeLastOrNull() }
    Scaffold(
        modifier = Modifier.background(LocalWasteNotGradients.current.canvas),
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding ->
        NavDisplay(
            backStack = stack,
            onBack = ::back,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            popTransitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding),
            entryProvider = entryProvider {
                entry<Login> {
                    AuthScreen(false, form, phase.reason, model::change, { model.submit(false) },
                        { model.clearPasswords(); if (stack.last() != Register) stack.add(Register) },
                        onTheme, Modifier.fillMaxSize(), logout, model::logout)
                }
                entry<Register> {
                    AuthScreen(true, form, phase.reason, model::change, { model.submit(true) }, ::back,
                        onTheme, Modifier.fillMaxSize(), logout, model::logout, onBack = ::back)
                }
            },
        )
    }
}
