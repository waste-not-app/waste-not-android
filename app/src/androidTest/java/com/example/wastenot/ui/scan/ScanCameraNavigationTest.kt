package com.example.wastenot.ui.scan

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.MainActivity
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.FixMethodOrder
import org.junit.runners.MethodSorters
import org.junit.rules.ExternalResource

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ScanCameraNavigationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    @get:Rule(order = 0) val fixtures = object : ExternalResource() {
        override fun before() {
            check(context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                "Revoke CAMERA before starting this instrumentation suite; revoking inside the runner kills its process."
            }
            com.example.wastenot.clearEditorDraftFixtures(context)
            listOf("dummy_session", "dummy_logs_dummy-owner", "appearance").forEach {
                check(context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit())
            }
        }
    }
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun aDeniedPermissionExplainsTheResultAndKeepsManualReachable() {
        openScan()
        compose.onNodeWithText("Aktifkan kamera").performScrollTo().performClick()
        permission("permission_deny_button")
        val message = "Izin kamera belum diberikan. Kamu tetap bisa menggunakan input manual."
        waitFor(message)
        compose.onNodeWithText(message).performScrollTo().assertIsDisplayed()
        manual().assertIsDisplayed()
        capture("camera-permission-denied")
        manual().performClick()
        compose.onNodeWithText("Catat aktivitas").assertIsDisplayed()
    }

    @Test fun bRealCaptureThenDummyReviewPrefillsWithoutSavingAndDeletesItsPrivatePhoto() {
        assumeTrue(context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY))
        openScan()
        val preferences = context.getSharedPreferences("dummy_logs_dummy-owner", Context.MODE_PRIVATE)
        val records = preferences.getString("records", null)
        val directory = File(context.cacheDir, "scan")
        val originalFiles = directory.listFiles()?.map { it.name }?.toSet().orEmpty()
        capture("camera-before-permission")
        compose.onNodeWithText("Aktifkan kamera").performScrollTo().performClick()
        permission("permission_allow_button")
        compose.waitUntil(60_000) { compose.onAllNodes(hasText("Ambil foto") and isEnabled()).fetchSemanticsNodes().size == 1 }
        capture("camera-preview")
        compose.onNodeWithText("Ambil foto").performScrollTo().performClick()
        waitFor("Tinjau foto")
        compose.waitUntil(30_000) {
            compose.onAllNodesWithContentDescription("Foto sampah yang akan diperiksa").fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithText("Tinjau foto").assertIsDisplayed()
        val created = directory.listFiles().orEmpty().filter { it.name !in originalFiles }
        assertEquals("Capture creates one private JPEG", 1, created.size)
        assertTrue(created.single().length() in 1..5 * 1024 * 1024)
        capture("camera-photo-review")
        compose.onNodeWithText("Scan foto").performScrollTo().performClick()
        waitFor("Periksa objek")
        compose.onNodeWithText("Konfirmasi objek").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Pilih material").performScrollTo().performClick()
        compose.onNode(hasText("Plastik") and hasAnyAncestor(isPopup())).performClick()
        compose.onNodeWithText("Pilih jenis benda").performScrollTo().performClick()
        compose.onNode(hasText("Botol plastik (contoh)") and hasAnyAncestor(isPopup())).performClick()
        capture("camera-object-review")
        compose.onNodeWithText("Konfirmasi objek").performScrollTo().performClick()
        compose.onNodeWithText("Kondisi objek").assertIsDisplayed()
        capture("camera-context")
        compose.onNodeWithText("Lihat saran").performScrollTo().performClick()
        waitFor("Periksa saran")
        capture("camera-recommendation")
        compose.onNodeWithText("Gunakan pilihan, isi jumlah").performScrollTo().performClick()
        compose.onNodeWithText("Catat aktivitas").assertIsDisplayed()
        compose.onNodeWithText("Recycle").performScrollTo().assertIsSelected()
        compose.onNodeWithContentDescription("Kategori").performScrollTo().assertTextContains("Plastik")
        compose.onNodeWithContentDescription("Berat").performScrollTo()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        assertEquals(records, preferences.getString("records", null))
        assertTrue("Leaving scan deletes the captured photo", created.none { it.exists() })
        capture("camera-editor-prefill")
        compose.onNodeWithContentDescription("Kembali").performClick()
        compose.onNodeWithText("Buang perubahan?").assertIsDisplayed()
        compose.onNodeWithText("Buang perubahan").performClick()
        waitFor("650 g")
        compose.onNodeWithText("Profil").performClick()
        compose.onNodeWithText("50 XP total").performScrollTo().assertIsDisplayed()
        assertEquals(records, preferences.getString("records", null))
    }

    private fun openScan() {
        waitFor("650 g")
        compose.onNodeWithContentDescription("Catat atau scan sampah").performClick()
        compose.onNodeWithText("Scan sampah").assertIsDisplayed()
        manual().assertIsDisplayed()
        instrumentation.uiAutomation.waitForIdle(500, 5_000)
    }
    private fun manual() = compose.onNode(hasText("Input manual") and hasAnyAncestor(hasTestTag("capture-sheet")))
    private fun waitFor(text: String) {
        compose.waitUntil(30_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun permission(id: String) {
        val automation = instrumentation.uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS }
        var button: AccessibilityNodeInfo? = null
        try {
            compose.waitUntil(30_000) {
                button = find(automation.rootInActiveWindow, id)
                button != null
            }
        } catch (failure: ComposeTimeoutException) {
            val directory = File(context.getExternalFilesDir(null), "scan-camera-qa").apply { mkdirs() }
            File(directory, "permission-$id.txt").writeText(tree(automation.rootInActiveWindow))
            automation.takeScreenshot()?.let { bitmap ->
                File(directory, "permission-$id.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            throw failure
        }
        check(requireNotNull(button).performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }
    private fun tree(node: AccessibilityNodeInfo?, depth: Int = 0): String {
        if (node == null || depth > 20) return ""
        return "${node.viewIdResourceName}: ${node.text}\n" + (0 until node.childCount).joinToString("") { tree(node.getChild(it), depth + 1) }
    }
    private fun find(node: AccessibilityNodeInfo?, id: String): AccessibilityNodeInfo? {
        if (node == null) return null
        val resource = node.viewIdResourceName
        if (resource?.endsWith("/$id") == true || (id == "permission_allow_button" &&
                resource?.endsWith("/permission_allow_foreground_only_button") == true)) return node
        for (index in 0 until node.childCount) find(node.getChild(index), id)?.let { return it }
        return null
    }
    private fun capture(name: String) {
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        instrumentation.waitForIdleSync()
        instrumentation.uiAutomation.waitForIdle(300, 5_000)
        val directory = File(context.getExternalFilesDir(null), "scan-camera-qa").apply { mkdirs() }
        check(instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() == context.packageName)
        if (compose.onAllNodesWithTag("capture-sheet").fetchSemanticsNodes().isNotEmpty()) {
            val panel = compose.onNodeWithTag("capture-sheet").captureToImage().asAndroidBitmap()
            File(directory, "$name-panel.png").outputStream().use { check(panel.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        }
        val image = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory, "$name.png").outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        image.recycle()
    }
}
