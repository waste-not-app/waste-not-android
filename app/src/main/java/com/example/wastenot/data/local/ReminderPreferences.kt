package com.example.wastenot.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

data class ReminderSetting(
    val enabled: Boolean = false,
    val hour: Int = 20,
    val minute: Int = 0,
) {
    init {
        require(hour in 0..23 && minute in 0..59)
    }
}

interface ReminderStore {
    fun observe(ownerId: String): Flow<ReminderSetting>
    suspend fun save(ownerId: String, setting: ReminderSetting)
    suspend fun clear(ownerId: String)
}

private val Context.reminderDataStore by preferencesDataStore(
    name = "reminders",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

class ReminderPreferences(private val dataStore: DataStore<Preferences>) : ReminderStore {
    private fun enabledKey(ownerId: String) = booleanPreferencesKey("enabled_$ownerId")
    private fun hourKey(ownerId: String) = intPreferencesKey("hour_$ownerId")
    private fun minuteKey(ownerId: String) = intPreferencesKey("minute_$ownerId")

    override fun observe(ownerId: String): Flow<ReminderSetting> = dataStore.data.map { prefs ->
        val enabled = prefs[enabledKey(ownerId)] ?: false
        val hour = (prefs[hourKey(ownerId)] ?: 20).coerceIn(0, 23)
        val minute = (prefs[minuteKey(ownerId)] ?: 0).coerceIn(0, 59)
        ReminderSetting(enabled = enabled, hour = hour, minute = minute)
    }.distinctUntilChanged()

    override suspend fun save(ownerId: String, setting: ReminderSetting) {
        dataStore.edit { prefs ->
            prefs[enabledKey(ownerId)] = setting.enabled
            prefs[hourKey(ownerId)] = setting.hour
            prefs[minuteKey(ownerId)] = setting.minute
        }
    }

    override suspend fun clear(ownerId: String) {
        dataStore.edit { prefs ->
            prefs.remove(enabledKey(ownerId))
            prefs.remove(hourKey(ownerId))
            prefs.remove(minuteKey(ownerId))
        }
    }

    companion object {
        fun create(context: Context) = ReminderPreferences(context.applicationContext.reminderDataStore)
    }
}

/** Notification permission belongs to the installation, not to a signed-in account. */
class ReminderPermissionPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "reminder_notification_permission", Context.MODE_PRIVATE,
    )

    val denied: Boolean get() = preferences.getBoolean("denied", false)

    fun recordResult(granted: Boolean) {
        preferences.edit().putBoolean("denied", !granted).apply()
    }
}

internal enum class ReminderPermissionAction { ENABLE, REQUEST, SETTINGS }

internal fun reminderPermissionAction(
    notificationsAllowed: Boolean,
    deniedBefore: Boolean,
    runtimePermissionRequired: Boolean,
): ReminderPermissionAction = when {
    notificationsAllowed -> ReminderPermissionAction.ENABLE
    deniedBefore || !runtimePermissionRequired -> ReminderPermissionAction.SETTINGS
    else -> ReminderPermissionAction.REQUEST
}
