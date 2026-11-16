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

class AccountDeletionViewModel(private val accounts: AccountManagement) : ViewModel() {
    private val mutableState = MutableStateFlow(AccountDeletionState())
    val state = mutableState.asStateFlow()

    fun password(value: String) {
        if (!state.value.submitting) mutableState.update { it.copy(password = value, passwordError = null, failure = null) }
    }

    fun acknowledge(value: Boolean) {
        if (!state.value.submitting) mutableState.update { it.copy(acknowledged = value, failure = null) }
    }

    fun dismiss() {
        if (!state.value.submitting) mutableState.value = AccountDeletionState()
    }

    fun delete() {
        val input = state.value
        if (input.submitting || input.deleted) return
        if (!isValidAccountPassword(input.password)) {
            mutableState.update { it.copy(passwordError = R.string.error_password_length) }
            return
        }
        if (!input.acknowledged) {
            mutableState.update { it.copy(failure = R.string.delete_account_confirm_required) }
            return
        }
        mutableState.update { it.copy(submitting = true, failure = null, passwordError = null) }
        viewModelScope.launch {
            when (val result = accounts.deleteAccount(input.password)) {
                is ApiResult.Success -> mutableState.value = AccountDeletionState(deleted = true)
                is ApiResult.Failure -> mutableState.update {
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
