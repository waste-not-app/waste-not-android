package com.example.wastenot.ui.main

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.wastenot.R
import com.example.wastenot.data.RecyclingPoints
import com.example.wastenot.ui.auth.MessageBanner
import com.example.wastenot.ui.components.GroupSectionLabel
import com.example.wastenot.ui.components.InsetGroupCard
import com.example.wastenot.ui.components.focusOutline

@Composable
fun RecyclingPointListScreen(
    padding: PaddingValues,
    onDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth(),
            contentPadding = pagePadding(padding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "intro") {
                InsetGroupCard(contentPadding = true) {
                    Text(stringResource(R.string.recycling_points_title), style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.recycling_points_intro))
                    Text(stringResource(R.string.recycling_points_notice), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (RecyclingPoints.all.isEmpty()) item(key = "empty") {
                Text(stringResource(R.string.recycling_points_empty))
            }
            items(RecyclingPoints.all, key = { it.id }) { point ->
                InsetGroupCard(contentPadding = true) {
                    Text(stringResource(point.name), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(point.address), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.recycling_point_materials_short), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    RecyclingPointButton(stringResource(R.string.recycling_point_detail), { onDetail(point.id) }, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
fun RecyclingPointDetailScreen(
    pointId: String,
    padding: PaddingValues,
    onList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val point = RecyclingPoints.find(pointId)
    val context = LocalContext.current
    var linkFailed by rememberSaveable(pointId) { mutableStateOf(false) }
    fun open(url: String) {
        linkFailed = try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            false
        } catch (_: ActivityNotFoundException) {
            true
        } catch (_: SecurityException) {
            true
        }
    }
    ScrollPage(padding, modifier, maxWidth = 560.dp) {
        if (point == null) {
            Text(stringResource(R.string.recycling_point_missing))
            RecyclingPointButton(stringResource(R.string.recycling_point_back), onList, Modifier.fillMaxWidth())
        } else {
            val address = stringResource(point.address)
            val mapsUrl = remember(address) { "https://www.google.com/maps/search/?api=1&query=${Uri.encode(address)}" }
            InsetGroupCard(contentPadding = true) {
                Text(stringResource(point.name), style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() })
                SelectionContainer { Text(address) }
                Text(stringResource(R.string.recycling_points_notice), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            GroupSectionLabel(stringResource(R.string.recycling_point_materials_title))
            InsetGroupCard(contentPadding = true) {
                Text(stringResource(R.string.recycling_point_materials))
                Text(stringResource(R.string.recycling_point_safety), style = MaterialTheme.typography.bodyMedium)
            }
            GroupSectionLabel(stringResource(R.string.recycling_point_hours_title))
            InsetGroupCard(contentPadding = true) {
                Text(stringResource(R.string.recycling_point_hours))
                SelectionContainer { Text(stringResource(R.string.recycling_point_contact)) }
                RecyclingPointButton(stringResource(R.string.recycling_source_contact),
                    { open("https://lingkunganhidup.jakarta.go.id/kontak-kami") }, Modifier.fillMaxWidth())
            }
            RecyclingPointButton(stringResource(R.string.recycling_point_maps), { open(mapsUrl) }, Modifier.fillMaxWidth())
            Text(stringResource(R.string.recycling_point_maps_notice), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (linkFailed) MessageBanner(stringResource(R.string.recycling_point_link_error), isError = true)
            GroupSectionLabel(stringResource(R.string.recycling_point_sources_title))
            InsetGroupCard(contentPadding = true) {
                Text(stringResource(R.string.recycling_point_checked, point.sourceCheckedOn), style = MaterialTheme.typography.bodyMedium)
                point.sources.forEach { source ->
                    RecyclingPointButton(stringResource(source.label), { open(source.url) }, Modifier.fillMaxWidth())
                    SelectionContainer { Text(source.url, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@Composable
private fun RecyclingPointButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val colors = MaterialTheme.colorScheme
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 52.dp)
            .focusOutline(focused, MaterialTheme.shapes.small, colors.primary, colors.surface),
        interactionSource = interactions,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(label)
    }
}
