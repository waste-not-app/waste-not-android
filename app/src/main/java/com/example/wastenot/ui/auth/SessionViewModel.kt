package com.example.wastenot.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.repository.AccountSession
import com.example.wastenot.data.repository.LogoutResult
import com.example.wastenot.data.repository.SessionState
import com.example.wastenot.model.AccountField
import com.example.wastenot.model.AccountInputError
import com.example.wastenot.model.initialAccountTimezone
import com.example.wastenot.model.validateLoginInput
import com.example.wastenot.model.validateRegistrationInput
import java.time.ZoneId
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SessionUiState(val phase: SessionState)

sealed interface LogoutUiState {
    data object None : LogoutUiState
    class Pending : LogoutUiState
    data class Complete(val result: LogoutResult) : LogoutUiState
}

data class AuthUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val confirmation: String = "",
    val timezone: ZoneId = initialAccountTimezone(ZoneId.systemDefault().id),
    val errors: Map<AccountField, AccountInputError> = emptyMap(),
    val serverFields: Set<AccountField> = emptySet(),
    val failure: ApiFailureKind? = null,
    val submitting: Boolean = false,
    val focusRequest: Int = 0,
) {
    override fun toString() = "AuthUiState(submitting=$submitting, errors=${errors.keys}, failure=$failure)"
}

class SessionViewModel(private val accounts: AccountSession) : ViewModel() {
    val session = accounts.state.map { SessionUiState(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SessionUiState(accounts.state.value))
    private val mutableAuth = MutableStateFlow(AuthUiState())
    val auth = mutableAuth.asStateFlow()
    private val mutableLogout = MutableStateFlow<LogoutUiState>(LogoutUiState.None)
    val logoutState = mutableLogout.asStateFlow()
    private val mutableBootstrapping = MutableStateFlow(false)
    val bootstrapping = mutableBootstrapping.asStateFlow()
    private var pending: Job? = null
    private var bootstrapStarted = false
    private var authRevision = 0L

    fun bootstrap(retry: Boolean = false) {
        if (pending?.isActive == true || (bootstrapStarted && !retry)) return
        bootstrapStarted = true
        val source = accounts
        mutableBootstrapping.value = true
        pending = viewModelScope.launch {
            try { source.bootstrap() } finally { mutableBootstrapping.value = false }
        }
    }

    fun change(field: AccountField, value: String) {
        mutableAuth.update { state ->
            if (state.submitting) state else {
                val changed = when (field) {
                    AccountField.NAME -> state.copy(name = value)
                    AccountField.EMAIL -> state.copy(email = value)
                    AccountField.PASSWORD -> state.copy(password = value)
                    AccountField.PASSWORD_CONFIRMATION -> state.copy(confirmation = value)
                }
                changed.copy(errors = state.errors - field, serverFields = state.serverFields - field, failure = null)
            }
        }
    }

    fun clearPasswords() {
        mutableAuth.update { it.copy(password = "", confirmation = "", errors = emptyMap(), serverFields = emptySet(), failure = null) }
    }

    fun cancelSubmission() {
        authRevision++
        pending?.cancel()
        clearPasswords()
        mutableAuth.update { it.copy(submitting = false) }
    }

    fun submit(register: Boolean) {
        if (pending?.isActive == true || accounts.state.value !is SessionState.SignedOut) return
        val input = mutableAuth.value
        val errors = if (register) validateRegistrationInput(input.name, input.email, input.password, input.confirmation)
            else validateLoginInput(input.email, input.password)
        if (errors.isNotEmpty()) {
            mutableAuth.update { it.copy(errors = errors, serverFields = emptySet(), failure = null, focusRequest = it.focusRequest + 1) }
            return
        }
        val source = accounts
        val revision = ++authRevision
        mutableLogout.value = LogoutUiState.None
        mutableAuth.update { it.copy(submitting = true, errors = emptyMap(), serverFields = emptySet(), failure = null) }
        pending = viewModelScope.launch {
            try {
                val result = if (register) source.register(input.name, input.email, input.password, input.timezone)
                    else source.login(input.email, input.password)
                if (authRevision != revision) return@launch
                when (result) {
                    is ApiResult.Success -> clearPasswords()
                    is ApiResult.Failure -> {
                        val fields = result.error.fields.keys.mapNotNull { key -> when (key) {
                            "name" -> if (register) AccountField.NAME else null
                            "email" -> AccountField.EMAIL
                            "password" -> AccountField.PASSWORD
                            else -> null
                        } }.toSet() + if (result.error.kind == ApiFailureKind.EMAIL_EXISTS) setOf(AccountField.EMAIL) else emptySet()
                        mutableAuth.update { it.copy(failure = result.error.kind, serverFields = fields, focusRequest = it.focusRequest + 1) }
                    }
                }
            } finally {
                if (authRevision == revision) mutableAuth.update { it.copy(submitting = false) }
            }
        }
    }

    fun logout() {
        if (mutableLogout.value is LogoutUiState.Pending) return
        authRevision++
        pending?.cancel()
        mutableAuth.value = AuthUiState()
        val source = accounts
        val operation = LogoutUiState.Pending()
        mutableLogout.value = operation
        viewModelScope.launch {
            val result = source.logout()
            // A new login replaces the pending outcome before an old revocation returns.
            if (mutableLogout.value === operation) mutableLogout.value = LogoutUiState.Complete(result)
        }
    }
}
