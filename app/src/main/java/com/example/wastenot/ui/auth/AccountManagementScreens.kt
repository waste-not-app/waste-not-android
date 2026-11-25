package com.example.wastenot.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.ui.platform.LocalLayoutDirection
import com.example.wastenot.theme.LocalWasteNotScaffoldPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.wastenot.R
import com.example.wastenot.ui.components.ActionButton
import com.example.wastenot.ui.components.IconActionButton
import com.example.wastenot.ui.components.InsetGroupCard
import com.example.wastenot.ui.components.TextActionButton
import com.example.wastenot.ui.components.WasteTextField
import com.example.wastenot.ui.components.rememberPrivacyPolicyAction

@Composable
fun PasswordRecoveryScreen(
    state: PasswordRecoveryState,
    onChange: (RecoveryField, String) -> Unit,
    onSubmit: () -> Unit,
    onAnotherRequest: () -> Unit,
    onExistingCode: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = LocalFocusManager.current
    val next = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) }, onDone = { onSubmit() })
    val onPrivacy = rememberPrivacyPolicyAction()
    Box(modifier, contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconActionButton(R.drawable.ic_back, stringResource(R.string.back), onBack, enabled = !state.submitting)
                Text(stringResource(R.string.password_recovery_title), style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f).semantics { heading() })
            }
            when (state.phase) {
                RecoveryPhase.REQUEST -> {
                    Text(stringResource(R.string.password_recovery_description))
                    WasteTextField(state.email, { onChange(RecoveryField.EMAIL, it) }, stringResource(R.string.account_email),
                        modifier = Modifier.fillMaxWidth(), enabled = !state.submitting, labelAbove = true,
                        errorText = state.errors[RecoveryField.EMAIL]?.let { stringResource(it) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                        keyboardActions = next, autofillType = ContentType.EmailAddress)
                }
                RecoveryPhase.CONFIRM -> {
                    if (state.requestAccepted) MessageBanner(stringResource(R.string.password_recovery_requested))
                    InsetGroupCard(contentPadding = true) {
                        WasteTextField(state.token, { onChange(RecoveryField.TOKEN, it) }, stringResource(R.string.password_recovery_token),
                            modifier = Modifier.fillMaxWidth(), enabled = !state.submitting, labelAbove = true,
                            helper = stringResource(R.string.password_recovery_token_helper),
                            errorText = state.errors[RecoveryField.TOKEN]?.let { stringResource(it) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next), keyboardActions = next)
                        WasteTextField(state.password, { onChange(RecoveryField.PASSWORD, it) }, stringResource(R.string.password_recovery_new_password),
                            modifier = Modifier.fillMaxWidth(), enabled = !state.submitting, password = true, labelAbove = true,
                            helper = stringResource(R.string.password_helper),
                            errorText = state.errors[RecoveryField.PASSWORD]?.let { stringResource(it) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                            keyboardActions = next, autofillType = ContentType.NewPassword)
                        WasteTextField(state.confirmation, { onChange(RecoveryField.CONFIRMATION, it) }, stringResource(R.string.password_recovery_confirm_password),
                            modifier = Modifier.fillMaxWidth(), enabled = !state.submitting, password = true, labelAbove = true,
                            errorText = state.errors[RecoveryField.CONFIRMATION]?.let { stringResource(it) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            keyboardActions = next, autofillType = ContentType.NewPassword)
                    }
                }
                RecoveryPhase.COMPLETE -> {
                    MessageBanner(stringResource(R.string.password_recovery_success))
                    ActionButton(stringResource(R.string.go_to_login), onBack, Modifier.fillMaxWidth())
                }
            }
            state.failure?.let { MessageBanner(stringResource(it), isError = true) }
            if (state.phase != RecoveryPhase.COMPLETE) {
                ActionButton(stringResource(if (state.phase == RecoveryPhase.REQUEST) R.string.password_recovery_request else R.string.password_recovery_confirm),
                    onSubmit, Modifier.fillMaxWidth(), loading = state.submitting,
                    loadingLabel = stringResource(R.string.account_management_processing))
                if (state.phase == RecoveryPhase.CONFIRM) TextActionButton(stringResource(R.string.password_recovery_another),
                    onAnotherRequest, Modifier.fillMaxWidth(), enabled = !state.submitting)
                else TextActionButton(stringResource(R.string.password_recovery_existing), onExistingCode,
                    Modifier.fillMaxWidth(), enabled = !state.submitting)
            }
            TextActionButton(stringResource(R.string.privacy_policy_open), onPrivacy, enabled = !state.submitting)
        }
    }
}

@Composable
fun AccountDeletionScreen(
    state: AccountDeletionState,
    padding: PaddingValues,
    onPassword: (String) -> Unit,
    onAcknowledge: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onPrivacy: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    hasPassword: Boolean = true,
    googleAvailable: Boolean = false,
) {
    val scaffold = LocalWasteNotScaffoldPadding.current
    val direction = LocalLayoutDirection.current
    val contentPadding = PaddingValues(
        start = padding.calculateStartPadding(direction) + scaffold.calculateStartPadding(direction) + 20.dp,
        top = padding.calculateTopPadding() + scaffold.calculateTopPadding() + 16.dp,
        end = padding.calculateEndPadding(direction) + scaffold.calculateEndPadding(direction) + 20.dp,
        bottom = padding.calculateBottomPadding() + scaffold.calculateBottomPadding() + 24.dp,
    )
    Box(modifier.consumeWindowInsets(padding).consumeWindowInsets(scaffold), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth()
                .imePadding().verticalScroll(rememberScrollState()).padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(stringResource(R.string.delete_account_title), style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() })
            MessageBanner(stringResource(R.string.delete_account_warning), isError = true)
            Text(stringResource(R.string.delete_account_backup_notice), style = MaterialTheme.typography.bodyMedium)
            TextActionButton(stringResource(R.string.privacy_policy_open), onPrivacy, enabled = !state.submitting)
            if (hasPassword) {
                WasteTextField(state.password, onPassword, stringResource(R.string.delete_account_password),
                    modifier = Modifier.fillMaxWidth(), enabled = !state.submitting, password = true, labelAbove = true,
                    errorText = state.passwordError?.let { stringResource(it) }, autofillType = ContentType.Password,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done))
            } else {
                Text(stringResource(if (googleAvailable) R.string.delete_account_google_description else
                    R.string.delete_account_google_unavailable), style = MaterialTheme.typography.bodyMedium)
            }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(state.acknowledged,
                    enabled = !state.submitting, role = Role.Checkbox, onValueChange = onAcknowledge),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = state.acknowledged, onCheckedChange = null, enabled = !state.submitting)
                Text(stringResource(R.string.delete_account_acknowledge), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            }
            state.failure?.let { MessageBanner(stringResource(it), isError = true) }
            ActionButton(stringResource(if (hasPassword) R.string.delete_account_confirm else R.string.delete_account_google_confirm),
                onDelete, Modifier.fillMaxWidth(),
                enabled = state.acknowledged && (hasPassword || googleAvailable), destructive = true, loading = state.submitting,
                loadingLabel = stringResource(R.string.account_management_processing))
            TextActionButton(stringResource(R.string.back), onBack, Modifier.fillMaxWidth(), enabled = !state.submitting)
        }
    }
}
