package com.example.wastenot.ui.auth

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.Lifecycle
import com.example.wastenot.BuildConfig
import com.example.wastenot.R
import com.example.wastenot.data.network.ApiFailure
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.data.network.ApiResult
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal sealed interface GoogleSelection {
    data object Canceled : GoogleSelection
    class Failure(val message: Int) : GoogleSelection
    class Selected(val idToken: String, val accountId: String) : GoogleSelection {
        override fun toString() = "GoogleSelection.Selected([redacted])"
    }
}

@Composable
internal fun rememberGoogleAction(
    begin: () -> Long?,
    requestChallenge: suspend () -> ApiResult<String>,
    complete: (Long, GoogleSelection) -> Unit,
): () -> Unit {
    val activity = LocalActivity.current as? ComponentActivity
    val scope = rememberCoroutineScope()
    return action@{
        if (activity == null || !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@action
        scope.launch {
            val attempt = begin() ?: return@launch
            var selection: GoogleSelection = GoogleSelection.Canceled
            try {
                selection = when (val challenge = requestChallenge()) {
                    is ApiResult.Success -> if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        selectGoogleCredential(activity, challenge.value)
                    } else GoogleSelection.Canceled
                    is ApiResult.Failure -> GoogleSelection.Failure(challenge.error.googleMessage())
                }
            } finally {
                complete(attempt, if (isActive) selection else GoogleSelection.Canceled)
            }
        }
    }
}

private suspend fun selectGoogleCredential(activity: ComponentActivity, nonce: String): GoogleSelection = try {
    val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).setNonce(nonce).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
    val credential = CredentialManager.create(activity).getCredential(activity, request).credential
    if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val tokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
        GoogleSelection.Selected(tokenCredential.idToken, tokenCredential.uniqueId)
    } else GoogleSelection.Failure(R.string.google_auth_failed)
} catch (_: GetCredentialCancellationException) {
    GoogleSelection.Canceled
} catch (_: NoCredentialException) {
    GoogleSelection.Failure(R.string.google_auth_no_account)
} catch (_: GetCredentialProviderConfigurationException) {
    GoogleSelection.Failure(R.string.google_auth_unavailable)
} catch (_: GetCredentialUnsupportedException) {
    GoogleSelection.Failure(R.string.google_auth_unavailable)
} catch (_: GetCredentialException) {
    GoogleSelection.Failure(R.string.google_auth_failed)
} catch (_: GoogleIdTokenParsingException) {
    GoogleSelection.Failure(R.string.google_auth_failed)
}

internal suspend fun clearGoogleCredentials(context: Context) {
    try {
        CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
    } catch (_: ClearCredentialException) {
        // Provider state must not prevent WasteNot sign-out.
    }
}

internal fun ApiFailure.googleMessage(linking: Boolean = false): Int = when {
    code == "GOOGLE_AUTH_UNAVAILABLE" || kind == ApiFailureKind.NOT_CONFIGURED -> R.string.google_auth_unavailable
    code == "GOOGLE_IDENTITY_CONFLICT" -> R.string.google_auth_identity_conflict
    code == "GOOGLE_REAUTH_FAILED" -> R.string.google_auth_wrong_account
    kind == ApiFailureKind.UNAUTHENTICATED && linking -> R.string.google_auth_link_failed
    kind == ApiFailureKind.UNAUTHENTICATED -> R.string.google_auth_expired
    kind == ApiFailureKind.RATE_LIMITED -> R.string.google_auth_rate_limited
    kind == ApiFailureKind.NETWORK || kind == ApiFailureKind.TIMEOUT -> R.string.google_auth_network
    else -> R.string.google_auth_failed
}
