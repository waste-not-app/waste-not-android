package com.example.wastenot.ui.main

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.BuildConfig
import com.example.wastenot.MainActivity
import com.example.wastenot.ThemeFixtureRule
import com.example.wastenot.WasteNotApplication
import com.example.wastenot.data.local.KeystoreRefreshTokenStore
import com.example.wastenot.data.local.TokenReadResult
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.repository.ActivityInput
import com.example.wastenot.data.repository.CoreRepository
import com.example.wastenot.data.repository.RemoteAccountSession
import com.example.wastenot.data.repository.SessionState
import com.example.wastenot.data.repository.SignOutReason
import com.example.wastenot.model.DisposalMethod
import com.example.wastenot.model.Quantity
import com.example.wastenot.model.WasteCategory
import java.io.File
import java.time.ZoneId
import java.util.UUID
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class RemoteSessionRecoveryTest {
    @get:Rule(order = 0) val appearance = ThemeFixtureRule()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun refreshAndRevocationAreReflectedAtTheRealAccountRoot() = runTest(timeout = 5.minutes) {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("verifyLiveSessionRecovery") == "true")
        val runId = UUID.fromString(requireNotNull(arguments.getString("fixtureRunId"))).toString()
        val container = (compose.activity.application as WasteNotApplication).container
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sessions = container.sessions
        val tokenStore = KeystoreRefreshTokenStore.create(context)
        val directory = File(context.getExternalFilesDir(null), "session-recovery/$runId").apply { mkdirs() }
        val evidence = JSONObject().put("run_id", runId)

        fun record(phase: String) {
            File(directory, "phase.json").writeText(evidence.put("phase", phase).toString())
        }
        fun awaitProbeChange(marker: String) {
            compose.waitUntil(60_000) { File(directory, marker).exists() }
        }

        assertTrue(BuildConfig.DEBUG)
        assertEquals("https://waste-not-api.bccdev.id/v1/", BuildConfig.API_BASE_URL)
        assertTrue(container.accountSession is RemoteAccountSession)
        waitFor("New here? Create an account")
        assertTrue(sessions.state.value is SessionState.SignedOut)
        assertEquals(TokenReadResult.Absent, tokenStore.read())

        try {
            val registration = sessions.register(
                "Android #112 session probe", "android-session-$runId@example.test",
                UUID.randomUUID().toString() + "Az9!", ZoneId.of("Asia/Jakarta"),
            )
            check(registration is ApiResult.Success) { "Probe registration failed: ${registration.failureKind()}" }
            val user = registration.value
            val originalSession = sessions.state.value as SessionState.Authenticated
            assertEquals(user.id, originalSession.user.id)
            evidence.put("user_id", user.id)
            waitFor("Log manually")
            assertTrue(container.core.dashboard() is ApiResult.Success)
            assertTrue(tokenStore.read() is TokenReadResult.Available)
            verifyVersionConflict(container.core, runId)
            record("awaiting_stale_access")

            awaitProbeChange("stale-access")
            val refreshed = container.core.dashboard()
            check(refreshed is ApiResult.Success) { "Probe refresh failed: ${refreshed.failureKind()}" }
            assertEquals(originalSession, sessions.state.value)
            assertTrue(tokenStore.read() is TokenReadResult.Available)
            waitFor("Log manually")
            captureUiPreview(compose, "root-after-refresh", "session-recovery/$runId", capturePanel = false)
            record("awaiting_revocation")

            awaitProbeChange("revoked")
            val revoked = container.core.dashboard()
            assertEquals(ApiFailureKind.UNAUTHENTICATED, revoked.failureKind())
            assertEquals(SessionState.SignedOut(SignOutReason.EXPIRED), sessions.state.value)
            assertEquals(TokenReadResult.Absent, tokenStore.read())
            waitFor("Your session can't be used right now. Sign in again to open your logs.")
            waitFor("New here? Create an account")
            compose.onNodeWithText("Log manually").assertDoesNotExist()
            captureUiPreview(compose, "root-after-revocation", "session-recovery/$runId", capturePanel = false)
            record("passed")
        } finally {
            if (sessions.state.value is SessionState.Authenticated) sessions.logout()
        }
    }

    private suspend fun verifyVersionConflict(core: CoreRepository, runId: String) {
        val date = success(core.dashboard()).date
        val input = ActivityInput(WasteCategory.PLASTIC, DisposalMethod.RECYCLE, Quantity.Weight.of(250), date, "Original note")
        val initial = success(core.createLog(input, UUID.randomUUID().toString())).log
        assertEquals(10, success(core.dashboard()).progress.xp)

        compose.onNode(hasText("History") and isSelectable()).performClick()
        waitFor("250 g")
        compose.onNodeWithText("250 g").performClick()
        waitFor("Edit log")
        compose.onNodeWithText("Edit log").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Weight").performScrollTo().performTextReplacement("300")
        compose.onNodeWithContentDescription("Notes (optional)").performScrollTo().performTextReplacement("300 g draft")

        val latest = success(core.updateLog(initial.id, initial.version,
            input.copy(quantity = Quantity.Weight.of(275), notes = "Version lain")))
        compose.onNodeWithText("Save changes").performScrollTo().performClick()
        waitFor("This log has changed")
        fun dialogText(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(isDialog()))
        dialogText("Load the latest version").performScrollTo().performClick()
        waitFor("Versi terbaru · v${latest.version}")
        dialogText("Version lain").performScrollTo().assertIsDisplayed()
        dialogText("300 g draft").performScrollTo().assertIsDisplayed()
        captureUiPreview(compose, "version-conflict", "session-recovery/$runId", capturePanel = false)
        dialogText("Use everything from my draft").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Weight").performScrollTo().assertTextContains("300")
        assertEquals(latest, success(core.wasteLog(initial.id)))

        compose.onNodeWithText("Save changes").performScrollTo().performClick()
        waitFor("Your log was already saved. No extra XP.")
        val updated = success(core.wasteLogs()).single()
        assertEquals(initial.id, updated.id)
        assertEquals(latest.version + 1, updated.version)
        assertEquals(Quantity.Weight.of(300), updated.quantity)
        assertEquals("300 g draft", updated.notes)
        assertEquals(10, success(core.dashboard()).progress.xp)
        compose.onNodeWithText("Back to home").performScrollTo().performClick()
        waitFor("Log manually")
    }

    private fun waitFor(text: String) {
        compose.waitUntil(30_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun <T> success(result: ApiResult<T>): T {
        check(result is ApiResult.Success) { "Probe call failed: ${result.failureKind()}" }
        return result.value
    }

    private fun ApiResult<*>.failureKind() = (this as? ApiResult.Failure)?.error?.kind
}
