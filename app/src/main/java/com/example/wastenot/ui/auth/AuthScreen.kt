package com.example.wastenot.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
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
import com.example.wastenot.ui.components.TextActionButton
import com.example.wastenot.ui.components.IconActionButton
import com.example.wastenot.ui.components.BrandWordmark
import com.example.wastenot.ui.components.WasteTextField
import com.example.wastenot.theme.LocalWasteNotGradients
import com.example.wastenot.theme.OnAccent
import com.example.wastenot.theme.WasteNotAuthHeading

@Composable
fun AuthScreen(
    register: Boolean,
    state: AuthUiState,
    reason: SignOutReason,
    onChange: (AccountField, String) -> Unit,
    onSubmit: () -> Unit,
    onSwitchForm: () -> Unit,
    onTheme: () -> Unit,
    modifier: Modifier = Modifier,
    logoutState: LogoutUiState = LogoutUiState.None,
    onRetryCleanup: () -> Unit = {},
    onBack: (() -> Unit)? = null,
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
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BrandWordmark()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onBack != null) IconActionButton(R.drawable.ic_back, stringResource(R.string.back), onBack)
                    IconActionButton(R.drawable.ic_device, stringResource(R.string.theme_title), onTheme)
                }
            }
            Box(
                Modifier.padding(vertical = 10.dp).size(64.dp)
                    .background(LocalWasteNotGradients.current.accent, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(if (register) R.drawable.ic_leaf else R.drawable.ic_recycle), null, Modifier.size(32.dp), tint = OnAccent)
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(if (register) R.string.register_heading else R.string.login_heading),
                    style = WasteNotAuthHeading,
                    modifier = Modifier.semantics { heading() },
                )
                Text(stringResource(if (register) R.string.register_description else R.string.login_description),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (logoutState == LogoutUiState.None && state.failure == null && reason !in setOf(SignOutReason.NONE, SignOutReason.LOGOUT_CONFIRMED, SignOutReason.LOGOUT_LOCAL_ONLY)) {
                MessageBanner(stringResource(R.string.session_expired_message))
            }
            when (logoutState) {
                LogoutUiState.None -> Unit
                is LogoutUiState.Pending -> MessageBanner(stringResource(R.string.logout_pending_message))
                is LogoutUiState.Complete -> when {
                    !logoutState.result.localCleared -> {
                        MessageBanner(stringResource(R.string.logout_cleanup_failed), isError = true)
                        TextActionButton(stringResource(R.string.logout_cleanup_retry), onRetryCleanup)
                    }
                    !logoutState.result.serverRevoked -> MessageBanner(stringResource(R.string.logout_local_message))
                    else -> Unit
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                    passwordHint = register,
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
            }
            TextActionButton(stringResource(if (register) R.string.go_to_login else R.string.go_to_register),
                onSwitchForm, Modifier.fillMaxWidth(), enabled = !state.submitting)
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
    passwordHint: Boolean = false,
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
        helper = if (passwordHint) stringResource(R.string.password_helper) else null,
        labelAbove = true,
        placeholder = stringResource(when (field) {
            AccountField.NAME -> R.string.account_name_placeholder
            AccountField.EMAIL -> R.string.account_email_placeholder
            AccountField.PASSWORD -> R.string.account_password_placeholder
            AccountField.PASSWORD_CONFIRMATION -> R.string.account_confirmation_placeholder
        }),
        autofillType = contentType,
        password = field == AccountField.PASSWORD || field == AccountField.PASSWORD_CONFIRMATION,
        enabled = !state.submitting,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        modifier = modifier
            .focusRequester(requester),
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
