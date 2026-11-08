package com.example.wastenot.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.wastenot.data.repository.ThemeStore
import com.example.wastenot.theme.ThemeMode
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal fun themeMigrations(context: Context, legacyName: String = "appearance") =
    listOf(SharedPreferencesMigration(context, legacyName, keysToMigrate = setOf("theme")))

internal fun themeCorruptionHandler() = ReplaceFileCorruptionHandler<Preferences> { emptyPreferences() }

private val Context.appearanceDataStore by preferencesDataStore(
    name = "appearance",
    corruptionHandler = themeCorruptionHandler(),
    produceMigrations = { themeMigrations(it) },
)

class ThemePreferences(private val dataStore: DataStore<Preferences>) : ThemeStore {
    private val key = stringPreferencesKey("theme")
    override val modes = dataStore.data.map { preferences ->
        val value = preferences.asMap()[key] as? String
        ThemeMode.entries.find { it.name == value } ?: ThemeMode.SYSTEM
    }.distinctUntilChanged()

    override suspend fun write(mode: ThemeMode) {
        dataStore.edit { it[key] = mode.name }
    }

    companion object {
        fun create(context: Context) = ThemePreferences(context.applicationContext.appearanceDataStore)
    }
}
