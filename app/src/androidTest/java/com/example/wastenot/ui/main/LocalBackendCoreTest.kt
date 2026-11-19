package com.example.wastenot.ui.main

import android.os.Process
import android.os.Debug
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Base64
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.BuildConfig
import com.example.wastenot.MainActivity
import com.example.wastenot.R
import com.example.wastenot.ThemeFixtureRule
import com.example.wastenot.WasteNotApplication
import com.example.wastenot.data.local.KeystoreRefreshTokenStore
import com.example.wastenot.data.local.LocalEditorDraftStore
import com.example.wastenot.data.local.TokenReadResult
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.data.network.WasteNotNetwork
import com.example.wastenot.data.repository.ActivityInput
import com.example.wastenot.data.repository.SessionState
import com.example.wastenot.model.DisposalMethod
import com.example.wastenot.model.Quantity
import com.example.wastenot.model.QuantityTotals
import com.example.wastenot.model.WasteCategory
import java.util.UUID
import java.io.File
import java.time.ZoneId
import java.time.Instant
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.json.JSONObject

class LocalBackendCoreTest {
    @get:Rule(order = 0) val appearance = ThemeFixtureRule()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    private val container get() = (compose.activity.application as WasteNotApplication).container
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val tokenStore get() = KeystoreRefreshTokenStore.create(instrumentation.targetContext)

    @Test fun rejectsDuplicateEmailAndInvalidPasswordsWithoutStartingSession() = runTest(timeout = 3.minutes) {
        val runId = requireLocalProbe()
        val email = "auth-$runId@example.test"
        val password = UUID.randomUUID().toString()
        assertSignedOut()
        try {
            register(email, password)
            waitFor("Log manually")
            val original = (container.sessions.state.value as SessionState.Authenticated).user
            assertTrue(container.sessions.logout().serverRevoked)
            assertSignedOut()

            register(email, password)
            waitFor("This email is already in use.")
            assertNoSession()
            tap("Already have an account? Sign in")
            fill("Email", email)
            fill("Password", "pendek")
            tap("Sign in")
            waitFor("Passwords need 12 to 128 characters, including spaces.")
            assertNoSession()

            fill("Password", UUID.randomUUID().toString())
            tap("Sign in")
            waitFor("That email and password don't match.")
            assertNoSession()

            fill("Password", password)
            tap("Sign in")
            waitFor("Log manually")
            assertEquals(original, (container.sessions.state.value as SessionState.Authenticated).user)
            assertTrue(tokenStore.read() is TokenReadResult.Available)
        } finally {
            if (container.sessions.state.value is SessionState.Authenticated) container.sessions.logout()
        }
    }

    @Test fun savesRecycleRecordBeforeProcessRestart() = runTest(timeout = 3.minutes) {
        val runId = requireLocalProbe()
        createRecycleDraft(runId)
        tap("Save log")
        waitFor("+10 XP for this new activity.")
        val logId = verifyOnlySavedRecord(runId)
        tap("Back to home")
        waitFor("Log manually")
        assertTrue(tokenStore.read() is TokenReadResult.Available)
        instrumentation.targetContext.cacheDir.resolve("core-restart-$runId")
            .writeText("${Process.myPid()}\n$logId")
        println("CORE_RESTART_SAVED pid=${Process.myPid()} log=$logId")
    }

    @Test fun opensSameRecycleRecordAfterProcessRestart() = runTest(timeout = 3.minutes) {
        val runId = requireLocalProbe()
        val marker = instrumentation.targetContext.cacheDir.resolve("core-restart-$runId")
        val (priorPid, logId) = marker.readLines()
        assertNotEquals(priorPid.toInt(), Process.myPid())
        try {
            waitFor("Log manually")
            val restored = (container.sessions.state.value as SessionState.Authenticated).user
            assertEquals("record-$runId@example.test", restored.email)
            compose.onNode(hasText("History") and isSelectable()).performClick()
            waitFor("250 g")
            compose.onNodeWithText("250 g").performClick()
            waitFor("Log details")
            compose.onNodeWithText("QA note $runId").performScrollTo().assertIsDisplayed()
            assertEquals(logId, verifyOnlySavedRecord(runId))
            println("CORE_RESTART_RESTORED pid=${Process.myPid()} log=$logId")
        } finally {
            if (container.sessions.state.value is SessionState.Authenticated) container.sessions.logout()
            marker.delete()
        }
    }

    @Test fun replaysCommittedWriteAfterConnectionDrops() = runTest(timeout = 3.minutes) {
        val runId = requireLocalProbe(port = 18091)
        try {
            createRecycleDraft(runId)
            tap("Save log")
            waitFor("We don't know whether this was saved. Try again with the same entries before changing the log.")
            compose.onNodeWithContentDescription("Weight").performScrollTo().assertTextContains("250")
            captureUiPreview(compose, "unknown-outcome", "local-write-recovery", capturePanel = false)

            tap("Try again")
            waitFor("Your log was already saved. No extra XP.")
            val logId = verifyOnlySavedRecord(runId)
            println("CORE_WRITE_REPLAYED log=$logId")
            captureUiPreview(compose, "replayed-write", "local-write-recovery", capturePanel = false)
        } finally {
            if (container.sessions.state.value is SessionState.Authenticated) container.sessions.logout()
        }
    }

    @Test fun paginatesOneThousandRecordsFromGoBackend() = runTest(timeout = 5.minutes) {
        val runId = requireLocalProbe()
        assertSignedOut()
        try {
            success(container.sessions.register("Android QA 112", "history-$runId@example.test",
                UUID.randomUUID().toString(), ZoneId.of("Asia/Jakarta")))
            waitFor("Log manually")
            // The host seeds this disposable account in the isolated database.
            withContext(Dispatchers.IO) {
                withTimeout(40_000) {
                    while (success(container.core.dashboard()).summary.activityCount != 1_000L) delay(500)
                }
            }
            compose.onNode(hasText("History") and isSelectable()).performClick()
            waitFor("20 logs loaded.")
            val list = compose.onNodeWithTag("history-list")
            val output = File(instrumentation.targetContext.getExternalFilesDir(null), "history-go-1000").apply { mkdirs() }
            fun graphics(suffix: String = "") = ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand("dumpsys gfxinfo ${instrumentation.targetContext.packageName}$suffix")
            ).bufferedReader().use { it.readText() }
            graphics(" reset")
            val startPss = Debug.getPss()
            val startedAt = SystemClock.elapsedRealtime()
            for (loaded in 20 until 1_000 step 20) {
                compose.waitUntil(15_000) {
                    runCatching {
                        list.performScrollToIndex(loaded + 2)
                        compose.onNodeWithText("Load more logs").isDisplayed()
                    }.getOrDefault(false)
                }
                compose.onNodeWithText("Load more logs").performClick()
            }
            compose.waitUntil(15_000) {
                runCatching {
                    list.performScrollToIndex(1_002)
                    compose.onNodeWithText("All logs loaded.").isDisplayed()
                }.getOrDefault(false)
            }
            list.performScrollToIndex(0)
            compose.onNodeWithText("1.000 logs loaded.").assertIsDisplayed()
            repeat(4) { list.performTouchInput { swipeUp() } }
            repeat(4) { list.performTouchInput { swipeDown() } }
            list.performScrollToIndex(0)
            File(output, "gfxinfo.txt").writeText(graphics())
            File(output, "result.txt").writeText(
                "records=1000\npages=50\nelapsed_ms=${SystemClock.elapsedRealtime() - startedAt}\n" +
                    "start_pss_kib=$startPss\nend_pss_kib=${Debug.getPss()}\n"
            )
            captureUiPreview(compose, "history-go-1000", "history-go-1000", capturePanel = false)
        } finally {
            if (container.sessions.state.value is SessionState.Authenticated) container.sessions.logout()
        }
    }

    @Test fun newerCombinedHistoryFilterWinsWhenOlderResponseArrivesLate() = runTest(timeout = 3.minutes) {
        val runId = requireLocalProbe(port = 18094)
        assertSignedOut()
        try {
            success(container.sessions.register("Android QA 112", "history-race-$runId@example.test",
                UUID.randomUUID().toString(), ZoneId.of("Asia/Jakarta")))
            waitFor("Log manually")
            val today = success(container.core.dashboard()).date
            for ((category, method, note) in listOf(
                Triple(WasteCategory.PLASTIC, DisposalMethod.RECYCLE, "QA previous filter"),
                Triple(WasteCategory.PLASTIC, DisposalMethod.TRASH, "QA excluded plastic"),
                Triple(WasteCategory.PAPER, DisposalMethod.RECYCLE, "QA combined filter"),
                Triple(WasteCategory.PAPER, DisposalMethod.TRASH, "QA excluded method"),
            )) {
                success(container.core.createLog(ActivityInput(category, method, Quantity.Weight.of(250),
                    today, note), UUID.randomUUID().toString()))
            }
            compose.onNode(hasText("History") and isSelectable()).performClick()
            waitFor("4 logs loaded.")

            fun openFilter() = compose.onNodeWithContentDescription("Filter history").performClick()
            fun choose(label: String, option: String) {
                compose.onNodeWithContentDescription(label).performScrollTo().performClick()
                compose.onNode(hasText(option) and hasAnyAncestor(isPopup())).performClick()
            }
            openFilter()
            choose("Category", "Plastic")
            tap("Apply filters")
            openFilter()
            choose("Category", "Paper")
            choose("Method", "Recycle")
            tap("Apply filters")
            waitFor("1 matching logs loaded.")
            withContext(Dispatchers.IO) { delay(7_500) }
            compose.onNodeWithText("QA combined filter").assertIsDisplayed()
            compose.onNodeWithText("QA previous filter").assertDoesNotExist()
            compose.onNodeWithText("QA excluded method").assertDoesNotExist()
            captureUiPreview(compose, "history-filter-race", "history-filter-race", capturePanel = false)
            openFilter()
            choose("Category", "Plastic")
            tap("Apply filters")
            waitFor("QA previous filter")
            compose.onNodeWithText("1 matching logs loaded.").assertExists()
            compose.onNodeWithText("QA excluded plastic").assertDoesNotExist()
        } finally {
            if (container.sessions.state.value is SessionState.Authenticated) container.sessions.logout()
        }
    }

    @Test fun goRejectsQuantityAndDateOutsideSupportedLimits() = runTest(timeout = 3.minutes) {
        val runId = requireLocalProbe()
        assertSignedOut()
        try {
            success(container.sessions.register("Android QA 112", "limits-$runId@example.test",
                UUID.randomUUID().toString(), ZoneId.of("Asia/Jakarta")))
            val today = success(container.core.dashboard()).date
            val network = checkNotNull(WasteNotNetwork.create(BuildConfig.API_BASE_URL, allowLocalHttp = true))
            suspend fun reject(kind: String, weight: Number?, items: Number?, age: Long, field: String? = null) {
                val input = buildJsonObject {
                    put("client_request_id", UUID.randomUUID().toString())
                    put("category", "PLASTIC")
                    put("method", "RECYCLE")
                    put("quantity_kind", kind)
                    weight?.let { put("weight_g", it) }
                    items?.let { put("item_count", it) }
                    put("activity_date", today.minusDays(age).toString())
                }
                val result = container.sessions.authenticated { credential ->
                    network.executor.execute({ Unit }) { network.api.createLog(input, credential) }
                }
                check(result is ApiResult.Failure)
                if (field == null) {
                    assertEquals(400, result.error.status)
                    assertEquals(ApiFailureKind.INVALID_REQUEST, result.error.kind)
                } else {
                    assertEquals(422, result.error.status)
                    assertEquals(ApiFailureKind.VALIDATION, result.error.kind)
                    assertTrue("$field missing in ${result.error.fields.keys}", field in result.error.fields)
                }
            }
            reject("WEIGHT", 0, null, 0, "weight_g")
            reject("WEIGHT", -1, null, 0, "weight_g")
            reject("WEIGHT", 100_001, null, 0, "weight_g")
            reject("WEIGHT", null, null, 0, "weight_g")
            reject("WEIGHT", 1, 1, 0, "weight_g")
            reject("WEIGHT", 1_234.5, null, 0)
            reject("COUNT", null, 0, 0, "item_count")
            reject("COUNT", null, 10_001, 0, "item_count")
            reject("COUNT", null, null, 0, "item_count")
            reject("COUNT", 1, 1, 0, "item_count")
            reject("COUNT", null, 2.5, 0)
            reject("WEIGHT", 250, null, 31, "activity_date")
            reject("WEIGHT", 250, null, -1, "activity_date")
            assertTrue(success(container.core.wasteLogs()).isEmpty())

            val accepted = listOf(Quantity.Weight.of(1), Quantity.Weight.of(100_000),
                Quantity.Count.of(1), Quantity.Count.of(10_000))
            accepted.forEach { quantity ->
                val record = ActivityInput(WasteCategory.PLASTIC, DisposalMethod.RECYCLE,
                    quantity, today.minusDays(30), "QA valid boundary")
                assertEquals(quantity, success(container.core.createLog(record, UUID.randomUUID().toString())).log.quantity)
            }
            assertEquals(accepted.toSet(), success(container.core.wasteLogs()).map { it.quantity }.toSet())
            assertEquals(40, success(container.core.dashboard()).progress.xp)
        } finally {
            if (container.sessions.state.value is SessionState.Authenticated) container.sessions.logout()
        }
    }

    @Test fun statisticsSeparatesUnitsAndDeleteRecomputesMultiDayStreak() = runTest(timeout = 3.minutes) {
        val runId = requireLocalProbe()
        assertSignedOut()
        try {
            success(container.sessions.register("Android QA 112", "summary-$runId@example.test",
                UUID.randomUUID().toString(), ZoneId.of("Asia/Jakarta")))
            waitFor("Log manually")
            val today = success(container.core.dashboard()).date
            suspend fun save(method: DisposalMethod, quantity: Quantity, daysAgo: Long, note: String) =
                success(container.core.createLog(ActivityInput(WasteCategory.PLASTIC, method, quantity,
                    today.minusDays(daysAgo), note), UUID.randomUUID().toString())).log
            fun tab(label: String) = compose.onNode(hasText(label) and isSelectable()).performClick()

            save(DisposalMethod.REDUCE, Quantity.Count.of(2), 0, "QA avoid only")
            tab("Stats")
            val noHandling = instrumentation.targetContext.getString(R.string.rate_no_handled)
            waitFor(noHandling)
            compose.onNodeWithText(noHandling).performScrollTo().assertIsDisplayed()
            val reduceOnly = success(container.core.dashboard()).summary
            assertNull(reduceOnly.recyclingRatePercent)
            assertEquals(QuantityTotals(0, 0), reduceOnly.handled)
            assertEquals(QuantityTotals(0, 2), reduceOnly.avoided)
            captureUiPreview(compose, "reduce-only", "local-core-summary", capturePanel = false)

            save(DisposalMethod.RECYCLE, Quantity.Weight.of(250), 0, "QA weight today")
            save(DisposalMethod.RECYCLE, Quantity.Count.of(3), 1, "QA remove middle day")
            save(DisposalMethod.REUSE, Quantity.Weight.of(250), 2, "QA older weight")
            tap("30 days")
            compose.onNodeWithText("30 days").assertIsSelected()
            waitFor("500 g")
            compose.onNodeWithText("3 items handled").performScrollTo().assertIsDisplayed()
            captureUiPreview(compose, "mixed-units", "local-core-summary", capturePanel = false)
            val before = success(container.core.dashboard()).progress
            assertEquals(3, before.currentStreak)
            assertEquals(40, before.xp)

            tab("History")
            waitFor("4 logs loaded.")
            compose.onNodeWithTag("history-list").performScrollToNode(hasText("QA remove middle day"))
            compose.onNodeWithText("QA remove middle day").performClick()
            waitFor("Delete log")
            tap("Delete log")
            compose.onNode(hasText("Clear") and hasAnyAncestor(isDialog())).performClick()
            waitFor("Log deleted.")
            val after = success(container.core.dashboard()).progress
            assertEquals(1, after.currentStreak)
            assertEquals(30, after.xp)
            tab("Profile")
            waitFor("30 XP from 3 active logs")
            compose.onNodeWithText("1-day streak").performScrollTo().assertIsDisplayed()
            captureUiPreview(compose, "streak-after-delete", "local-core-summary", capturePanel = false)
        } finally {
            if (container.sessions.state.value is SessionState.Authenticated) container.sessions.logout()
        }
    }

    @Test fun accessTokenExpiresOnItsOwnAndOneRefreshRestoresTheRequest() = runTest(timeout = 18.minutes) {
        val runId = requireLocalProbe()
        assumeTrue(InstrumentationRegistry.getArguments().getString("verifyNaturalExpiry") == "true")
        assertSignedOut()
        try {
            val user = success(container.sessions.register("Android QA 112", "expiry-$runId@example.test",
                UUID.randomUUID().toString(), ZoneId.of("Asia/Jakarta")))
            waitFor("Log manually")
            val initial = success(container.sessions.authenticated { ApiResult.Success(it) })
            val claims = JSONObject(String(Base64.decode(initial.token.value.split('.')[1],
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING), Charsets.UTF_8))
            val expiry = Instant.ofEpochSecond(claims.getLong("exp"))
            assertEquals(15.minutes.inWholeSeconds, expiry.epochSecond - claims.getLong("iat"))
            val delayMs = expiry.toEpochMilli() - System.currentTimeMillis() + 5_000
            check(delayMs in 1L..930_000L) { "Unexpected remaining access lifetime: ${delayMs}ms" }
            val network = checkNotNull(WasteNotNetwork.create(BuildConfig.API_BASE_URL, allowLocalHttp = true))
            assertEquals(200, network.api.me(initial).code())
            println("CORE_NATURAL_EXPIRY waiting ${delayMs}ms for signed access expiry")
            withContext(Dispatchers.IO) { delay(delayMs) }
            val statuses = mutableListOf<Int>()
            val restored = success(container.sessions.authenticated { credential ->
                network.executor.execute({ it.toModel() }) {
                    network.api.me(credential).also { statuses += it.code() }
                }
            })
            assertEquals(listOf(401, 200), statuses)
            assertEquals(user.id, restored.id)
            val updated = success(container.sessions.authenticated { ApiResult.Success(it) })
            assertEquals(initial.epoch, updated.epoch)
            assertEquals(initial.revision + 1, updated.revision)
            assertNotEquals(initial.token, updated.token)
            waitFor("Log manually")
            println("CORE_NATURAL_EXPIRY requests=401,200 rotations=1")
        } finally {
            if (container.sessions.state.value is SessionState.Authenticated) container.sessions.logout()
        }
    }

    @Test fun persistsUnsubmittedDraftBeforeProcessRestart() = runTest(timeout = 3.minutes) {
        val runId = requireLocalProbe()
        assertSignedOut()
        register("draft-$runId@example.test", UUID.randomUUID().toString())
        waitFor("Log manually")
        val user = (container.sessions.state.value as SessionState.Authenticated).user
        tap("Log manually")
        val note = "QA draft $runId"
        fill("Notes (optional)", note)
        val store = LocalEditorDraftStore.create(instrumentation.targetContext, user.id)
        compose.waitUntil(10_000) { runBlocking { success(store.read())?.fields?.get(12) == note } }
        val draft = checkNotNull(success(store.read()))
        instrumentation.targetContext.cacheDir.resolve("core-draft-$runId")
            .writeText("${Process.myPid()}\n${user.id}\n${draft.requestId}")
        assertTrue(success(container.core.wasteLogs()).isEmpty())
    }

    @Test fun restoresUnsubmittedDraftForSameAccountAfterProcessRestart() = runTest(timeout = 3.minutes) {
        val runId = requireLocalProbe()
        val marker = instrumentation.targetContext.cacheDir.resolve("core-draft-$runId")
        val (priorPid, owner, requestId) = marker.readLines()
        assertNotEquals(priorPid.toInt(), Process.myPid())
        try {
            waitFor("Draft restored. Check your entries before saving.")
            val user = (container.sessions.state.value as SessionState.Authenticated).user
            assertEquals(owner, user.id)
            compose.onNodeWithContentDescription("Notes (optional)").performScrollTo().assertTextContains("QA draft $runId")
            val store = LocalEditorDraftStore.create(instrumentation.targetContext, user.id)
            assertEquals(requestId, checkNotNull(success(store.read())).requestId)
            assertTrue(success(container.core.wasteLogs()).isEmpty())
            assertTrue(container.sessions.logout().serverRevoked)
            waitFor("New here? Create an account")
            val password = compose.onNodeWithContentDescription("Password").performScrollTo()
                .fetchSemanticsNode().config[SemanticsProperties.EditableText].text
            assertEquals("", password)
            println("CORE_DRAFT_RESTORED previous_pid=$priorPid current_pid=${Process.myPid()}")
        } finally {
            if (container.sessions.state.value is SessionState.Authenticated) container.sessions.logout()
            success(LocalEditorDraftStore.create(instrumentation.targetContext, owner).clear(null))
            marker.delete()
        }
    }

    private fun requireLocalProbe(port: Int = 18089): UUID {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("verifyLocalCore") == "true")
        assertTrue(BuildConfig.DEBUG)
        assertEquals("http://10.0.2.2:$port/v1/", BuildConfig.API_BASE_URL)
        return UUID.fromString(requireNotNull(arguments.getString("fixtureRunId")))
    }

    private fun register(email: String, password: String) {
        tap("New here? Create an account")
        fill("Name", "Android QA 112")
        fill("Email", email)
        fill("Password", password)
        fill("Confirm password", password)
        tap("Create account")
    }

    private suspend fun createRecycleDraft(runId: UUID) {
        assertSignedOut()
        register("record-$runId@example.test", UUID.randomUUID().toString())
        waitFor("Log manually")
        tap("Log manually")
        tap("Recycle")
        compose.onNodeWithContentDescription("Category").performScrollTo().performClick()
        compose.onNode(hasText("Plastic") and hasAnyAncestor(isPopup())).performClick()
        fill("Weight", "250")
        fill("Notes (optional)", "QA note $runId")
    }

    private suspend fun assertSignedOut() {
        waitFor("New here? Create an account")
        assertNoSession()
    }

    private suspend fun assertNoSession() {
        assertTrue(container.sessions.state.value is SessionState.SignedOut)
        assertEquals(TokenReadResult.Absent, tokenStore.read())
        compose.onNodeWithText("Log manually").assertDoesNotExist()
    }

    private suspend fun verifyOnlySavedRecord(runId: UUID): String {
        val log = success(container.core.wasteLogs()).single()
        assertEquals(WasteCategory.PLASTIC, log.category)
        assertEquals(DisposalMethod.RECYCLE, log.method)
        assertEquals(Quantity.Weight.of(250), log.quantity)
        assertEquals("QA note $runId", log.notes)
        assertEquals(10, success(container.core.dashboard()).progress.xp)
        return log.id
    }

    private fun waitFor(text: String) {
        compose.waitUntil(30_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun tap(text: String) {
        compose.onNode(hasText(text) and hasClickAction()).performScrollTo().assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
    }

    private fun fill(label: String, value: String) {
        compose.onNodeWithContentDescription(label).performScrollTo().performTextReplacement(value)
    }

    private fun <T> success(result: ApiResult<T>): T {
        check(result is ApiResult.Success) { "Local backend returned ${(result as? ApiResult.Failure)?.error?.kind}" }
        return result.value
    }
}
