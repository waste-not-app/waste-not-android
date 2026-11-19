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
    @get:Rule(order = -1) val navigationData = com.example.wastenot.NavigationFixtureRule()
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
        compose.onNodeWithText("Turn on camera").performScrollTo().performClick()
        permission("permission_deny_button")
        val message = "Camera permission wasn't granted. You can still log manually."
        waitFor(message)
        compose.onNodeWithText(message).performScrollTo().assertIsDisplayed()
        manual().assertIsDisplayed()
        capture("camera-permission-denied")
        manual().performClick()
        compose.onNodeWithText("Log activity").assertIsDisplayed()
    }

    @Test fun bRealCaptureThenPrototypeReviewKeepsManualAvailableAndDeletesItsPrivatePhoto() {
        assumeTrue(context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY))
        openScan()
        val preferences = context.getSharedPreferences("dummy_logs_dummy-owner", Context.MODE_PRIVATE)
        val records = preferences.getString("records", null)
        val directory = File(context.cacheDir, "scan")
        val originalFiles = directory.listFiles()?.map { it.name }?.toSet().orEmpty()
        capture("camera-before-permission")
        compose.onNodeWithText("Turn on camera").performScrollTo().performClick()
        permission("permission_allow_button")
        compose.waitUntil(60_000) { compose.onAllNodes(hasText("Take photo") and isEnabled()).fetchSemanticsNodes().size == 1 }
        capture("camera-preview")
        compose.onNodeWithText("Take photo").performScrollTo().performClick()
        waitFor("Review photo")
        compose.waitUntil(30_000) {
            compose.onAllNodesWithContentDescription("Photo of the waste to check").fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithText("Review photo").assertIsDisplayed()
        val created = directory.listFiles().orEmpty().filter { it.name !in originalFiles }
        assertEquals("Capture creates one private JPEG", 1, created.size)
        assertTrue(created.single().length() in 1..5 * 1024 * 1024)
        capture("camera-photo-review")
        compose.onNodeWithText("Scan photo").performScrollTo().performClick()
        waitFor("Check the object")
        compose.onNodeWithText("Confirm object").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Choose a material").performScrollTo().performClick()
        compose.onNode(hasText("Plastic") and hasAnyAncestor(isPopup())).performClick()
        compose.onNodeWithText("Choose an item type").performScrollTo().performClick()
        compose.onNode(hasText("Plastic bottle (sample)") and hasAnyAncestor(isPopup())).performClick()
        capture("camera-object-review")
        compose.onNodeWithText("Confirm object").performScrollTo().performClick()
        compose.onNodeWithText("Item condition").assertIsDisplayed()
        capture("camera-context")
        compose.onNodeWithText("See advice").performScrollTo().performClick()
        waitFor("Review the advice")
        capture("camera-recommendation")
        compose.onNodeWithText("Use this, add amount").assertDoesNotExist()
        manual().performClick()
        compose.onNodeWithText("Log activity").assertIsDisplayed()
        compose.onNodeWithText("Recycle").performScrollTo().assertIsNotSelected()
        compose.onNodeWithContentDescription("Category").performScrollTo().assertTextContains("Plastic")
        compose.onNodeWithContentDescription("Weight").performScrollTo()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        assertEquals(records, preferences.getString("records", null))
        assertTrue("Leaving scan deletes the captured photo", created.none { it.exists() })
        capture("camera-editor-prefill")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Discard changes?").assertIsDisplayed()
        compose.onNodeWithText("Discard changes").performClick()
        waitFor("650 g")
        compose.onNodeWithText("Profile").performClick()
        compose.onNodeWithText("50 XP total").performScrollTo().assertIsDisplayed()
        assertEquals(records, preferences.getString("records", null))
    }

    private fun openScan() {
        waitFor("650 g")
        compose.onNodeWithContentDescription("Log or scan waste").performClick()
        compose.onNodeWithText("Scan waste").assertIsDisplayed()
        manual().assertIsDisplayed()
        instrumentation.uiAutomation.waitForIdle(500, 5_000)
    }
    private fun manual() = compose.onNode(hasText("Log manually") and hasAnyAncestor(hasTestTag("capture-sheet")))
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
