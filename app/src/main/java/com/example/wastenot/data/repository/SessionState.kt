package com.example.wastenot.data.repository

import com.example.wastenot.data.network.ApiFailure
import com.example.wastenot.model.User

sealed interface SessionState {
    data object Checking : SessionState
    data class Authenticated(val user: User, val epoch: Long, val offline: Boolean = false) : SessionState
    data class SignedOut(val reason: SignOutReason = SignOutReason.NONE) : SessionState
    data class ConnectionRequired(val error: ApiFailure) : SessionState
}

enum class SignOutReason {
    NONE, EXPIRED, INVALID_CREDENTIAL, ROTATION_UNCERTAIN, STORAGE_UNAVAILABLE,
    LOGOUT_LOCAL_ONLY, LOGOUT_CONFIRMED,
}

data class LogoutResult(val localCleared: Boolean, val serverRevoked: Boolean)

@JvmInline
value class AccessToken private constructor(val value: String) {
    override fun toString(): String = "AccessToken([redacted])"

    companion object {
        private val format = Regex("[A-Za-z0-9._~+/-]+=*")
        fun of(value: String): AccessToken {
            require(value.length in 1..8192 && format.matches(value)) { "Invalid access token." }
            return AccessToken(value)
        }
    }
}

data class RequestCredential(val token: AccessToken, val epoch: Long, val revision: Long)
