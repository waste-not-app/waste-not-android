package com.example.wastenot.data.dummy

import android.content.SharedPreferences
import com.example.wastenot.data.network.ApiFailure
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.repository.AccountSession
import com.example.wastenot.data.repository.LogoutResult
import com.example.wastenot.data.repository.SessionState
import com.example.wastenot.model.AccountField
import com.example.wastenot.model.User
import com.example.wastenot.model.normalizeAccountEmail
import com.example.wastenot.model.validateLoginInput
import com.example.wastenot.model.validateRegistrationInput
import java.time.DateTimeException
import java.time.ZoneId
import java.util.UUID
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Local fixture adapter, selected at composition root until the account API is integrated. */
class DummyAccountSession(private val preferences: SharedPreferences) : AccountSession {
    private val mutableState = MutableStateFlow<SessionState>(SessionState.Checking)
    override val state = mutableState.asStateFlow()

    private var epoch = 0L
    private val activeKeys = listOf("id", "name", "email", "timezone", "signed_out")

    override suspend fun bootstrap() = withContext(Dispatchers.IO) {
        val operation = currentCoroutineContext()
        synchronized(preferences) {
            val result = storage { ApiResult.Success(currentUser()) }
            operation.ensureActive()
            mutableState.value = when (result) {
                is ApiResult.Success -> result.value?.let { SessionState.Authenticated(it, ++epoch) } ?: SessionState.SignedOut()
                is ApiResult.Failure -> SessionState.ConnectionRequired(result.error)
            }
        }
    }

    override suspend fun login(email: String, password: String): ApiResult<User> {
        if (validateLoginInput(email, password).isNotEmpty()) return invalid()
        return withContext(Dispatchers.IO) {
          val operation = currentCoroutineContext()
          synchronized(preferences) { storage {
            val normalized = normalizeAccountEmail(email)
            val previous = currentUser()
            val user = profile(normalized) ?: previous?.takeIf { it.email == normalized }
                ?: User(accountId(normalized), "Pengguna", normalized, ZoneId.of("Asia/Jakarta"), null)
            publish(user, previous, operation)
        } } }
    }

    override suspend fun register(name: String, email: String, password: String, timezone: ZoneId): ApiResult<User> {
        if (validateRegistrationInput(name, email, password, password).isNotEmpty() ||
            timezone.id !in ZoneId.getAvailableZoneIds()) return invalid()
        return withContext(Dispatchers.IO) {
          val operation = currentCoroutineContext()
          synchronized(preferences) { storage {
            val normalized = normalizeAccountEmail(email)
            val previous = currentUser()
            if (profile(normalized) != null || previous?.email == normalized) {
                ApiResult.Failure(ApiFailure(ApiFailureKind.EMAIL_EXISTS))
            } else publish(User(accountId(normalized), name.trim(), normalized, timezone, null), previous, operation)
        } } }
    }

    override suspend fun logout(): LogoutResult = withContext(Dispatchers.IO) {
        synchronized(preferences) {
            // A corrupt active cache must still allow signing out without erasing the account registry.
            val previous = (storage { ApiResult.Success(currentUser()) } as? ApiResult.Success)?.value
            val result = storage {
                if (!persist(null, previous)) storageFailure() else {
                    mutableState.value = SessionState.SignedOut()
                    ApiResult.Success(Unit)
                }
            }
            LogoutResult(localCleared = result is ApiResult.Success, serverRevoked = false)
        }
    }

    private fun currentUser(): User? {
        val values = listOf("id", "name", "email", "timezone").map { preferences.getString(it, null) }
        if (preferences.getBoolean("signed_out", false)) return null
        if (values.all { it == null }) return profile("pengguna@example.invalid")
            ?: User("dummy-owner", "Pengguna", "pengguna@example.invalid", ZoneId.of("Asia/Jakarta"), null)
        require(values.all { it != null })
        return checkedUser(checkNotNull(values[0]), checkNotNull(values[1]), checkNotNull(values[2]), checkNotNull(values[3]))
    }

    private fun profile(email: String): User? {
        val id = accountId(email)
        val name = preferences.getString("profile.$id.name", null)
        val zone = preferences.getString("profile.$id.timezone", null)
        if (name == null && zone == null) return null
        return checkedUser(id, requireNotNull(name), email, requireNotNull(zone))
    }

    private fun checkedUser(id: String, name: String, email: String, zone: String): User {
        require(name == name.trim() && name.codePointCount(0, name.length) in 2..60)
        require(email == normalizeAccountEmail(email) && AccountField.EMAIL !in validateLoginInput(email, ""))
        require(id == accountId(email) && zone in ZoneId.getAvailableZoneIds())
        return User(id, name, email, ZoneId.of(zone), null)
    }

    private fun publish(user: User, previous: User?, operation: CoroutineContext): ApiResult<User> {
        if (!persist(user, previous, operation)) return storageFailure()
        mutableState.value = SessionState.Authenticated(user, ++epoch)
        return ApiResult.Success(user)
    }

    private fun persist(user: User?, previous: User?, operation: CoroutineContext? = null): Boolean {
        operation?.ensureActive()
        val profiles = listOfNotNull(previous, user).associateBy { it.id }.values
        val keys = activeKeys + profiles.flatMap { listOf("profile.${it.id}.name", "profile.${it.id}.timezone") }
        val before = preferences.all
        val editor = preferences.edit()
        activeKeys.forEach { editor.remove(it) }
        profiles.forEach {
            editor.putString("profile.${it.id}.name", it.name).putString("profile.${it.id}.timezone", it.timezone.id)
        }
        editor.putBoolean("signed_out", user == null)
        if (user != null) editor.putString("id", user.id).putString("name", user.name)
            .putString("email", user.email).putString("timezone", user.timezone.id)
        if (editor.commit() && operation?.isActive != false) return true
        // A failed commit or cancelled sign-in must restore Android's already-updated preference cache.
        val restore = preferences.edit()
        keys.forEach { key ->
            when (val value = before[key]) {
                null -> restore.remove(key)
                is String -> restore.putString(key, value)
                is Boolean -> restore.putBoolean(key, value)
                is Int -> restore.putInt(key, value)
                is Long -> restore.putLong(key, value)
                is Float -> restore.putFloat(key, value)
                is Set<*> -> restore.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
        restore.commit()
        operation?.ensureActive()
        return false
    }

    private fun <T> storage(block: () -> ApiResult<T>): ApiResult<T> = try { block() }
        catch (_: ClassCastException) { storageFailure() }
        catch (_: IllegalArgumentException) { storageFailure() }
        catch (_: DateTimeException) { storageFailure() }

    private fun accountId(email: String): String = if (email == "pengguna@example.invalid") "dummy-owner"
        else UUID.nameUUIDFromBytes(email.toByteArray(Charsets.UTF_8)).toString()

    private fun invalid() = ApiResult.Failure(ApiFailure(ApiFailureKind.INVALID_REQUEST))
    private fun storageFailure() = ApiResult.Failure(ApiFailure(ApiFailureKind.STORAGE))
}
