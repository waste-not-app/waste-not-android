package com.example.wastenot.ui.scan

import android.Manifest
import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.view.accessibility.AccessibilityNodeInfo
import android.accessibilityservice.AccessibilityServiceInfo
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.camera.view.PreviewView
import androidx.camera.view.LifecycleCameraController
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.data.local.ScanPhotoCache
import com.example.wastenot.model.ScanPhoto
import com.example.wastenot.theme.LocalWasteNotGradients
import com.example.wastenot.theme.WasteNotTheme
import com.example.wastenot.ui.main.captureUiPreview
import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource

/** Each method runs in its own instrumentation; the host restores CAMERA permission between runs. */
class ScanCameraLifecycleTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val dark get() = InstrumentationRegistry.getArguments().getString("cameraDark") == "true"
    private val suffix get() = if (dark) "dark" else "light"
    private lateinit var directory: File
    private lateinit var cache: ScanPhotoCache
    private var open by mutableStateOf(true)
    private var state by mutableStateOf(ScanState(session = "camera-lifecycle"))
    private var photos = 0
    private var manuals = 0
    private var settings = 0
    private val problems = mutableListOf<String>()
    private val monitor = object : Instrumentation.ActivityMonitor() {
        override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
            if (intent.action != Settings.ACTION_APPLICATION_DETAILS_SETTINGS) return null
            assertEquals("package:${context.packageName}", intent.dataString)
            settings++
            return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
        }
    }
    @get:Rule(order = 0) val fixtures = object : ExternalResource() {
        override fun before() {
            check(context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            directory = File(context.cacheDir, "camera-lifecycle-${System.nanoTime()}")
            cache = ScanPhotoCache(directory, "camera-lifecycle-fixture") {}
            instrumentation.addMonitor(monitor)
        }
        override fun after() {
            instrumentation.removeMonitor(monitor)
            directory.deleteRecursively()
        }
    }
    @get:Rule(order = 1) val compose = createAndroidComposeRule<ComponentActivity>()

    private fun show() {
        compose.setContent { WasteNotTheme(dark) {
            SideEffect {
                compose.activity.enableEdgeToEdge(statusBarStyle = SystemBarStyle.auto(0, 0) { dark },
                    navigationBarStyle = SystemBarStyle.auto(0, 0) { dark })
            }
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(Modifier.fillMaxSize().background(LocalWasteNotGradients.current.canvas)) {
                    if (open) ScanSheet(state, { open = false }, { manuals++; open = false },
                        {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, camera = {
                            ScanCamera(state.session, cache, { photo ->
                                photos++; state = state.copy(photo = photo, phase = ScanPhase.PHOTO)
                            }, { problem -> state = state.copy(phase = ScanPhase.FAILURE, problem = problem) })
                        })
                    else Text("Scan ditutup", Modifier.safeDrawingPadding())
                }
            }
        } }
    }

    @Test fun settingsGrantRemovesTheBlockedStateWithoutActivatingCamera() {
        show()
        repeat(2) {
            compose.onNodeWithText("Aktifkan kamera").performScrollTo().performClick()
            denyPermission()
            compose.onNodeWithText("Izin kamera belum diberikan. Kamu tetap bisa menggunakan input manual.")
                .performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithText("Buka pengaturan kamera").performScrollTo().assertIsDisplayed()
        capture("permission-blocked")
        compose.onNodeWithText("Buka pengaturan kamera").performClick()
        assertEquals(1, settings)
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.CAMERA)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
        capture("permission-granted-return")
        if (compose.onAllNodesWithText("Aktifkan kamera").fetchSemanticsNodes().size != 1)
            problems.add("Granted permission must restore the explicit activate action after Settings")
        if (compose.onAllNodesWithText("Buka pengaturan kamera").fetchSemanticsNodes().isNotEmpty())
            problems.add("The blocked Settings action must disappear after permission is granted")
        assertNull("Permission grant alone must not start the camera", preview())
        compose.onNodeWithText("Input manual").assertIsDisplayed().performClick()
        compose.onNodeWithText("Scan ditutup").assertIsDisplayed()
        assertEquals(1, manuals)
        assertEquals(0, photos)
        assertTrue(problems.joinToString("; "), problems.isEmpty())
    }

    @Test fun streamReadinessCaptureAndCloseReleaseTheRealCamera() {
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.CAMERA)
        show()
        activate()
        val view = checkNotNull(preview())
        val controller = compose.runOnIdle { checkNotNull(view.controller as? LifecycleCameraController) }
        awaitStream(view)
        compose.onNodeWithText("Ambil foto").performScrollTo().assertIsEnabled()
        capture("preview-streaming")
        compose.runOnIdle { controller.unbind() }
        compose.waitUntil(15_000) { compose.runOnIdle { view.previewStreamState.value == PreviewView.StreamState.IDLE } }
        capture("preview-idle")
        if (compose.onAllNodes(hasText("Ambil foto") and isEnabled()).fetchSemanticsNodes().isNotEmpty())
            problems.add("Capture must be disabled while the actual preview is IDLE")
        if (compose.onAllNodesWithText("Menyiapkan kamera…").fetchSemanticsNodes().isEmpty())
            problems.add("IDLE preview needs its preparing status")
        else compose.onNodeWithText("Menyiapkan kamera…").assertIsNotEnabled()
        compose.runOnIdle { controller.bindToLifecycle(compose.activity) }
        awaitStream(view)
        compose.onNodeWithText("Ambil foto").performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(30_000) { state.phase == ScanPhase.PHOTO }
        val photo = checkNotNull(state.photo)
        assertTrue(File(photo.path).isFile && File(photo.path).length() in 1..5 * 1024 * 1024)
        assertTrue(photo.width in 1..1600 && photo.height in 1..1600)
        compose.onNodeWithText("Tinjau foto").assertIsDisplayed()
        capture("photo-review")
        awaitReleased(view)
        assertEquals(1, photos)
        compose.onNodeWithContentDescription("Tutup scan").performClick()
        compose.runOnIdle { state = ScanState(session = "camera-second"); open = true }
        activate()
        val closing = checkNotNull(preview())
        awaitStream(closing)
        capture("preview-before-close")
        compose.onNodeWithContentDescription("Tutup scan").performClick()
        compose.onNodeWithText("Scan ditutup").assertIsDisplayed()
        awaitReleased(closing)
        assertEquals(1, photos)
        assertTrue(problems.joinToString("; "), problems.isEmpty())
    }

    private fun activate() {
        compose.onNodeWithText("Aktifkan kamera").performScrollTo().performClick()
        compose.waitUntil(30_000) { preview() != null || state.phase == ScanPhase.FAILURE }
        assertNotEquals("Camera must open on the configured emulator", ScanPhase.FAILURE, state.phase)
    }

    private fun preview(): PreviewView? = compose.runOnIdle {
        fun find(view: View): PreviewView? {
            if (view is PreviewView) return view
            if (view is ViewGroup) repeat(view.childCount) { find(view.getChildAt(it))?.let { return it } }
            return null
        }
        WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull(::find)
    }

    private fun awaitStream(view: PreviewView) {
        compose.waitUntil(30_000) { compose.runOnIdle { view.previewStreamState.value == PreviewView.StreamState.STREAMING } }
        compose.waitUntil(20_000) { compose.runOnIdle {
            view.bitmap?.let { image ->
                try { visiblePixels(image) } finally { image.recycle() }
            } ?: false
        } }
    }

    private fun visiblePixels(image: Bitmap): Boolean {
        val colors = mutableSetOf<Int>()
        for (y in 0 until 20) for (x in 0 until 20) colors.add(image.getPixel(x * image.width / 20, y * image.height / 20) and 0xffe0e0e0.toInt())
        return colors.size > 4
    }

    private fun awaitReleased(view: PreviewView) {
        compose.waitUntil(15_000) { compose.runOnIdle { view.previewStreamState.value == PreviewView.StreamState.IDLE } }
        compose.runOnIdle {
            if (view.controller != null) problems.add("Released preview must detach its controller")
            if (view.previewStreamState.hasObservers()) problems.add("Released preview must remove its stream observer")
        }
    }

    private fun capture(name: String) {
        captureUiPreview(compose, "$name-$suffix", "camera-lifecycle-review")
        val info = JSONObject().put("theme", suffix).put("photoCallbacks", photos).put("manualCallbacks", manuals)
            .put("cameraPermission", context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
        val view = preview()
        info.put("previewState", compose.runOnIdle { view?.previewStreamState?.value?.name ?: "absent" })
        info.put("captureEnabled", compose.onAllNodes(hasText("Ambil foto") and isEnabled()).fetchSemanticsNodes().isNotEmpty())
        File(context.getExternalFilesDir(null), "camera-lifecycle-review/$name-$suffix.json").writeText(info.toString(2))
    }

    private fun denyPermission() {
        val automation = instrumentation.uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS }
        fun find(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.viewIdResourceName?.substringAfterLast('/') in
                setOf("permission_deny_button", "permission_deny_and_dont_ask_again_button")) return node
            repeat(node.childCount) { find(node.getChild(it))?.let { return it } }
            return null
        }
        var button: AccessibilityNodeInfo? = null
        compose.waitUntil(20_000) { button = find(automation.rootInActiveWindow); button != null }
        check(checkNotNull(button).performAction(AccessibilityNodeInfo.ACTION_CLICK))
        compose.waitUntil(10_000) { automation.rootInActiveWindow?.packageName?.toString() == context.packageName }
    }
}
