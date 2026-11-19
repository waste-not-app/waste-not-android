package com.example.wastenot.ui.main

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.data.dummy.DummyActivityStore
import com.example.wastenot.data.dummy.DummyGuideStore
import com.example.wastenot.data.dummy.DummyScanRepository
import com.example.wastenot.data.local.LocalEditorDraftStore
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.repository.ActivityStore
import com.example.wastenot.model.*
import com.example.wastenot.theme.LocalWasteNotGradients
import com.example.wastenot.theme.ThemeMode
import com.example.wastenot.theme.WasteNotTheme
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AccountDayNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val zone = ZoneId.of("Asia/Jakarta")
    private val dark = InstrumentationRegistry.getArguments().getString("rolloverDark") == "true"
    private val mode get() = if (dark) "dark" else "light"
    private val clock = AdjustableClock(Instant.parse("2026-11-09T16:59:59.750Z"))
    private val dashboardDates = ConcurrentLinkedQueue<LocalDate>()
    private val ranges = ConcurrentLinkedQueue<ActivityDateRange>()
    private val owner = "account-day-${UUID.randomUUID()}"
    private val preferencesName = "qa-$owner"

    @After fun cleanup() {
        compose.activityRule.scenario.close()
        runBlocking { assertTrue(LocalEditorDraftStore.create(context, owner).clear(null) is ApiResult.Success) }
        assertTrue(context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE).edit().clear().commit())
    }

    private fun showNavigation() {
        val fixture = DummyActivityStore(context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE), zone,
            today = { LocalDate.now(clock.withZone(zone)) })
        val source = object : ActivityStore by fixture {
            override val referenceDate: LocalDate? = null
            override suspend fun dashboard(): ApiResult<DashboardSnapshot> {
                val result = fixture.dashboard()
                if (result is ApiResult.Success) {
                    val date = result.value.date
                    dashboardDates.add(date)
                    return ApiResult.Success(result.value.copy(progress = result.value.progress.copy(
                        currentStreak = if (date in LocalDate.of(2026, 11, 9)..LocalDate.of(2026, 11, 10)) 2 else 0,
                        bestStreak = 2)))
                }
                return result
            }
            override suspend fun statistics(range: ActivityDateRange): ApiResult<StatisticsSnapshot> {
                ranges.add(range)
                return fixture.statistics(range)
            }
        }
        val user = User(owner, "Test User", "day@example.test", zone, null)
        compose.setContent {
            WasteNotTheme(dark) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxSize().background(LocalWasteNotGradients.current.canvas)) {
                        AccountNavigation(user, source, DummyGuideStore(), { DummyScanRepository() },
                            if (dark) ThemeMode.DARK else ThemeMode.LIGHT, {}, {},
                            Modifier.fillMaxSize(), clock = clock)
                    }
                }
            }
        }
        compose.waitUntil(20_000) { compose.onAllNodesWithText("650 g").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }

    @Test fun foregroundMidnightAndResumeRefreshTheSameSelectedPeriod() {
        showNavigation()
        tab("Stats")
        compose.onNodeWithText("30 days").performClick()
        waitFor("11 Okt 2026 to 9 Nov 2026")
        capture("statistics-before-midnight")
        clock.set(Instant.parse("2026-11-09T17:00:00Z"))
        try {
            compose.waitUntil("Account day must refresh dashboard and statistics without navigation", 6_000) {
                dashboardDates.lastOrNull() == LocalDate.of(2026, 11, 10) &&
                    ranges.lastOrNull()?.to == LocalDate.of(2026, 11, 10)
            }
        } finally {
            capture("statistics-at-day-boundary")
        }
        compose.onNodeWithText("30 days").assertIsSelected()
        waitFor("12 Okt 2026 to 10 Nov 2026")
        tab("Home")
        waitFor("Selasa, 10 November")
        compose.onNodeWithText("Nothing logged today yet.").assertExists()
        capture("home-after-midnight")
        tab("Profile")
        compose.onNodeWithText("You haven't logged today yet.").performScrollTo().assertIsDisplayed()
        capture("profile-yesterday-streak")

        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        clock.set(Instant.parse("2026-11-11T17:00:00Z"))
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitUntil("Resuming must read the current account day immediately", 6_000) {
            dashboardDates.lastOrNull() == LocalDate.of(2026, 11, 12) &&
                ranges.lastOrNull()?.to == LocalDate.of(2026, 11, 12)
        }
        tab("Stats")
        compose.onNodeWithText("30 days").assertIsSelected()
        waitFor("14 Okt 2026 to 12 Nov 2026")
        capture("statistics-after-resume")
        val dashboardCalls = dashboardDates.size
        val statisticsCalls = ranges.size
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
        assertEquals("Same-day resume must not repeatedly reload data", dashboardCalls, dashboardDates.size)
        assertEquals(statisticsCalls, ranges.size)
    }

    @Test fun yesterdayStreakExplainsThatNoActivityHasBeenRecordedToday() {
        var progress by mutableStateOf(UserProgress(5, 50, 1, 50, 50, 2, 2, false))
        compose.setContent {
            WasteNotTheme(dark) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxSize().background(LocalWasteNotGradients.current.canvas)) {
                        ProfileScreen(User(owner, "Test User", "day@example.test", zone, null), progress,
                            false, null, PaddingValues(), {}, {}, {}, {})
                    }
                }
            }
        }
        compose.onNodeWithText("Sign out").performScrollTo()
        capture("streak-today-pending")
        compose.onNodeWithText("You haven't logged today yet.").assertIsDisplayed()
        compose.runOnIdle { progress = progress.copy(hasLoggedToday = true) }
        compose.onNodeWithText("You haven't logged today yet.").assertDoesNotExist()
        capture("streak-logged-today")
        compose.runOnIdle { progress = progress.copy(hasLoggedToday = false, currentStreak = 0) }
        compose.onNodeWithText("You haven't logged today yet.").assertDoesNotExist()
        compose.runOnIdle { progress = progress.copy(currentStreak = null) }
        compose.onNodeWithText("You haven't logged today yet.").assertDoesNotExist()
    }

    @Test fun anOpenDraftKeepsItsDateAndValuesAndANewDraftUsesTheNewDay() {
        showNavigation()
        compose.onNodeWithText("Log manually").performScrollTo().performClick()
        compose.onNodeWithText("Recycle").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Weight").performScrollTo().performTextReplacement("250")
        compose.onNodeWithContentDescription("Notes (optional)").performScrollTo()
            .performTextReplacement("Note past midnight")
        compose.runOnUiThread { compose.activity.window.insetsController?.hide(android.view.WindowInsets.Type.ime()) }
        compose.onNodeWithText("9 Nov 2026").performScrollTo().assertIsDisplayed()
        capture("draft-before-midnight")
        clock.set(Instant.parse("2026-11-09T17:00:00Z"))
        compose.waitUntil("The current account day must advance while editing", 6_000) {
            dashboardDates.lastOrNull() == LocalDate.of(2026, 11, 10)
        }
        compose.onNodeWithText("9 Nov 2026").assertExists()
        assertEquals("250", editable("Weight"))
        assertEquals("Note past midnight", editable("Notes (optional)"))
        capture("draft-preserved-after-midnight")
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNode(hasText("Discard changes") and hasAnyAncestor(isDialog())).performClick()
        waitFor("Selasa, 10 November")
        compose.onNodeWithText("Log manually").performScrollTo().performClick()
        compose.onNodeWithText("10 Nov 2026").performScrollTo().assertIsDisplayed()
        assertEquals("", editable("Weight"))
        assertEquals("", editable("Notes (optional)"))
        capture("new-draft-current-day")
    }

    private fun tab(label: String) = compose.onNode(hasText(label) and isSelectable()).performClick()
    private fun waitFor(label: String) = compose.waitUntil(15_000) {
        compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
    }
    private fun editable(label: String) = compose.onNodeWithContentDescription(label).fetchSemanticsNode()
        .config[SemanticsProperties.EditableText].text
    private fun capture(name: String) = captureUiPreview(compose, "$name-$mode", "account-day", capturePanel = false)

    private class AdjustableClock(private val value: AtomicReference<Instant>, private val zone: ZoneId) : Clock() {
        constructor(initial: Instant) : this(AtomicReference(initial), ZoneId.of("UTC"))
        override fun getZone(): ZoneId = zone
        override fun withZone(zone: ZoneId): Clock = AdjustableClock(value, zone)
        override fun instant(): Instant = value.get()
        fun set(instant: Instant) { value.set(instant) }
    }
}
