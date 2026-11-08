package com.example.wastenot.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.wastenot.R
import com.example.wastenot.model.WasteFormat
import com.example.wastenot.model.WasteLog
import com.example.wastenot.ui.components.ActionButton
import com.example.wastenot.ui.components.SecondaryActionButton
import com.example.wastenot.ui.components.labelResource

@Composable
fun SaveConfirmationScreen(
    log: WasteLog,
    created: Boolean,
    padding: PaddingValues,
    onView: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = stringResource(R.string.save_confirm_title)
    val quantity = WasteFormat.quantity(log.quantity)
    ScrollPage(padding, modifier, maxWidth = 560.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.tertiary),
                contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_check), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onTertiary)
            }
            Text(stringResource(R.string.save_confirm_heading), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite; stateDescription = status })
            Text(stringResource(if (created) R.string.save_confirm_created else R.string.save_confirm_existing),
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
        Surface(color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.save_confirm_category_method, stringResource(log.category.labelResource), stringResource(log.method.labelResource)),
                    style = MaterialTheme.typography.labelMedium)
                Text(stringResource(R.string.save_confirm_quantity_date, quantity.amount, stringResource(quantity.unit.labelResource), WasteFormat.activityDate(log.activityDate)),
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        ActionButton(stringResource(R.string.save_confirm_view), onView, Modifier.fillMaxWidth())
        SecondaryActionButton(stringResource(R.string.save_confirm_home), onHome, Modifier.fillMaxWidth())
    }
}
