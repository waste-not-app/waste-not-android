package com.example.wastenot.data.network.dto

import kotlinx.serialization.Serializable

@Serializable
class PasswordResetRequestDto(val email: String) {
    override fun toString() = "PasswordResetRequestDto([redacted])"
}

@Serializable
class PasswordResetConfirmationDto(val token: String, val password: String) {
    override fun toString() = "PasswordResetConfirmationDto([redacted])"
}

@Serializable
class DeleteAccountDto(val password: String) {
    override fun toString() = "DeleteAccountDto([redacted])"
}

@Serializable
data class PasswordResetAcknowledgementDto(val message: String)
