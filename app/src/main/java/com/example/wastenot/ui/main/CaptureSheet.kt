package com.example.wastenot.ui.main

import com.example.wastenot.data.repository.ScanRepository
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.wastenot.data.local.ScanPhotoCache
import com.example.wastenot.model.ScanPrefill
import com.example.wastenot.ui.scan.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.wastenot.R
import com.example.wastenot.theme.LocalWasteNotGradients
import com.example.wastenot.theme.OnAccent
import com.example.wastenot.theme.Pine900
import com.example.wastenot.ui.components.ActionButton
import com.example.wastenot.ui.components.focusOutline

@Composable
internal fun RecordAction(onClick: () -> Unit, modifier: Modifier = Modifier, vertical: Boolean = false) {
    val description = stringResource(R.string.scan_action_description)
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(26.dp)
    Surface(
        modifier = modifier.widthIn(min = 136.dp).heightIn(min = 52.dp)
            .focusOutline(focused, shape, colors.primary, colors.surface)
            .clip(shape)
            .clickable(interactionSource = interactions, indication = ripple(), role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = description },
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(3.dp, MaterialTheme.colorScheme.surface),
        shadowElevation = 2.dp,
    ) {
        if (vertical) {
            Column(
                modifier = Modifier.background(LocalWasteNotGradients.current.accent).padding(horizontal = 12.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(painterResource(R.drawable.ic_camera), null, Modifier.size(24.dp), tint = OnAccent)
                Text(stringResource(R.string.scan_action), color = OnAccent, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
            }
        } else {
            Row(
                modifier = Modifier.background(LocalWasteNotGradients.current.accent).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                Icon(painterResource(R.drawable.ic_camera), null, Modifier.size(24.dp), tint = OnAccent)
                Text(stringResource(R.string.scan_action), color = OnAccent,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CaptureSheet(userId: String, repositoryFactory: (ScanPhotoCache) -> ScanRepository, session: String,
    isPhotoScanDisabled: suspend () -> Boolean, onDismiss: () -> Unit,
    onPrefill: (ScanPrefill) -> Unit, modifier: Modifier = Modifier,
    restorePhotoReview: Boolean = false, onPhotoReviewChanged: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    val cache = remember(userId) { ScanPhotoCache.create(context, userId) }
    val factory = remember(cache, repositoryFactory) { viewModelFactory { initializer {
        ScanViewModel(repositoryFactory(cache), cache)
    } } }
    val model: ScanViewModel = viewModel(key = "scan-$userId", factory = factory)
    val state by model.state.collectAsStateWithLifecycle()
    val scanDisabled by produceState<Boolean?>(null, session) { value = isPhotoScanDisabled() }
    val photoCacheReady by produceState(false, session) {
        model.start(session)
        val interrupted = restorePhotoReview && model.state.value.photo == null
        withContext(Dispatchers.IO) {
            if (interrupted) cache.removeOwnerPhotos()
            cache.removeExpired()
        }
        if (interrupted) model.problem(session, ScanProblem.EXPIRED)
        value = true
    }
    DisposableEffect(model, session) {
        onDispose { if (context.activity()?.isChangingConfigurations != true) model.close(session) }
    }
    fun dismiss() {
        model.close(session)
        onPhotoReviewChanged(false)
        onDismiss()
    }
    fun retake() {
        model.retake()
        onPhotoReviewChanged(false)
    }
    fun use(manual: Boolean) {
        if (model.state.value.session != session) return
        val prefill = model.handoff(confirmRecommendation = !manual) ?: return
        model.close(session)
        onPhotoReviewChanged(false)
        onPrefill(prefill)
    }
    val visibleState = if (scanDisabled == true) state.copy(phase = ScanPhase.FAILURE, problem = ScanProblem.DISABLED) else state
    ScanSheet(visibleState, ::dismiss, { use(true) }, ::retake, model::detect, model::select, model::category,
        model::confirmObject, model::correctObject, model::context, model::recommend, model::method,
        { use(false) }, { model.retry(); if (model.state.value.photo == null) onPhotoReviewChanged(false) },
        modifier, model::objectKey, model::reviewContext, model::confirmConditions) {
        if (scanDisabled == false && photoCacheReady) {
            ScanCamera(session, cache, { photo ->
                model.photo(session, photo)
                if (model.state.value.photo == photo) onPhotoReviewChanged(true)
            }, { model.problem(session, it) })
        } else {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}

@Composable
internal fun SheetSystemBar() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = if (android.os.Build.VERSION.SDK_INT < 26) generateSequence(view) { it.parent as? android.view.View }
            .filterIsInstance<DialogWindowProvider>().firstOrNull()?.window else null
        val previous = window?.navigationBarColor
        // API 24–25 cannot draw dark navigation icons over the sheet's light surface.
        if (window != null) window.navigationBarColor = Pine900.toArgb()
        onDispose { if (window != null && previous != null) window.navigationBarColor = previous }
    }
}

@Composable
internal fun SheetDragHandle(modifier: Modifier = Modifier) {
    Box(modifier.padding(vertical = 20.dp).size(width = 32.dp, height = 4.dp)
        .background(MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(2.dp)))
}
