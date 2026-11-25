package com.example.wastenot.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.wastenot.theme.WasteNotStateHeading
import com.valentinilk.shimmer.shimmer

@Composable
internal fun AsyncStatePanel(
    title: String,
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    description: String? = null,
    loading: Boolean = false,
    action: (@Composable () -> Unit)? = null,
) {
    InsetGroupCard(modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                modifier = Modifier.size(64.dp).testTag("async-state-symbol"),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(painterResource(icon), null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Text(title, Modifier.widthIn(max = 400.dp).semantics { heading(); liveRegion = LiveRegionMode.Polite },
                style = WasteNotStateHeading, textAlign = TextAlign.Center)
            if (description != null) Text(description, Modifier.widthIn(max = 270.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center)
            if (loading) {
                LinearProgressIndicator(
                    modifier = Modifier.widthIn(max = 400.dp).fillMaxWidth().height(8.dp).clip(RoundedCornerShape(8.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    gapSize = 0.dp,
                )
                Column(
                    modifier = Modifier.widthIn(max = 400.dp).fillMaxWidth().shimmer(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        Modifier.fillMaxWidth().height(64.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                    Box(
                        Modifier.fillMaxWidth(0.75f).height(20.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
            }
            if (action != null && !loading) {
                Column(Modifier.widthIn(max = 400.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    action()
                }
            }
        }
    }
}
