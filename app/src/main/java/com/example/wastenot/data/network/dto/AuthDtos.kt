package com.example.wastenot.data.network.dto

import com.example.wastenot.data.local.RefreshToken
import com.example.wastenot.data.repository.AccessToken
import com.example.wastenot.model.User
import com.example.wastenot.model.UserProgress
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginDto(val email: String, val password: String) {
    override fun toString() = "LoginDto([redacted])"
}

@Serializable
class GoogleChallengeDto(val nonce: String) {
    override fun toString() = "GoogleChallengeDto([redacted])"
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
class GoogleLoginDto(
    @SerialName("id_token") val idToken: String,
    val timezone: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("current_password") val currentPassword: String? = null,
) {
    override fun toString() = "GoogleLoginDto([redacted])"
}

@Serializable
data class RegisterDto(val name: String, val email: String, val password: String, val timezone: String) {
    override fun toString() = "RegisterDto([redacted])"
}

@Serializable
data class RefreshDto(@SerialName("refresh_token") val refreshToken: String) {
    override fun toString() = "RefreshDto([redacted])"
}

@Serializable
data class SessionDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("access_expires_at") val accessExpiresAt: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("refresh_expires_at") val refreshExpiresAt: String,
    @SerialName("session_id") val sessionId: String,
    val user: UserDto? = null,
) {
    override fun toString() = "SessionDto([redacted])"

    fun toModel(): SessionTokens {
        val canonicalSessionId = UUID.fromString(sessionId).toString()
        require(canonicalSessionId.equals(sessionId, ignoreCase = true))
        return SessionTokens(AccessToken.of(accessToken), RefreshToken.of(refreshToken), canonicalSessionId,
            Instant.parse(accessExpiresAt), Instant.parse(refreshExpiresAt), user?.toModel())
    }
}

data class SessionTokens(
    val access: AccessToken,
    val refresh: RefreshToken,
    val sessionId: String,
    val accessExpiresAt: Instant,
    val refreshExpiresAt: Instant,
    val user: User?,
)

@Serializable
data class UserDto(
    val id: String,
    val name: String,
    val email: String,
    val timezone: String,
    val progress: ProgressDto? = null,
    @SerialName("has_password") val hasPassword: Boolean = true,
) {
    fun toModel(): User {
        val canonicalId = UUID.fromString(id).toString()
        require(canonicalId.equals(id, ignoreCase = true))
        require(name.isNotBlank() && name.codePointCount(0, name.length) <= 60 && email.isNotBlank() && email.length <= 254)
        require(timezone in ZoneId.getAvailableZoneIds())
        return User(canonicalId, name, email, ZoneId.of(timezone), progress?.toModel(), hasPassword)
    }
}

@Serializable
data class ProgressDto(
    @SerialName("active_log_count") val activeLogCount: Int,
    val xp: Int,
    val level: Int,
    @SerialName("xp_into_level") val xpIntoLevel: Int,
    @SerialName("xp_to_next_level") val xpToNextLevel: Int,
    @SerialName("current_streak") val currentStreak: Int? = null,
    @SerialName("best_streak") val bestStreak: Int? = null,
    @SerialName("has_logged_today") val hasLoggedToday: Boolean,
) {
    fun toModel(): UserProgress {
        require(activeLogCount >= 0 && xp >= 0 && level >= 1)
        require(xpIntoLevel in 0..99 && xpToNextLevel in 1..100)
        require(currentStreak == null || currentStreak >= 0)
        require(bestStreak == null || bestStreak >= 0)
        return UserProgress(activeLogCount, xp, level, xpIntoLevel, xpToNextLevel,
            currentStreak, bestStreak, hasLoggedToday)
    }
}
