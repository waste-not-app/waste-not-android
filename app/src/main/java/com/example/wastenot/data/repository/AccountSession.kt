package com.example.wastenot.data.repository

import com.example.wastenot.data.network.ApiFailure
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.model.User
import java.time.ZoneId
import kotlinx.coroutines.flow.StateFlow

interface AccountSession {
    val state: StateFlow<SessionState>
    suspend fun bootstrap()
    suspend fun login(email: String, password: String): ApiResult<User>
    suspend fun googleChallenge(): ApiResult<String> = googleNotConfigured()
    suspend fun googleLogin(idToken: String, timezone: ZoneId, currentPassword: String?): ApiResult<User> =
        googleNotConfigured()
    suspend fun register(name: String, email: String, password: String, timezone: ZoneId): ApiResult<User>
    suspend fun logout(): LogoutResult
}

class RemoteAccountSession(
    private val sessions: SessionRepository,
    private val networkAvailable: () -> Boolean,
    private val clearProviderCredentials: suspend () -> Unit = {},
) : AccountSession {
    override val state = sessions.state

    override suspend fun bootstrap() { sessions.bootstrap(networkAvailable()) }
    override suspend fun login(email: String, password: String) = sessions.login(email, password)
    override suspend fun register(name: String, email: String, password: String, timezone: ZoneId) =
        sessions.register(name, email, password, timezone)
    override suspend fun googleChallenge() = sessions.googleChallenge()
    override suspend fun googleLogin(idToken: String, timezone: ZoneId, currentPassword: String?) =
        sessions.googleLogin(idToken, timezone, currentPassword)
    override suspend fun logout(): LogoutResult {
        val result = sessions.logout()
        clearProviderCredentials()
        return result
    }
}

internal fun googleNotConfigured() = ApiResult.Failure(ApiFailure(ApiFailureKind.NOT_CONFIGURED))
