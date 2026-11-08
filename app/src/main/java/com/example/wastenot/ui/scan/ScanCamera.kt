package com.example.wastenot.ui.scan

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Size as CaptureSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.wastenot.R
import com.example.wastenot.data.local.ScanPhotoCache
import com.example.wastenot.model.ScanPhoto
import com.example.wastenot.ui.components.ActionButton
import com.example.wastenot.ui.components.SecondaryActionButton
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
internal fun ScanCamera(session: String, cache: ScanPhotoCache, onPhoto: (ScanPhoto) -> Unit,
    onProblem: (ScanProblem) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    var active by remember(session, lifecycle) { mutableStateOf(false) }
    var denied by rememberSaveable { mutableStateOf(false) }
    var blocked by rememberSaveable { mutableStateOf(false) }
    val latestPhoto by rememberUpdatedState(onPhoto)
    val latestProblem by rememberUpdatedState(onProblem)
    val alive = remember(session, lifecycle) { AtomicBoolean(true) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (alive.get() && lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            active = granted
            denied = !granted
            blocked = !granted && context.activity()?.let {
                !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
            } == true
        }
    }
    DisposableEffect(session, lifecycle) {
        alive.set(true)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) active = false
            if (event == Lifecycle.Event.ON_RESUME &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                denied = false
                blocked = false
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { alive.set(false); lifecycle.lifecycle.removeObserver(observer) }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (active) key(session, lifecycle) { LiveCamera(cache, latestPhoto, latestProblem, Modifier.fillMaxWidth()) }
        else {
            Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF13261D), contentColor = Color.White,
                border = BorderStroke(1.dp, Color(0xFF647A6A))) {
                Box(Modifier.fillMaxWidth().heightIn(min = 230.dp)
                    .background(Brush.linearGradient(listOf(Color(0xFF13261D), Color(0xFF294735)))) , contentAlignment = Alignment.Center) {
                    Canvas(Modifier.matchParentSize()) {
                        drawRoundRect(Color(0xFF99B9A0), Offset(24.dp.toPx(), 24.dp.toPx()),
                            Size(size.width - 48.dp.toPx(), size.height - 48.dp.toPx()), CornerRadius(14.dp.toPx()),
                            style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx()))))
                    }
                    Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(painterResource(R.drawable.ic_camera), null, Modifier.size(40.dp))
                        Text(stringResource(R.string.scan_aim), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                        Text(stringResource(if (denied) R.string.scan_permission_denied else R.string.scan_camera_hint),
                            style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                    }
                }
            }
            if (blocked) SecondaryActionButton(stringResource(R.string.scan_open_settings), onClick = {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
            }, modifier = Modifier.fillMaxWidth())
            else ActionButton(stringResource(R.string.scan_activate), onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) active = true
                else permission.launch(Manifest.permission.CAMERA)
            }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun LiveCamera(cache: ScanPhotoCache, onPhoto: (ScanPhoto) -> Unit, onProblem: (ScanProblem) -> Unit,
    modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val controller = remember(context) { LifecycleCameraController(context).apply {
        setEnabledUseCases(CameraController.IMAGE_CAPTURE)
        imageCaptureMode = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
        imageCaptureResolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(ResolutionStrategy(CaptureSize(1280, 960), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
            .build()
    } }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val main = remember(context) { ContextCompat.getMainExecutor(context) }
    val alive = remember { AtomicBoolean(true) }
    var ready by remember { mutableStateOf(false) }
    var streaming by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    val streamObserver = remember { Observer<PreviewView.StreamState> { streaming = it == PreviewView.StreamState.STREAMING } }
    val latestPhoto by rememberUpdatedState(onPhoto)
    val latestProblem by rememberUpdatedState(onProblem)
    DisposableEffect(controller, owner) {
        alive.set(true)
        var observedCamera: LiveData<CameraState>? = null
        val cameraErrors = Observer<CameraState> { state ->
            if (alive.get() && state.error?.type == CameraState.ErrorType.CRITICAL) latestProblem(ScanProblem.CAMERA)
        }
        controller.initializationFuture.addListener({
            if (alive.get()) try {
                controller.initializationFuture.get()
                if (!controller.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                    if (!controller.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) throw IllegalStateException("No camera")
                    controller.cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                }
                controller.bindToLifecycle(owner)
                observedCamera = controller.cameraInfo?.cameraState
                observedCamera?.observe(owner, cameraErrors)
                ready = true
            } catch (_: Exception) { latestProblem(ScanProblem.CAMERA) }
        }, main)
        onDispose {
            alive.set(false)
            observedCamera?.removeObserver(cameraErrors)
            controller.unbind()
            // A pending callback must still run to close its ImageProxy and delete an outgoing capture.
            if (!capturing) executor.shutdown()
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AndroidView(factory = { PreviewView(it).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FIT_CENTER
            this.controller = controller
            previewStreamState.observe(owner, streamObserver)
        } }, modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF13261D)), onReset = null, onRelease = {
            it.previewStreamState.removeObserver(streamObserver)
            it.controller = null
        })
        ActionButton(stringResource(R.string.scan_take_photo), onClick = {
            if (alive.get() && ready && streaming && !capturing) {
                capturing = true
                try {
                    controller.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            var photo: ScanPhoto? = null
                            try {
                                val buffer = image.planes[0].buffer
                                photo = cache.save(ByteArray(buffer.remaining()).also(buffer::get), image.imageInfo.rotationDegrees)
                            } catch (_: Exception) {
                                main.execute { if (alive.get()) { capturing = false; latestProblem(ScanProblem.PHOTO) } }
                            } finally { image.close() }
                            photo?.let { saved -> main.execute {
                                if (alive.get()) { capturing = false; latestPhoto(saved) } else cache.delete(saved)
                            } }
                            if (!alive.get()) executor.shutdown()
                        }
                        override fun onError(exception: ImageCaptureException) {
                            main.execute { if (alive.get()) { capturing = false; latestProblem(ScanProblem.CAMERA) } }
                            if (!alive.get()) executor.shutdown()
                        }
                    })
                } catch (_: Exception) { capturing = false; latestProblem(ScanProblem.CAMERA) }
            }
        }, modifier = Modifier.fillMaxWidth(), enabled = ready && streaming && !capturing,
            loading = !ready || !streaming || capturing,
            loadingLabel = stringResource(if (capturing) R.string.scan_capturing else R.string.scan_camera_preparing))
    }
}

internal fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
