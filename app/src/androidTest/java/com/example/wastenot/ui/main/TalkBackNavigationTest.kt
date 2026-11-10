package com.example.wastenot.ui.main

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.os.Bundle
import android.os.SystemClock
import android.os.ParcelFileDescriptor
import android.view.InputDevice
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
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
            ActivityScenario.launch(MainActivity::class.java).use {
                await("login screen") { text(automation.rootInActiveWindow).contains("Belum punya akun?") }
                val email = seek(automation, { shortcut(79) }) { it.isEditable && text(it).contains("Email") }
                setText(email, "reader@example.invalid")
                val password = seek(automation, { shortcut(79) }) { it.isEditable && it.isPassword }
                setText(password, "short")
                seek(automation, { shortcut(79) }) { text(it).trim() == "Masuk" }
                shortcut(clickKey)
                val error = "Kata sandi perlu 12–128 karakter, termasuk spasi."
                await("password validation") { text(automation.rootInActiveWindow).contains(error) }
                seek(automation, { shortcut(79) }) { text(it).contains(error) }
                seek(automation, { shortcut(79) }) { text(it).trim() == "Masuk" }
                assertTrue(manager.isTouchExplorationEnabled)
                val file = java.io.File(instrumentation.targetContext.cacheDir, "qa/talkback-font200.png")
                file.parentFile!!.mkdirs()
                val bitmap = checkNotNull(automation.takeScreenshot())
                file.outputStream().use { check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
                bitmap.recycle()
            }
        } finally { keyboard.close(); pipe[0].close() }
    }

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

    private fun setText(node: AccessibilityNodeInfo, value: String) {
        assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }))
    }

    private fun text(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val hint = if (android.os.Build.VERSION.SDK_INT >= 26) node.hintText else null
        return listOfNotNull(node.text, node.contentDescription, node.error, hint).joinToString(" ") +
            (0 until node.childCount).joinToString(" ") { text(node.getChild(it)) }
    }

    private fun await(label: String, predicate: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (!predicate() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(200)
        assertTrue(label, predicate())
    }
}
