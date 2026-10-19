package com.example.wastenot.data.dummy

import android.content.Context
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.repository.ActivityInput
import com.example.wastenot.data.repository.SessionState
import com.example.wastenot.model.*
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class DummyDataTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val zone = ZoneId.of("Asia/Jakarta")
    private val date = LocalDate.of(2026, 10, 19)

    @Test fun recordsAndIdempotencySurviveStoreRecreationAndAccountDisposal(): Unit = runBlocking {
        val preferences = context.getSharedPreferences("qa-dummy-records", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        try {
            val store = DummyActivityStore(preferences, zone) { date }
            val initial = (store.load() as ApiResult.Success).value
            assertEquals(5, initial.size)
            assertEquals(3, initial.count { it.activityDate == date })
            val input = ActivityInput(WasteCategory.PLASTIC, DisposalMethod.RECYCLE, Quantity.Count.of(3), date, "Catatan fixture")
            val created = (store.create(input, "fixture-request") as ApiResult.Success).value
            store.clear()
            val restored = DummyActivityStore(preferences, zone) { date }
            val replay = (restored.create(input, "fixture-request") as ApiResult.Success).value
            assertEquals(created, replay)
            assertEquals(6, (restored.load() as ApiResult.Success).value.size)
            val changed = (restored.update(created.id, 1, input.copy(notes = "Diubah")) as ApiResult.Success).value
            assertEquals(created.createdAt, changed.createdAt)
            assertTrue(changed.updatedAt > created.updatedAt)
            assertEquals(2, changed.version)
            val stale = restored.update(created.id, 1, input) as ApiResult.Failure
            assertEquals(ApiFailureKind.VERSION_CONFLICT, stale.error.kind)
            assertEquals("Diubah", (DummyActivityStore(preferences, zone) { date }.load() as ApiResult.Success).value.first { it.id == created.id }.notes)
            val other = context.getSharedPreferences("qa-dummy-other", Context.MODE_PRIVATE)
            other.edit().clear().commit()
            val otherStore = DummyActivityStore(other, zone) { date }
            assertFalse((otherStore.load() as ApiResult.Success).value.any { it.id == created.id })
            assertEquals(ApiFailureKind.NOT_FOUND, (otherStore.get(created.id) as ApiResult.Failure).error.kind)
            assertEquals(ApiFailureKind.NOT_FOUND, (otherStore.get(initial.first().id) as ApiResult.Failure).error.kind)
            other.edit().clear().commit()
        } finally { preferences.edit().clear().commit() }
    }

    @Test fun corruptFixtureDoesNotSilentlyBecomeASeededSuccess(): Unit = runBlocking {
        val preferences = context.getSharedPreferences("qa-dummy-corrupt", Context.MODE_PRIVATE)
        preferences.edit().clear().putString("records", "broken-json").commit()
        try {
            val result = DummyActivityStore(preferences, zone) { date }.load() as ApiResult.Failure
            assertEquals(ApiFailureKind.STORAGE, result.error.kind)
            assertEquals("broken-json", preferences.getString("records", null))
        } finally { preferences.edit().clear().commit() }
    }

    @Test fun dummySessionRestoresIdentityAndLogoutWithoutPersistingAPassword(): Unit = runBlocking {
        val preferences = context.getSharedPreferences("qa-dummy-session", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        try {
            val session = DummyAccountSession(preferences)
            session.bootstrap()
            assertTrue(session.state.value is SessionState.Authenticated)
            val initial = (session.state.value as SessionState.Authenticated).user
            assertTrue(session.logout().localCleared)
            val signedOut = DummyAccountSession(preferences)
            signedOut.bootstrap()
            assertTrue(signedOut.state.value is SessionState.SignedOut)
            val returning = signedOut.login(initial.email, "fixture-password-12") as ApiResult.Success
            assertEquals(initial.id, returning.value.id)
            val result = signedOut.register("Fixture", "fixture@example.invalid", "fixture-password-12", zone) as ApiResult.Success
            val restored = DummyAccountSession(preferences)
            restored.bootstrap()
            assertEquals(result.value, (restored.state.value as SessionState.Authenticated).user)
            assertFalse(preferences.all.toString().contains("fixture-password-12"))
        } finally { preferences.edit().clear().commit() }
    }
    @Test fun deletionPersistsWithItsReplayTombstoneAndVersionGuard(): Unit = runBlocking {
        val preferences = context.getSharedPreferences("qa-dummy-delete", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        try {
            val store = DummyActivityStore(preferences, zone) { date }
            val input = ActivityInput(WasteCategory.METAL, DisposalMethod.RECYCLE, Quantity.Count.of(2), date, "")
            val request = UUID.randomUUID().toString()
            val created = (store.create(input, request) as ApiResult.Success).value
            assertEquals(created.id, UUID.fromString(created.id).toString())
            val edited = (store.update(created.id, created.version, input.copy(notes = "Terbaru")) as ApiResult.Success).value
            assertEquals(ApiFailureKind.VERSION_CONFLICT, (store.delete(created.id, created.version) as ApiResult.Failure).error.kind)
            assertEquals(6, (store.load() as ApiResult.Success).value.size)
            assertTrue(store.delete(edited.id, edited.version) is ApiResult.Success)
            val restored = DummyActivityStore(preferences, zone) { date }
            assertEquals(5, (restored.load() as ApiResult.Success).value.size)
            assertEquals(ApiFailureKind.DELETED, (restored.create(input, request) as ApiResult.Failure).error.kind)
            assertTrue(restored.delete(created.id, created.version) is ApiResult.Success)
            assertEquals(ApiFailureKind.NOT_FOUND, (restored.delete(UUID.randomUUID().toString(), 1) as ApiResult.Failure).error.kind)
            assertEquals(ApiFailureKind.NOT_FOUND, (restored.update(created.id, edited.version, input) as ApiResult.Failure).error.kind)
            val remaining = (restored.load() as ApiResult.Success).value
            assertTrue(remaining.all { UUID.fromString(it.id).toString() == it.id })
            remaining.forEach { assertTrue(restored.delete(it.id, it.version) is ApiResult.Success) }
            assertTrue((DummyActivityStore(preferences, zone) { date }.load() as ApiResult.Success).value.isEmpty())
        } finally { preferences.edit().clear().commit() }
    }

    @Test fun failedDeleteCommitLeavesRecordsAndReplayKeysIntact(): Unit = runBlocking {
        val preferences = context.getSharedPreferences("qa-dummy-delete-failure", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        try {
            val healthy = DummyActivityStore(preferences, zone) { date }
            val input = ActivityInput(WasteCategory.PAPER, DisposalMethod.RECYCLE, Quantity.Weight.of(150), date, "")
            val request = UUID.randomUUID().toString()
            val created = (healthy.create(input, request) as ApiResult.Success).value
            listOf(false, true).forEach { updateCache ->
                var commits = 0
                val failing = failingPreferences(preferences) { editor ->
                    commits++
                    if (updateCache) editor.commit()
                    false
                }
                val result = DummyActivityStore(failing, zone) { date }.delete(created.id, created.version) as ApiResult.Failure
                assertEquals(ApiFailureKind.STORAGE, result.error.kind)
                assertEquals(2, commits)
                assertEquals(6, (healthy.load() as ApiResult.Success).value.size)
                assertEquals(created, (healthy.create(input, request) as ApiResult.Success).value)
                assertFalse(preferences.getStringSet("deleted", emptySet())!!.contains(created.id))
            }
        } finally { preferences.edit().clear().commit() }
    }

    @Test fun recreatedAdaptersSerializeConcurrentRequests(): Unit = runBlocking {
        val preferences = context.getSharedPreferences("qa-dummy-parallel", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        try {
            val input = ActivityInput(WasteCategory.OTHER, DisposalMethod.TRASH, Quantity.Count.of(1), date, "")
            val request = UUID.randomUUID().toString()
            val values = coroutineScope { (1..16).map {
                async { (DummyActivityStore(preferences, zone) { date }.create(input, request) as ApiResult.Success).value }
            }.awaitAll() }
            assertEquals(1, values.map { it.id }.toSet().size)
            assertEquals(6, (DummyActivityStore(preferences, zone) { date }.load() as ApiResult.Success).value.size)
        } finally { preferences.edit().clear().commit() }
    }

    @Test fun corruptDateRemainsAStorageFailure(): Unit = runBlocking {
        val preferences = context.getSharedPreferences("qa-dummy-date", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        try {
            val store = DummyActivityStore(preferences, zone) { date }
            assertTrue(store.load() is ApiResult.Success)
            val records = org.json.JSONArray(preferences.getString("records", null))
            records.getJSONObject(0).put("date", "invalid-date")
            val raw = records.toString()
            preferences.edit().putString("records", raw).commit()
            assertEquals(ApiFailureKind.STORAGE, (store.load() as ApiResult.Failure).error.kind)
            assertEquals(raw, preferences.getString("records", null))
        } finally { preferences.edit().clear().commit() }
    }

    @Test fun failedSeedCommitDoesNotMasqueradeAsAnInitializedStore(): Unit = runBlocking {
        val preferences = context.getSharedPreferences("qa-dummy-seed-failure", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        try {
            val failing = failingPreferences(preferences) { editor -> editor.commit(); false }
            assertEquals(ApiFailureKind.STORAGE, (DummyActivityStore(failing, zone) { date }.load() as ApiResult.Failure).error.kind)
            assertFalse(preferences.contains("records"))
            assertEquals(5, (DummyActivityStore(preferences, zone) { date }.load() as ApiResult.Success).value.size)
            preferences.edit().remove("records").putString("requests", "{}").commit()
            assertEquals(ApiFailureKind.STORAGE, (DummyActivityStore(preferences, zone) { date }.load() as ApiResult.Failure).error.kind)
            assertFalse(preferences.contains("records"))
        } finally { preferences.edit().clear().commit() }
    }

    private fun failingPreferences(preferences: SharedPreferences, commit: (SharedPreferences.Editor) -> Boolean): SharedPreferences =
        object : SharedPreferences by preferences {
            override fun edit(): SharedPreferences.Editor {
                val editor = preferences.edit()
                return object : SharedPreferences.Editor by editor {
                    override fun putString(key: String?, value: String?): SharedPreferences.Editor { editor.putString(key, value); return this }
                    override fun putStringSet(key: String?, values: Set<String>?): SharedPreferences.Editor { editor.putStringSet(key, values); return this }
                    override fun commit(): Boolean = commit(editor)
                }
            }
        }

}
