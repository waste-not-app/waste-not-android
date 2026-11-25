package com.example.wastenot.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wastenot.R
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
    val googleSubmitting: Boolean = false,
    val googleFailure: Int? = null,
    val googleLinkRequired: Boolean = false,
    val googleLinkPassword: String = "",
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
    private var googleSelectionAttempt: Long? = null
    private var googleLinkAccountId: String? = null

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
        googleLinkAccountId = null
        mutableAuth.update { it.copy(password = "", confirmation = "", errors = emptyMap(), serverFields = emptySet(),
            failure = null, googleFailure = null, googleLinkRequired = false, googleLinkPassword = "") }
    }

    fun cancelSubmission() {
        authRevision++
        googleSelectionAttempt = null
        pending?.cancel()
        clearPasswords()
        mutableAuth.update { it.copy(submitting = false, googleSubmitting = false) }
    }

    internal fun changeGoogleLinkPassword(password: String) {
        mutableAuth.update {
            if (!it.googleLinkRequired || it.submitting) it else it.copy(googleLinkPassword = password, googleFailure = null)
        }
    }

    internal fun dismissGoogleLink() {
        if (mutableAuth.value.submitting) return
        googleLinkAccountId = null
        mutableAuth.update {
            it.copy(googleLinkRequired = false, googleLinkPassword = "", googleFailure = null)
        }
    }

    internal suspend fun googleChallenge() = accounts.googleChallenge()

    internal fun beginGoogle(): Long? {
        val input = mutableAuth.value
        if (input.submitting || pending?.isActive == true || mutableLogout.value is LogoutUiState.Pending ||
            accounts.state.value !is SessionState.SignedOut || (input.googleLinkRequired && input.googleLinkPassword.isEmpty())) {
            return null
        }
        val attempt = ++authRevision
        googleSelectionAttempt = attempt
        mutableAuth.update { it.copy(submitting = true, googleSubmitting = true, failure = null, googleFailure = null) }
        return attempt
    }

    internal fun completeGoogle(attempt: Long, selection: GoogleSelection) {
        if (googleSelectionAttempt != attempt) return
        googleSelectionAttempt = null
        if (selection !is GoogleSelection.Selected) {
            mutableAuth.update { it.copy(submitting = false, googleSubmitting = false,
                googleFailure = (selection as? GoogleSelection.Failure)?.message) }
            return
        }
        val input = mutableAuth.value
        val password = input.googleLinkPassword.takeIf { input.googleLinkRequired }
        if (input.googleLinkRequired && selection.accountId != googleLinkAccountId) {
            mutableAuth.update { it.copy(submitting = false, googleSubmitting = false,
                googleFailure = R.string.google_auth_link_wrong_account) }
            return
        }
        mutableLogout.value = LogoutUiState.None
        pending = viewModelScope.launch {
            try {
                val result = accounts.googleLogin(selection.idToken, input.timezone, password)
                if (authRevision != attempt) return@launch
                when (result) {
                    is ApiResult.Success -> clearPasswords()
                    is ApiResult.Failure -> if (result.error.code == "GOOGLE_LINK_REQUIRED") {
                        googleLinkAccountId = selection.accountId
                        mutableAuth.update { it.copy(googleLinkRequired = true, googleLinkPassword = "") }
                    } else {
                        mutableAuth.update { it.copy(googleFailure = result.error.googleMessage(linking = password != null)) }
                    }
                }
            } finally {
                if (authRevision == attempt) mutableAuth.update { it.copy(submitting = false, googleSubmitting = false) }
            }
        }
    }

    fun submit(register: Boolean) {
        if (mutableAuth.value.submitting || pending?.isActive == true || accounts.state.value !is SessionState.SignedOut) return
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
        mutableAuth.update { it.copy(submitting = true, errors = emptyMap(), serverFields = emptySet(),
            failure = null, googleFailure = null) }
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
        googleSelectionAttempt = null
        googleLinkAccountId = null
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
