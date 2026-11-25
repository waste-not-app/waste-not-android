package com.example.wastenot.data.repository

import com.example.wastenot.data.network.ApiFailure
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.network.WasteNotNetwork
import com.example.wastenot.data.network.dto.PasswordResetConfirmationDto
import com.example.wastenot.data.network.dto.PasswordResetRequestDto
import com.example.wastenot.model.normalizeAccountEmail

interface AccountManagement {
    suspend fun requestPasswordReset(email: String): ApiResult<Unit>
    suspend fun confirmPasswordReset(token: String, password: String): ApiResult<Unit>
    suspend fun deleteAccount(password: String): ApiResult<Unit>
    suspend fun googleChallenge(): ApiResult<String> = googleNotConfigured()
    suspend fun deleteGoogleAccount(idToken: String): ApiResult<Unit> = googleNotConfigured()
}

class RemoteAccountManagement(
    private val network: WasteNotNetwork?,
    private val sessions: SessionRepository,
    private val clearDeletedAccount: suspend (owner: String) -> Unit,
) : AccountManagement {
    override suspend fun requestPasswordReset(email: String): ApiResult<Unit> {
        val connection = network ?: return unavailable()
        return connection.executor.execute({ Unit }) {
            connection.api.requestPasswordReset(PasswordResetRequestDto(normalizeAccountEmail(email)))
        }
    }

    override suspend fun confirmPasswordReset(token: String, password: String): ApiResult<Unit> {
        val connection = network ?: return unavailable()
        return connection.executor.executeEmpty {
            connection.api.confirmPasswordReset(PasswordResetConfirmationDto(token.trim(), password))
        }
    }

    override suspend fun deleteAccount(password: String) = sessions.deleteAccount(password, clearDeletedAccount)
    override suspend fun googleChallenge() = sessions.googleChallenge()
    override suspend fun deleteGoogleAccount(idToken: String) = sessions.deleteGoogleAccount(idToken, clearDeletedAccount)

    private fun unavailable() = ApiResult.Failure(ApiFailure(ApiFailureKind.NOT_CONFIGURED))
}
