package com.example.wastenot.ui.main

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.example.wastenot.MainNavigation
import com.example.wastenot.data.network.*
import com.example.wastenot.data.repository.*
import com.example.wastenot.model.User
import com.example.wastenot.theme.ThemeMode
import com.example.wastenot.theme.WasteNotTheme
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

class SessionRootTest {
    @get:Rule val compose = createComposeRule()
    private val tabs = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
    private fun start(source: FixtureSession) {
        compose.setContent { WasteNotTheme(darkTheme = false) {
            MainNavigation(source, CoreRepository(null), com.example.wastenot.data.dummy.DummyGuideStore(), ThemeMode.LIGHT, {}, Modifier.fillMaxSize())
        } }
        compose.waitForIdle()
    }

    @Test fun unverifiedIdentityHidesOldProfileAndRetryReplacesRootOnce() {
        val source = FixtureSession().apply { state.value = SessionState.Authenticated(user, 1) }
        start(source)
        compose.onNode(tabs and hasText("Profil")).performClick()
        compose.onNodeWithText("Fixture A").assertIsDisplayed()
        compose.runOnIdle { source.state.value = SessionState.ConnectionRequired(ApiFailure(ApiFailureKind.NETWORK)) }
        compose.onNodeWithText("Belum bisa memeriksa sesi").assertIsDisplayed()
        compose.onNodeWithText("Fixture A").assertDoesNotExist()
        compose.onAllNodes(tabs).assertCountEquals(0)
        source.bootstrapTarget = SessionState.SignedOut(SignOutReason.EXPIRED)
        compose.onNodeWithText("Coba lagi").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Masuk").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Lihat data contoh").assertDoesNotExist()
        assertEquals(2, source.bootstrapCalls)
        compose.onNodeWithText("Fixture A").assertDoesNotExist()
        compose.runOnIdle { source.state.value = SessionState.Authenticated(source.user.copy(
            id = "00000000-0000-4000-8000-000000000010", name = "Fixture B", email = "b@example.invalid"), 2) }
        compose.onNode(tabs and hasText("Beranda")).assertIsSelected()
        compose.onNode(tabs and hasText("Profil")).performClick()
        compose.onNodeWithText("Fixture B").assertIsDisplayed()
        compose.onNodeWithText("Fixture A").assertDoesNotExist()
    }

    @Test fun imeSubmitsOnceAndCredentialFailureKeepsInputWithoutPrivateRoot() {
        val source = FixtureSession().apply { loginGate = CompletableDeferred() }
        start(source)
        val email = compose.onNode(hasContentDescription("Email") and hasSetTextAction())
        val password = compose.onNode(hasContentDescription("Kata sandi") and hasSetTextAction())
        email.assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentType, ContentType.EmailAddress))
        password.assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentType, ContentType.Password))
        email.performTextReplacement("fixture@example.invalid")
        password.performTextReplacement(" fixture-password-12 ")
        password.performImeAction()
        compose.onNodeWithText("Sedang masuk…").performScrollTo().assertIsNotEnabled()
        assertEquals(1, source.loginCalls)
        source.loginGate!!.complete(Unit)
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Email atau kata sandi belum cocok.").fetchSemanticsNodes().isNotEmpty() }
        email.assertTextContains("fixture@example.invalid")
        assertEquals("•".repeat(" fixture-password-12 ".length), password.fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        assertEquals(" fixture-password-12 ", source.receivedPassword)
        compose.onAllNodes(tabs).assertCountEquals(0)
    }

    @Test fun startupRetryAndLoginAgainRemainReachableInShortWindows() {
        val source = FixtureSession().apply { state.value = SessionState.ConnectionRequired(ApiFailure(ApiFailureKind.NETWORK)) }
        start(source)
        compose.onAllNodes(tabs).assertCountEquals(0)
        compose.onNodeWithText("Coba lagi").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(2, source.bootstrapCalls)
        compose.onNodeWithText("Masuk ulang").performScrollTo().performClick()
        compose.onNodeWithText("Masuk").performScrollTo().assertIsDisplayed()
        compose.onAllNodes(tabs).assertCountEquals(0)
    }

    @Test fun registrationValidatesFirstThenUsesVisibleTimezoneAndClearsAuthRoot() {
        val source = FixtureSession(demo = true)
        start(source)
        compose.onNodeWithText("Belum punya akun? Daftar").performScrollTo().performClick()
        compose.onNodeWithText("Buat akun").performScrollTo().performClick()
        compose.onNode(hasContentDescription("Nama") and hasSetTextAction()).assertIsFocused()
        assertEquals(0, source.registerCalls)
        compose.onNode(hasContentDescription("Nama") and hasSetTextAction()).performTextReplacement("Sam Demo")
        compose.onNode(hasContentDescription("Email") and hasSetTextAction()).performTextReplacement("sam@example.invalid")
        compose.onNode(hasContentDescription("Kata sandi") and hasSetTextAction()).performTextReplacement(" fixture-password-12 ")
        val confirmation = compose.onNode(hasContentDescription("Konfirmasi kata sandi") and hasSetTextAction())
        confirmation.assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentType, ContentType.NewPassword))
        confirmation.performTextReplacement("fixture-password-12")
        compose.onNodeWithText("Buat akun").performScrollTo().performClick()
        compose.onNodeWithText("Konfirmasi kata sandi belum sama.").assertExists()
        assertEquals(0, source.registerCalls)
        confirmation.performTextReplacement(" fixture-password-12 ")
        confirmation.performImeAction()
        compose.waitForIdle()
        compose.onAllNodes(tabs).assertCountEquals(5)
        compose.onNode(tabs and hasText("Profil")).performClick()
        compose.onNodeWithText("Sam Demo").assertIsDisplayed()
        assertNotNull(source.receivedTimezone)
        compose.onNodeWithText(source.receivedTimezone!!.id).assertIsDisplayed()
        assertEquals(1, source.registerCalls)
        compose.onNodeWithText("Buat akun contoh").assertDoesNotExist()
    }

    @Test fun aNewAccountLifetimeDoesNotRestoreThePreviousGuideQuery() {
        val source = FixtureSession(demo = true).apply { state.value = SessionState.Authenticated(user, 1) }
        start(source)
        compose.onNode(tabs and hasText("Panduan")).performClick()
        compose.onNodeWithContentDescription("Cari panduan").performTextReplacement("karton")
        compose.onNodeWithContentDescription("Cari panduan").performImeAction()
        compose.runOnIdle { source.state.value = SessionState.SignedOut() }
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Masuk") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(tabs).assertCountEquals(0)
        compose.runOnIdle { source.state.value = SessionState.Authenticated(source.user, 2) }
        compose.waitUntil(5_000) { compose.onAllNodes(tabs and hasText("Panduan")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(tabs and hasText("Panduan")).performClick()
        assertEquals("", compose.onNodeWithContentDescription("Cari panduan").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
    }

    private class FixtureSession(private val demo: Boolean = false) : AccountSession {
        val user = User("00000000-0000-4000-8000-000000000009", "Fixture A", "fixture@example.invalid", ZoneId.of("Asia/Jakarta"), null)
        override val state = MutableStateFlow<SessionState>(SessionState.SignedOut())
        override val isDemo = demo
        var bootstrapCalls = 0
        var bootstrapTarget: SessionState? = null
        var loginCalls = 0
        var registerCalls = 0
        var receivedPassword: String? = null
        var receivedTimezone: ZoneId? = null
        var loginGate: CompletableDeferred<Unit>? = null
        override suspend fun bootstrap() { bootstrapCalls++; bootstrapTarget?.let { state.value = it } }
        override suspend fun login(email: String, password: String): ApiResult<User> {
            loginCalls++; receivedPassword = password
            loginGate?.await()
            return ApiResult.Failure(ApiFailure(ApiFailureKind.UNAUTHENTICATED, fields = mapOf("name" to "unexpected login field")))
        }
        override suspend fun register(name: String, email: String, password: String, timezone: ZoneId): ApiResult<User> {
            registerCalls++; receivedTimezone = timezone
            val created = user.copy(name = name.trim(), email = email.trim(), timezone = timezone)
            state.value = SessionState.Authenticated(created, 2)
            return ApiResult.Success(created)
        }
        override suspend fun logout(): LogoutResult {
            state.value = SessionState.SignedOut()
            return LogoutResult(true, false)
        }
    }
}
