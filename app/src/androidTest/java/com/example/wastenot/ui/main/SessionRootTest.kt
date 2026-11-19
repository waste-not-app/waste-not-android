package com.example.wastenot.ui.main

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.MainNavigation
import com.example.wastenot.data.network.*
import com.example.wastenot.data.repository.*
import com.example.wastenot.model.User
import com.example.wastenot.theme.ThemeMode
import com.example.wastenot.theme.WasteNotTheme
import java.time.ZoneId
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject

class SessionRootTest {
    @get:Rule val compose = createComposeRule()
    private val tabs = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
    private fun start(source: FixtureSession, restoration: StateRestorationTester? = null) {
        com.example.wastenot.clearEditorDraftFixtures(InstrumentationRegistry.getInstrumentation().targetContext)
        val content: @androidx.compose.runtime.Composable () -> Unit = { WasteNotTheme(darkTheme = false) {
            MainNavigation(source, CoreRepository(null), com.example.wastenot.data.dummy.DummyGuideStore(),
                com.example.wastenot.data.repository.AiRepository(null), ThemeMode.LIGHT, {}, Modifier.fillMaxSize(), activityStoreFactory = { user ->
                    com.example.wastenot.navigationActivityFixture(InstrumentationRegistry.getInstrumentation().targetContext, user) })
        } }
        if (restoration == null) compose.setContent(content) else restoration.setContent(content)
        compose.waitForIdle()
    }

    @Test fun checkingShowsNoPrivateRootAndResolutionNeedsNoOnboardingAction() {
        val source = FixtureSession().apply {
            state.value = SessionState.Checking
            bootstrapGate = CompletableDeferred()
            bootstrapTarget = SessionState.SignedOut()
        }
        start(source)
        compose.onNodeWithText("Getting your logs ready…").performScrollTo().assertIsDisplayed()
        compose.onAllNodes(tabs).assertCountEquals(0)
        compose.onNodeWithText("Fixture A").assertDoesNotExist()
        assertEquals(1, source.bootstrapCalls)
        observeResolution { source.bootstrapGate!!.complete(Unit) }
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Sign in") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        compose.onNodeWithText("Getting your logs ready…").assertDoesNotExist()
        compose.onNodeWithText("Simulasikan", substring = true).assertDoesNotExist()
        compose.onAllNodes(tabs).assertCountEquals(0)
        assertEquals(1, source.bootstrapCalls)
    }

    private fun observeResolution(resolve: () -> Unit) {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "startup-qa").apply { mkdirs() }
        val frames = JSONArray()
        var destinationFrame: Int? = null
        val start = compose.mainClock.currentTime
        compose.mainClock.autoAdvance = false
        try {
            resolve()
            for (frame in 0 until 60) {
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
                val login = compose.onAllNodes(hasText("Sign in") and hasClickAction()).fetchSemanticsNodes().isNotEmpty()
                compose.onAllNodes(tabs).assertCountEquals(0)
                compose.onNodeWithText("Fixture A").assertDoesNotExist()
                if (destinationFrame == null && login) destinationFrame = frame
                frames.put(JSONObject().put("frame", frame).put("login_entry", login))
                if (destinationFrame != null && frame - destinationFrame in listOf(0, 4, 12, 16)) {
                    val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
                    File(directory, "startup-to-login-$frame.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
                if (destinationFrame != null && frame - destinationFrame >= 16) break
            }
            assertNotNull("Session resolution must replace the root without another user action", destinationFrame)
        } finally {
            File(directory, "startup-transition.json").writeText(JSONObject().put("destination_frame", destinationFrame)
                .put("compose_clock_elapsed_ms", compose.mainClock.currentTime - start).put("frames", frames).toString(2))
            compose.mainClock.autoAdvance = true
        }
        compose.waitForIdle()
    }

    @Test fun unverifiedIdentityHidesOldProfileAndRetryReplacesRootOnce() {
        val source = FixtureSession().apply { state.value = SessionState.Authenticated(user, 1) }
        start(source)
        compose.onNode(tabs and hasText("Profile")).performClick()
        compose.onNodeWithText("Fixture A").assertIsDisplayed()
        compose.runOnIdle { source.state.value = SessionState.ConnectionRequired(ApiFailure(ApiFailureKind.NETWORK)) }
        compose.onNodeWithText("Can't check your session yet").assertIsDisplayed()
        compose.onNodeWithText("Fixture A").assertDoesNotExist()
        compose.onAllNodes(tabs).assertCountEquals(0)
        source.bootstrapTarget = SessionState.SignedOut(SignOutReason.EXPIRED)
        compose.onNodeWithText("Try again").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Sign in").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("See sample data").assertDoesNotExist()
        assertEquals(2, source.bootstrapCalls)
        compose.onNodeWithText("Fixture A").assertDoesNotExist()
        compose.runOnIdle { source.state.value = SessionState.Authenticated(source.user.copy(
            id = "00000000-0000-4000-8000-000000000010", name = "Fixture B", email = "b@example.invalid"), 2) }
        compose.onNode(tabs and hasText("Home")).assertIsSelected()
        compose.onNode(tabs and hasText("Profile")).performClick()
        compose.onNodeWithText("Fixture B").assertIsDisplayed()
        compose.onNodeWithText("Fixture A").assertDoesNotExist()
    }

    @Test fun imeSubmitsOnceAndCredentialFailureKeepsInputWithoutPrivateRoot() {
        val source = FixtureSession().apply { loginGate = CompletableDeferred() }
        start(source)
        val email = compose.onNode(hasContentDescription("Email") and hasSetTextAction())
        val password = compose.onNode(hasContentDescription("Password") and hasSetTextAction())
        email.assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentType, ContentType.EmailAddress))
        password.assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentType, ContentType.Password))
        email.performTextReplacement("fixture@example.invalid")
        password.performTextReplacement(" fixture-password-12 ")
        password.performImeAction()
        compose.onNodeWithText("Signing in…").performScrollTo().assertIsNotEnabled()
        assertEquals(1, source.loginCalls)
        source.loginGate!!.complete(Unit)
        compose.waitUntil(5_000) { compose.onAllNodesWithText("That email and password don't match.").fetchSemanticsNodes().isNotEmpty() }
        email.assertTextContains("fixture@example.invalid")
        assertEquals("•".repeat(" fixture-password-12 ".length), password.fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        assertEquals(" fixture-password-12 ", source.receivedPassword)
        compose.onAllNodes(tabs).assertCountEquals(0)
    }

    @Test fun startupRetryAndLoginAgainRemainReachableInShortWindows() {
        val source = FixtureSession().apply { state.value = SessionState.ConnectionRequired(ApiFailure(ApiFailureKind.NETWORK)) }
        start(source)
        compose.onAllNodes(tabs).assertCountEquals(0)
        compose.onNodeWithText("Try again").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(2, source.bootstrapCalls)
        compose.onNodeWithText("Sign in again").performScrollTo().performClick()
        compose.onNodeWithText("Sign in").performScrollTo().assertIsDisplayed()
        compose.onAllNodes(tabs).assertCountEquals(0)
    }

    @Test fun registrationValidatesFirstThenUsesVisibleTimezoneAndClearsAuthRoot() {
        val source = FixtureSession()
        start(source)
        compose.onNodeWithText("New here? Create an account").performScrollTo().performClick()
        compose.onNodeWithText("Create account").performScrollTo().performClick()
        compose.onNode(hasContentDescription("Name") and hasSetTextAction()).assertIsFocused()
        assertEquals(0, source.registerCalls)
        compose.onNode(hasContentDescription("Name") and hasSetTextAction()).performTextReplacement("Sam Demo")
        compose.onNode(hasContentDescription("Email") and hasSetTextAction()).performTextReplacement("sam@example.invalid")
        compose.onNode(hasContentDescription("Password") and hasSetTextAction()).performTextReplacement(" fixture-password-12 ")
        val confirmation = compose.onNode(hasContentDescription("Confirm password") and hasSetTextAction())
        confirmation.assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentType, ContentType.NewPassword))
        confirmation.performTextReplacement("fixture-password-12")
        compose.onNodeWithText("Create account").performScrollTo().performClick()
        compose.onNodeWithText("The passwords don't match yet.").assertExists()
        assertEquals(0, source.registerCalls)
        confirmation.performTextReplacement(" fixture-password-12 ")
        confirmation.performImeAction()
        compose.waitForIdle()
        compose.onAllNodes(tabs).assertCountEquals(5)
        compose.onNode(tabs and hasText("Profile")).performClick()
        compose.onNodeWithText("Sam Demo").assertIsDisplayed()
        assertNotNull(source.receivedTimezone)
        compose.onNodeWithText(source.receivedTimezone!!.id).assertIsDisplayed()
        assertEquals(1, source.registerCalls)
        compose.onNodeWithText("Create sample account").assertDoesNotExist()
    }

    @Test fun aNewAccountLifetimeDoesNotRestoreThePreviousGuideQuery() {
        val source = FixtureSession().apply { state.value = SessionState.Authenticated(user, 1) }
        start(source)
        compose.onNode(tabs and hasText("Guide")).performClick()
        compose.onNodeWithContentDescription("Search guides").performTextReplacement("karton")
        compose.onNodeWithContentDescription("Search guides").performImeAction()
        compose.runOnIdle { source.state.value = SessionState.SignedOut() }
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Sign in") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(tabs).assertCountEquals(0)
        compose.runOnIdle { source.state.value = SessionState.Authenticated(source.user, 2) }
        compose.waitUntil(5_000) { compose.onAllNodes(tabs and hasText("Guide")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(tabs and hasText("Guide")).performClick()
        assertEquals("", compose.onNodeWithContentDescription("Search guides").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
    }

    @Test fun changingIdentityNeverRestoresAnotherAccountsDraft() {
        val source = FixtureSession()
        val a = source.user.copy(id = "00000000-0000-4000-8000-000000000031")
        val b = source.user.copy(id = "00000000-0000-4000-8000-000000000032", name = "Fixture B")
        source.state.value = SessionState.Authenticated(a, 1)
        start(source)
        compose.waitUntil(10_000) { compose.onAllNodes(tabs).fetchSemanticsNodes().size == 5 }
        compose.onNodeWithText("Log manually").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Notes (optional)").performScrollTo().performTextReplacement("Account A draft, private")
        fun disk(owner: String): StoredEditorDraft? {
            var draft: StoredEditorDraft? = null
            kotlinx.coroutines.test.runTest {
                draft = (com.example.wastenot.data.local.LocalEditorDraftStore.create(draftTestContext, owner).read() as ApiResult.Success).value
            }
            return draft
        }
        compose.waitUntil(10_000) { disk(a.id)?.fields?.get(12) == "Account A draft, private" }
        val requestA = checkNotNull(disk(a.id)).requestId
        compose.onNodeWithContentDescription("Notes (optional)").performTextReplacement("Account A draft, latest")
        compose.runOnIdle { source.state.value = SessionState.Authenticated(b, 2) }
        compose.waitUntil(10_000) { compose.onAllNodes(tabs).fetchSemanticsNodes().size == 5 }
        compose.onNodeWithText("Account A draft", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Log manually").performScrollTo().performClick()
        assertEquals("", compose.onNodeWithContentDescription("Notes (optional)").performScrollTo()
            .fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        compose.waitUntil(10_000) { disk(b.id) != null }
        assertEquals("", checkNotNull(disk(b.id)).fields[12])
        org.junit.Assert.assertNotEquals(requestA, checkNotNull(disk(b.id)).requestId)
        compose.runOnIdle { source.state.value = SessionState.Authenticated(a, 3) }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Draft restored. Check your entries before saving.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Notes (optional)").performScrollTo().assertTextContains("Account A draft", substring = true)
        assertEquals(requestA, checkNotNull(disk(a.id)).requestId)
        captureDraftFixture("draft-account-A-restored")
    }

    @Test fun failedLogoutKeepsTheAccountDialogAndAnExplicitRetryCanFinish() {
        val source = FixtureSession().apply {
            state.value = SessionState.Authenticated(user, 1)
            logoutResult = LogoutResult(false, false)
        }
        start(source)
        compose.waitUntil(10_000) { compose.onAllNodes(tabs).fetchSemanticsNodes().size == 5 }
        compose.onNode(tabs and hasText("Profile")).performClick()
        compose.onNodeWithText("Sign out").performScrollTo().performClick()
        compose.onNode(hasText("Sign out") and hasClickAction() and hasAnyAncestor(isDialog())).performScrollTo().performClick()
        compose.waitUntil(5_000) { source.logoutCompletions == 1 }
        org.junit.Assert.assertTrue(source.state.value is SessionState.Authenticated)
        compose.onNode(isDialog()).assertExists()
        compose.onNodeWithText("Couldn't sign out on this device. You're still signed in. Try signing out again.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Fixture A").assertExists()
        captureDraftFixture("logout-account-failed")
        compose.onNode(hasText("Cancel") and hasAnyAncestor(isDialog())).performScrollTo().performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.onNode(tabs and hasText("Profile")).assertIsSelected()
        source.logoutResult = LogoutResult(true, false)
        compose.onNodeWithText("Sign out").performScrollTo().performClick()
        compose.onNode(hasText("Sign out") and hasClickAction() and hasAnyAncestor(isDialog())).performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Sign in") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(tabs).assertCountEquals(0)
        compose.onNodeWithText("Fixture A").assertDoesNotExist()
        assertEquals(2, source.logoutCalls)
    }

    @Test fun pendingLogoutBlocksDuplicateConfirmationBackAndEscapeUntilItsResult() {
        val source = FixtureSession().apply {
            state.value = SessionState.Authenticated(user, 1)
            logoutResult = LogoutResult(false, false)
            logoutGate = CompletableDeferred()
        }
        val restoration = StateRestorationTester(compose)
        start(source, restoration)
        compose.waitUntil(10_000) { compose.onAllNodes(tabs).fetchSemanticsNodes().size == 5 }
        compose.onNode(tabs and hasText("Profile")).performClick()
        compose.onNodeWithText("Sign out").performScrollTo().performClick()
        compose.onNode(hasText("Sign out") and hasClickAction() and hasAnyAncestor(isDialog())).performScrollTo().performClick()
        compose.waitUntil(5_000) { source.logoutCalls == 1 }
        restoration.emulateSavedInstanceStateRestore()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Signing out on this device…").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Signing out on this device…").performScrollTo().assertIsNotEnabled()
        compose.onNode(hasText("Cancel") and hasAnyAncestor(isDialog())).assertIsNotEnabled()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_ESCAPE)
        compose.onNode(isDialog()).assertExists()
        compose.onNodeWithText("Signing out on this device…").performScrollTo().performTouchInput { click() }
        assertEquals(1, source.logoutCalls)
        captureDraftFixture("logout-account-pending")
        source.logoutGate!!.complete(Unit)
        compose.waitUntil(5_000) { source.logoutCompletions == 1 }
        compose.onNodeWithText("Couldn't sign out on this device. You're still signed in. Try signing out again.").performScrollTo().assertIsDisplayed()
        compose.onNode(hasText("Cancel") and hasAnyAncestor(isDialog())).assertIsEnabled()
    }

    @Test fun aClosedAccountRootKeepsSessionCleanupRetryOnTheLoginScreen() {
        val source = FixtureSession().apply {
            state.value = SessionState.Authenticated(user, 1)
            logoutResult = LogoutResult(false, false)
            logoutClosesRoot = true
            logoutGate = CompletableDeferred()
        }
        start(source)
        compose.waitUntil(10_000) { compose.onAllNodes(tabs).fetchSemanticsNodes().size == 5 }
        compose.onNode(tabs and hasText("Profile")).performClick()
        compose.onNodeWithText("Sign out").performScrollTo().performClick()
        compose.onNode(hasText("Sign out") and hasClickAction() and hasAnyAncestor(isDialog())).performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Signing out on this device…").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(tabs).assertCountEquals(0)
        compose.onNodeWithText("Fixture A").assertDoesNotExist()
        source.logoutGate!!.complete(Unit)
        compose.waitUntil(5_000) { source.logoutCompletions == 1 }
        compose.onNodeWithText("Your logs are closed, but the saved session couldn't be cleared. Try again before you hand over this device.")
            .performScrollTo().assertIsDisplayed()
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithText("Try clearing the session", useUnmergedTree = true).performScrollTo()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        org.junit.Assert.assertFalse("cleanup label box=${layouts.single().size}, paragraph=${layouts.single().multiParagraph.width}", layouts.single().didOverflowWidth)
        org.junit.Assert.assertFalse(layouts.single().didOverflowHeight)
        captureDraftFixture("logout-auth-cleanup-failed")
        source.logoutResult = LogoutResult(true, false)
        compose.onNodeWithText("Try clearing the session").performScrollTo().performClick()
        compose.waitUntil(5_000) { source.logoutCompletions == 2 }
        compose.onNodeWithText("Try clearing the session").assertDoesNotExist()
        compose.onNode(hasText("Sign in") and hasClickAction()).performScrollTo().assertIsDisplayed()
        compose.onAllNodes(tabs).assertCountEquals(0)
    }

    private class FixtureSession : AccountSession {
        val user = User("00000000-0000-4000-8000-000000000009", "Fixture A", "fixture@example.invalid", ZoneId.of("Asia/Jakarta"), null)
        override val state = MutableStateFlow<SessionState>(SessionState.SignedOut())

        var bootstrapCalls = 0
        var bootstrapTarget: SessionState? = null
        var bootstrapGate: CompletableDeferred<Unit>? = null
        var loginCalls = 0
        var registerCalls = 0
        var receivedPassword: String? = null
        var receivedTimezone: ZoneId? = null
        var loginGate: CompletableDeferred<Unit>? = null
        var logoutCalls = 0
        var logoutCompletions = 0
        var logoutGate: CompletableDeferred<Unit>? = null
        var logoutResult = LogoutResult(true, false)
        var logoutClosesRoot = false
        override suspend fun bootstrap() { bootstrapCalls++; bootstrapGate?.await(); bootstrapTarget?.let { state.value = it } }
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
            logoutCalls++
            if (logoutClosesRoot) state.value = SessionState.SignedOut()
            logoutGate?.await()
            if (logoutResult.localCleared) state.value = SessionState.SignedOut()
            logoutCompletions++
            return logoutResult
        }
    }
}
