package com.example.wastenot.model

import java.net.IDN
import java.time.ZoneId
import java.util.Locale

enum class AccountField { NAME, EMAIL, PASSWORD, PASSWORD_CONFIRMATION }
enum class AccountInputError { NAME_LENGTH, EMAIL_FORMAT, PASSWORD_LENGTH, PASSWORD_MISMATCH }

private val emailLocalPart = Regex("[A-Za-z0-9!#$%&'*+/=?^_`{|}~.-]+")
private val domainLabel = Regex("[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?")

fun normalizeAccountEmail(rawEmail: String): String = rawEmail.trim().lowercase(Locale.ROOT)

// shortcut: quoted/SMTPUTF8 local parts await the B03 account-email contract.
private fun hasValidEmailFormat(email: String): Boolean {
    val separator = email.indexOf('@')
    if (separator <= 0 || separator != email.lastIndexOf('@')) return false
    val local = email.substring(0, separator)
    if (local.length > 64 || !emailLocalPart.matches(local) ||
        local.startsWith('.') || local.endsWith('.') || ".." in local) return false
    val domain = try {
        IDN.toASCII(email.substring(separator + 1), IDN.USE_STD3_ASCII_RULES)
    } catch (_: IllegalArgumentException) {
        return false
    }
    val labels = domain.split('.')
    return local.length + 1 + domain.length <= 254 && labels.size >= 2 &&
        labels.all { domainLabel.matches(it) } && labels.last().any { it.isLetter() }
}

fun validateLoginInput(email: String, password: String): Map<AccountField, AccountInputError> = buildMap {
    val normalizedEmail = normalizeAccountEmail(email)
    if (normalizedEmail.length > 254 || !hasValidEmailFormat(normalizedEmail)) {
        put(AccountField.EMAIL, AccountInputError.EMAIL_FORMAT)
    }
    if (password.codePointCount(0, password.length) !in 12..128) {
        put(AccountField.PASSWORD, AccountInputError.PASSWORD_LENGTH)
    }
}

fun validateRegistrationInput(
    name: String, email: String, password: String, confirmation: String,
): Map<AccountField, AccountInputError> = buildMap {
    val trimmedName = name.trim()
    if (trimmedName.codePointCount(0, trimmedName.length) !in 2..60) {
        put(AccountField.NAME, AccountInputError.NAME_LENGTH)
    }
    putAll(validateLoginInput(email, password))
    if (password != confirmation) {
        put(AccountField.PASSWORD_CONFIRMATION, AccountInputError.PASSWORD_MISMATCH)
    }
}

fun initialAccountTimezone(deviceTimezoneId: String): ZoneId =
    ZoneId.of(if (deviceTimezoneId in ZoneId.getAvailableZoneIds()) deviceTimezoneId else "Asia/Jakarta")
