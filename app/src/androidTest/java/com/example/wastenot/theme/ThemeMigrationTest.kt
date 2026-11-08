package com.example.wastenot.theme

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.data.local.ThemePreferences
import com.example.wastenot.data.local.themeCorruptionHandler
import com.example.wastenot.data.local.themeMigrations
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class ThemeMigrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val directory = File(context.cacheDir, "theme-migration-${UUID.randomUUID()}").apply { mkdirs() }
    private val jobs = mutableListOf<Job>()
    private val names = mutableListOf<String>()
    private fun legacy() = "theme-migration-${UUID.randomUUID()}".also(names::add)
    private fun open(file: File, name: String): Pair<ThemePreferences, Job> {
        val job = SupervisorJob(); jobs.add(job)
        val data = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO),
            corruptionHandler = themeCorruptionHandler(), migrations = themeMigrations(context, name), produceFile = { file })
        return ThemePreferences(data) to job
    }
    @After fun cleanup() = runBlocking {
        jobs.forEach { it.cancelAndJoin() }
        names.forEach(context::deleteSharedPreferences)
        directory.deleteRecursively()
        Unit
    }

    @Test fun legacyChoicesMigrateOnceAndPreserveOtherSharedPreferences() = runBlocking {
        for (mode in ThemeMode.entries) {
            val name = legacy()
            val preferences = context.getSharedPreferences(name, 0)
            check(preferences.edit().putString("theme", mode.name).putString("retained", "keep").commit())
            val file = File(directory, "$name.preferences_pb")
            val (store, job) = open(file, name)
            assertEquals(mode, store.modes.first())
            assertFalse(preferences.contains("theme"))
            assertEquals("keep", preferences.getString("retained", null))
            store.write(ThemeMode.DARK)
            job.cancelAndJoin()
            check(preferences.edit().putString("theme", "LIGHT").commit())
            val (reopened, reopenedJob) = open(file, name)
            assertEquals(ThemeMode.DARK, reopened.modes.first())
            reopenedJob.cancelAndJoin()
        }
    }

    @Test fun wrongTypedAndUnknownLegacyChoicesDoNotCrashStartup() = runBlocking {
        repeat(2) { index ->
            val name = legacy()
            val editor = context.getSharedPreferences(name, 0).edit()
            check((if (index == 0) editor.putInt("theme", 4) else editor.putString("theme", "OLD_CHOICE")).commit())
            val (store, job) = open(File(directory, "$name.preferences_pb"), name)
            assertEquals(ThemeMode.SYSTEM, store.modes.first())
            store.write(ThemeMode.LIGHT)
            assertEquals(ThemeMode.LIGHT, store.modes.first())
            job.cancelAndJoin()
        }
    }

    @Test fun migrationFailureKeepsTheOriginalChoiceAvailableForRetry() = runBlocking {
        val name = legacy()
        val preferences = context.getSharedPreferences(name, 0)
        check(preferences.edit().putString("theme", "DARK").commit())
        val blocked = File(directory, "not-a-directory").apply { writeText("occupied") }
        val (store, job) = open(File(blocked, "appearance.preferences_pb"), name)
        try { store.modes.first(); fail("Expected a real file write failure") }
        catch (_: IOException) { assertEquals("DARK", preferences.getString("theme", null)) }
        finally { job.cancelAndJoin() }
        val (retried, _) = open(File(directory, "recovered.preferences_pb"), name)
        assertEquals(ThemeMode.DARK, retried.modes.first())
    }
}
