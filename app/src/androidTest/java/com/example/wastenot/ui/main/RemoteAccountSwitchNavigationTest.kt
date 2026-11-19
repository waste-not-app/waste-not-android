package com.example.wastenot.ui.main

import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.*
import com.example.wastenot.data.local.KeystoreRefreshTokenStore
import com.example.wastenot.data.local.LocalEditorDraftStore
import com.example.wastenot.data.local.TokenReadResult
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.repository.RemoteAccountSession
import com.example.wastenot.data.repository.SessionState
import com.example.wastenot.model.QuantityTotals
import java.io.File
import java.util.UUID
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class RemoteAccountSwitchNavigationTest {
    @get:Rule(order = 0) val appearance = ThemeFixtureRule()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val container get() = (compose.activity.application as WasteNotApplication).container
    private val owners = mutableListOf<String>()

    @Test fun realSessionsKeepDraftsQueriesAndPrivateRootsBoundToTheirOwner() = runTest(timeout = 6.minutes) {
        assumeTrue(InstrumentationRegistry.getArguments().getString("verifyLiveAccountIsolation") == "true")
        assertTrue(BuildConfig.DEBUG)
        assertEquals("https://waste-not-api.bccdev.id/v1/", BuildConfig.API_BASE_URL)
        assertTrue(container.accountSession is RemoteAccountSession)
        val runId = UUID.fromString(requireNotNull(InstrumentationRegistry.getArguments().getString("fixtureRunId"))).toString()
        val passwordA = UUID.randomUUID().toString() + "Az9!"
        val passwordB = UUID.randomUUID().toString() + "Az9!"
        val emailA = "android-switch-a-$runId@example.test"
        val emailB = "android-switch-b-$runId@example.test"
        val steps = JSONArray()
        val evidence = JSONObject().put("run_id", runId)
        val output = File(context.cacheDir, "qa/account-isolation/navigation.json")
        fun record(step: String) {
            output.parentFile!!.mkdirs()
            output.writeText(evidence.put("passed_steps", steps.put(step)).toString(2))
        }
        try {
            waitFor("New here? Create an account")
            assertTrue(container.sessions.state.value is SessionState.SignedOut)
            register("Android switch A", emailA, passwordA)
            val a = authenticated(emailA)
            owners.add(a.id)
            evidence.put("first_user_id", a.id)
            tap("Log manually")
            tap("Recycle")
            choose("Category", "Plastic")
            fill("Weight", "250")
            fill("Notes (optional)", "Saved account A record")
            tap("Save log")
            waitFor("+10 XP for this new activity.")
            tap("Back to home")
            waitFor("250 g")
            val saved = success(container.core.dashboard()).recentLogs.single()
            evidence.put("first_log_id", saved.id)
            capture("a-home-saved")
            record("a_registers_and_creates_one_real_record")

            tab("History")
            compose.onNodeWithContentDescription("Filter history").performClick()
            waitFor("Apply filters")
            choose("Category", "Plastic")
            tap("Apply filters")
            waitFor("Clear filters")
            tab("Stats")
            waitFor("250 g")
            tap("30 days")
            compose.onNodeWithText("30 days").assertIsSelected()
            tab("Guide")
            fill("Search guides", "Account A private query")
            tab("Home")
            tap("Log manually")
            fill("Notes (optional)", "Account A private draft")
            compose.waitUntil(10_000) { draft(a.id)?.fields?.get(12) == "Account A private draft" }
            val requestA = checkNotNull(draft(a.id)).requestId
            capture("a-draft-before-session-end")
            record("a_changes_filter_range_query_and_persists_an_unsaved_draft")

            // End the real session while the editor is open; exercise reauthentication, not D02's draft deletion.
            assertTrue(container.sessions.logout().serverRevoked)
            waitFor("New here? Create an account")
            assertTrue(KeystoreRefreshTokenStore.create(context).read() is TokenReadResult.Absent)
            compose.onNodeWithText("Account A private draft").assertDoesNotExist()
            assertNotNull(draft(a.id))
            register("Android switch B", emailB, passwordB)
            val b = authenticated(emailB)
            owners.add(b.id)
            evidence.put("second_user_id", b.id)
            assertNotEquals(a.id, b.id)
            compose.onNode(hasText("Home") and isSelectable()).assertIsSelected()
            waitFor("Nothing logged today yet.")
            assertEquals(0L, success(container.core.dashboard()).summary.activityCount)
            assertEquals(0, success(container.core.dashboard()).progress.xp)
            capture("b-home-empty")
            tab("History")
            waitFor("No logs yet")
            compose.onNodeWithText("Clear filters").assertDoesNotExist()
            compose.onNodeWithText("Saved account A record").assertDoesNotExist()
            capture("b-history-without-a-filter")
            tab("Stats")
            waitFor("No logs in this period")
            compose.onNodeWithText("7 days").assertIsSelected()
            capture("b-statistics-default-range", "No logs in this period")
            tab("Guide")
            assertEquals("", compose.onNodeWithContentDescription("Search guides").fetchSemanticsNode()
                .config[SemanticsProperties.EditableText].text)
            tab("Profile")
            waitFor("0 XP from 0 active logs")
            compose.onNodeWithText("Android switch B").assertExists()
            compose.onNodeWithText("Android switch A").assertDoesNotExist()
            capture("b-profile-own-identity")
            record("b_has_only_own_empty_data_default_queries_range_and_profile")

            tab("Home")
            tap("Log manually")
            assertEquals("", fieldValue("Notes (optional)"))
            fill("Notes (optional)", "Account B private draft")
            compose.waitUntil(10_000) { draft(b.id)?.fields?.get(12) == "Account B private draft" }
            assertNotEquals(requestA, checkNotNull(draft(b.id)).requestId)
            assertEquals("Account A private draft", checkNotNull(draft(a.id)).fields[12])
            assertTrue(container.sessions.logout().serverRevoked)
            waitFor("New here? Create an account")
            login(emailA, passwordA)
            assertEquals(a.id, authenticated(emailA).id)
            waitFor("Draft restored. Check your entries before saving.")
            assertEquals("Account A private draft", fieldValue("Notes (optional)"))
            assertEquals(requestA, checkNotNull(draft(a.id)).requestId)
            assertEquals(saved, success(container.core.wasteLog(saved.id)))
            assertEquals(QuantityTotals(250, 0), success(container.core.dashboard()).summary.handled)
            assertEquals(10, success(container.core.dashboard()).progress.xp)
            capture("a-own-draft-restored")
            record("relogin_a_restores_only_a_draft_and_own_saved_record")

            backAndDiscard()
            tab("Profile")
            waitFor("10 XP from 1 active logs")
            tap("Sign out")
            compose.onNode(hasText("Sign out") and hasAnyAncestor(isDialog())).performClick()
            waitFor("New here? Create an account")
            assertNull(draft(a.id))
            assertNotNull(draft(b.id))
            compose.activityRule.scenario.recreate()
            waitFor("New here? Create an account")
            compose.onNodeWithText("Android switch A").assertDoesNotExist()
            capture("signed-out-root-after-recreate")
            login(emailB, passwordB)
            assertEquals(b.id, authenticated(emailB).id)
            waitFor("Draft restored. Check your entries before saving.")
            assertEquals("Account B private draft", fieldValue("Notes (optional)"))
            capture("b-own-draft-restored")
            record("ui_logout_preserves_b_draft_and_b_can_restore_its_own")
            backAndDiscard()
            tab("Profile")
            tap("Sign out")
            compose.onNode(hasText("Sign out") and hasAnyAncestor(isDialog())).performClick()
            waitFor("New here? Create an account")
            record("final_ui_logout")
        } finally {
            val current = (container.sessions.state.value as? SessionState.Authenticated)?.user
            if (current?.email in setOf(emailA, emailB)) container.sessions.logout()
            owners.forEach { success(LocalEditorDraftStore.create(context, it).clear(null)) }
        }
    }

    private fun register(name: String, email: String, password: String) {
        tap("New here? Create an account")
        fill("Name", name); fill("Email", email); fill("Password", password); fill("Confirm password", password)
        tap("Create account")
        waitFor("Log manually")
    }
    private fun login(email: String, password: String) {
        fill("Email", email); fill("Password", password)
        tap("Sign in")
        compose.waitUntil(30_000) { container.sessions.state.value is SessionState.Authenticated }
    }
    private fun authenticated(email: String) = (container.sessions.state.value as SessionState.Authenticated).user.also { assertEquals(email, it.email) }
    private fun draft(owner: String) = runBlocking { success(LocalEditorDraftStore.create(context, owner).read()) }
    private fun waitFor(text: String) = compose.waitUntil(30_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun tap(text: String) {
        compose.onNode(hasText(text) and hasClickAction()).performScrollTo().assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
    }
    private fun fieldValue(label: String) = compose.onNodeWithContentDescription(label).performScrollTo()
        .fetchSemanticsNode().config[SemanticsProperties.EditableText].text
    private fun fill(label: String, value: String) { compose.onNodeWithContentDescription(label).performScrollTo().performTextReplacement(value) }
    private fun choose(label: String, value: String) {
        compose.onNodeWithContentDescription(label).performScrollTo().performClick()
        compose.onNode(hasText(value) and hasAnyAncestor(isPopup())).performClick()
    }
    private fun tab(label: String) { compose.onNode(hasText(label) and isSelectable()).performClick() }
    private fun backAndDiscard() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        waitFor("Discard changes?")
        compose.onNodeWithText("Discard changes").performClick()
        waitFor("Log manually")
    }
    private fun capture(name: String, expectedText: String? = null) {
        expectedText?.let(::waitFor)
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle(); instrumentation.waitForIdleSync()
        instrumentation.uiAutomation.waitForIdle(300, 5_000)
        instrumentation.uiAutomation.takeScreenshot()?.recycle()
        android.os.SystemClock.sleep(200)
        expectedText?.let { compose.onNodeWithText(it).assertIsDisplayed() }
        assertEquals(context.packageName, instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString())
        val image = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val file = File(context.cacheDir, "qa/account-isolation/captures/$name.png")
        file.parentFile!!.mkdirs()
        expectedText?.let { compose.onNodeWithText(it).assertIsDisplayed() }
        file.outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        image.recycle()
    }
    private fun <T> success(result: ApiResult<T>): T {
        assertTrue("Expected a successful owned request", result is ApiResult.Success)
        return (result as ApiResult.Success).value
    }
}
