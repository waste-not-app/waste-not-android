package com.example.wastenot.data.demo

import com.example.wastenot.data.network.ApiFailure
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.repository.AccountSession
import com.example.wastenot.data.repository.LogoutResult
import com.example.wastenot.data.repository.SessionState
import com.example.wastenot.model.User
import com.example.wastenot.model.UserProgress
import com.example.wastenot.model.normalizeAccountEmail
import com.example.wastenot.model.validateLoginInput
import com.example.wastenot.model.validateRegistrationInput
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** An explicit UI preview source. It never calls the server or issues/stores tokens. */
class DemoAccountSession : AccountSession {
    private val mutableState = MutableStateFlow<SessionState>(SessionState.Checking)
    override val state = mutableState.asStateFlow()

    private var epoch = 0L

    override suspend fun bootstrap() { mutableState.value = SessionState.SignedOut() }

    fun openPreview() {
        publish("Pengguna Demo", "demo@example.invalid", ZoneId.of("Asia/Jakarta"))
    }

    override suspend fun login(email: String, password: String): ApiResult<User> {
        if (validateLoginInput(email, password).isNotEmpty()) return invalid()
        return ApiResult.Success(publish("Pengguna Demo", normalizeAccountEmail(email), ZoneId.of("Asia/Jakarta")))
    }

    override suspend fun register(name: String, email: String, password: String, timezone: ZoneId): ApiResult<User> {
        if (validateRegistrationInput(name, email, password, password).isNotEmpty()) return invalid()
        return ApiResult.Success(publish(name.trim(), normalizeAccountEmail(email), timezone))
    }

    override suspend fun logout(): LogoutResult {
        epoch++
        mutableState.value = SessionState.SignedOut()
        return LogoutResult(localCleared = true, serverRevoked = false)
    }

    private fun publish(name: String, email: String, timezone: ZoneId): User {
        val user = User(
            id = "00000000-0000-4000-8000-000000000001",
            name = name,
            email = email,
            timezone = timezone,
            progress = UserProgress(5, 50, 1, 50, 50, 2, 2, true),
        )
        epoch++
        mutableState.value = SessionState.Authenticated(user, epoch)
        return user
    }

    private fun invalid() = ApiResult.Failure(ApiFailure(ApiFailureKind.INVALID_REQUEST))
}
