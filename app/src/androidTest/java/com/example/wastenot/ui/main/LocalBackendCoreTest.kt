package com.example.wastenot.ui.main

import android.os.Process
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.BuildConfig
import com.example.wastenot.MainActivity
import com.example.wastenot.ThemeFixtureRule
import com.example.wastenot.WasteNotApplication
import com.example.wastenot.data.local.KeystoreRefreshTokenStore
import com.example.wastenot.data.local.TokenReadResult
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.repository.SessionState
import com.example.wastenot.model.DisposalMethod
import com.example.wastenot.model.Quantity
import com.example.wastenot.model.WasteCategory
import java.util.UUID
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

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
            waitFor("Input manual")
            val original = (container.sessions.state.value as SessionState.Authenticated).user
            assertTrue(container.sessions.logout().serverRevoked)
            assertSignedOut()

            register(email, password)
            waitFor("Email ini sudah digunakan.")
            assertNoSession()
            tap("Sudah punya akun? Masuk")
            fill("Email", email)
            fill("Kata sandi", "pendek")
            tap("Masuk")
            waitFor("Kata sandi perlu 12–128 karakter, termasuk spasi.")
            assertNoSession()

            fill("Kata sandi", UUID.randomUUID().toString())
            tap("Masuk")
            waitFor("Email atau kata sandi belum cocok.")
            assertNoSession()

            fill("Kata sandi", password)
            tap("Masuk")
            waitFor("Input manual")
            assertEquals(original, (container.sessions.state.value as SessionState.Authenticated).user)
            assertTrue(tokenStore.read() is TokenReadResult.Available)
        } finally {
            if (container.sessions.state.value is SessionState.Authenticated) container.sessions.logout()
        }
    }

    @Test fun savesRecycleRecordBeforeProcessRestart() = runTest(timeout = 3.minutes) {
        val runId = requireLocalProbe()
        assertSignedOut()
        register("record-$runId@example.test", UUID.randomUUID().toString())
        waitFor("Input manual")
        tap("Input manual")
        tap("Recycle")
        compose.onNodeWithContentDescription("Kategori").performScrollTo().performClick()
        compose.onNode(hasText("Plastik") and hasAnyAncestor(isPopup())).performClick()
        fill("Berat", "250")
        fill("Catatan (opsional)", "Catatan QA $runId")
        tap("Simpan catatan")
        waitFor("+10 XP untuk aktivitas baru ini.")
        val logId = verifyOnlySavedRecord(runId)
        tap("Kembali ke beranda")
        waitFor("Input manual")
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
            waitFor("Input manual")
            val restored = (container.sessions.state.value as SessionState.Authenticated).user
            assertEquals("record-$runId@example.test", restored.email)
            compose.onNode(hasText("Riwayat") and isSelectable()).performClick()
            waitFor("250 g")
            compose.onNodeWithText("250 g").performClick()
            waitFor("Detail catatan")
            compose.onNodeWithText("Catatan QA $runId").performScrollTo().assertIsDisplayed()
            assertEquals(logId, verifyOnlySavedRecord(runId))
            println("CORE_RESTART_RESTORED pid=${Process.myPid()} log=$logId")
        } finally {
            if (container.sessions.state.value is SessionState.Authenticated) container.sessions.logout()
            marker.delete()
        }
    }

    private fun requireLocalProbe(): UUID {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("verifyLocalCore") == "true")
        assertTrue(BuildConfig.DEBUG)
        assertEquals("http://10.0.2.2:18089/v1/", BuildConfig.API_BASE_URL)
        return UUID.fromString(requireNotNull(arguments.getString("fixtureRunId")))
    }

    private fun register(email: String, password: String) {
        tap("Belum punya akun? Daftar")
        fill("Nama", "Android QA 112")
        fill("Email", email)
        fill("Kata sandi", password)
        fill("Konfirmasi kata sandi", password)
        tap("Buat akun")
    }

    private suspend fun assertSignedOut() {
        waitFor("Belum punya akun? Daftar")
        assertNoSession()
    }

    private suspend fun assertNoSession() {
        assertTrue(container.sessions.state.value is SessionState.SignedOut)
        assertEquals(TokenReadResult.Absent, tokenStore.read())
        compose.onNodeWithText("Input manual").assertDoesNotExist()
    }

    private suspend fun verifyOnlySavedRecord(runId: UUID): String {
        val log = success(container.core.wasteLogs()).single()
        assertEquals(WasteCategory.PLASTIC, log.category)
        assertEquals(DisposalMethod.RECYCLE, log.method)
        assertEquals(Quantity.Weight.of(250), log.quantity)
        assertEquals("Catatan QA $runId", log.notes)
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
