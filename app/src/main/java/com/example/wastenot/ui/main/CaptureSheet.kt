package com.example.wastenot.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.wastenot.R
import com.example.wastenot.theme.LocalWasteNotGradients
import com.example.wastenot.theme.OnAccent
import com.example.wastenot.theme.Pine900
import com.example.wastenot.ui.components.ActionButton

@Composable
internal fun RecordAction(onClick: () -> Unit, modifier: Modifier = Modifier, vertical: Boolean = false) {
    val description = stringResource(R.string.scan_action_description)
    Surface(
        onClick = onClick,
        modifier = modifier.widthIn(min = 136.dp).heightIn(min = 52.dp).semantics { contentDescription = description },
        shape = RoundedCornerShape(26.dp),
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
                modifier = Modifier.background(LocalWasteNotGradients.current.accent).padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                Icon(painterResource(R.drawable.ic_camera), null, Modifier.size(24.dp), tint = OnAccent)
                Text(stringResource(R.string.scan_action), color = OnAccent, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CaptureSheet(onDismiss: () -> Unit, onManual: () -> Unit, modifier: Modifier = Modifier) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        val view = LocalView.current
        DisposableEffect(view) {
            val window = if (android.os.Build.VERSION.SDK_INT < 26) generateSequence(view) { it.parent as? android.view.View }
                .filterIsInstance<DialogWindowProvider>().firstOrNull()?.window else null
            val previous = window?.navigationBarColor
            // API 24–25 cannot draw dark navigation icons over the sheet's light surface.
            if (window != null) window.navigationBarColor = Pine900.toArgb()
            onDispose { if (window != null && previous != null) window.navigationBarColor = previous }
        }
        Column(
            modifier = Modifier.fillMaxWidth().testTag("capture-sheet").verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.scan_title), Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.headlineMedium)
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
            }
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(painterResource(R.drawable.ic_camera), null, Modifier.size(40.dp))
                    Text(stringResource(R.string.scan_camera_title), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.scan_camera_description))
                    Text(stringResource(R.string.scan_camera_pending), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(stringResource(R.string.scan_manual_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
            ActionButton(stringResource(R.string.manual_input), onManual, Modifier.fillMaxWidth())
        }
    }
}
