package com.example.wastenot.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wastenot.R
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.repository.AccountManagement
import com.example.wastenot.model.isValidAccountEmail
import com.example.wastenot.model.isValidAccountPassword
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class RecoveryField { EMAIL, TOKEN, PASSWORD, CONFIRMATION }
enum class RecoveryPhase { REQUEST, CONFIRM, COMPLETE }

data class PasswordRecoveryState(
    val email: String = "",
    val token: String = "",
    val password: String = "",
    val confirmation: String = "",
    val phase: RecoveryPhase = RecoveryPhase.REQUEST,
    val errors: Map<RecoveryField, Int> = emptyMap(),
    val failure: Int? = null,
    val submitting: Boolean = false,
    val requestAccepted: Boolean = false,
) {
    override fun toString() = "PasswordRecoveryState(phase=$phase, submitting=$submitting, errors=${errors.keys})"
}

class PasswordRecoveryViewModel(private val accounts: AccountManagement) : ViewModel() {
    private val mutableState = MutableStateFlow(PasswordRecoveryState())
    val state = mutableState.asStateFlow()

    fun change(field: RecoveryField, value: String) {
        if (state.value.submitting) return
        mutableState.update { current ->
            val next = when (field) {
                RecoveryField.EMAIL -> current.copy(email = value)
                RecoveryField.TOKEN -> current.copy(token = value)
                RecoveryField.PASSWORD -> current.copy(password = value)
                RecoveryField.CONFIRMATION -> current.copy(confirmation = value)
            }
            next.copy(errors = next.errors - field, failure = null)
        }
    }

    fun requestAnother() {
        if (state.value.submitting) return
        mutableState.update { PasswordRecoveryState(email = it.email) }
    }

    fun useExistingCode() {
        if (!state.value.submitting) mutableState.update { PasswordRecoveryState(email = it.email, phase = RecoveryPhase.CONFIRM) }
    }

    fun submit() {
        val input = state.value
        if (input.submitting || input.phase == RecoveryPhase.COMPLETE) return
        val errors = buildMap {
            if (input.phase == RecoveryPhase.REQUEST) {
                if (!isValidAccountEmail(input.email)) put(RecoveryField.EMAIL, R.string.error_email_format)
            } else {
                if (!resetToken.matches(input.token.trim())) put(RecoveryField.TOKEN, R.string.password_recovery_invalid_token)
                if (!isValidAccountPassword(input.password)) put(RecoveryField.PASSWORD, R.string.error_password_length)
                if (input.password != input.confirmation) put(RecoveryField.CONFIRMATION, R.string.error_password_mismatch)
            }
        }
        if (errors.isNotEmpty()) {
            mutableState.update { it.copy(errors = errors, failure = null) }
            return
        }
        mutableState.update { it.copy(submitting = true, failure = null, errors = emptyMap()) }
        viewModelScope.launch {
            try {
                val result = if (input.phase == RecoveryPhase.REQUEST) accounts.requestPasswordReset(input.email)
                    else accounts.confirmPasswordReset(input.token, input.password)
                when (result) {
                    is ApiResult.Success -> mutableState.update {
                        it.copy(phase = if (input.phase == RecoveryPhase.REQUEST) RecoveryPhase.CONFIRM else RecoveryPhase.COMPLETE,
                            requestAccepted = input.phase == RecoveryPhase.REQUEST || it.requestAccepted,
                            token = "", password = "", confirmation = "", submitting = false)
                    }
                    is ApiResult.Failure -> mutableState.update {
                        it.copy(submitting = false, failure = when {
                            result.error.kind == ApiFailureKind.NOT_CONFIGURED || result.error.code == "UNAVAILABLE" -> R.string.password_recovery_unavailable
                            result.error.kind == ApiFailureKind.RATE_LIMITED -> R.string.account_management_rate_limited
                            result.error.kind == ApiFailureKind.NETWORK || result.error.kind == ApiFailureKind.TIMEOUT -> R.string.account_management_network_error
                            input.phase == RecoveryPhase.CONFIRM && result.error.kind == ApiFailureKind.VALIDATION -> R.string.password_recovery_invalid_token
                            else -> R.string.account_management_failed
                        })
                    }
                }
            } catch (error: CancellationException) {
                throw error
            }
        }
    }

    companion object {
        private val resetToken = Regex("[A-Za-z0-9_-]{43}")
    }
}
