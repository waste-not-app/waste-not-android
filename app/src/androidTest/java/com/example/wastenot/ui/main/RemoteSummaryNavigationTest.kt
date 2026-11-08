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
        require(base == "https://pred-autoscaler.bccdev.id/wastenot/api/v1/" || base == "http://10.0.2.2:18088/v1/")
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
                    and hasAnyDescendant(hasContentDescription("Buka profil")))
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
            waitFor("Belum punya akun? Daftar")
            assertTrue(session.state.value is SessionState.SignedOut)
            compose.onNodeWithText("Belum punya akun? Daftar").performScrollTo().performClick()
            val password = UUID.randomUUID().toString() + "Az9!"
            compose.onNodeWithContentDescription("Nama").performScrollTo().performTextReplacement(name)
            compose.onNodeWithContentDescription("Email").performScrollTo().performTextReplacement(email)
            compose.onNodeWithContentDescription("Kata sandi").performScrollTo().performTextReplacement(password)
            compose.onNodeWithContentDescription("Konfirmasi kata sandi").performScrollTo().performTextReplacement(password)
            fun field(label: String) = compose.onNodeWithContentDescription(label).fetchSemanticsNode()
                .config[SemanticsProperties.EditableText].text
            val inputChecks = mapOf("name_matches" to (field("Nama") == name), "email_matches" to (field("Email") == email),
                "password_length_matches" to (field("Kata sandi").length == password.length),
                "confirmation_length_matches" to (field("Konfirmasi kata sandi").length == password.length))
            evidence.put("input_checks", JSONObject(inputChecks))
            assertTrue("Registration input has not settled", inputChecks.values.all { it })
            compose.onNodeWithText("Buat akun").performScrollTo().assertIsEnabled()
                .performSemanticsAction(SemanticsActions.OnClick) { it() }
            try {
                waitFor("Input manual")
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
            compose.onNodeWithText("Belum ada catatan hari ini.").assertExists()
            capture("remote-home-empty")
            if (args.getString("verifyEmptyGuideCatalog") == "true") {
                assertTrue("This live check requires an empty published catalog", success(core.guides("")).isEmpty())
                tab("Panduan")
                waitFor("Panduan belum ditemukan")
                compose.onNodeWithText("Muat ulang panduan").assertExists()
                capture("remote-guide-empty")
                compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Muat ulang panduan"))
                compose.onNodeWithText("Muat ulang panduan").performClick()
                waitFor("Panduan belum ditemukan")
                record("ui_empty_published_guide_catalog")
                tab("Beranda")
            }
            compose.onNodeWithText("Input manual").performScrollTo().performClick()
            compose.onNodeWithText("Recycle").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Kategori").performScrollTo().performClick()
            compose.onNode(hasText("Plastik") and hasAnyAncestor(isPopup())).performClick()
            compose.onNodeWithContentDescription("Berat").performScrollTo().performTextReplacement("250")
            waitEnabled("Simpan catatan")
            compose.onNodeWithText("Simpan catatan").performScrollTo().performClick()
            waitFor("+10 XP untuk aktivitas baru ini.")
            compose.onNodeWithText("Kembali ke beranda").performScrollTo().performClick()
            waitFor("250 g")
            compose.onNodeWithText("1 aktivitas tercatat").assertExists()
            capture("remote-home-created")
            record("ui_create_updates_dashboard")

            tab("Statistik")
            waitFor("250 g")
            compose.onNodeWithText("7 hari").assertIsSelected()
            compose.onNodeWithText("30 hari").performClick()
            compose.waitUntil(30_000) { compose.onAllNodesWithTag("statistics-summary").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("30 hari").assertIsSelected()
            compose.onNode(hasText("250 g") and hasAnyAncestor(hasTestTag("statistics-summary"))).assertExists()
            capture("remote-statistics-month")
            tab("Profil")
            waitFor("10 XP dari 1 catatan aktif")
            capture("remote-profile-created")
            record("ui_statistics_and_profile_use_server_progress")
            tab("Statistik")
            compose.onNodeWithText("30 hari").assertIsSelected()
            tab("Beranda")
            compose.onNode(hasText("Plastik") and hasClickAction()).performScrollTo().performClick()
            waitEnabled("Ubah catatan")
            compose.onNodeWithText("Ubah catatan").performScrollTo().performClick()
            waitFor("Simpan perubahan")
            compose.onNodeWithContentDescription("Satuan").performScrollTo().performClick()
            compose.onNode(hasText("Benda") and hasAnyAncestor(isPopup())).performClick()
            compose.onNodeWithContentDescription("Jumlah benda").performScrollTo().performTextReplacement("3")
            waitEnabled("Simpan perubahan")
            compose.onNodeWithText("Simpan perubahan").performScrollTo().performClick()
            waitFor("Catatanmu sudah tersimpan. Tidak ada XP tambahan.")
            compose.onNodeWithText("Kembali ke beranda").performScrollTo().performClick()
            waitFor("3 benda ditangani")
            capture("remote-home-counted")
            tab("Statistik")
            waitFor("3 benda ditangani")
            compose.onNodeWithText("30 hari").assertIsSelected()
            compose.onNodeWithTag("daily-count-chart").performScrollTo().assertIsDisplayed()
            capture("remote-statistics-counted")
            tab("Profil")
            waitFor("10 XP dari 1 catatan aktif")
            record("ui_edit_refreshes_units_without_extra_xp")

            tab("Beranda")
            compose.onNode(hasText("Plastik") and hasClickAction()).performScrollTo().performClick()
            waitEnabled("Hapus catatan")
            compose.onNodeWithText("Hapus catatan").performScrollTo().performClick()
            compose.onNodeWithText("Hapus", useUnmergedTree = true).performClick()
            compose.waitUntil(30_000) { compose.onAllNodesWithText("Hapus catatan?").fetchSemanticsNodes().isEmpty() }
            tab("Statistik")
            waitFor("Belum ada catatan pada periode ini")
            compose.onNodeWithText("30 hari").assertIsSelected()
            capture("remote-statistics-deleted")
            tab("Profil")
            waitFor("0 XP dari 0 catatan aktif")
            capture("remote-profile-deleted")
            val dashboard = success(core.dashboard())
            assertTrue(dashboard.recentLogs.isEmpty())
            assertEquals(0, dashboard.progress.xp)
            assertEquals(QuantityTotals(0, 0), dashboard.summary.handled)
            assertEquals(0L, success(core.statistics(ActivityDateRange.lastDays(dashboard.date, 30))).summary.activityCount)
            record("ui_delete_refreshes_all_server_aggregates")
            compose.onNodeWithText("Keluar").performScrollTo().performClick()
            compose.onNode(hasText("Keluar") and hasAnyAncestor(isDialog())).performClick()
            waitFor("Belum punya akun? Daftar")
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
