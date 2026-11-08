package com.example.wastenot.ui.main

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.MainActivity
import com.example.wastenot.clearEditorDraftFixtures
import com.example.wastenot.data.dummy.DummyActivityStore
import com.example.wastenot.data.local.LocalEditorDraftStore
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.repository.StoredEditorDraft
import java.io.File
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource

internal val draftTestContext: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
internal fun resetDraftAccountFixture() {
    clearEditorDraftFixtures(draftTestContext)
    listOf("dummy_session", "dummy_logs_dummy-owner", "appearance").forEach {
        check(draftTestContext.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit())
    }
}
internal fun readDraftFixture(): StoredEditorDraft? {
    var value: StoredEditorDraft? = null
    runTest { value = (LocalEditorDraftStore.create(draftTestContext, "dummy-owner").read() as ApiResult.Success).value }
    return value
}
internal fun captureDraftFixture(name: String) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    instrumentation.waitForIdleSync()
    instrumentation.uiAutomation.waitForIdle(500, 5_000)
    instrumentation.uiAutomation.takeScreenshot()?.recycle()
    android.os.SystemClock.sleep(150)
    val image = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
    val dir = File(draftTestContext.getExternalFilesDir(null), "draft-qa").apply { mkdirs() }
    File(dir, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }; image.recycle()
}

class EditorPersistenceNavigationTest {
    @get:Rule(order = -1) val navigationData = com.example.wastenot.NavigationFixtureRule()
    @get:Rule(order = 0) val fixture = object : ExternalResource() { override fun before() = resetDraftAccountFixture() }
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    private fun waitFor(text: String) { compose.waitUntil(30_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
    private fun fill() {
        waitFor("650 g")
        compose.onNodeWithText("Input manual").performScrollTo().performClick()
        compose.onNodeWithText("Recycle").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Kategori").performScrollTo().performClick()
        compose.onNode(hasText("Plastik") and hasAnyAncestor(isPopup())).performClick()
        compose.onNodeWithContentDescription("Satuan").performScrollTo().performClick()
        compose.onNode(hasText("kg") and hasAnyAncestor(isPopup())).performClick()
        compose.onNodeWithContentDescription("Berat").performScrollTo().performTextReplacement("0,250")
        compose.onNodeWithContentDescription("Catatan (opsional)").performScrollTo().performTextReplacement("  Draf perangkat saya  ")
        compose.onNodeWithText("Draf tersimpan di perangkat ini.").performScrollTo()
        compose.waitUntil(10_000) { readDraftFixture()?.fields?.get(12) == "  Draf perangkat saya  " }
    }

    @Test fun recreationAndContinueKeepRawFieldsAndExplicitDiscardRemovesTheDraft() {
        fill()
        val original = checkNotNull(readDraftFixture())
        compose.activityRule.scenario.recreate()
        compose.onNodeWithContentDescription("Berat").performScrollTo().assertTextContains("0,250")
        compose.onNodeWithContentDescription("Catatan (opsional)").performScrollTo().assertTextContains("  Draf perangkat saya  ")
        assertEquals(original.requestId, readDraftFixture()!!.requestId)
        compose.onNodeWithContentDescription("Kembali").performClick()
        compose.onNodeWithText("Lanjut mengisi").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Catatan (opsional)").performScrollTo().assertTextContains("  Draf perangkat saya  ")
        captureDraftFixture("draft-continue")
        compose.onNodeWithContentDescription("Kembali").performClick()
        compose.onNodeWithText("Buang perubahan").performScrollTo().performClick()
        waitFor("650 g")
        assertNull(readDraftFixture())
        compose.onNodeWithText("Input manual").performScrollTo().performClick()
        assertEquals("", compose.onNodeWithContentDescription("Berat").performScrollTo().fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        assertNotEquals(original.requestId, readDraftFixture()?.requestId)
    }

    @Test fun successfulSaveCleansTheDraftAndRecreationDoesNotSubmitAgain() {
        fill()
        compose.onNodeWithText("Simpan catatan").performScrollTo().performClick()
        waitFor("+10 XP untuk aktivitas baru ini.")
        assertNull(readDraftFixture())
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Cara pengelolaan").assertDoesNotExist()
        runTest {
            val source = DummyActivityStore(draftTestContext.getSharedPreferences("dummy_logs_dummy-owner", Context.MODE_PRIVATE), ZoneId.of("Asia/Jakarta"))
            assertEquals(6, (source.load() as ApiResult.Success).value.size)
        }
        assertNull(readDraftFixture())
        captureDraftFixture("draft-save-cleaned")
    }
}
