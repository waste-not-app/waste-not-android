package com.example.wastenot.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wastenot.R
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.repository.AccountManagement
import com.example.wastenot.model.isValidAccountPassword
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountDeletionState(
    val password: String = "",
    val acknowledged: Boolean = false,
    val submitting: Boolean = false,
    val passwordError: Int? = null,
    val failure: Int? = null,
    val deleted: Boolean = false,
) {
    override fun toString() = "AccountDeletionState(submitting=$submitting, deleted=$deleted)"
}

class AccountDeletionViewModel(
    private val accounts: AccountManagement,
    private val hasPassword: Boolean = true,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AccountDeletionState())
    val state = mutableState.asStateFlow()
    private var googleAttempt = 0L
    private var googleSelecting = false

    fun password(value: String) {
        if (hasPassword && !state.value.submitting) mutableState.update { it.copy(password = value, passwordError = null, failure = null) }
    }

    fun acknowledge(value: Boolean) {
        if (!state.value.submitting) mutableState.update { it.copy(acknowledged = value, failure = null) }
    }

    fun dismiss() {
        if (!state.value.submitting) mutableState.value = AccountDeletionState()
    }

    internal suspend fun googleChallenge() = accounts.googleChallenge()

    internal fun beginGoogle(): Long? {
        if (hasPassword || state.value.submitting || state.value.deleted) return null
        if (!confirmAcknowledgement()) return null
        mutableState.update { it.copy(submitting = true, failure = null) }
        googleSelecting = true
        return ++googleAttempt
    }

    internal fun completeGoogle(attempt: Long, selection: GoogleSelection) {
        if (attempt != googleAttempt || !googleSelecting) return
        googleSelecting = false
        when (selection) {
            GoogleSelection.Canceled -> mutableState.update { it.copy(submitting = false) }
            is GoogleSelection.Failure -> mutableState.update { it.copy(submitting = false, failure = selection.message) }
            is GoogleSelection.Selected -> delete { accounts.deleteGoogleAccount(selection.idToken) }
        }
    }

    fun delete() {
        val input = state.value
        if (!hasPassword || input.submitting || input.deleted) return
        if (!isValidAccountPassword(input.password)) {
            mutableState.update { it.copy(passwordError = R.string.error_password_length) }
            return
        }
        if (!confirmAcknowledgement()) return
        mutableState.update { it.copy(submitting = true, failure = null, passwordError = null) }
        delete { accounts.deleteAccount(input.password) }
    }

    private fun confirmAcknowledgement(): Boolean {
        if (state.value.acknowledged) return true
        mutableState.update { it.copy(failure = R.string.delete_account_confirm_required) }
        return false
    }

    private fun delete(request: suspend () -> ApiResult<Unit>) {
        viewModelScope.launch {
            when (val result = request()) {
                is ApiResult.Success -> mutableState.value = AccountDeletionState(deleted = true)
                is ApiResult.Failure -> mutableState.update {
                    if (!hasPassword) return@update it.copy(submitting = false, failure = result.error.googleMessage())
                    if (result.error.status == 403) it.copy(submitting = false, passwordError = R.string.delete_account_wrong_password)
                    else it.copy(submitting = false, failure = when (result.error.kind) {
                        ApiFailureKind.NETWORK, ApiFailureKind.TIMEOUT -> R.string.account_management_network_error
                        ApiFailureKind.RATE_LIMITED -> R.string.account_management_rate_limited
                        else -> R.string.account_management_failed
                    })
                }
            }
        }
    }
}
