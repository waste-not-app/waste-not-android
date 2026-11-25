package com.example.wastenot.ui.auth

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.wastenot.R
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.data.repository.SignOutReason
import com.example.wastenot.model.AccountField
import com.example.wastenot.model.AccountInputError
import com.example.wastenot.ui.components.ActionButton
import com.example.wastenot.ui.components.TextActionButton
import com.example.wastenot.ui.components.IconActionButton
import com.example.wastenot.ui.components.InsetGroupCard
import com.example.wastenot.ui.components.WasteTextField
import com.example.wastenot.theme.WasteNotWordmark
import com.example.wastenot.theme.WasteNotAuthHeading
import kotlinx.coroutines.delay

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
    onPasswordRecovery: (() -> Unit)? = null,
    onPrivacy: (() -> Unit)? = null,
    onGoogle: (() -> Unit)? = null,
    onGoogleLinkPassword: (String) -> Unit = {},
    onDismissGoogleLink: () -> Unit = {},
) {
    val focus = LocalFocusManager.current
    val view = LocalView.current
    val requesters = remember { AccountField.entries.associateWith { FocusRequester() } }
    val configuration = LocalConfiguration.current
    val landscapeLogin = !register &&
        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
        configuration.screenWidthDp >= 600 && configuration.fontScale < 1.5f
    val submitRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(state.focusRequest) {
        val first = state.errors.keys.firstOrNull() ?: state.serverFields.firstOrNull()
        if (state.focusRequest <= 0 || first == null) return@LaunchedEffect
        if (landscapeLogin && first == AccountField.PASSWORD) {
            // Make room for validation and the retry action in the short landscape viewport.
            focus.clearFocus()
            // Android can enqueue another IME show while replacing the active text input.
            delay(120)
            ViewCompat.getWindowInsetsController(view)?.hide(WindowInsetsCompat.Type.ime())
            submitRequester.bringIntoView()
        } else {
            requesters.getValue(first).requestFocus()
        }
    }
    val next = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) }, onDone = { onSubmit() })
    val passwordField: @Composable (Modifier) -> Unit = { placement ->
        AuthField(
            AccountField.PASSWORD, state, requesters.getValue(AccountField.PASSWORD), onChange,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = if (register) ImeAction.Next else ImeAction.Done),
            keyboardActions = next, contentType = if (register) ContentType.NewPassword else ContentType.Password,
            modifier = placement, passwordHint = register,
        )
    }

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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painter = painterResource(R.drawable.wastenot_brand_icon),
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                    )
                    Text(
                        text = stringResource(R.string.app_name),
                        modifier = Modifier.weight(1f, fill = false),
                        style = WasteNotWordmark,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onBack != null) IconActionButton(R.drawable.ic_back, stringResource(R.string.back), onBack)
                    IconActionButton(R.drawable.ic_device, stringResource(R.string.theme_title), onTheme)
                }
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
            if (logoutState == LogoutUiState.None && state.failure == null && reason !in setOf(
                    SignOutReason.NONE, SignOutReason.LOGOUT_CONFIRMED, SignOutReason.LOGOUT_LOCAL_ONLY,
                    SignOutReason.ACCOUNT_DELETED, SignOutReason.ACCOUNT_DELETION_PENDING, SignOutReason.DELETION_CLEANUP_FAILED)) {
                MessageBanner(stringResource(R.string.session_expired_message))
            }
            when (reason) {
                SignOutReason.ACCOUNT_DELETED -> MessageBanner(stringResource(R.string.delete_account_success))
                SignOutReason.ACCOUNT_DELETION_PENDING -> MessageBanner(stringResource(R.string.delete_account_cleanup_pending))
                SignOutReason.DELETION_CLEANUP_FAILED -> MessageBanner(stringResource(R.string.delete_account_local_cleanup_failed), isError = true)
                else -> Unit
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
            InsetGroupCard(contentPadding = true) {
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
                if (landscapeLogin) {
                    state.failure?.let { MessageBanner(stringResource(it.messageResource), isError = true) }
                    Row(
                        modifier = Modifier.fillMaxWidth().semantics { isTraversalGroup = true },
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        passwordField(Modifier.weight(1f).semantics { isTraversalGroup = true })
                        AuthSubmit(false, state, onSubmit,
                            Modifier.widthIn(min = 128.dp, max = 160.dp)
                                .bringIntoViewRequester(submitRequester)
                                .semantics { traversalIndex = 1f })
                    }
                } else {
                    passwordField(Modifier.fillMaxWidth())
                }
                if (register) {
                    AuthField(
                        AccountField.PASSWORD_CONFIRMATION, state, requesters.getValue(AccountField.PASSWORD_CONFIRMATION), onChange,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = next, contentType = ContentType.NewPassword, modifier = Modifier.fillMaxWidth(),
                    )
                    Text(stringResource(R.string.account_timezone, state.timezone.id), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!landscapeLogin) {
                    state.failure?.let { MessageBanner(stringResource(it.messageResource), isError = true) }
                    AuthSubmit(register, state, onSubmit, Modifier.fillMaxWidth())
                }
            }
            if (onGoogle != null) {
                if (!state.googleLinkRequired) state.googleFailure?.let { MessageBanner(stringResource(it), isError = true) }
                GoogleSignInButton(
                    onClick = { focus.clearFocus(); onGoogle() },
                    enabled = !state.submitting && logoutState !is LogoutUiState.Pending,
                    loading = state.googleSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Column(Modifier.fillMaxWidth()) {
                if (!register && onPasswordRecovery != null) TextActionButton(
                    stringResource(R.string.password_recovery_open), onPasswordRecovery,
                    Modifier.fillMaxWidth(), enabled = !state.submitting,
                )
                TextActionButton(stringResource(if (register) R.string.go_to_login else R.string.go_to_register),
                    onSwitchForm, Modifier.fillMaxWidth(), enabled = !state.submitting)
                if (onPrivacy != null) TextActionButton(stringResource(R.string.privacy_policy_open), onPrivacy, Modifier.fillMaxWidth())
            }
        }
    }
    if (onGoogle != null && state.googleLinkRequired) {
        GoogleLinkDialog(state, onGoogleLinkPassword, onGoogle, onDismissGoogleLink)
    }
}

@Composable
private fun AuthSubmit(register: Boolean, state: AuthUiState, onSubmit: () -> Unit, modifier: Modifier) {
    ActionButton(
        label = stringResource(if (register) R.string.register_action else R.string.login_action),
        onClick = onSubmit,
        enabled = !state.googleSubmitting,
        loading = state.submitting && !state.googleSubmitting,
        loadingLabel = stringResource(if (register) R.string.register_loading else R.string.login_loading),
        modifier = modifier,
    )
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
