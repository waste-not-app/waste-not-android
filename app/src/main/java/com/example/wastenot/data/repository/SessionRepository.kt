package com.example.wastenot.data.repository

import com.example.wastenot.data.local.RefreshToken
import com.example.wastenot.data.local.RefreshTokenStore
import com.example.wastenot.data.local.TokenReadResult
import com.example.wastenot.data.local.SessionIdentityStore
import com.example.wastenot.data.network.ApiFailure
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.network.WasteNotNetwork
import com.example.wastenot.data.network.dto.LoginDto
import com.example.wastenot.data.network.dto.RefreshDto
import com.example.wastenot.data.network.dto.RegisterDto
import com.example.wastenot.data.network.dto.SessionTokens
import com.example.wastenot.model.User
import com.example.wastenot.data.network.dto.DeleteAccountDto
import com.example.wastenot.model.normalizeAccountEmail
import java.io.IOException
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class SessionRepository(
    private val network: WasteNotNetwork?,
    private val tokenStore: RefreshTokenStore,
    private val identityStore: SessionIdentityStore? = null,
    private val clearAccountData: suspend (userId: String, epoch: Long) -> Unit = { _, _ -> },
) {
    private val lock = Any()
    private val persistence = Mutex()
    private val refresh = Mutex()
    private val authentication = Mutex()
    private var epoch = 0L
    private var revision = 0L
    private var storedEpoch = Long.MIN_VALUE
    private var record: Record? = null
    private val accountJobs = mutableMapOf<Job, Long>()
    private val mutableState = MutableStateFlow<SessionState>(SessionState.Checking)
    val state = mutableState.asStateFlow()

    suspend fun login(email: String, password: String): ApiResult<User> = authenticate {
        val connection = network ?: return@authenticate failure(ApiFailureKind.NOT_CONFIGURED)
        val input = LoginDto(normalizeAccountEmail(email), password)
        connection.executor.execute({ it.toModel() }) { connection.api.login(input) }
    }

    suspend fun register(name: String, email: String, password: String, timezone: ZoneId): ApiResult<User> = authenticate {
        val connection = network ?: return@authenticate failure(ApiFailureKind.NOT_CONFIGURED)
        val input = RegisterDto(name.trim(), normalizeAccountEmail(email), password, timezone.id)
        connection.executor.execute({ it.toModel() }) { connection.api.register(input) }
    }

    suspend fun bootstrap(networkAvailable: Boolean = true): SessionState {
        if (!authentication.tryLock()) return state.value
        var operation: Operation? = null
        try {
            val existing = synchronized(lock) { record }
            if (existing?.user != null) return state.value
            if (existing != null) {
                if (!networkAvailable) {
                    setState(existing.credential.epoch, SessionState.ConnectionRequired(ApiFailure(ApiFailureKind.NETWORK)))
                    return state.value
                }
                return verifyIdentity(existing.credential.epoch)
            }
            operation = beginBootstrap()
            val stored = persistence.withLock { tokenStore.read() }
            if (!isCurrent(operation.epoch)) return state.value
            when (stored) {
                TokenReadResult.Absent -> setState(operation.epoch, SessionState.SignedOut())
                TokenReadResult.Invalidated -> setState(operation.epoch,
                    SessionState.SignedOut(SignOutReason.INVALID_CREDENTIAL))
                is TokenReadResult.Available -> {
                    if (!networkAvailable || network == null) {
                        val cached = persistence.withLock { identityStore?.read() }
                        if (cached != null && !networkAvailable) {
                            setState(operation.epoch, SessionState.Authenticated(cached, operation.epoch, offline = true))
                        } else setState(operation.epoch, SessionState.ConnectionRequired(ApiFailure(
                            if (network == null) ApiFailureKind.NOT_CONFIGURED else ApiFailureKind.NETWORK)))
                    } else if (rotate(operation.epoch, null, stored.token,
                            persistence.withLock { identityStore?.read()?.id })) {
                        verifyIdentity(operation.epoch)
                    }
                }
            }
            return state.value
        } catch (error: CancellationException) {
            operation?.let { expire(it.epoch, SignOutReason.ROTATION_UNCERTAIN) }
            throw error
        } catch (_: IOException) {
            operation?.let { expire(it.epoch, SignOutReason.STORAGE_UNAVAILABLE) }
            return state.value
        } finally {
            operation?.let { release(it) }
            authentication.unlock()
        }
    }

    suspend fun <T> authenticated(call: suspend (RequestCredential) -> ApiResult<T>): ApiResult<T> =
        request(allowUnverified = false, call)

    private suspend fun <T> request(
        allowUnverified: Boolean,
        call: suspend (RequestCredential) -> ApiResult<T>,
    ): ApiResult<T> {
        val job = currentCoroutineContext()[Job] ?: error("Account request needs a caller job.")
        val snapshot = synchronized(lock) {
            record?.takeIf { allowUnverified || it.user != null }?.also { accountJobs[job] = epoch }
        } ?: return failure(ApiFailureKind.UNAUTHENTICATED)
        val operation = Operation(snapshot.credential.epoch, job)
        try {
            val first = call(snapshot.credential)
            if (!isCurrent(operation.epoch)) return failure(ApiFailureKind.UNAUTHENTICATED)
            if (first !is ApiResult.Failure || first.error.kind != ApiFailureKind.UNAUTHENTICATED) return first
            if (!refreshAfter401(snapshot.credential)) return first
            val next = synchronized(lock) { record?.credential?.takeIf { it.epoch == operation.epoch } }
                ?: return failure(ApiFailureKind.UNAUTHENTICATED)
            val second = call(next)
            if (!isCurrent(operation.epoch)) return failure(ApiFailureKind.UNAUTHENTICATED)
            if (second is ApiResult.Failure && second.error.kind == ApiFailureKind.UNAUTHENTICATED) {
                expire(operation.epoch, SignOutReason.EXPIRED)
            }
            return second
        } finally {
            release(operation)
        }
    }

    suspend fun logout(): LogoutResult {
        val caller = currentCoroutineContext()[Job] ?: error("Logout needs a caller job.")
        val operation = checkNotNull(transition(SessionState.SignedOut(SignOutReason.LOGOUT_LOCAL_ONLY),
            caller, trackCaller = false))
        val old = operation.previous
        return withContext(NonCancellable) {
            val localCleared = wipe(operation.epoch, old)
            val revoked = if (old == null || network == null) false else try {
                network.api.logout(old.credential).code() == 204
            } catch (_: IOException) {
                false
            }
            if (revoked && localCleared) setState(operation.epoch, SessionState.SignedOut(SignOutReason.LOGOUT_CONFIRMED))
            LogoutResult(localCleared, revoked)
        }
    }

    suspend fun deleteAccount(password: String, clearDeletedAccount: suspend (String) -> Unit): ApiResult<Unit> {
        val account = state.value as? SessionState.Authenticated ?: return failure(ApiFailureKind.UNAUTHENTICATED)
        if (account.offline) return failure(ApiFailureKind.NETWORK)
        val connection = network ?: return failure(ApiFailureKind.NOT_CONFIGURED)
        val result = authenticated { credential ->
            connection.executor.executeEmpty { connection.api.deleteAccount(DeleteAccountDto(password), credential) }
        }
        val cleanupPending = result is ApiResult.Failure && result.error.code == "ACCOUNT_DELETION_PENDING"
        if (result is ApiResult.Failure && !cleanupPending) return result
        val caller = currentCoroutineContext()[Job] ?: error("Account deletion needs a caller job.")
        return withContext(NonCancellable) {
            val reason = if (cleanupPending) SignOutReason.ACCOUNT_DELETION_PENDING else SignOutReason.ACCOUNT_DELETED
            val operation = transition(SessionState.SignedOut(reason),
                caller, expected = account.epoch, trackCaller = false)
            val wiped = operation?.let { wipe(it.epoch, it.previous) } ?: true
            val purged = try {
                clearDeletedAccount(account.user.id)
                true
            } catch (_: IOException) {
                false
            }
            if (!wiped || !purged) {
                operation?.let { setState(it.epoch, SessionState.SignedOut(SignOutReason.DELETION_CLEANUP_FAILED)) }
                failure(ApiFailureKind.STORAGE)
            } else if (cleanupPending) result else ApiResult.Success(Unit)
        }
    }

    private suspend fun authenticate(call: suspend () -> ApiResult<SessionTokens>): ApiResult<User> {
        if (!authentication.tryLock()) return failure(ApiFailureKind.BUSY)
        var operation: Operation? = null
        try {
            operation = begin(SessionState.SignedOut())
            if (!wipe(operation.epoch, operation.previous)) return failure(ApiFailureKind.STORAGE)
            val result = call()
            if (!isCurrent(operation.epoch)) return failure(ApiFailureKind.UNAUTHENTICATED)
            if (result is ApiResult.Failure) return result
            val tokens = (result as ApiResult.Success).value
            val user = tokens.user ?: return failure(ApiFailureKind.MALFORMED_RESPONSE)
            return if (persist(operation.epoch, tokens, user)) ApiResult.Success(user)
                else failure(ApiFailureKind.UNAUTHENTICATED)
        } catch (error: CancellationException) {
            operation?.let { expire(it.epoch, SignOutReason.ROTATION_UNCERTAIN) }
            throw error
        } catch (_: IOException) {
            operation?.let { expire(it.epoch, SignOutReason.STORAGE_UNAVAILABLE) }
            return failure(ApiFailureKind.STORAGE)
        } finally {
            operation?.let { release(it) }
            authentication.unlock()
        }
    }

    private suspend fun refreshAfter401(failed: RequestCredential): Boolean = refresh.withLock {
        val current = synchronized(lock) { record?.takeIf { it.credential.epoch == failed.epoch } }
            ?: return@withLock false
        if (current.credential.revision != failed.revision) return@withLock true
        try {
            val token = persistence.withLock { tokenStore.read() } as? TokenReadResult.Available
            if (token == null) {
                expire(failed.epoch, SignOutReason.INVALID_CREDENTIAL)
                false
            } else rotate(failed.epoch, current, token.token)
        } catch (error: CancellationException) {
            expire(failed.epoch, SignOutReason.ROTATION_UNCERTAIN)
            throw error
        } catch (_: IOException) {
            expire(failed.epoch, SignOutReason.STORAGE_UNAVAILABLE)
            false
        }
    }

    private suspend fun rotate(expected: Long, previous: Record?, token: RefreshToken,
        cachedOwner: String? = null): Boolean {
        val connection = network ?: return false
        if (!isCurrent(expected)) return false
        val result = connection.executor.execute({ it.toModel() }) { connection.api.refresh(RefreshDto(token.value)) }
        if (!isCurrent(expected)) return false
        if (result is ApiResult.Failure) {
            expire(expected, if (result.error.kind == ApiFailureKind.UNAUTHENTICATED)
                SignOutReason.EXPIRED else SignOutReason.ROTATION_UNCERTAIN)
            return false
        }
        val tokens = (result as ApiResult.Success).value
        if (tokens.refresh == token || cachedOwner != null && tokens.user != null && tokens.user.id != cachedOwner ||
            previous != null && tokens.user != null &&
                previous.expectedUserId != null && tokens.user.id != previous.expectedUserId) {
            expire(expected, SignOutReason.ROTATION_UNCERTAIN)
            return false
        }
        // Bootstrap publishes no identity until GET /me verifies the new access token.
        return persist(expected, tokens, previous?.user, previous?.expectedUserId ?: cachedOwner ?: tokens.user?.id)
    }

    private suspend fun verifyIdentity(expected: Long): SessionState {
        val connection = network ?: return state.value
        val result = request(allowUnverified = true) { credential ->
            connection.executor.execute({ it.toModel() }) { connection.api.me(credential) }
        }
        if (result is ApiResult.Success) {
            val owner = synchronized(lock) { record?.takeIf { it.credential.epoch == expected }?.expectedUserId }
            if (owner != null && owner != result.value.id) {
                expire(expected, SignOutReason.INVALID_CREDENTIAL)
                return state.value
            }
            persistence.withLock {
                if (isCurrent(expected)) {
                    identityStore?.write(result.value)
                    synchronized(lock) {
                        if (epoch == expected && record != null) {
                            record = record!!.copy(user = result.value)
                            mutableState.value = SessionState.Authenticated(result.value, expected)
                        }
                    }
                }
            }
        } else if (result is ApiResult.Failure && isCurrent(expected)) {
            if ((state.value as? SessionState.Authenticated)?.offline != true) {
                setState(expected, SessionState.ConnectionRequired(result.error))
            }
        }
        return state.value
    }

    private suspend fun persist(
        expected: Long, tokens: SessionTokens, user: User?, expectedUserId: String? = user?.id ?: tokens.user?.id,
    ): Boolean = persistence.withLock {
        if (!isCurrent(expected)) return@withLock false
        try {
            tokenStore.write(tokens.refresh)
            if (user != null) identityStore?.write(user)
            currentCoroutineContext().ensureActive()
            storedEpoch = expected
            val installed = synchronized(lock) {
                if (epoch != expected) false else {
                    revision++
                    record = Record(RequestCredential(tokens.access, expected, revision), user, expectedUserId)
                    if (user != null) mutableState.value = SessionState.Authenticated(user, expected)
                    true
                }
            }
            if (!installed) {
                withContext(NonCancellable) { tokenStore.clear() }
                storedEpoch = Long.MIN_VALUE
            }
            installed
        } catch (error: Exception) {
            try { withContext(NonCancellable) { tokenStore.clear() } }
            catch (cleanupError: IOException) { error.addSuppressed(cleanupError) }
            storedEpoch = Long.MIN_VALUE
            throw error
        }
    }

    private suspend fun expire(expected: Long, reason: SignOutReason) {
        val caller = currentCoroutineContext()[Job] ?: error("Session expiry needs a caller job.")
        withContext(NonCancellable) {
            val operation = transition(SessionState.SignedOut(reason), caller, expected) ?: return@withContext
            try { wipe(operation.epoch, operation.previous) } finally { release(operation) }
        }
    }

    private suspend fun wipe(through: Long, old: Record?): Boolean = withContext(NonCancellable) {
        try {
            persistence.withLock {
                if (storedEpoch <= through) {
                    tokenStore.clear()
                    identityStore?.clear()
                    storedEpoch = Long.MIN_VALUE
                }
                old?.user?.let { clearAccountData(it.id, old.credential.epoch) }
            }
            true
        } catch (_: IOException) {
            setState(through, SessionState.SignedOut(SignOutReason.STORAGE_UNAVAILABLE))
            false
        }
    }

    private suspend fun begin(nextState: SessionState): Operation {
        val caller = currentCoroutineContext()[Job] ?: error("Session operation needs a caller job.")
        return checkNotNull(transition(nextState, caller))
    }

    private suspend fun beginBootstrap(): Operation {
        val caller = currentCoroutineContext()[Job] ?: error("Bootstrap needs a caller job.")
        // Verifying an offline account must not replace its navigation/ViewModel owner.
        return synchronized(lock) {
            if ((mutableState.value as? SessionState.Authenticated)?.offline == true) {
                accountJobs[caller] = epoch
                Operation(epoch, caller)
            } else null
        } ?: begin(SessionState.Checking)
    }

    private fun transition(
        nextState: SessionState, caller: Job, expected: Long? = null, trackCaller: Boolean = true,
    ): Operation? {
        val canceled: List<Job>
        val operation = synchronized(lock) {
            if (expected != null && epoch != expected) return null
            canceled = accountJobs.keys.filter { it != caller }
            accountJobs.clear()
            epoch++
            val previous = record
            record = null
            mutableState.value = nextState
            if (trackCaller) accountJobs[caller] = epoch
            Operation(epoch, caller, previous)
        }
        canceled.forEach { it.cancel() }
        return operation
    }

    private fun release(operation: Operation) = synchronized(lock) {
        if (accountJobs[operation.job] == operation.epoch) accountJobs.remove(operation.job)
    }

    private fun isCurrent(expected: Long) = synchronized(lock) { epoch == expected }
    private fun setState(expected: Long, next: SessionState) = synchronized(lock) {
        if (epoch == expected) mutableState.value = next
    }

    private fun failure(kind: ApiFailureKind) = ApiResult.Failure(ApiFailure(kind))
    private data class Record(
        val credential: RequestCredential, val user: User?, val expectedUserId: String?,
    )
    private data class Operation(val epoch: Long, val job: Job, val previous: Record? = null)
}
