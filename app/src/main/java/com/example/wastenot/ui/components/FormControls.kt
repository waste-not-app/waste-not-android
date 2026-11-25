package com.example.wastenot.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
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
import com.example.wastenot.theme.ActionHighlight
import com.example.wastenot.theme.LocalWasteNotSurfaceTokens
import com.example.wastenot.theme.ActionShadow

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
    val view = LocalView.current
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    Button(
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onClick()
        },
        modifier = modifier
            .heightIn(min = 52.dp)
            .focusOutline(actionable && focused, MaterialTheme.shapes.small, colors.primary, colors.surface)
            .then(if (actionable && !destructive) Modifier
                .dropShadow(MaterialTheme.shapes.small, Shadow(12.dp, ActionShadow, spread = (-9).dp, offset = DpOffset(0.dp, 5.dp)))
                .background(LocalWasteNotGradients.current.action, MaterialTheme.shapes.small)
                .innerShadow(MaterialTheme.shapes.small, Shadow(0.dp, ActionHighlight, offset = DpOffset(0.dp, 1.dp)))
            else Modifier),
        enabled = actionable,
        interactionSource = interactions,
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
                Modifier.weight(1f, fill = false).semantics { liveRegion = LiveRegionMode.Polite }, textAlign = TextAlign.Center)
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
    val fieldShape = MaterialTheme.shapes.small
    val field: @Composable (Modifier) -> Unit = { placement ->
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = if (labelAbove) null else ({ Text(label) }),
            placeholder = if (placeholder != null) ({ Text(placeholder) }) else null,
            modifier = placement
                .heightIn(min = 54.dp)
                .semantics {
                    if (labelAbove) contentDescription = label
                    if (autofillType != null) contentType = autofillType
                    if (errorText != null) error(errorText)
                },
            shape = fieldShape,
            colors = wasteTextFieldColors(),
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

@Composable
internal fun wasteTextFieldColors(): TextFieldColors {
    val colors = MaterialTheme.colorScheme
    val tokens = LocalWasteNotSurfaceTokens.current
    return OutlinedTextFieldDefaults.colors(
        focusedContainerColor = tokens.fieldWellFocus,
        unfocusedContainerColor = tokens.fieldWell,
        errorContainerColor = tokens.fieldWellFocus,
        focusedBorderColor = colors.primary,
        unfocusedBorderColor = tokens.fieldEdge,
        disabledContainerColor = colors.surfaceVariant,
        disabledTextColor = colors.onSurfaceVariant,
        disabledBorderColor = colors.outlineVariant,
        disabledPlaceholderColor = colors.onSurfaceVariant,
        disabledLabelColor = colors.onSurfaceVariant,
        disabledSupportingTextColor = colors.onSurfaceVariant,
        disabledTrailingIconColor = colors.onSurfaceVariant,
    )
}
