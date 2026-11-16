package com.example.wastenot.ui.auth

import androidx.lifecycle.viewModelScope
import com.example.wastenot.R
import com.example.wastenot.data.network.ApiFailure
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.repository.AccountManagement
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountManagementViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = mutableListOf<androidx.lifecycle.ViewModel>()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { models.forEach { it.viewModelScope.cancel() }; Dispatchers.resetMain() }

    @Test fun unavailableEmailDeliveryDoesNotClaimMailWasSentOrAdvanceToCodeEntry() = runTest {
        val source = Accounts().apply { requestResult = ApiResult.Failure(ApiFailure(ApiFailureKind.SERVER, 503, "UNAVAILABLE")) }
        val model = PasswordRecoveryViewModel(source).also { models += it }
        model.change(RecoveryField.EMAIL, "owner@example.com")
        model.submit()
        advanceUntilIdle()
        assertEquals(RecoveryPhase.REQUEST, model.state.value.phase)
        assertEquals(R.string.password_recovery_unavailable, model.state.value.failure)
        assertFalse(model.state.value.submitting)
    }

    @Test fun resetRequiresMatchingPasswordsAndClearsSecretsOnlyAfterConfirmation() = runTest {
        val source = Accounts().apply { confirmationGate = CompletableDeferred() }
        val model = PasswordRecoveryViewModel(source).also { models += it }
        model.useExistingCode()
        model.change(RecoveryField.TOKEN, "A".repeat(43))
        model.change(RecoveryField.PASSWORD, " new-password-raw ")
        model.change(RecoveryField.CONFIRMATION, "different-password")
        model.submit()
        assertEquals(R.string.error_password_mismatch, model.state.value.errors[RecoveryField.CONFIRMATION])
        assertEquals(0, source.confirmations)
        model.change(RecoveryField.CONFIRMATION, " new-password-raw ")
        model.submit()
        model.submit()
        runCurrent()
        assertEquals(1, source.confirmations)
        assertEquals(" new-password-raw ", source.confirmedPassword)
        assertTrue(model.state.value.submitting)
        source.confirmationGate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(RecoveryPhase.COMPLETE, model.state.value.phase)
        assertEquals("", model.state.value.password)
        assertEquals("", model.state.value.confirmation)
        assertEquals("", model.state.value.token)
    }

    @Test fun rejectedCodeKeepsUserInputForCorrectionInsteadOfReportingResetSuccess() = runTest {
        val source = Accounts().apply { confirmResult = ApiResult.Failure(ApiFailure(ApiFailureKind.VALIDATION, 422, "INVALID_RESET_TOKEN")) }
        val model = PasswordRecoveryViewModel(source).also { models += it }
        model.useExistingCode()
        model.change(RecoveryField.TOKEN, "A".repeat(43))
        model.change(RecoveryField.PASSWORD, "new-password-raw")
        model.change(RecoveryField.CONFIRMATION, "new-password-raw")
        model.submit()
        advanceUntilIdle()
        assertEquals(RecoveryPhase.CONFIRM, model.state.value.phase)
        assertEquals(R.string.password_recovery_invalid_token, model.state.value.failure)
        assertEquals("A".repeat(43), model.state.value.token)
        assertEquals("new-password-raw", model.state.value.password)
    }

    @Test fun deletingAccountRequiresExplicitAcknowledgementAndIsSingleFlight() = runTest {
        val source = Accounts().apply { deletionGate = CompletableDeferred() }
        val model = AccountDeletionViewModel(source).also { models += it }
        model.password("current-password")
        model.delete()
        assertEquals(R.string.delete_account_confirm_required, model.state.value.failure)
        assertEquals(0, source.deletions)
        model.acknowledge(true)
        model.delete()
        model.delete()
        runCurrent()
        assertEquals(1, source.deletions)
        assertTrue(model.state.value.submitting)
        source.deletionGate!!.complete(Unit)
        advanceUntilIdle()
        assertTrue(model.state.value.deleted)
        assertEquals("", model.state.value.password)
        assertFalse(model.state.value.acknowledged)
    }

    @Test fun wrongDeletionPasswordCanBeCorrectedWithoutClaimingTheAccountWasRemoved() = runTest {
        val source = Accounts().apply { deletionResult = ApiResult.Failure(ApiFailure(ApiFailureKind.HTTP, 403, "WRONG_PASSWORD")) }
        val model = AccountDeletionViewModel(source).also { models += it }
        model.password("wrong-password")
        model.acknowledge(true)
        model.delete()
        advanceUntilIdle()
        assertEquals(R.string.delete_account_wrong_password, model.state.value.passwordError)
        assertFalse(model.state.value.deleted)
        model.dismiss()
        assertEquals("", model.state.value.password)
        assertFalse(model.state.value.acknowledged)
    }

    private class Accounts : AccountManagement {
        var requestResult: ApiResult<Unit> = ApiResult.Success(Unit)
        var confirmResult: ApiResult<Unit> = ApiResult.Success(Unit)
        var deletionResult: ApiResult<Unit> = ApiResult.Success(Unit)
        var confirmationGate: CompletableDeferred<Unit>? = null
        var deletionGate: CompletableDeferred<Unit>? = null
        var confirmations = 0
        var deletions = 0
        var confirmedPassword: String? = null
        override suspend fun requestPasswordReset(email: String) = requestResult
        override suspend fun confirmPasswordReset(token: String, password: String): ApiResult<Unit> {
            confirmations++; confirmedPassword = password; confirmationGate?.await()
            return confirmResult
        }
        override suspend fun deleteAccount(password: String): ApiResult<Unit> {
            deletions++; deletionGate?.await()
            return deletionResult
        }
    }
}
