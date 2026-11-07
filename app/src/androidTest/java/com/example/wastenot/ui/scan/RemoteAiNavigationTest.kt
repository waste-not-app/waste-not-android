package com.example.wastenot.ui.scan

import android.content.Context
import android.content.res.Configuration
import android.os.ParcelFileDescriptor
import android.view.WindowInsets
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.BuildConfig
import com.example.wastenot.MainActivity
import com.example.wastenot.R
import com.example.wastenot.WasteNotApplication
import com.example.wastenot.data.local.KeystoreRefreshTokenStore
import com.example.wastenot.data.local.TokenReadResult
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.repository.SessionState
import com.example.wastenot.model.WasteCategory
import com.example.wastenot.ui.main.captureUiPreview
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
import org.junit.rules.ExternalResource

class RemoteAiNavigationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private var originalTheme: String? = null
    private val captureChecks = JSONArray()
    @get:Rule(order = 0) val appearance = object : ExternalResource() {
        override fun before() {
            if (InstrumentationRegistry.getArguments().getString("verifyLiveAiUi") != "true") return
            val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
            originalTheme = prefs.getString("theme", null)
            check(prefs.edit().putString("theme", "SYSTEM").commit())
        }
        override fun after() {
            if (InstrumentationRegistry.getArguments().getString("verifyLiveAiUi") != "true") return
            check(context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit().putString("theme", originalTheme).commit())
        }
    }
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun cameraAdviceSaveEditDeleteAndLogoutUseRealBackend() = runTest(timeout = 6.minutes) {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("verifyLiveAiUi") == "true")
        assertTrue(BuildConfig.DEBUG)
        assertFalse(BuildConfig.USE_DUMMY_DATA)
        assertEquals("https://pred-autoscaler.bccdev.id/wastenot/api/v1/", BuildConfig.API_BASE_URL)
        val runId = UUID.fromString(requireNotNull(args.getString("fixtureRunId"))).toString()
        val email = "android-$runId@example.test"
        val name = "Android transport probe"
        val container = (compose.activity.application as WasteNotApplication).container
        val core = container.core
        val steps = JSONArray()
        val evidence = JSONObject().put("run_id", runId).put("email", email).put("name", name)
            .put("context_is_fixture", true).put("camera_source", "emulator virtual scene with public PET poster")
        val output = File(context.cacheDir, "qa/backend/ai-fixture.json")
        fun record(step: String) {
            output.parentFile!!.mkdirs()
            output.writeText(evidence.put("passed_steps", steps.put(step)).toString(2))
        }
        val night = shell("cmd uimode night").substringAfter(":").trim()
        require(night in setOf("auto", "no", "yes", "custom"))
        try {
            setNight(false)
            waitFor("Belum punya akun? Daftar")
            click("Belum punya akun? Daftar")
            val password = UUID.randomUUID().toString() + "Az9!"
            for ((label, value) in listOf("Nama" to name, "Email" to email, "Kata sandi" to password,
                    "Konfirmasi kata sandi" to password)) {
                compose.onNodeWithContentDescription(label).performScrollTo().performTextReplacement(value)
            }
            click("Buat akun")
            waitFor("Input manual")
            val user = (container.sessions.state.value as SessionState.Authenticated).user
            evidence.put("user_id", user.id)
            val pet = success(container.ai.catalog()).items.single { it.key == "pet_bottle" && it.category == WasteCategory.PLASTIC }
            record("ui_registration_and_live_catalog")

            val photos = File(context.cacheDir, "scan")
            val beforePhotos = photos.listFiles()?.map { it.name }?.toSet().orEmpty()
            compose.onNodeWithContentDescription("Catat atau scan sampah").performClick()
            click("Aktifkan kamera")
            waitEnabled("Ambil foto")
            capture("S21-camera-light")
            click("Ambil foto")
            waitFor("Tinjau foto")
            compose.waitUntil(30_000) { compose.onAllNodesWithContentDescription("Foto sampah yang akan diperiksa").fetchSemanticsNodes().size == 1 }
            val captured = photos.listFiles().orEmpty().filter { it.name !in beforePhotos }
            assertEquals(1, captured.size)
            evidence.put("captured_photo_bytes", captured.single().length())
            evidence.put("photo_heading_before_capture", visibleHeading("Tinjau foto"))
            capture("S21-photo-before-theme")
            evidence.put("photo_heading_after_capture", visibleHeading("Tinjau foto"))
            captureThemes("S21-photo", "Tinjau foto")
            record("real_camerax_photo_survives_theme_changes")
            click("Scan foto")
            compose.waitUntil(45_000) {
                compose.onAllNodesWithText("Periksa objek").fetchSemanticsNodes().isNotEmpty() ||
                    compose.onAllNodesWithText("Scan belum tersedia").fetchSemanticsNodes().isNotEmpty()
            }
            if (compose.onAllNodesWithText("Scan belum tersedia").fetchSemanticsNodes().isNotEmpty()) capture("S25-unexpected-fallback")
            compose.onNodeWithText("Periksa objek").assertIsDisplayed()
            captureThemes("S22-object", "Periksa objek")
            choose(R.string.scan_material, "Plastik")
            choose(R.string.scan_object_type, pet.label)
            click("Konfirmasi objek")
            waitFor("Kondisi objek")
            choose(R.string.scan_cleanliness, "Bersih")
            choose(R.string.scan_integrity, "Utuh")
            choose(R.string.scan_reusability, "Tidak")
            choose(R.string.scan_reuse_intention, context.getString(R.string.scan_will_not_reuse))
            choose(R.string.scan_recycling_access, "Ya")
            captureThemes("S23-context", "Kondisi objek")
            click("Lihat saran")
            waitFor("Lengkapi informasi benda", 45_000)
            capture("S24-needs-context-light")
            click("Periksa kondisi benda")
            choose(R.string.scan_former_contents, context.getString(R.string.scan_food_drink))
            click("Lihat saran")
            waitEnabled("Gunakan pilihan, isi jumlah", 45_000)
            captureThemes("S24-recommendation", "Periksa saran")
            val empty = success(core.dashboard())
            assertEquals(0L, empty.summary.activityCount)
            assertEquals(0, empty.progress.xp)
            record("real_ai_ui_context_and_recommendation_do_not_auto_save")

            click("Gunakan pilihan, isi jumlah")
            waitFor("Cara pengelolaan")
            compose.onNodeWithContentDescription("Berat").performScrollTo()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
                .performTextReplacement("250")
            compose.runOnUiThread { compose.activity.window.insetsController?.hide(WindowInsets.Type.ime()) }
            assertTrue(captured.none { it.exists() })
            capture("S05-ai-prefill-light")
            click("Simpan catatan")
            waitFor("Catatan tersimpan")
            compose.onNodeWithText("+10 XP untuk aktivitas baru ini.").performScrollTo().assertIsDisplayed()
            capture("S06-created-light")
            val created = success(core.dashboard())
            assertEquals(1L, created.summary.activityCount)
            assertEquals(10, created.progress.xp)
            val log = created.recentLogs.single()
            evidence.put("log_id", log.id)
            record("ui_explicit_save_keeps_ai_reference_and_awards_ten_xp")
            click("Lihat catatan")
            waitEnabled("Ubah catatan")
            capture("S08-created-light")
            click("Ubah catatan")
            compose.onNodeWithContentDescription("Catatan (opsional)").performScrollTo().performTextReplacement("UI AI fixture edited")
            click("Simpan perubahan")
            waitFor("Catatan tersimpan")
            compose.onNodeWithText("Catatanmu sudah tersimpan. Tidak ada XP tambahan.").performScrollTo().assertIsDisplayed()
            assertEquals("UI AI fixture edited", success(core.wasteLog(log.id)).notes)
            assertEquals(10, success(core.dashboard()).progress.xp)
            record("ui_edit_updates_existing_record_without_more_xp")
            click("Lihat catatan")
            waitEnabled("Hapus catatan")
            click("Hapus catatan")
            compose.onNode(hasText("Hapus") and hasAnyAncestor(isDialog())).performClick()
            compose.waitUntil(30_000) { compose.onAllNodes(hasText("Profil") and isSelectable()).fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(hasText("Profil") and isSelectable()).performClick()
            waitFor("0 XP total")
            assertEquals(0L, success(core.dashboard()).summary.activityCount)
            capture("S14-after-delete-light")
            record("ui_delete_refreshes_server_profile_and_summary")
            click("Keluar")
            compose.onNode(hasText("Keluar") and hasAnyAncestor(isDialog())).performClick()
            waitFor("Belum punya akun? Daftar")
            assertTrue(container.sessions.state.value is SessionState.SignedOut)
            assertEquals(TokenReadResult.Absent, KeystoreRefreshTokenStore.create(context).read())
            record("ui_logout_clears_device_credential")
        } finally {
            output.parentFile!!.mkdirs()
            output.writeText(evidence.put("passed_steps", steps).put("capture_checks", captureChecks).toString(2))
            shell("cmd uimode night $night")
            container.sessions.logout()
        }
    }

    private fun choose(label: Int, value: String) {
        compose.onNodeWithContentDescription(context.getString(label)).performScrollTo().performClick()
        compose.onNode(hasText(value) and hasAnyAncestor(isPopup())).performScrollTo().performClick()
    }

    private fun click(label: String) = compose.onNodeWithText(label).performScrollTo().assertIsDisplayed().performClick()
    private fun waitFor(label: String, timeout: Long = 30_000) = compose.waitUntil(timeout) {
        compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
    }
    private fun waitEnabled(label: String, timeout: Long = 60_000) = compose.waitUntil(timeout) {
        compose.onAllNodes(hasText(label) and hasClickAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty()
    }

    private fun captureThemes(id: String, heading: String) {
        for (dark in listOf(false, true)) {
            setNight(dark)
            waitFor(heading)
            try {
                compose.waitUntil(15_000) { visibleHeading(heading) }
            } finally {
                captureChecks.put(JSONObject().put("screen", id).put("dark", dark)
                    .put("activity_identity", System.identityHashCode(compose.activity))
                    .put("window_id", instrumentation.uiAutomation.rootInActiveWindow?.windowId)
                    .put("visible_heading", visibleHeading(heading)))
            }
            val scroll = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and hasAnyAncestor(hasTestTag("capture-sheet"))
            compose.onAllNodes(scroll).fetchSemanticsNodes().indices.forEach { index ->
                compose.onAllNodes(scroll)[index].performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -100_000f) }
            }
            capture("$id-${if (dark) "dark" else "light"}")
        }
        setNight(false)
        waitFor(heading)
    }

    private fun setNight(dark: Boolean) {
        shell("cmd uimode night ${if (dark) "yes" else "no"}")
        compose.waitUntil(15_000) {
            val mode = compose.activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            mode == if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        compose.waitForIdle()
    }

    private fun capture(name: String) = captureUiPreview(compose, name, "live-ai-ui")
    private fun visibleHeading(label: String): Boolean {
        fun contains(node: AccessibilityNodeInfo?, depth: Int): Boolean {
            if (node == null || depth > 30) return false
            if (node.isVisibleToUser && node.text?.contains(label) == true) return true
            return (0 until node.childCount).any { contains(node.getChild(it), depth + 1) }
        }
        return contains(instrumentation.uiAutomation.rootInActiveWindow, 0)
    }
    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }
    private fun <T> success(result: ApiResult<T>): T {
        assertTrue("API failure: ${(result as? ApiResult.Failure)?.error?.kind}", result is ApiResult.Success)
        return (result as ApiResult.Success).value
    }
}
