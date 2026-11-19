package com.example.wastenot.ui.main

import android.graphics.Bitmap
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.data.dummy.DummyGuideStore
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.model.GuideReview
import com.example.wastenot.theme.WasteNotTheme
import java.io.File
import java.net.URI
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class GuideStateDesignTest {
    @get:Rule val compose = createComposeRule()

    @Test fun imeSearchCallsItsActionAndReleasesTheFieldFocus() {
        var searches = 0
        compose.setContent { WasteNotTheme(false) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                GuideListScreen(GuideUiState(query = "karton", articles = DummyGuideStore.fixtures, loading = false), PaddingValues(),
                    {}, {}, { searches++ }, {}, {}, Modifier.fillMaxSize())
            }
        } }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription("Search guides"))
        val field = compose.onNodeWithContentDescription("Search guides")
        field.performClick().assertIsFocused().performImeAction()
        compose.runOnIdle { assertEquals(1, searches) }
        field.assertIsNotFocused()
    }

    @Test fun initialSearchErrorKeepsTheQueryAndRetryHasARealAction() {
        var retries = 0
        compose.setContent { WasteNotTheme(false) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                GuideListScreen(GuideUiState(query = "kaca", loading = false, error = ApiFailureKind.NETWORK), PaddingValues(),
                    {}, {}, { retries++ }, {}, {}, Modifier.fillMaxSize())
            }
        } }
        compose.onNodeWithContentDescription("Search guides").assertTextContains("kaca")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Couldn't load the guides"))
        compose.onNodeWithText("Couldn't load the guides").performScrollTo().assertIsDisplayed()
        capture("guide-search-error")
        compose.onNodeWithText("Try again").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test fun missingArticleCannotPrefillAnEditorAndOffersTheList() {
        var exits = 0
        compose.setContent { WasteNotTheme(false) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                GuideDetailScreen(GuideDetailState("removed", loading = false, error = ApiFailureKind.NOT_FOUND), PaddingValues(),
                    {}, { exits++ }, { error("Missing article must not create an editor") }, {}, Modifier.fillMaxSize())
            }
        } }
        compose.onNodeWithText("This guide is no longer available").assertIsDisplayed()
        compose.onNodeWithText("Log this activity").assertDoesNotExist()
        capture("guide-removed")
        compose.onNodeWithText("Back to guides").performClick()
        compose.runOnIdle { assertEquals(1, exits) }
    }

    @Test fun refreshErrorKeepsExistingRowsAndOffersAnExplicitRetry() {
        var retries = 0
        compose.setContent { WasteNotTheme(false) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                GuideListScreen(GuideUiState(articles = DummyGuideStore.fixtures, loading = false, error = ApiFailureKind.NETWORK), PaddingValues(),
                    {}, {}, { retries++ }, {}, {}, Modifier.fillMaxSize())
            }
        } }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Plastic bottles"))
        compose.onNodeWithText("Plastic bottles").assertExists()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Couldn't load the guides"))
        compose.onNodeWithText("Couldn't load the guides").performScrollTo().assertIsDisplayed()
        capture("guide-refresh-error")
        compose.onNodeWithText("Try again").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test fun suppliedReviewMetadataShowsItsHostAndAPlatformFailureRemainsRecoverable() {
        val source = "https://example.org/materials/glass"
        val article = DummyGuideStore.fixtures[1].copy(cautions = listOf("What facilities accept varies."),
            review = GuideReview(URI(source), LocalDate.of(2026, 1, 15), "Reviewer fixture"))
        var sourceError by mutableStateOf(false)
        val calls = mutableListOf<String>()
        compose.setContent { WasteNotTheme(true) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                GuideDetailScreen(GuideDetailState(article.id, article, loading = false), PaddingValues(), {}, {}, {},
                    { calls.add(it); sourceError = true }, Modifier.fillMaxSize(), sourceUnavailable = sourceError)
            }
        } }
        compose.onNodeWithText("Keep in mind").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("What facilities accept varies.").assertExists()
        compose.onNodeWithText("example.org").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(source), calls) }
        compose.onNodeWithText("Reviewed 15 Jan 2026").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Reviewed by Reviewer fixture").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Draft guide").assertDoesNotExist()
        compose.onNodeWithText("Editorial draft").assertDoesNotExist()
        compose.onNodeWithText("Couldn't open the source link. Try again from the link above.").performScrollTo().assertIsDisplayed()
        capture("guide-source-error")
        compose.onNodeWithText("example.org").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(source, source), calls) }
    }

    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(200)
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.waitForIdle(300, 5_000)
        assertEquals("Screenshot must show the app, without a system dialog", instrumentation.targetContext.packageName,
            instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString())
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val file = File(instrumentation.targetContext.cacheDir, "qa/iteration-2/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
}
