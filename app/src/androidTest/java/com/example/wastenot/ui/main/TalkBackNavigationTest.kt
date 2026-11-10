package com.example.wastenot.ui.main

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.os.SystemClock
import android.os.ParcelFileDescriptor
import android.view.InputDevice
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class TalkBackNavigationTest {
    @Test fun readerReachesLabelsValidationAndSubmitAtLargeFont() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("Run explicitly on a dedicated TalkBack-enabled emulator",
            arguments.getString("talkback") == "true")
        assumeTrue(android.os.Build.VERSION.SDK_INT >= 36)
        val recordSpeech = arguments.getString("talkbackRecordSpeech") == "true"
        val landscape = arguments.getString("talkbackLandscape") == "true"
        val (modifierKey, clickKey) = when (val keymap = arguments.getString("talkbackKeymap", "default")) {
            "default" -> 4 to 40 // Alt + Enter
            "enhanced" -> 8 to 44 // Action (Windows) + Space
            else -> error("Unsupported TalkBack keymap: $keymap")
        }
        instrumentation.targetContext.getSharedPreferences("dummy_session", android.content.Context.MODE_PRIVATE)
            .edit().clear().putBoolean("signed_out", true).commit()
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        val manager = instrumentation.targetContext.getSystemService(AccessibilityManager::class.java)
        assertTrue(manager.isTouchExplorationEnabled)
        assertTrue(manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_SPOKEN)
            .any { it.resolveInfo.serviceInfo.packageName == "com.google.android.marvin.talkback" })
        assertTrue(instrumentation.targetContext.resources.configuration.fontScale >= 1.99f)

        // External HID input reaches TalkBack; shell-injected keys did not move its focus.
        val pipe = automation.executeShellCommandRw("hid -")
        val keyboard = ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])
        try {
            fun send(value: String) { keyboard.write((value + "\n").toByteArray()); keyboard.flush() }
            val descriptor = intArrayOf(5,1,9,6,161,1,5,7,25,224,41,231,21,0,37,1,117,1,149,8,129,2,
                149,1,117,8,129,1,149,6,117,8,21,0,37,101,5,7,25,0,41,101,129,0,192)
            send("""{"id":1,"command":"register","name":"WasteNot QA keyboard","vid":6353,"pid":260,"bus":"usb","descriptor":[${descriptor.joinToString()}]}""")
            await("external QA keyboard") { InputDevice.getDeviceIds().any { id ->
                InputDevice.getDevice(id)?.let { it.name == "WasteNot QA keyboard" && it.isExternal } == true
            } }
            fun shortcut(key: Int) {
                send("""{"id":1,"command":"report","report":[$modifierKey,0,$key,0,0,0,0,0]}""")
                SystemClock.sleep(100)
                send("""{"id":1,"command":"report","report":[0,0,0,0,0,0,0,0]}""")
                SystemClock.sleep(250)
            }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                if (landscape) {
                    scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
                    await("landscape activity orientation") {
                        var orientation = Configuration.ORIENTATION_UNDEFINED
                        scenario.onActivity { orientation = it.resources.configuration.orientation }
                        orientation == Configuration.ORIENTATION_LANDSCAPE
                    }
                }
                await("login screen") { text(appRoot(automation)).contains("Catat kebiasaanmu lagi.") }
                fun isEmail(node: AccessibilityNodeInfo) =
                    node.className == "android.widget.EditText" && !node.isPassword && text(node).contains("Email")
                fun isPassword(node: AccessibilityNodeInfo) = node.isEditable && node.isPassword
                val passwordBeforeKeyboard = findNode(appRoot(automation), ::isPassword)
                // Set up the password field within the form after Gboard may have taken focus.
                await("enter fixture email") {
                    val root = appRoot(automation)
                    val email = findNode(root, ::isEmail)
                    if (email != null) return@await setText(email, "reader@example.invalid")
                    findNode(root) { it.isScrollable }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                    false
                }
                await("focus the password input") {
                    val field = findNode(appRoot(automation), ::isPassword)
                        ?: passwordBeforeKeyboard?.takeIf { it.refresh() }
                    if (field == null) findNode(appRoot(automation)) { it.isScrollable }
                        ?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                    else if (!field.isFocused) field.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                    field?.isFocused == true
                }
                fun keyboardVisible() = automation.windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
                await("password keyboard visible") {
                    if (keyboardVisible()) return@await true
                    findNode(appRoot(automation), ::isPassword)?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    false
                }
                await("enter fixture password") {
                    findNode(appRoot(automation), ::isPassword)?.let { setText(it, "short") } == true
                }
                focusFormControl(automation, "password visibility") {
                    it.contentDescription == "Tampilkan kata sandi"
                }
                shortcut(79)
                await("TalkBack moves from password visibility to Masuk") {
                    automation.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)?.let(::isSubmit) == true
                }
                assertTrue(keyboardVisible())
                if (recordSpeech) SystemClock.sleep(2_000)
                shortcut(clickKey)
                val error = "Kata sandi perlu 12–128 karakter, termasuk spasi."
                await("password validation") { text(appRoot(automation)).contains(error) }
                if (landscape) {
                    await("landscape validation dismisses the keyboard") { !keyboardVisible() }
                    // Explore the visible inline message after the IME window closes.
                    focusFormControl(automation, "password validation message") { it.text?.toString() == error }
                } else {
                    val focusedAfterSubmit = automation.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
                    val towardValidation = if (focusedAfterSubmit?.let(::isSubmit) == true) 80 else 79
                    seek(automation, { shortcut(towardValidation) }) { text(it).contains(error) }
                }
                if (recordSpeech) SystemClock.sleep(5_000)
                seek(automation, { shortcut(79) }) { isSubmit(it) }
                assertTrue(manager.isTouchExplorationEnabled)
                val fileName = if (landscape) "talkback-landscape-font200.png" else "talkback-font200.png"
                val file = java.io.File(instrumentation.targetContext.cacheDir, "qa/$fileName")
                file.parentFile!!.mkdirs()
                val bitmap = checkNotNull(automation.takeScreenshot())
                file.outputStream().use { check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
                bitmap.recycle()
            }
        } finally { keyboard.close(); pipe[0].close() }
    }

    private fun appRoot(automation: UiAutomation) = automation.windows.firstOrNull {
        it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.root?.packageName == "com.example.wastenot"
    }?.root

    private fun focusFormControl(
        automation: UiAutomation,
        label: String,
        matches: (AccessibilityNodeInfo) -> Boolean,
    ) {
        fun isControl(node: AccessibilityNodeInfo) =
            node.packageName == "com.example.wastenot" && node.isVisibleToUser && matches(node)
        var focusedSince: Long? = null
        await("TalkBack focused $label") {
            val focused = automation.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
            if (focused?.let(::isControl) == true) {
                val now = SystemClock.uptimeMillis()
                val start = focusedSince ?: now.also { focusedSince = it }
                return@await now - start >= 600
            }
            focusedSince = null
            findNode(appRoot(automation), ::isControl)?.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
            false
        }
    }

    private fun findNode(node: AccessibilityNodeInfo?, matches: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (node == null || matches(node)) return node
        return (0 until node.childCount).firstNotNullOfOrNull { findNode(node.getChild(it), matches) }
    }

    private fun isSubmit(node: AccessibilityNodeInfo) = node.isClickable && node.isEnabled && text(node).trim() == "Masuk"

    private fun seek(automation: UiAutomation, next: () -> Unit, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        val observed = linkedSetOf<String>()
        repeat(50) {
            automation.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)?.let { focused ->
                observed += "${focused.packageName}/${focused.className}: " + if (focused.isPassword) "[password]" else text(focused)
                if (focused.packageName == "com.example.wastenot" && predicate(focused)) return focused
            }
            next()
        }
        error("TalkBack did not reach the expected label/action: $observed")
    }

    private fun setText(node: AccessibilityNodeInfo, value: String): Boolean =
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        })

    private fun text(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val hint = if (android.os.Build.VERSION.SDK_INT >= 26) node.hintText else null
        return listOfNotNull(node.text, node.contentDescription, node.error, hint).joinToString(" ") +
            (0 until node.childCount).joinToString(" ") { text(node.getChild(it)) }
    }

    private fun await(label: String, predicate: () -> Boolean) {
        awaitValue(label) { Unit.takeIf { predicate() } }
    }

    private fun <T : Any> awaitValue(label: String, read: () -> T?): T {
        val deadline = SystemClock.uptimeMillis() + 15_000
        do {
            read()?.let { return it }
            SystemClock.sleep(200)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError(label)
    }
}
