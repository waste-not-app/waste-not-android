package com.example.wastenot.data.dummy

import android.content.Context
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.repository.SessionState
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import com.example.wastenot.model.User
import org.junit.Assert.*
import org.junit.Test

class DummyAccountIdentityTest {
    private val preferences = InstrumentationRegistry.getInstrumentation().targetContext
        .getSharedPreferences("qa-dummy-identities", Context.MODE_PRIVATE)
    private val password = "fixture-password-12"
    private val tokyo = ZoneId.of("Asia/Tokyo")

    @Test fun registeredProfilesSurviveLogoutRecreationAndSwitchingAccounts() = runTest {
        preferences.edit().clear().commit()
        try {
            val first = DummyAccountSession(preferences)
            val a = (first.register("Wulan Wijaya", "wulan@example.invalid", password, tokyo) as ApiResult.Success).value
            assertTrue(first.logout().localCleared)
            val next = DummyAccountSession(preferences)
            next.bootstrap()
            assertTrue(next.state.value is SessionState.SignedOut)
            assertEquals(a, (next.login(" WULAN@example.invalid ", password) as ApiResult.Success).value)
            val b = (next.register("Nadia Putri", "nadia@example.invalid", password, ZoneId.of("Europe/London")) as ApiResult.Success).value
            assertNotEquals(a.id, b.id)
            next.logout()
            assertEquals(a, (DummyAccountSession(preferences).login(a.email, password) as ApiResult.Success).value)
            assertEquals(b, (DummyAccountSession(preferences).login(b.email, password) as ApiResult.Success).value)
            assertFalse(preferences.all.toString().contains(password))
        } finally { preferences.edit().clear().commit() }
    }

    @Test fun legacyActiveProfileIsRetainedOnLogoutAndDoesNotBecomeTheNextAccount() = runTest {
        val email = "legacy@example.invalid"
        val id = UUID.nameUUIDFromBytes(email.toByteArray(Charsets.UTF_8)).toString()
        preferences.edit().clear().putString("id", id).putString("name", "Akun Lama")
            .putString("email", email).putString("timezone", tokyo.id).commit()
        try {
            val session = DummyAccountSession(preferences)
            session.bootstrap()
            val original = (session.state.value as SessionState.Authenticated).user
            assertTrue(session.logout().localCleared)
            assertFalse(preferences.contains("email"))
            val other = (session.login("other@example.invalid", password) as ApiResult.Success).value
            assertNotEquals(original.email, other.email)
            assertEquals(original, (session.login(email, password) as ApiResult.Success).value)
        } finally { preferences.edit().clear().commit() }
    }

    @Test fun duplicateRegistrationCannotOverwriteTheStoredProfileOrActiveIdentity() = runTest {
        preferences.edit().clear().commit()
        try {
            val session = DummyAccountSession(preferences)
            val original = (session.register("Nama Asli", "same@example.invalid", password, tokyo) as ApiResult.Success).value
            val duplicate = session.register("Nama Lain", "SAME@example.invalid", password, ZoneId.of("UTC")) as ApiResult.Failure
            assertEquals(ApiFailureKind.EMAIL_EXISTS, duplicate.error.kind)
            assertEquals(original, (session.state.value as SessionState.Authenticated).user)
            session.logout()
            assertEquals(original, (session.login(original.email, password) as ApiResult.Success).value)
        } finally { preferences.edit().clear().commit() }
    }

    @Test fun corruptProfileFailsExplicitlyAndCorruptActiveIdentityCanStillSignOut() = runTest {
        preferences.edit().clear().commit()
        try {
            val session = DummyAccountSession(preferences)
            val original = (session.register("Nama Asli", "corrupt@example.invalid", password, tokyo) as ApiResult.Success).value
            session.logout()
            val key = "profile.${original.id}.timezone"
            preferences.edit().putString(key, "not-a-zone").commit()
            assertEquals(ApiFailureKind.STORAGE, (session.login(original.email, password) as ApiResult.Failure).error.kind)
            assertTrue(session.state.value is SessionState.SignedOut)
            assertEquals("not-a-zone", preferences.getString(key, null))
            preferences.edit().putBoolean("signed_out", false).putString("id", original.id)
                .putString("email", original.email).putString("timezone", tokyo.id).putInt("name", 17).commit()
            val broken = DummyAccountSession(preferences)
            broken.bootstrap()
            assertTrue(broken.state.value is SessionState.ConnectionRequired)
            assertTrue(broken.logout().localCleared)
            assertTrue(broken.state.value is SessionState.SignedOut)
            assertFalse(preferences.contains("name"))
            assertEquals("not-a-zone", preferences.getString(key, null))
        } finally { preferences.edit().clear().commit() }
    }

    @Test fun failedWritesDoNotPublishAnotherAccountOrLoseTheConfirmedProfile() = runTest {
        preferences.edit().clear().commit()
        try {
            val initial = DummyAccountSession(preferences)
            val original = (initial.register("Nama Asli", "saved@example.invalid", password, tokyo) as ApiResult.Success).value
            val before = preferences.all.toMap()
            val failing = DummyAccountSession(failingPreferences())
            failing.bootstrap()
            assertEquals(ApiFailureKind.STORAGE,
                (failing.register("Nama Lain", "failed@example.invalid", password, ZoneId.of("UTC")) as ApiResult.Failure).error.kind)
            assertEquals(before, preferences.all)
            assertEquals(original, (failing.state.value as SessionState.Authenticated).user)
            assertFalse(failing.logout().localCleared)
            assertEquals(before, preferences.all)
            assertEquals(original, (failing.state.value as SessionState.Authenticated).user)
            assertFalse(preferences.all.toString().contains(password))
        } finally { preferences.edit().clear().commit() }
    }

    @Test fun invalidInputAndNonAccountTimezonesDoNotWriteAProfile() = runTest {
        preferences.edit().clear().commit()
        try {
            val session = DummyAccountSession(preferences)
            assertEquals(ApiFailureKind.INVALID_REQUEST,
                (session.register("Nama", "valid@example.invalid", password, ZoneId.of("+07:00")) as ApiResult.Failure).error.kind)
            assertEquals(ApiFailureKind.INVALID_REQUEST, (session.login("invalid", password) as ApiResult.Failure).error.kind)
            assertEquals(ApiFailureKind.INVALID_REQUEST,
                (session.register("N", "valid@example.invalid", password, tokyo) as ApiResult.Failure).error.kind)
            assertTrue(preferences.all.isEmpty())
            assertFalse(session.state.value is SessionState.Authenticated)
        } finally { preferences.edit().clear().commit() }
    }

    @Test fun cancellingDuringCommitCannotPublishALateLogin() = runTest {
        preferences.edit().clear().commit()
        try {
            val initial = DummyAccountSession(preferences)
            val user = (initial.register("Nama Asli", "cancel@example.invalid", password, tokyo) as ApiResult.Success).value
            initial.logout()
            val before = preferences.all.toMap()
            lateinit var signingIn: Deferred<ApiResult<User>>
            val session = DummyAccountSession(commitPreferences { editor ->
                val saved = editor.commit()
                signingIn.cancel()
                saved
            })
            session.bootstrap()
            signingIn = async(start = CoroutineStart.LAZY) { session.login(user.email, password) }
            signingIn.start()
            try { signingIn.await(); fail("Cancelled login must not complete successfully") }
            catch (_: CancellationException) { }
            assertTrue(session.state.value is SessionState.SignedOut)
            assertEquals(before, preferences.all)
        } finally { preferences.edit().clear().commit() }
    }

    @Test fun cancellingBootstrapCannotExposeAnIdentityAfterItsCallerLeaves() = runTest {
        preferences.edit().clear().commit()
        try {
            DummyAccountSession(preferences).register("Nama Asli", "bootstrap@example.invalid", password, tokyo)
            lateinit var restoring: Deferred<Unit>
            val session = DummyAccountSession(object : SharedPreferences by preferences {
                override fun getBoolean(key: String?, fallback: Boolean): Boolean {
                    if (key == "signed_out") restoring.cancel()
                    return preferences.getBoolean(key, fallback)
                }
            })
            restoring = async(start = CoroutineStart.LAZY) { session.bootstrap() }
            restoring.start()
            try { restoring.await(); fail("Cancelled bootstrap must not complete successfully") }
            catch (_: CancellationException) { }
            assertTrue(session.state.value is SessionState.Checking)
        } finally { preferences.edit().clear().commit() }
    }

    private fun failingPreferences(): SharedPreferences = commitPreferences { editor -> editor.commit(); false }

    private fun commitPreferences(commit: (SharedPreferences.Editor) -> Boolean): SharedPreferences = object : SharedPreferences by preferences {
        override fun edit(): SharedPreferences.Editor {
            val editor = preferences.edit()
            return object : SharedPreferences.Editor by editor {
                override fun putString(key: String?, value: String?): SharedPreferences.Editor { editor.putString(key, value); return this }
                override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor { editor.putBoolean(key, value); return this }
                override fun remove(key: String?): SharedPreferences.Editor { editor.remove(key); return this }
                override fun commit(): Boolean = commit(editor)
            }
        }
    }
}
