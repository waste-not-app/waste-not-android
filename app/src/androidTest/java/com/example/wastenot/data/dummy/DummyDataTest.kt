package com.example.wastenot.data.dummy

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.repository.ActivityInput
import com.example.wastenot.data.repository.SessionState
import com.example.wastenot.model.*
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
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
            assertFalse((DummyActivityStore(other, zone) { date }.load() as ApiResult.Success).value.any { it.id == created.id })
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
}
