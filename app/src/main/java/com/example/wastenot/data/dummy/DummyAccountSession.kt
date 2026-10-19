package com.example.wastenot.data.dummy

import android.content.SharedPreferences
import com.example.wastenot.data.network.ApiFailure
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.repository.AccountSession
import com.example.wastenot.data.repository.LogoutResult
import com.example.wastenot.data.repository.SessionState
import com.example.wastenot.model.User
import com.example.wastenot.model.normalizeAccountEmail
import com.example.wastenot.model.validateLoginInput
import com.example.wastenot.model.validateRegistrationInput
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Local fixture adapter, selected at composition root until the account API is integrated. */
class DummyAccountSession(private val preferences: SharedPreferences) : AccountSession {
    private val mutableState = MutableStateFlow<SessionState>(SessionState.Checking)
    override val state = mutableState.asStateFlow()
    override val isDemo = true
    private var epoch = 0L

    override suspend fun bootstrap() {
        val user = withContext(Dispatchers.IO) {
            if (preferences.getBoolean("signed_out", false)) null else User(
                id = preferences.getString("id", "dummy-owner")!!,
                name = preferences.getString("name", "Pengguna")!!,
                email = preferences.getString("email", "pengguna@example.invalid")!!,
                timezone = ZoneId.of(preferences.getString("timezone", "Asia/Jakarta")!!),
                progress = null,
            )
        }
        mutableState.value = if (user == null) SessionState.SignedOut() else SessionState.Authenticated(user, ++epoch)
    }

    override suspend fun login(email: String, password: String): ApiResult<User> {
        if (validateLoginInput(email, password).isNotEmpty()) return invalid()
        return publish("Pengguna", email, ZoneId.of("Asia/Jakarta"))
    }

    override suspend fun register(name: String, email: String, password: String, timezone: ZoneId): ApiResult<User> {
        if (validateRegistrationInput(name, email, password, password).isNotEmpty()) return invalid()
        return publish(name.trim(), email, timezone)
    }

    override suspend fun logout(): LogoutResult {
        val saved = withContext(Dispatchers.IO) { preferences.edit().clear().putBoolean("signed_out", true).commit() }
        if (saved) mutableState.value = SessionState.SignedOut()
        return LogoutResult(localCleared = saved, serverRevoked = false)
    }

    private suspend fun publish(name: String, email: String, zone: ZoneId): ApiResult<User> {
        val normalized = normalizeAccountEmail(email)
        val id = if (normalized == "pengguna@example.invalid") "dummy-owner"
            else UUID.nameUUIDFromBytes(normalized.toByteArray(Charsets.UTF_8)).toString()
        val user = User(id, name, normalized, zone, null)
        val saved = withContext(Dispatchers.IO) {
            preferences.edit().clear().putString("id", id).putString("name", name)
                .putString("email", normalized).putString("timezone", zone.id).commit()
        }
        if (!saved) return ApiResult.Failure(ApiFailure(ApiFailureKind.STORAGE))
        mutableState.value = SessionState.Authenticated(user, ++epoch)
        return ApiResult.Success(user)
    }

    private fun invalid() = ApiResult.Failure(ApiFailure(ApiFailureKind.INVALID_REQUEST))
}
