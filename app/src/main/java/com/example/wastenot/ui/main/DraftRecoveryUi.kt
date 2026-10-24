package com.example.wastenot.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import com.example.wastenot.R
import com.example.wastenot.theme.WasteNotStateHeading
import com.example.wastenot.ui.auth.MessageBanner
import com.example.wastenot.ui.components.ActionButton

@Composable
internal fun DraftRestoreScreen(loading: Boolean, onRetry: () -> Unit, onDiscard: () -> Unit, modifier: Modifier = Modifier) {
    ScrollPage(PaddingValues(0.dp), modifier.safeDrawingPadding(), maxWidth = 400.dp) {
      Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                Icon(painterResource(if (loading) R.drawable.ic_clock else R.drawable.ic_alert), null,
                    Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        Text(stringResource(if (loading) R.string.draft_checking else R.string.draft_read_failed),
            Modifier.fillMaxWidth().semantics { heading() }, style = WasteNotStateHeading, textAlign = TextAlign.Center)
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        else {
            Text(stringResource(R.string.draft_read_help), Modifier.widthIn(max = 270.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            ActionButton(stringResource(R.string.retry), onRetry, Modifier.fillMaxWidth())
            OutlinedButton(onDiscard, Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = MaterialTheme.shapes.small,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                Text(stringResource(R.string.draft_discard_stored), Modifier.width(IntrinsicSize.Max), color = MaterialTheme.colorScheme.onSurface)
            }
        }
      }
    }
}

@Composable
internal fun PausedDraftNotice(onResume: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MessageBanner(stringResource(R.string.draft_pending_message))
        OutlinedButton(onResume, Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = MaterialTheme.shapes.small,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
            Text(stringResource(R.string.draft_resume), Modifier.width(IntrinsicSize.Max), color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
