package com.example.wastenot.ui.main

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.BuildConfig
import com.example.wastenot.MainActivity
import com.example.wastenot.ThemeFixtureRule
import com.example.wastenot.R
import com.example.wastenot.WasteNotApplication
import com.example.wastenot.data.local.LocalEditorDraftStore
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.repository.SessionState
import com.example.wastenot.model.ActivityDateRange
import com.example.wastenot.model.QuantityTotals
import java.io.File
import java.util.UUID
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class RemoteSummaryNavigationTest {
    @get:Rule(order = 0) val appearance = ThemeFixtureRule()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun realAccountRefreshesHomeStatisticsAndProfileAfterUiWrites() = runTest(timeout = 3.minutes) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("verifyLiveFixture") == "true")
        assertTrue(BuildConfig.DEBUG)
        assertTrue((androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as com.example.wastenot.WasteNotApplication).container.accountSession is com.example.wastenot.data.repository.RemoteAccountSession)
        val runId = UUID.fromString(requireNotNull(args.getString("fixtureRunId"))).toString()
        val base = requireNotNull(args.getString("recordsTestBaseUrl"))
        require(base == "https://waste-not-api.bccdev.id/v1/" || base == "http://10.0.2.2:18088/v1/")
        require(BuildConfig.API_BASE_URL == base)
        val container = (compose.activity.application as WasteNotApplication).container
        val session = container.sessions
        val core = container.core
        val email = "android-$runId@example.test"
        val name = "Android transport probe"
        val steps = JSONArray()
        val evidence = JSONObject().put("run_id", runId).put("email", email).put("name", name)
        val file = File(instrumentation.targetContext.cacheDir, "qa/backend/records-fixture.json")
        fun record(step: String) {
            file.parentFile!!.mkdirs()
            file.writeText(evidence.put("passed_steps", steps.put(step)).toString(2))
        }
        fun waitFor(text: String) {
            compose.waitUntil(30_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        }
        fun waitEnabled(text: String) {
            compose.waitUntil(30_000) { compose.onAllNodes(hasText(text) and hasClickAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        }
        fun tab(text: String) { compose.onNode(hasText(text) and isSelectable()).performClick() }
        fun capture(name: String) {
            if (name.startsWith("remote-home")) {
                compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
                    and hasAnyDescendant(hasContentDescription("Open profile")))
                    .performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -100_000f) }
            }
            compose.mainClock.advanceTimeBy(1_000)
            compose.waitForIdle()
            instrumentation.waitForIdleSync()
            instrumentation.uiAutomation.waitForIdle(300, 5000)
            instrumentation.uiAutomation.takeScreenshot()?.recycle()
            android.os.SystemClock.sleep(200)
        assertEquals("Screenshot must show the app, without a system dialog", instrumentation.targetContext.packageName,
            instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString())
        val image = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
            val output = File(instrumentation.targetContext.cacheDir, "qa/summary-screens/$name.png")
            output.parentFile!!.mkdirs()
            output.outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            image.recycle()
        }
        try {
            waitFor("New here? Create an account")
            assertTrue(session.state.value is SessionState.SignedOut)
            compose.onNodeWithText("New here? Create an account").performScrollTo().performClick()
            val password = UUID.randomUUID().toString() + "Az9!"
            compose.onNodeWithContentDescription("Name").performScrollTo().performTextReplacement(name)
            compose.onNodeWithContentDescription("Email").performScrollTo().performTextReplacement(email)
            compose.onNodeWithContentDescription("Password").performScrollTo().performTextReplacement(password)
            compose.onNodeWithContentDescription("Confirm password").performScrollTo().performTextReplacement(password)
            fun field(label: String) = compose.onNodeWithContentDescription(label).fetchSemanticsNode()
                .config[SemanticsProperties.EditableText].text
            val inputChecks = mapOf("name_matches" to (field("Name") == name), "email_matches" to (field("Email") == email),
                "password_length_matches" to (field("Password").length == password.length),
                "confirmation_length_matches" to (field("Confirm password").length == password.length))
            evidence.put("input_checks", JSONObject(inputChecks))
            assertTrue("Registration input has not settled", inputChecks.values.all { it })
            compose.onNodeWithText("Create account").performScrollTo().assertIsEnabled()
                .performSemanticsAction(SemanticsActions.OnClick) { it() }
            try {
                waitFor("Log manually")
            } catch (error: AssertionError) {
                val labels = listOf(R.string.error_name_length, R.string.error_email_format, R.string.error_password_length,
                    R.string.error_password_mismatch, R.string.error_email_exists, R.string.error_field_invalid,
                    R.string.error_network, R.string.error_account_unavailable, R.string.error_rate_limited,
                    R.string.error_secure_storage, R.string.error_account_request).map(instrumentation.targetContext::getString)
                evidence.put("visible_errors", JSONArray(labels.filter { compose.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty() }))
                evidence.put("session_phase", session.state.value.javaClass.simpleName)
                file.parentFile!!.mkdirs()
                file.writeText(evidence.toString(2))
                throw error
            }
            val user = (session.state.value as SessionState.Authenticated).user
            evidence.put("user_id", user.id)
            record("register")
            assertEquals(0, success(core.dashboard()).progress.xp)
            compose.onNodeWithText("Nothing logged today yet.").assertExists()
            capture("remote-home-empty")
            if (args.getString("verifyEmptyGuideCatalog") == "true") {
                assertTrue("This live check requires an empty published catalog", success(core.guides("")).isEmpty())
                tab("Guide")
                waitFor("No guides found")
                compose.onNodeWithText("Reload guides").assertExists()
                capture("remote-guide-empty")
                compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Reload guides"))
                compose.onNodeWithText("Reload guides").performClick()
                waitFor("No guides found")
                record("ui_empty_published_guide_catalog")
                tab("Home")
            }
            compose.onNodeWithText("Log manually").performScrollTo().performClick()
            compose.onNodeWithText("Recycle").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Category").performScrollTo().performClick()
            compose.onNode(hasText("Plastic") and hasAnyAncestor(isPopup())).performClick()
            compose.onNodeWithContentDescription("Weight").performScrollTo().performTextReplacement("250")
            waitEnabled("Save log")
            compose.onNodeWithText("Save log").performScrollTo().performClick()
            waitFor("+10 XP for this new activity.")
            compose.onNodeWithText("Back to home").performScrollTo().performClick()
            waitFor("250 g")
            compose.onNodeWithText("1 activities logged").assertExists()
            capture("remote-home-created")
            record("ui_create_updates_dashboard")

            tab("Stats")
            waitFor("250 g")
            compose.onNodeWithText("7 days").assertIsSelected()
            compose.onNodeWithText("30 days").performClick()
            compose.waitUntil(30_000) { compose.onAllNodesWithTag("statistics-summary").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("30 days").assertIsSelected()
            compose.onNode(hasText("250 g") and hasAnyAncestor(hasTestTag("statistics-summary"))).assertExists()
            capture("remote-statistics-month")
            tab("Profile")
            waitFor("10 XP from 1 active logs")
            capture("remote-profile-created")
            record("ui_statistics_and_profile_use_server_progress")
            tab("Stats")
            compose.onNodeWithText("30 days").assertIsSelected()
            tab("Home")
            compose.onNode(hasText("Plastic") and hasClickAction()).performScrollTo().performClick()
            waitEnabled("Edit log")
            compose.onNodeWithText("Edit log").performScrollTo().performClick()
            waitFor("Save changes")
            compose.onNodeWithContentDescription("Unit").performScrollTo().performClick()
            compose.onNode(hasText("Items") and hasAnyAncestor(isPopup())).performClick()
            compose.onNodeWithContentDescription("Item count").performScrollTo().performTextReplacement("3")
            waitEnabled("Save changes")
            compose.onNodeWithText("Save changes").performScrollTo().performClick()
            waitFor("Your log was already saved. No extra XP.")
            compose.onNodeWithText("Back to home").performScrollTo().performClick()
            waitFor("3 items handled")
            capture("remote-home-counted")
            tab("Stats")
            waitFor("3 items handled")
            compose.onNodeWithText("30 days").assertIsSelected()
            compose.onNodeWithTag("daily-count-chart").performScrollTo().assertIsDisplayed()
            capture("remote-statistics-counted")
            tab("Profile")
            waitFor("10 XP from 1 active logs")
            record("ui_edit_refreshes_units_without_extra_xp")

            tab("Home")
            compose.onNode(hasText("Plastic") and hasClickAction()).performScrollTo().performClick()
            waitEnabled("Delete log")
            compose.onNodeWithText("Delete log").performScrollTo().performClick()
            compose.onNodeWithText("Clear", useUnmergedTree = true).performClick()
            compose.waitUntil(30_000) { compose.onAllNodesWithText("Delete this log?").fetchSemanticsNodes().isEmpty() }
            tab("Stats")
            waitFor("No logs in this period")
            compose.onNodeWithText("30 days").assertIsSelected()
            capture("remote-statistics-deleted")
            tab("Profile")
            waitFor("0 XP from 0 active logs")
            capture("remote-profile-deleted")
            val dashboard = success(core.dashboard())
            assertTrue(dashboard.recentLogs.isEmpty())
            assertEquals(0, dashboard.progress.xp)
            assertEquals(QuantityTotals(0, 0), dashboard.summary.handled)
            assertEquals(0L, success(core.statistics(ActivityDateRange.lastDays(dashboard.date, 30))).summary.activityCount)
            record("ui_delete_refreshes_all_server_aggregates")
            compose.onNodeWithText("Sign out").performScrollTo().performClick()
            compose.onNode(hasText("Sign out") and hasAnyAncestor(isDialog())).performClick()
            waitFor("New here? Create an account")
            assertTrue(session.state.value is SessionState.SignedOut)
            record("ui_logout_returns_to_login")
        } finally {
            val own = (session.state.value as? SessionState.Authenticated)?.user?.takeIf { it.email == email }
            if (own != null) {
                session.logout()
                success(LocalEditorDraftStore.create(instrumentation.targetContext, own.id).clear(null))
            }
        }
    }

    private fun <T> success(result: ApiResult<T>): T {
        assertTrue("Expected success: $result", result is ApiResult.Success)
        return (result as ApiResult.Success).value
    }
}
