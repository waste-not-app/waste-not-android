package com.example.wastenot.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.example.wastenot.theme.ThemeMode
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ThemePreferencesTest {
    @get:Rule val temporary = TemporaryFolder()
    private val jobs = mutableListOf<Job>()
    private fun open(file: File): Pair<DataStore<Preferences>, Job> {
        val job = SupervisorJob(); jobs.add(job)
        return PreferenceDataStoreFactory.create(corruptionHandler = themeCorruptionHandler(),
            scope = CoroutineScope(job + Dispatchers.IO), produceFile = { file }) to job
    }
    @After fun cleanup() = runBlocking { jobs.forEach { it.cancelAndJoin() } }

    @Test fun everyChoiceSurvivesClosingAndReopeningTheFile() = runBlocking {
        val file = File(temporary.root, "appearance.preferences_pb")
        for (mode in ThemeMode.entries) {
            val (data, job) = open(file)
            val store = ThemePreferences(data)
            store.write(mode)
            assertEquals(mode, store.modes.first())
            job.cancelAndJoin()
            val (reopened, reopenedJob) = open(file)
            assertEquals(mode, ThemePreferences(reopened).modes.first())
            reopenedJob.cancelAndJoin()
        }
    }

    @Test fun unknownAndWrongTypedValuesFollowSystemAndCanBeReplaced() = runBlocking {
        val (data, _) = open(File(temporary.root, "appearance.preferences_pb"))
        val store = ThemePreferences(data)
        assertEquals(ThemeMode.SYSTEM, store.modes.first())
        data.edit { it[stringPreferencesKey("theme")] = "REMOVED_OPTION" }
        assertEquals(ThemeMode.SYSTEM, store.modes.first())
        data.edit { it[intPreferencesKey("theme")] = 1 }
        assertEquals(ThemeMode.SYSTEM, store.modes.first())
        store.write(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, store.modes.first())
    }

    @Test fun replacingACorruptAppearanceFileRestoresOnlyTheThemeDefault() = runBlocking {
        val file = File(temporary.root, "appearance.preferences_pb")
        file.writeBytes(byteArrayOf(0x0f, 0x7f, 0xff.toByte()))
        val (data, job) = open(file)
        val store = ThemePreferences(data)
        assertEquals(ThemeMode.SYSTEM, store.modes.first())
        store.write(ThemeMode.LIGHT)
        job.cancelAndJoin()
        val (reopened, _) = open(file)
        assertEquals(ThemeMode.LIGHT, ThemePreferences(reopened).modes.first())
    }

    @Test fun updatingTheThemePreservesUnrelatedPreferenceKeys() = runBlocking {
        val (data, _) = open(File(temporary.root, "appearance.preferences_pb"))
        val other = booleanPreferencesKey("other_preference")
        data.edit { it[other] = true }
        ThemePreferences(data).write(ThemeMode.DARK)
        assertEquals(true, data.data.first()[other])
    }
}
