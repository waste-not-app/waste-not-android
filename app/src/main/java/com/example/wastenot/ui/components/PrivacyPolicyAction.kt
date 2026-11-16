package com.example.wastenot.ui.components

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import com.example.wastenot.R

@Composable
fun rememberPrivacyPolicyAction(): () -> Unit {
    val handler = LocalUriHandler.current
    val context = LocalContext.current
    val unavailable = stringResource(R.string.external_link_unavailable)
    return remember(handler, context, unavailable) {
        {
            runCatching { handler.openUri("https://waste-not.bccdev.id/privacy/") }
                .onFailure { Toast.makeText(context, unavailable, Toast.LENGTH_LONG).show() }
            Unit
        }
    }
}
