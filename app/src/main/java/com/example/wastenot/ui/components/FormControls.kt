package com.example.wastenot.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.wastenot.R
import com.example.wastenot.theme.LocalWasteNotGradients

@Composable
fun ActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    loadingLabel: String = label,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val actionable = enabled && !loading
    val colors = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 52.dp)
            .then(if (actionable && !destructive) Modifier.background(LocalWasteNotGradients.current.action, MaterialTheme.shapes.small) else Modifier),
        enabled = actionable,
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (destructive) colors.error else Color.Transparent,
            contentColor = if (destructive) colors.onError else colors.onPrimary,
            disabledContainerColor = colors.surfaceContainer,
            disabledContentColor = colors.onSurfaceVariant,
        ),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = LocalContentColor.current, strokeWidth = 2.dp)
            Text(if (loading) loadingLabel else label,
                Modifier.weight(1f, fill = false).width(IntrinsicSize.Max).semantics { liveRegion = LiveRegionMode.Polite }, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun WasteTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    helper: String? = null,
    errorText: String? = null,
    enabled: Boolean = true,
    password: Boolean = false,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    labelAbove: Boolean = false,
    placeholder: String? = null,
    autofillType: ContentType? = null,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val support = errorText ?: helper
    val field: @Composable (Modifier) -> Unit = { placement ->
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = if (labelAbove) null else ({ Text(label) }),
            placeholder = if (placeholder != null) ({ Text(placeholder) }) else null,
            modifier = placement
                .heightIn(min = 56.dp)
                .semantics {
                    if (labelAbove) contentDescription = label
                    if (autofillType != null) contentType = autofillType
                    if (errorText != null) error(errorText)
                },
            shape = MaterialTheme.shapes.small,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
                errorContainerColor = colors.surface,
                disabledContainerColor = colors.surfaceContainer,
                disabledTextColor = colors.onSurfaceVariant,
                disabledBorderColor = colors.outline,
                disabledPlaceholderColor = colors.onSurfaceVariant,
                disabledLabelColor = colors.onSurfaceVariant,
                disabledSupportingTextColor = colors.onSurfaceVariant,
                disabledTrailingIconColor = colors.onSurfaceVariant,
            ),
            enabled = enabled,
            isError = errorText != null,
            singleLine = singleLine,
            minLines = if (singleLine) 1 else 3,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            supportingText = if (!labelAbove && support != null) ({
                Text(support, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }) else null,
            visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
            trailingIcon = if (password) ({
                IconButton(onClick = { visible = !visible }, enabled = enabled,
                    colors = IconButtonDefaults.iconButtonColors(disabledContentColor = colors.onSurfaceVariant)) {
                    Icon(
                        painterResource(if (visible) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                        stringResource(if (visible) R.string.hide_password else R.string.show_password),
                    )
                }
            }) else null,
        )
    }
    if (labelAbove) Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        field(Modifier.fillMaxWidth())
        if (support != null) Text(support, style = MaterialTheme.typography.bodySmall,
            color = if (errorText != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    } else field(modifier)
}
