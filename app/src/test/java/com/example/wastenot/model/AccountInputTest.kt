package com.example.wastenot.model

import java.util.Locale
import com.example.wastenot.data.network.dto.UserDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class AccountInputTest {
    private val email = "fixture@example.com"
    private val password = "fixture-password"

    @Test
    fun loginAcceptsTrimmedEmailAndPasswordLengthBoundaries() {
        listOf(12, 128).forEach { length ->
            assertTrue(validateLoginInput(" FIXTURE+tag@EXAMPLE.COM ", "a".repeat(length)).isEmpty())
        }
        listOf(0, 11, 129).forEach { length ->
            assertEquals(mapOf(AccountField.PASSWORD to AccountInputError.PASSWORD_LENGTH),
                validateLoginInput(email, "a".repeat(length)))
        }
    }

    @Test
    fun rejectsEmptyMalformedAndInternallySpacedEmails() {
        listOf("", " \n", "fixture", "@example.com", "fixture@", "fixture@example",
            "fixture @example.com", "fixture@example .com", "fixture\n@example.com", "a@@example.com",
            ".fixture@example.com", "fixture.@example.com", "a..b@example.com", "a@-example.com",
            "a@example-.com", "a@example..com", "a@127.0.0.1", "a".repeat(65) + "@example.com",
            "a@" + "b".repeat(64) + ".com")
            .forEach { input ->
                assertEquals(input, mapOf(AccountField.EMAIL to AccountInputError.EMAIL_FORMAT),
                    validateLoginInput(input, password))
            }
        assertTrue(validateLoginInput("fixture+tag@sub.example.technology", password).isEmpty())
        assertTrue(validateLoginInput("fixture@bücher.de", password).isEmpty())
        assertTrue(validateLoginInput("o'connor@example.com", password).isEmpty())
    }

    @Test
    fun emailLimitIsAppliedAfterOuterWhitespaceIsRemoved() {
        val prefix = "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "."
        val boundary = prefix + "d".repeat(57) + ".com"
        assertEquals(254, boundary.length)
        assertTrue(validateLoginInput(" $boundary ", password).isEmpty())
        assertEquals(AccountInputError.EMAIL_FORMAT,
            validateLoginInput(prefix + "d".repeat(58) + ".com", password)[AccountField.EMAIL])
    }

    @Test
    fun emailNormalizationDoesNotDependOnTheDeviceLanguage() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("i+tag@example.com", normalizeAccountEmail(" I+TAG@EXAMPLE.COM "))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun nameLengthUsesTrimmedUnicodeCharacters() {
        listOf("Ab", "a".repeat(60), "😀😀", "😀".repeat(60)).forEach { name ->
            assertTrue(validateRegistrationInput(" $name ", email, password, password).isEmpty())
        }
        listOf("", "  ", "A", "a".repeat(61), "😀", "😀".repeat(61)).forEach { name ->
            assertEquals(AccountInputError.NAME_LENGTH,
                validateRegistrationInput(name, email, password, password)[AccountField.NAME])
        }
    }

    @Test
    fun registrationNameBoundaryAlsoFitsTheReturnedProfile() {
        val name = "😀".repeat(60)
        assertTrue(validateRegistrationInput(name, email, password, password).isEmpty())
        val dto = UserDto("00000000-0000-4000-8000-000000000001", name, email, "Asia/Jakarta")
        assertEquals(name, dto.toModel().name)
        assertThrows(IllegalArgumentException::class.java) { dto.copy(name = name + "😀").toModel() }
    }

    @Test
    fun passwordLengthCountsUnicodeCharactersWithoutTrimming() {
        listOf(12, 128).forEach { length ->
            assertTrue(validateRegistrationInput("Name", email, "😀".repeat(length), "😀".repeat(length)).isEmpty())
        }
        listOf(11, 129).forEach { length ->
            assertEquals(AccountInputError.PASSWORD_LENGTH,
                validateLoginInput(email, "😀".repeat(length))[AccountField.PASSWORD])
        }
        assertTrue(validateLoginInput(email, " ".repeat(12)).isEmpty())
        assertTrue(validateLoginInput(email, " " + "a".repeat(10) + " ").isEmpty())
    }

    @Test
    fun passwordConfirmationRequiresExactEqualityIncludingWhitespace() {
        val raw = " $password "
        assertTrue(validateRegistrationInput("Name", email, raw, raw).isEmpty())
        listOf(password, "$raw ", "", raw.uppercase()).forEach { confirmation ->
            assertEquals(mapOf(AccountField.PASSWORD_CONFIRMATION to AccountInputError.PASSWORD_MISMATCH),
                validateRegistrationInput("Name", email, raw, confirmation))
        }
    }

    @Test
    fun reportsAllInvalidFieldsWithoutReturningTheirValues() {
        val errors = validateRegistrationInput("X", "private-email", "private-pw", "private-confirmation")
        assertEquals(mapOf(AccountField.NAME to AccountInputError.NAME_LENGTH,
            AccountField.EMAIL to AccountInputError.EMAIL_FORMAT,
            AccountField.PASSWORD to AccountInputError.PASSWORD_LENGTH,
            AccountField.PASSWORD_CONFIRMATION to AccountInputError.PASSWORD_MISMATCH), errors)
        listOf("private-email", "private-pw", "private-confirmation").forEach {
            assertFalse(errors.toString().contains(it))
        }
    }

    @Test
    fun registrationUsesAnAvailableDeviceTimezone() {
        listOf("Asia/Jakarta", "Asia/Makassar", "Asia/Jayapura", "Etc/UTC", "Europe/London").forEach {
            assertEquals(it, initialAccountTimezone(it).id)
        }
    }

    @Test
    fun unsupportedDeviceTimezoneFallsBackToJakarta() {
        listOf("", "not/a-zone", "+07:00", "GMT+07:00").forEach {
            assertEquals("Asia/Jakarta", initialAccountTimezone(it).id)
        }
    }
}
