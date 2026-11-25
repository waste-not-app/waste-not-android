package com.example.wastenot.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.wastenot.R
import com.example.wastenot.ui.components.WasteTextField
import com.example.wastenot.ui.components.focusOutline

@Composable
internal fun GoogleSignInButton(
    onClick: () -> Unit,
    enabled: Boolean,
    loading: Boolean,
    modifier: Modifier = Modifier,
) {
    val background = colorResource(R.color.google_sign_in_background)
    val foreground = colorResource(R.color.google_sign_in_foreground)
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val shape = RoundedCornerShape(4.dp)
    val actionable = enabled && !loading
    OutlinedButton(
        onClick = onClick,
        enabled = actionable,
        interactionSource = interactions,
        modifier = modifier.heightIn(min = 48.dp).focusOutline(actionable && focused, shape, foreground, background),
        shape = shape,
        border = BorderStroke(1.dp, colorResource(R.color.google_sign_in_border)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = background,
            contentColor = foreground,
            disabledContainerColor = background,
            disabledContentColor = foreground.copy(alpha = 0.7f),
        ),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Image(painterResource(R.drawable.ic_google), contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(stringResource(if (loading) R.string.google_auth_loading else R.string.google_auth_continue),
            Modifier.weight(1f, fill = false).semantics { liveRegion = LiveRegionMode.Polite }, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun GoogleLinkDialog(
    state: AuthUiState,
    onPassword: (String) -> Unit,
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
) {
    val canContinue = !state.submitting && state.googleLinkPassword.isNotEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.google_auth_link_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.google_auth_link_description), style = MaterialTheme.typography.bodyMedium)
                WasteTextField(
                    state.googleLinkPassword, onPassword, stringResource(R.string.google_auth_link_password),
                    modifier = Modifier.fillMaxWidth(), enabled = !state.submitting, password = true, labelAbove = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (canContinue) onContinue() }),
                )
                state.googleFailure?.let { MessageBanner(stringResource(it), isError = true) }
            }
        },
        confirmButton = { GoogleSignInButton(onContinue, canContinue, state.googleSubmitting) },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.submitting) { Text(stringResource(R.string.back)) }
        },
    )
}
