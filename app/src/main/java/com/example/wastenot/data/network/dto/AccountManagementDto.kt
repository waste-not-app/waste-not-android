package com.example.wastenot.data.network.dto

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class PasswordResetRequestDto(val email: String) {
    override fun toString() = "PasswordResetRequestDto([redacted])"
}

@Serializable
class PasswordResetConfirmationDto(val token: String, val password: String) {
    override fun toString() = "PasswordResetConfirmationDto([redacted])"
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
class DeleteAccountDto(
    @EncodeDefault(EncodeDefault.Mode.NEVER) val password: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("google_id_token") val googleIdToken: String? = null,
) {
    init { require((password == null) != (googleIdToken == null)) }

    override fun toString() = "DeleteAccountDto([redacted])"
}

@Serializable
data class PasswordResetAcknowledgementDto(val message: String)
