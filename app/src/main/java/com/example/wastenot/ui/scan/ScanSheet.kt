package com.example.wastenot.ui.scan

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.wastenot.R
import com.example.wastenot.model.*
import com.example.wastenot.theme.OnAccent
import com.example.wastenot.theme.Pine900
import com.example.wastenot.theme.LimeEnd
import com.example.wastenot.theme.LocalWasteNotGradients
import com.example.wastenot.ui.components.ActionButton
import com.example.wastenot.ui.components.SecondaryActionButton
import com.example.wastenot.ui.components.labelResource
import com.example.wastenot.ui.main.SheetDragHandle
import com.example.wastenot.ui.main.SheetSystemBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

internal data class ScanImageRect(val left: Float, val top: Float, val width: Float, val height: Float)
internal fun fitScanBounds(imageWidth: Int, imageHeight: Int, viewWidth: Float, viewHeight: Float, box: ScanBounds): ScanImageRect {
    require(imageWidth > 0 && imageHeight > 0 && viewWidth >= 0 && viewHeight >= 0)
    val scale = min(viewWidth / imageWidth, viewHeight / imageHeight)
    val width = imageWidth * scale; val height = imageHeight * scale
    return ScanImageRect((viewWidth - width) / 2 + box.left * width, (viewHeight - height) / 2 + box.top * height,
        (box.right - box.left) * width, (box.bottom - box.top) * height)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScanSheet(state: ScanState, onDismiss: () -> Unit, onManual: () -> Unit,
    onRetake: () -> Unit, onDetect: () -> Unit, onSelect: (String) -> Unit, onCategory: (WasteCategory) -> Unit,
    onConfirmObject: () -> Unit, onCorrectObject: () -> Unit, onContext: (ScanContext) -> Unit,
    onRecommend: () -> Unit, onMethod: (DisposalMethod) -> Unit, onUse: () -> Unit, onRetry: () -> Unit,
    modifier: Modifier = Modifier, camera: @Composable () -> Unit) {
    val title = when (state.phase) {
        ScanPhase.CAMERA -> R.string.scan_title
        ScanPhase.PHOTO, ScanPhase.DETECTING -> R.string.scan_review_photo
        ScanPhase.OBJECT -> R.string.scan_review_object
        ScanPhase.CONTEXT, ScanPhase.RECOMMENDING -> R.string.scan_context_title
        ScanPhase.RECOMMENDATION -> R.string.scan_review_recommendation
        ScanPhase.FAILURE -> R.string.scan_unavailable
    }
    val horizontalPadding = if (LocalConfiguration.current.screenWidthDp <= 400) 16.dp else 24.dp
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null) {
        SheetSystemBar()
        Column(Modifier.fillMaxWidth().fillMaxHeight(.94f).testTag("capture-sheet")) {
            Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 16.dp, bottom = 10.dp).size(38.dp, 4.dp)
                .background(MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(2.dp)))
            Row(Modifier.fillMaxWidth().padding(horizontal = horizontalPadding).padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(title), Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
                FilledTonalIconButton(onClick = onDismiss, modifier = Modifier.size(48.dp), shape = MaterialTheme.shapes.medium,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface)) {
                    Icon(painterResource(R.drawable.ic_close), stringResource(R.string.scan_close))
                }
            }
            HorizontalDivider()
            val scroll = remember(state.phase) { ScrollState(0) }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal = horizontalPadding, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ScanSteps(state.step)
                when (state.phase) {
                    ScanPhase.CAMERA -> camera()
                    ScanPhase.PHOTO, ScanPhase.DETECTING -> {
                        state.photo?.let { ScanPhotoPreview(it, emptyList(), null, onSelect) }
                        Text(stringResource(R.string.scan_photo_consent), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (state.phase == ScanPhase.DETECTING) ScanBusy(R.string.scan_detecting)
                        else {
                            ActionButton(stringResource(R.string.scan_send), onDetect, Modifier.fillMaxWidth())
                            ScanSecondary(R.string.scan_retake, onRetake)
                        }
                    }
                    ScanPhase.OBJECT -> {
                        state.photo?.let { ScanPhotoPreview(it, state.objects, state.selectedId, onSelect) }
                        Text(stringResource(R.string.scan_object_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(Modifier.selectableGroup()) {
                            state.objects.forEach { candidate ->
                                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                    .selectable(candidate.id == state.selectedId, role = Role.RadioButton, onClick = { onSelect(candidate.id) })
                                    .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(candidate.id == state.selectedId, null)
                                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                        Text(candidate.name, style = MaterialTheme.typography.titleMedium)
                                        Text(stringResource(R.string.scan_detection_score, (candidate.score * 100).roundToInt()), style = MaterialTheme.typography.bodySmall)
                                        if (candidate.score < .7f) Text(stringResource(R.string.scan_uncertain), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        ScanChoice(stringResource(R.string.scan_material), state.category?.let { stringResource(it.labelResource) }
                            ?: stringResource(R.string.scan_choose_material), WasteCategory.entries,
                            { stringResource(it.labelResource) }, onCategory)
                        ActionButton(stringResource(R.string.scan_confirm_object), onConfirmObject, Modifier.fillMaxWidth(),
                            enabled = state.category != null && state.selected != null)
                        ScanSecondary(R.string.scan_retake, onRetake)
                    }
                    ScanPhase.CONTEXT, ScanPhase.RECOMMENDING -> {
                        state.category?.let { Text(stringResource(R.string.scan_context_category, stringResource(it.labelResource)), style = MaterialTheme.typography.titleSmall) }
                        Text(stringResource(R.string.scan_context_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (state.phase == ScanPhase.RECOMMENDING) {
                            ScanBusy(R.string.scan_recommending)
                            ScanSecondary(R.string.scan_correct_object, onCorrectObject)
                        }
                        else {
                            ScanChoice(stringResource(R.string.scan_cleanliness), cleanlinessLabel(state.context.cleanliness), Cleanliness.entries,
                                { cleanlinessLabel(it) }, { onContext(state.context.copy(cleanliness = it)) })
                            ScanChoice(stringResource(R.string.scan_integrity), integrityLabel(state.context.integrity), Integrity.entries,
                                { integrityLabel(it) }, { onContext(state.context.copy(integrity = it)) })
                            ScanChoice(stringResource(R.string.scan_reusability), reusableLabel(state.context.reusable), Reusability.entries,
                                { reusableLabel(it) }, { onContext(state.context.copy(reusable = it)) })
                            ScanChoice(stringResource(R.string.scan_reuse_intention), intentionLabel(state.context.reuseIntention), listOf(null, true, false),
                                { intentionLabel(it) }, { onContext(state.context.copy(reuseIntention = it)) })
                            ActionButton(stringResource(R.string.scan_get_recommendation), onRecommend, Modifier.fillMaxWidth())
                            ScanSecondary(R.string.scan_correct_object, onCorrectObject)
                        }
                    }
                    ScanPhase.RECOMMENDATION -> {
                        val recommendation = state.recommendation
                        if (recommendation != null && state.method != null && state.selected?.specialHandling == false) {
                            Surface(shape = MaterialTheme.shapes.large, color = Pine900, contentColor = Color.White) {
                                Column(Modifier.fillMaxWidth().background(LocalWasteNotGradients.current.summary).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(stringResource(R.string.scan_suggested_method), style = MaterialTheme.typography.labelLarge)
                                    Text(stringResource(recommendation.method!!.labelResource), style = MaterialTheme.typography.headlineMedium)
                                    Text(recommendation.reason)
                                }
                            }
                            recommendation.cautions.forEach { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Text(stringResource(R.string.scan_method_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            ScanChoice(stringResource(R.string.method), stringResource(state.method.labelResource),
                                listOf(DisposalMethod.REUSE, DisposalMethod.RECYCLE, DisposalMethod.TRASH),
                                { stringResource(it.labelResource) }, onMethod)
                            var expanded by remember(recommendation) { mutableStateOf(false) }
                            OutlinedButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = MaterialTheme.shapes.small) {
                                Text(stringResource(if (expanded) R.string.scan_hide_basis else R.string.scan_basis))
                            }
                            if (expanded) {
                                Text("${recommendation.guideTitle} · ${recommendation.guideVersion}", style = MaterialTheme.typography.titleSmall)
                                recommendation.steps.forEachIndexed { index, step -> Text("${index + 1}. $step") }
                            }
                            ActionButton(stringResource(R.string.scan_use), onUse, Modifier.fillMaxWidth())
                            Text(stringResource(R.string.scan_quantity_next), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text(stringResource(if (state.selected?.specialHandling == true) R.string.scan_special else R.string.scan_no_guide),
                                style = MaterialTheme.typography.titleMedium)
                            recommendation?.reason?.takeIf { it.isNotBlank() }?.let { Text(it) }
                        }
                        ScanSecondary(R.string.scan_correct_object, onCorrectObject)
                    }
                    ScanPhase.FAILURE -> {
                        Text(stringResource(when (state.problem) {
                            ScanProblem.NO_OBJECT -> R.string.scan_no_object
                            ScanProblem.EXPIRED -> R.string.scan_photo_expired
                            ScanProblem.PHOTO -> R.string.scan_photo_error
                            ScanProblem.PERMISSION -> R.string.scan_permission_denied
                            ScanProblem.CAMERA -> R.string.scan_camera_error
                            ScanProblem.RECOMMENDATION -> R.string.scan_recommendation_error
                            else -> R.string.scan_detection_error
                        }), style = MaterialTheme.typography.bodyLarge)
                        ScanSecondary(R.string.retry, onRetry)
                    }
                }
            }
            HorizontalDivider()
            Column(Modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.scan_manual_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ScanSecondary(R.string.manual_input, onManual)
            }
        }
    }
}

@Composable
private fun ScanSteps(current: Int) {
    val steps = listOf(R.string.scan_step_photo, R.string.scan_step_object, R.string.scan_step_context, R.string.scan_step_advice)
    val columns = if (LocalDensity.current.fontScale >= 1.3f) 2 else 4
    val markerLineHeight = MaterialTheme.typography.labelMedium.lineHeight
    val markerSize = with(LocalDensity.current) { markerLineHeight.toDp() + 8.dp }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        steps.chunked(columns).forEachIndexed { row, entries ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                entries.forEachIndexed { column, label ->
                    val selected = row * columns + column == current
                    val description = stringResource(if (selected) R.string.scan_current_step else R.string.scan_other_step)
                    Column(Modifier.weight(1f).semantics(mergeDescendants = true) { stateDescription = description },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(modifier = Modifier.size(markerSize).testTag("scan-step-${row * columns + column}"),
                            shape = CircleShape, color = if (selected) LimeEnd else MaterialTheme.colorScheme.surface,
                            contentColor = if (selected) OnAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                            border = BorderStroke(1.dp, if (selected) OnAccent else MaterialTheme.colorScheme.outline)) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                if (row * columns + column < current) Icon(painterResource(R.drawable.ic_check), null, Modifier.size(16.dp))
                                else Text("${row * columns + column + 1}", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        Text(stringResource(label), style = MaterialTheme.typography.labelMedium,
                            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
internal fun <T> ScanChoice(label: String, value: String, options: List<T>, optionLabel: @Composable (T) -> String, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = MaterialTheme.shapes.small) {
                Text(value, Modifier.weight(1f))
                Icon(painterResource(R.drawable.ic_chevron_down), null, Modifier.padding(start = 8.dp).size(20.dp))
            }
            DropdownMenu(expanded, { expanded = false }) {
                options.forEach { option -> DropdownMenuItem(text = { Text(optionLabel(option)) },
                    onClick = { expanded = false; onSelect(option) }, modifier = Modifier.heightIn(min = 48.dp)) }
            }
        }
    }
}

@Composable private fun ScanSecondary(label: Int, onClick: () -> Unit) {
    SecondaryActionButton(stringResource(label), onClick, Modifier.fillMaxWidth())
}
@Composable private fun ScanBusy(label: Int) {
    LinearProgressIndicator(Modifier.fillMaxWidth())
    Text(stringResource(label))
}
@Composable private fun cleanlinessLabel(value: Cleanliness) = stringResource(when (value) {
    Cleanliness.UNKNOWN -> R.string.scan_unknown; Cleanliness.CLEAN -> R.string.scan_clean; Cleanliness.DIRTY -> R.string.scan_dirty
})
@Composable private fun integrityLabel(value: Integrity) = stringResource(when (value) {
    Integrity.UNKNOWN -> R.string.scan_unknown; Integrity.INTACT -> R.string.scan_intact; Integrity.DAMAGED -> R.string.scan_damaged
})
@Composable private fun reusableLabel(value: Reusability) = stringResource(when (value) {
    Reusability.UNKNOWN -> R.string.scan_unknown; Reusability.YES -> R.string.scan_yes; Reusability.NO -> R.string.scan_no
})
@Composable private fun intentionLabel(value: Boolean?) = stringResource(when (value) {
    null -> R.string.scan_not_decided; true -> R.string.scan_will_reuse; false -> R.string.scan_will_not_reuse
})

@Composable
private fun ScanPhotoPreview(photo: ScanPhoto, objects: List<ScanObject>, selectedId: String?, onSelect: (String) -> Unit) {
    val image by produceState<ImageBitmap?>(null, photo.path) {
        value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(photo.path)?.asImageBitmap() }
    }
    val highlight = MaterialTheme.colorScheme.primary
    Box(Modifier.fillMaxWidth().height(220.dp).clip(MaterialTheme.shapes.large)) {
        image?.let { Image(it, stringResource(R.string.scan_photo_description), Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
        Canvas(Modifier.fillMaxSize().pointerInput(photo, objects, selectedId) {
            detectTapGestures { tap -> objects.firstOrNull { candidate ->
                val box = fitScanBounds(photo.width, photo.height, size.width.toFloat(), size.height.toFloat(), candidate.bounds)
                tap.x in box.left..(box.left + box.width) && tap.y in box.top..(box.top + box.height)
            }?.let { onSelect(it.id) } }
        }) {
            objects.forEach { candidate ->
                val box = fitScanBounds(photo.width, photo.height, size.width, size.height, candidate.bounds)
                drawRect(Color.White, Offset(box.left, box.top), Size(box.width, box.height), style = Stroke(5.dp.toPx()))
                drawRect(if (candidate.id == selectedId) highlight else Pine900,
                    Offset(box.left, box.top), Size(box.width, box.height), style = Stroke(2.dp.toPx()))
            }
        }
    }
}
