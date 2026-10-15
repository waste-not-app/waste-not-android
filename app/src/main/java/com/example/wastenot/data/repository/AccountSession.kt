package com.example.wastenot.data.repository

import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.model.User
import java.time.ZoneId
import kotlinx.coroutines.flow.StateFlow

interface AccountSession {
    val state: StateFlow<SessionState>
    val isDemo: Boolean
    suspend fun bootstrap()
    suspend fun login(email: String, password: String): ApiResult<User>
    suspend fun register(name: String, email: String, password: String, timezone: ZoneId): ApiResult<User>
    suspend fun logout(): LogoutResult
}

class RemoteAccountSession(
    private val sessions: SessionRepository,
    private val networkAvailable: () -> Boolean,
) : AccountSession {
    override val state = sessions.state
    override val isDemo = false
    override suspend fun bootstrap() { sessions.bootstrap(networkAvailable()) }
    override suspend fun login(email: String, password: String) = sessions.login(email, password)
    override suspend fun register(name: String, email: String, password: String, timezone: ZoneId) =
        sessions.register(name, email, password, timezone)
    override suspend fun logout() = sessions.logout()
}
