package com.example.wastenot.ui.auth

import androidx.lifecycle.viewModelScope
import com.example.wastenot.data.network.ApiFailure
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.repository.AccountSession
import com.example.wastenot.data.repository.LogoutResult
import com.example.wastenot.data.repository.SessionState
import com.example.wastenot.model.AccountField
import com.example.wastenot.model.AccountInputError
import com.example.wastenot.model.User
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = mutableListOf<SessionViewModel>()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() {
        models.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }
    private fun model(source: FakeSession = FakeSession()) = SessionViewModel(source).also { models.add(it) }

    @Test fun invalidRegistrationOnlyProducesFieldErrors() = runTest {
        val source = FakeSession()
        val vm = model(source)
        vm.change(AccountField.NAME, " ")
        vm.change(AccountField.EMAIL, "bad")
        vm.change(AccountField.PASSWORD, "short")
        vm.change(AccountField.PASSWORD_CONFIRMATION, "different")
        vm.submit(register = true)
        runCurrent()
        assertEquals(AccountField.entries.toSet(), vm.auth.value.errors.keys)
        assertEquals(AccountInputError.PASSWORD_MISMATCH, vm.auth.value.errors[AccountField.PASSWORD_CONFIRMATION])
        assertEquals(0, source.registerCalls)
        assertEquals(1, vm.auth.value.focusRequest)
    }

    @Test fun doubleTapIsSingleFlightAndSuccessClearsInMemoryPasswords() = runTest {
        val source = FakeSession().apply { loginGate = CompletableDeferred() }
        val vm = model(source)
        vm.change(AccountField.EMAIL, " SAM@example.invalid ")
        vm.change(AccountField.PASSWORD, " 123456789012 ")
        vm.submit(register = false)
        vm.submit(register = false)
        runCurrent()
        assertEquals(1, source.loginCalls)
        assertTrue(vm.auth.value.submitting)
        assertEquals(" 123456789012 ", source.receivedPassword)
        source.loginGate!!.complete(Unit)
        advanceUntilIdle()
        assertTrue(vm.session.value.phase is SessionState.Authenticated)
        assertEquals("", vm.auth.value.password)
        vm.submit(register = false)
        runCurrent()
        assertEquals(1, source.loginCalls)
    }

    @Test fun duplicateEmailPreservesFieldsAndDoesNotInventASession() = runTest {
        val source = FakeSession().apply { failure = ApiFailureKind.EMAIL_EXISTS }
        val vm = model(source)
        vm.change(AccountField.NAME, "Sam Demo")
        vm.change(AccountField.EMAIL, "sam@example.invalid")
        vm.change(AccountField.PASSWORD, "fixture-password")
        vm.change(AccountField.PASSWORD_CONFIRMATION, "fixture-password")
        vm.submit(register = true)
        advanceUntilIdle()
        assertEquals(setOf(AccountField.EMAIL), vm.auth.value.serverFields)
        assertEquals("Sam Demo", vm.auth.value.name)
        assertEquals("fixture-password", vm.auth.value.password)
        assertTrue(vm.session.value.phase is SessionState.SignedOut)
        assertFalse(vm.auth.value.toString().contains("fixture-password"))
        assertEquals("", model().auth.value.password)
    }

    @Test fun bootstrapKeepsTheInjectedAccountSourceWithoutPreviewSwitching() = runTest {
        val source = FakeSession()
        val vm = model(source)
        runCurrent()
        assertFalse(vm.session.value.isDemo)
        vm.bootstrap()
        advanceUntilIdle()
        assertTrue(vm.session.value.phase is SessionState.SignedOut)
        assertFalse(vm.session.value.isDemo)
        assertEquals(0, source.loginCalls)
        assertEquals(0, source.registerCalls)
    }

    @Test fun serverFieldErrorsOnlyFocusFieldsPresentInTheSubmittedForm() = runTest {
        val source = FakeSession().apply {
            failure = ApiFailureKind.VALIDATION
            failureFields = mapOf("name" to "invalid", "email" to "invalid", "unexpected" to "invalid")
        }
        val vm = model(source)
        vm.change(AccountField.EMAIL, "sam@example.invalid")
        vm.change(AccountField.PASSWORD, "fixture-password-12")
        vm.submit(register = false)
        advanceUntilIdle()
        assertEquals(setOf(AccountField.EMAIL), vm.auth.value.serverFields)
        vm.change(AccountField.NAME, "Sam Demo")
        vm.change(AccountField.PASSWORD_CONFIRMATION, "fixture-password-12")
        vm.submit(register = true)
        advanceUntilIdle()
        assertEquals(setOf(AccountField.NAME, AccountField.EMAIL), vm.auth.value.serverFields)
    }

    @Test fun pendingServerRevocationDoesNotBlockANewLogin() = runTest {
        val source = FakeSession().apply {
            state.value = SessionState.Authenticated(user, 1)
            logoutGate = CompletableDeferred()
        }
        val vm = model(source)
        vm.logout()
        runCurrent()
        assertTrue(vm.session.value.phase is SessionState.SignedOut)
        vm.change(AccountField.EMAIL, "new@example.invalid")
        vm.change(AccountField.PASSWORD, "new-password-12")
        vm.submit(register = false)
        runCurrent()
        assertEquals(1, source.loginCalls)
        assertTrue(vm.session.value.phase is SessionState.Authenticated)
        source.logoutGate!!.complete(Unit)
        advanceUntilIdle()
        assertTrue(vm.session.value.phase is SessionState.Authenticated)
        assertEquals(LogoutUiState.None, vm.logoutState.value)
    }

    @Test fun logoutNeverClaimsLocalCleanupBeforeItsResult() = runTest {
        val source = FakeSession().apply {
            state.value = SessionState.Authenticated(user, 1)
            logoutGate = CompletableDeferred()
            logoutResult = LogoutResult(false, true)
        }
        val vm = model(source)
        vm.logout()
        runCurrent()
        assertTrue(vm.session.value.phase is SessionState.SignedOut)
        assertTrue(vm.logoutState.value is LogoutUiState.Pending)
        source.logoutGate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(LogoutResult(false, true), (vm.logoutState.value as LogoutUiState.Complete).result)
    }

    @Test fun cancelledRequestCannotUnlockANewerSubmission() = runTest {
        val oldGate = CompletableDeferred<Unit>()
        val newGate = CompletableDeferred<Unit>()
        val source = FakeSession().apply { loginGate = oldGate; nonCancellableWait = true }
        val vm = model(source)
        vm.change(AccountField.EMAIL, "sam@example.invalid")
        vm.change(AccountField.PASSWORD, "first-password-12")
        vm.submit(register = false)
        runCurrent()
        vm.cancelSubmission()
        source.loginGate = newGate
        vm.change(AccountField.PASSWORD, "second-password-12")
        vm.submit(register = false)
        runCurrent()
        assertTrue(vm.auth.value.submitting)
        oldGate.complete(Unit)
        runCurrent()
        assertTrue(vm.auth.value.submitting)
        newGate.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.auth.value.submitting)
        assertTrue(vm.session.value.phase is SessionState.Authenticated)
    }

    @Test fun bootstrapIsStartedByRouteAndRetriesOnlyWhenRequested() = runTest {
        val source = FakeSession()
        val vm = model(source)
        runCurrent()
        assertEquals(0, source.bootstrapCalls)
        vm.bootstrap()
        vm.bootstrap()
        advanceUntilIdle()
        vm.bootstrap()
        advanceUntilIdle()
        assertEquals(1, source.bootstrapCalls)
        vm.bootstrap(retry = true)
        advanceUntilIdle()
        assertEquals(2, source.bootstrapCalls)
    }

    private class FakeSession : AccountSession {
        override val state = MutableStateFlow<SessionState>(SessionState.SignedOut())
        override val isDemo = false
        val user = User("00000000-0000-4000-8000-000000000002", "Sample User", "sample@example.invalid", ZoneId.of("Asia/Jakarta"), null)
        var bootstrapCalls = 0
        var loginCalls = 0
        var registerCalls = 0
        var receivedPassword: String? = null
        var failure: ApiFailureKind? = null
        var failureFields: Map<String, String> = emptyMap()
        var loginGate: CompletableDeferred<Unit>? = null
        var logoutGate: CompletableDeferred<Unit>? = null
        var logoutResult = LogoutResult(true, true)
        var nonCancellableWait = false
        override suspend fun bootstrap() { bootstrapCalls++ }
        override suspend fun login(email: String, password: String): ApiResult<User> {
            loginCalls++
            receivedPassword = password
            val gate = loginGate
            if (nonCancellableWait) withContext(NonCancellable) { gate?.await() } else gate?.await()
            return result()
        }
        override suspend fun register(name: String, email: String, password: String, timezone: ZoneId): ApiResult<User> {
            registerCalls++
            return result()
        }
        override suspend fun logout(): LogoutResult {
            state.value = SessionState.SignedOut()
            logoutGate?.await()
            return logoutResult
        }
        private fun result(): ApiResult<User> {
            failure?.let { return ApiResult.Failure(ApiFailure(it, fields = failureFields)) }
            state.value = SessionState.Authenticated(user, 2)
            return ApiResult.Success(user)
        }
    }
}
