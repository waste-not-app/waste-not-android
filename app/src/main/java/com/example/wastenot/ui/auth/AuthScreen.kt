package com.example.wastenot.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.wastenot.R
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.data.repository.SignOutReason
import com.example.wastenot.model.AccountField
import com.example.wastenot.model.AccountInputError
import com.example.wastenot.ui.components.ActionButton
import com.example.wastenot.ui.components.WasteTextField

@Composable
fun AuthScreen(
    register: Boolean,
    state: AuthUiState,
    isDemo: Boolean,
    reason: SignOutReason,
    onChange: (AccountField, String) -> Unit,
    onSubmit: () -> Unit,
    onSwitchForm: () -> Unit,
    modifier: Modifier = Modifier,
    logoutState: LogoutUiState = LogoutUiState.None,
    onRetryCleanup: () -> Unit = {},
) {
    val focus = LocalFocusManager.current
    val requesters = remember { AccountField.entries.associateWith { FocusRequester() } }
    LaunchedEffect(state.focusRequest) {
        val first = state.errors.keys.firstOrNull() ?: state.serverFields.firstOrNull()
        if (state.focusRequest > 0 && first != null) requesters.getValue(first).requestFocus()
    }
    val next = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) }, onDone = { onSubmit() })

    Box(modifier, contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(
                stringResource(if (register) R.string.register_heading else R.string.login_heading),
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.semantics { heading() },
            )
            if (logoutState == LogoutUiState.None && state.failure == null && reason !in setOf(SignOutReason.NONE, SignOutReason.LOGOUT_CONFIRMED, SignOutReason.LOGOUT_LOCAL_ONLY)) {
                MessageBanner(stringResource(R.string.session_expired_message))
            }
            when (logoutState) {
                LogoutUiState.None -> Unit
                is LogoutUiState.Pending -> MessageBanner(stringResource(R.string.logout_pending_message))
                is LogoutUiState.Complete -> when {
                    !logoutState.result.localCleared -> {
                        MessageBanner(stringResource(R.string.logout_cleanup_failed), isError = true)
                        TextButton(onClick = onRetryCleanup, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.logout_cleanup_retry))
                        }
                    }
                    !logoutState.result.serverRevoked && !logoutState.isDemo -> MessageBanner(stringResource(R.string.logout_local_message))
                    else -> Unit
                }
            }
            if (register) AuthField(
                AccountField.NAME, state, requesters.getValue(AccountField.NAME), onChange,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                keyboardActions = next, contentType = ContentType.PersonFullName, modifier = Modifier.fillMaxWidth(),
            )
            AuthField(
                AccountField.EMAIL, state, requesters.getValue(AccountField.EMAIL), onChange,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                keyboardActions = next, contentType = ContentType.EmailAddress, modifier = Modifier.fillMaxWidth(),
            )
            AuthField(
                AccountField.PASSWORD, state, requesters.getValue(AccountField.PASSWORD), onChange,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = if (register) ImeAction.Next else ImeAction.Done),
                keyboardActions = next, contentType = if (register) ContentType.NewPassword else ContentType.Password, modifier = Modifier.fillMaxWidth(),
            )
            if (register) {
                AuthField(
                    AccountField.PASSWORD_CONFIRMATION, state, requesters.getValue(AccountField.PASSWORD_CONFIRMATION), onChange,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = next, contentType = ContentType.NewPassword, modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.account_timezone, state.timezone.id), style = MaterialTheme.typography.bodyMedium)
            }
            state.failure?.let { MessageBanner(stringResource(it.messageResource), isError = true) }
            ActionButton(
                label = stringResource(if (register) R.string.register_action else R.string.login_action),
                onClick = onSubmit,
                loading = state.submitting,
                loadingLabel = stringResource(if (register) R.string.register_loading else R.string.login_loading),
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = onSwitchForm, enabled = !state.submitting, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(if (register) R.string.go_to_login else R.string.go_to_register))
            }
        }
    }
}

@Composable
private fun AuthField(
    field: AccountField,
    state: AuthUiState,
    requester: FocusRequester,
    onChange: (AccountField, String) -> Unit,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
    contentType: ContentType,
    modifier: Modifier = Modifier,
) {
    val value = when (field) {
        AccountField.NAME -> state.name
        AccountField.EMAIL -> state.email
        AccountField.PASSWORD -> state.password
        AccountField.PASSWORD_CONFIRMATION -> state.confirmation
    }
    val label = when (field) {
        AccountField.NAME -> R.string.account_name
        AccountField.EMAIL -> R.string.account_email
        AccountField.PASSWORD -> R.string.account_password
        AccountField.PASSWORD_CONFIRMATION -> R.string.account_confirmation
    }
    val error = state.errors[field]?.messageResource
        ?: if (field in state.serverFields) if (field == AccountField.EMAIL && state.failure == ApiFailureKind.EMAIL_EXISTS) R.string.error_email_exists else R.string.error_field_invalid else null
    WasteTextField(
        value = value,
        onValueChange = { onChange(field, it) },
        label = stringResource(label),
        errorText = error?.let { stringResource(it) },
        helper = if (field == AccountField.PASSWORD) stringResource(R.string.password_helper) else null,
        password = field == AccountField.PASSWORD || field == AccountField.PASSWORD_CONFIRMATION,
        enabled = !state.submitting,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        modifier = modifier
            .focusRequester(requester)
            .semantics { this.contentType = contentType },
    )
}

@Composable
fun MessageBanner(message: String, modifier: Modifier = Modifier, isError: Boolean = false) {
    Surface(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.small,
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
        contentColor = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
    ) { Text(message, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium) }
}

private val AccountInputError.messageResource: Int get() = when (this) {
    AccountInputError.NAME_LENGTH -> R.string.error_name_length
    AccountInputError.EMAIL_FORMAT -> R.string.error_email_format
    AccountInputError.PASSWORD_LENGTH -> R.string.error_password_length
    AccountInputError.PASSWORD_MISMATCH -> R.string.error_password_mismatch
}

private val ApiFailureKind.messageResource: Int get() = when (this) {
    ApiFailureKind.UNAUTHENTICATED -> R.string.error_credentials
    ApiFailureKind.EMAIL_EXISTS -> R.string.error_email_exists
    ApiFailureKind.NETWORK, ApiFailureKind.TIMEOUT -> R.string.error_network
    ApiFailureKind.NOT_CONFIGURED -> R.string.error_account_unavailable
    ApiFailureKind.RATE_LIMITED -> R.string.error_rate_limited
    ApiFailureKind.STORAGE -> R.string.error_secure_storage
    else -> R.string.error_account_request
}
