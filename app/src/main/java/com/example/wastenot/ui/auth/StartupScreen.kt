package com.example.wastenot.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.wastenot.R
import com.example.wastenot.theme.LocalWasteNotGradients
import com.example.wastenot.theme.WasteNotStateHeading
import com.example.wastenot.ui.components.ActionButton
import com.example.wastenot.ui.components.SecondaryActionButton
import com.example.wastenot.ui.components.BrandWordmark

@Composable
internal fun StartupScreen(error: Boolean, onRetry: () -> Unit, onLoginAgain: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.background(LocalWasteNotGradients.current.canvas).safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(top = 88.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)) {
            BrandWordmark(Modifier.align(Alignment.CenterHorizontally))
            Column(Modifier.align(Alignment.CenterHorizontally).widthIn(max = 400.dp).fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            painterResource(if (error) R.drawable.ic_alert else R.drawable.ic_clock),
                            null,
                            Modifier.size(30.dp),
                            tint = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(stringResource(if (error) R.string.session_connection_required else R.string.session_preparing),
                    Modifier.fillMaxWidth().semantics { heading(); liveRegion = LiveRegionMode.Polite },
                    style = WasteNotStateHeading, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
                Text(stringResource(if (error) R.string.session_connection_help else R.string.session_preparing_help),
                    Modifier.widthIn(max = 270.dp), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                if (error) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ActionButton(stringResource(R.string.retry), onRetry, Modifier.fillMaxWidth())
                        SecondaryActionButton(stringResource(R.string.login_again), onLoginAgain, Modifier.fillMaxWidth())
                    }
                } else LinearProgressIndicator(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(8.dp)),
                    color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceContainer, gapSize = 0.dp)
            }
        }
    }
}
