package com.example.wastenot.ui.main

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.wastenot.R
import com.example.wastenot.data.local.ReminderPermissionPreferences
import com.example.wastenot.data.local.ReminderSaveResult
import com.example.wastenot.data.local.ReminderSetting
import com.example.wastenot.theme.WasteNotTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ReminderPermissionRecoveryHostTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun recoveringPermissionWithoutEnablingRemindersKeepsOptInOff() = recover(enableThroughSwitch = false)
    @Test fun explicitEnableCanResumeAfterSettingsWithoutSavingAutomatically() = recover(enableThroughSwitch = true)

    @Test fun saveWaitsForOutcomeAndRetriesTheSameDraftAfterFailure() {
        val resources = compose.activity.resources
        val observed = mutableStateOf(ReminderSetting(enabled = true, hour = 18, minute = 25))
        val finish = CompletableDeferred<ReminderSaveResult>()
        val requests = mutableListOf<ReminderSetting>()
        compose.setContent {
            WasteNotTheme {
                ReminderSettingsScreen(observed.value, PaddingValues(), { next ->
                    requests += next
                    if (requests.size == 1) finish.await() else ReminderSaveResult.SAVED
                })
            }
        }

        compose.onNode(isToggleable()).performClick().assertIsOff()
        val save = compose.onNodeWithText(resources.getString(R.string.reminder_save))
        val off = compose.onNodeWithText(resources.getString(R.string.reminder_disabled_saved))
        save.performScrollTo().performClick()
        val working = compose.onNodeWithText(resources.getString(R.string.reminder_saving))
        working.assertIsDisplayed().assertIsNotEnabled()
        off.assertDoesNotExist()
        compose.onNode(isToggleable()).assertIsNotEnabled()

        compose.runOnIdle {
            observed.value = ReminderSetting(enabled = true, hour = 20, minute = 0)
            finish.complete(ReminderSaveResult.SCHEDULING_FAILED)
        }
        compose.onNode(isToggleable()).assertIsOff()
        compose.onNodeWithText(resources.getString(R.string.reminder_cancel_failed)).performScrollTo().assertIsDisplayed()
        off.assertDoesNotExist()
        val retry = compose.onNodeWithText(resources.getString(R.string.retry))
        retry.performScrollTo().assertIsEnabled().performClick()
        off.performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            val draft = ReminderSetting(enabled = false, hour = 18, minute = 25)
            assertEquals(listOf(draft, draft), requests)
        }
    }

    private fun recover(enableThroughSwitch: Boolean) {
        val application = RuntimeEnvironment.getApplication()
        shadowOf(application.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(true)
        var allowed = false
        var opened = 0
        val context = object : ContextWrapper(compose.activity) {
            override fun getApplicationContext(): Context = this
            override fun checkPermission(permission: String, pid: Int, uid: Int): Int =
                if (permission == Manifest.permission.POST_NOTIFICATIONS) {
                    if (allowed) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
                } else super.checkPermission(permission, pid, uid)
            override fun startActivity(intent: Intent) { opened++ }
        }
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle get() = registry
        }
        val permissions = ReminderPermissionPreferences(context)
        permissions.recordResult(granted = false)
        var saved: ReminderSetting? = null
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context, LocalLifecycleOwner provides owner) {
                WasteNotTheme {
                    ReminderSettingsScreen(ReminderSetting(), PaddingValues(), {
                        saved = it
                        ReminderSaveResult.SAVED
                    })
                }
            }
        }
        compose.onNode(isToggleable()).assertIsOff()
        if (enableThroughSwitch) compose.onNode(isToggleable()).performClick()
        else compose.onNodeWithText(application.getString(R.string.reminder_open_settings)).performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, opened)
            assertNull(saved)
            owner.registry.currentState = Lifecycle.State.CREATED
            allowed = true
        }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.waitForIdle()
        if (enableThroughSwitch) compose.onNode(isToggleable()).assertIsOn()
        else compose.onNode(isToggleable()).assertIsOff()
        compose.runOnIdle { assertNull(saved) }
        compose.onNodeWithText(application.getString(R.string.reminder_save)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(enableThroughSwitch, saved?.enabled) }
    }
}
