package com.example.wastenot.ui.main

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.data.local.ReminderPermissionPreferences
import com.example.wastenot.data.local.ReminderSetting
import com.example.wastenot.theme.WasteNotTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@SdkSuppress(minSdkVersion = 33)
class ReminderPermissionRecoveryTest {
    @get:Rule val compose = createComposeRule()

    @Test fun enablingAfterRecordedDenialOpensSystemSettingsAgainAfterScreenReopen() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val opened = mutableListOf<Intent>()
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun checkPermission(permission: String, pid: Int, uid: Int): Int =
                if (permission == Manifest.permission.POST_NOTIFICATIONS) PackageManager.PERMISSION_DENIED
                else super.checkPermission(permission, pid, uid)
            override fun startActivity(intent: Intent) { opened.add(intent) }
        }
        val preferences = ReminderPermissionPreferences(context)
        preferences.recordResult(granted = false)
        val visible = mutableStateOf(true)
        var saves = 0
        try {
            compose.setContent {
                CompositionLocalProvider(LocalContext provides context) {
                    WasteNotTheme {
                        if (visible.value) ReminderSettingsScreen(ReminderSetting(), PaddingValues(), { saves++ })
                    }
                }
            }
            repeat(2) {
                compose.onNode(isToggleable()).assertIsOff()
                compose.onNodeWithText("Open notification settings", substring = true).assertIsDisplayed()
                compose.onNode(isToggleable()).performClick()
                compose.runOnIdle {
                    assertEquals(it + 1, opened.size)
                    assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, opened.last().action)
                    assertEquals(0, saves)
                    assertEquals(base.packageName, opened.last().getStringExtra(Settings.EXTRA_APP_PACKAGE))
                    visible.value = false
                }
                compose.waitForIdle()
                compose.runOnIdle { visible.value = true }
            }
        } finally {
            preferences.recordResult(granted = true)
        }
    }
}
