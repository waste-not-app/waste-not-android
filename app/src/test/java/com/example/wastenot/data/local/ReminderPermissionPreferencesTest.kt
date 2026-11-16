package com.example.wastenot.data.local

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ReminderPermissionPreferencesTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun denialSurvivesScreenReopenAndAccountCleanupWithoutOptingAnotherAccountIn() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("reminder_notification_permission", Context.MODE_PRIVATE).edit().clear().commit()
        val permission = ReminderPermissionPreferences(context)
        assertEquals(ReminderPermissionAction.REQUEST, reminderPermissionAction(false, permission.denied, true))
        permission.recordResult(granted = false)
        val job = SupervisorJob()
        try {
            val accounts = ReminderPreferences(PreferenceDataStoreFactory.create(
                scope = CoroutineScope(job + Dispatchers.IO),
                produceFile = { File(temporary.root, "reminders.preferences_pb") },
            ))
            accounts.save("first", ReminderSetting(enabled = true, hour = 18, minute = 30))
            accounts.clear("first")
            val reopened = ReminderPermissionPreferences(context)
            assertTrue(reopened.denied)
            assertEquals(ReminderPermissionAction.SETTINGS, reminderPermissionAction(false, reopened.denied, true))
            assertFalse(accounts.observe("second").first().enabled)
            assertFalse(accounts.observe("first").first().enabled)
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test fun settingsGrantAllowsEnableAndLegacySystemBlockingOpensSettings() {
        val context = RuntimeEnvironment.getApplication()
        val permission = ReminderPermissionPreferences(context)
        permission.recordResult(granted = false)
        assertEquals(ReminderPermissionAction.ENABLE, reminderPermissionAction(true, permission.denied, true))
        permission.recordResult(granted = true)
        assertFalse(ReminderPermissionPreferences(context).denied)
        assertEquals(ReminderPermissionAction.SETTINGS, reminderPermissionAction(false, false, false))
    }
}
