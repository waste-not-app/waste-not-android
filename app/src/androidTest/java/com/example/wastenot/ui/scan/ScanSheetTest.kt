package com.example.wastenot.ui.scan

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.model.*
import com.example.wastenot.data.local.ScanPhotoCache
import com.example.wastenot.data.network.ApiFailure
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.data.network.dto.AiCatalogDto
import com.example.wastenot.data.network.dto.AiRecommendationDto
import com.example.wastenot.theme.WasteNotTheme
import com.example.wastenot.theme.LocalWasteNotGradients
import com.example.wastenot.ui.main.captureUiPreview
import java.io.File
import java.net.URI
import java.time.Instant
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.decodeFromString

class ScanSheetTest {
    private val openedSources = mutableListOf<String>()
    private val sourceMonitor = object : Instrumentation.ActivityMonitor() {
        override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
            if (intent.action != Intent.ACTION_VIEW || intent.data?.scheme != "https") return null
            openedSources.add(checkNotNull(intent.dataString))
            return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
        }
    }
    @get:Rule(order = 0) val sources = object : ExternalResource() {
        override fun before() { InstrumentationRegistry.getInstrumentation().addMonitor(sourceMonitor) }
        override fun after() { InstrumentationRegistry.getInstrumentation().removeMonitor(sourceMonitor) }
    }
    @get:Rule(order = 1) val compose = createComposeRule()
    private val candidate = AiDetectionObject("bottle", 0, "bottle", .94, ScanBounds(.2f, .1f, .8f, .9f),
        AiConfidenceBand.OFFERED, true, true, listOf("plastic_bottle"), emptyList(), emptyList(), null)
    private val catalog = AiCatalog("native-fixture", listOf(AiCatalogItem("plastic_bottle", "Plastic bottles",
        WasteCategory.PLASTIC, false, AiGuideStatus.PUBLISHED)), "fixture", "fixture", "fixture",
        emptyMap(), emptySet(), mapOf("bottle" to "Bottle"))
    private val objects = ScanState(session = "native-test", phase = ScanPhase.OBJECT, objects = listOf(candidate),
        selectedId = candidate.id, catalog = catalog)
    private val recommendation = AiRecommendation("00000000-0000-4000-8000-000000000002", Instant.now().plusSeconds(3600),
        AiRecommendationStatus.READY, "00000000-0000-4000-8000-000000000001", candidate.id, 1,
        "plastic_bottle", WasteCategory.PLASTIC, DisposalMethod.RECYCLE, AiRecommendationScope.HANDLING,
        AiRecommendationBasis.CONFIRMED, emptyList(), emptyList(), "Periksa penerimaan fasilitas setempat.", null,
        false, "Keep it apart from other waste.", "native-fixture", false, "fixture", listOf("fixture-evidence"),
        listOf(URI("https://example.org/fixture")), listOf(AiSourceReference(URI("https://example.org/fixture"),
            "Fixture source", "Fixture publisher", "Fixture region", "2026-10-20", "Fixture scope, not a production guide.")),
        listOf(AiGuideReference("fixture-pet", 6)), listOf("Check the packaging material."))

    @Test fun scanDesignViewportsLightAndDark() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val photoFile = File(instrumentation.targetContext.cacheDir, "scan-design-pet.jpg")
        instrumentation.context.assets.open("ai/pet-bottle.jpg").use { input ->
            photoFile.outputStream().use { output -> input.copyTo(output) }
        }
        val photo = ScanPhoto(photoFile.absolutePath, 1045, 2431, System.currentTimeMillis())
        val cameraDirectory = File(instrumentation.targetContext.cacheDir, "scan-design-camera-${System.nanoTime()}")
        val cameraCache = ScanPhotoCache(cameraDirectory, "preview") {}
        val fixture = objects.copy(photo = photo, category = WasteCategory.PLASTIC,
            objectKey = "plastic_bottle", objectConfirmed = true)
        var dark by mutableStateOf(false)
        var state by mutableStateOf(fixture.copy(phase = ScanPhase.PHOTO))
        compose.setContent { WasteNotTheme(dark) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                Box(Modifier.fillMaxSize().background(LocalWasteNotGradients.current.canvas)) {
                    key(dark) {
                        Sheet(state, camera = { ScanCamera("design-preview", cameraCache, {}, {}) })
                    }
                }
            }
        } }
        try {
            val phases = listOf(
                Triple("S21-camera", ScanPhase.CAMERA, "Turn on camera"),
                Triple("S21-photo", ScanPhase.PHOTO, "Retake"),
                Triple("S21-scanning", ScanPhase.DETECTING, "Identifying the object…"),
                Triple("S22", ScanPhase.OBJECT, "Confirm object"),
                Triple("S23", ScanPhase.CONTEXT, "See advice"),
                Triple("S24", ScanPhase.RECOMMENDATION, "Use this, add amount"),
                Triple("S23-waiting", ScanPhase.RECOMMENDING, "Stop and check object"),
            )
            for (theme in listOf(false, true)) {
                for ((id, phase, action) in phases) {
                    compose.runOnIdle {
                        dark = theme
                        state = fixture.copy(phase = phase, recommendation = recommendation.copy(
                            prototypeOnly = false, reason = "Example: check the material and what the collector accepts before recycling.",
                            caution = "", sourceDetails = emptyList(), sources = emptyList()), method = DisposalMethod.RECYCLE)
                    }
                    if (phase == ScanPhase.PHOTO || phase == ScanPhase.DETECTING || phase == ScanPhase.OBJECT) {
                        val description = instrumentation.targetContext.getString(com.example.wastenot.R.string.scan_photo_description)
                        compose.waitUntil(5_000) {
                            compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
                        }
                    }
                    if (phase == ScanPhase.DETECTING) assertBusy("Identifying the object…")
                    if (phase == ScanPhase.RECOMMENDING) assertBusy("Preparing advice…")
                    val name = "$id-${if (theme) "dark" else "light"}"
                    captureUiPreview(compose, name, "scan-design-preview")
                    if (phase == ScanPhase.CONTEXT) {
                        compose.onNodeWithContentDescription("Cleanliness").assertHeightIsAtLeast(56.dp)
                    }
                    val target = compose.onNodeWithText(action)
                    if (!target.isDisplayed()) target.performScrollTo()
                    target.assertIsDisplayed()
                    val tail = when (phase) {
                        ScanPhase.OBJECT -> "Retake"
                        ScanPhase.CONTEXT -> "Correct the object"
                        ScanPhase.RECOMMENDATION -> "Check the item's condition"
                        else -> action
                    }
                    val tailTarget = compose.onNodeWithText(tail)
                    if (!tailTarget.isDisplayed()) tailTarget.performScrollTo()
                    tailTarget.assertIsDisplayed()
                    captureUiPreview(compose, "$name-end", "scan-design-preview")
                }
            }
        } finally {
            photoFile.delete()
            cameraDirectory.deleteRecursively()
        }
    }

    @Test fun recoveryOffersManualEntryWithoutAddingItToUsableAdvice() {
        var state by mutableStateOf(ScanState())
        var dark by mutableStateOf(false)
        var manual = 0
        compose.setContent { WasteNotTheme(dark) {
            Sheet(state, onManual = { manual++ })
        } }
        val states = listOf(
            ScanState() to true,
            objects to false,
            objects.copy(phase = ScanPhase.CONTEXT, category = WasteCategory.PLASTIC, objectConfirmed = true) to false,
            objects.copy(phase = ScanPhase.RECOMMENDATION, category = WasteCategory.PLASTIC, objectKey = "plastic_bottle",
                objectConfirmed = true, recommendation = recommendation, method = DisposalMethod.RECYCLE) to false,
            objects.copy(phase = ScanPhase.RECOMMENDATION, recommendation = recommendation.copy(
                status = AiRecommendationStatus.UNAVAILABLE, method = null, scope = null, basis = null), method = null) to true,
            ScanState(phase = ScanPhase.FAILURE, problem = ScanProblem.CAMERA) to true,
            ScanState(phase = ScanPhase.DETECTING) to false,
            ScanState(phase = ScanPhase.RECOMMENDING) to false,
        )
        for (theme in listOf(false, true)) {
            compose.runOnIdle { dark = theme }
            states.forEachIndexed { index, (next, manualExpected) ->
                compose.runOnIdle { state = next }
                if (manualExpected) {
                    compose.onNodeWithText("Log manually").assertIsDisplayed().performClick()
                } else {
                    compose.onNodeWithText("Log manually").assertDoesNotExist()
                }
                compose.onNodeWithText("Use simulated example").assertDoesNotExist()
                compose.onNodeWithText("Pengaturan simulasi").assertDoesNotExist()
                val markers = (0..3).map { compose.onNodeWithTag("scan-step-$it", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot }
                markers.forEach { marker ->
                    assertEquals("Step markers must remain circles at the user's text scale", marker.width, marker.height, 1f)
                    assertEquals("Completed and current steps must keep equal-sized markers", markers.first().height, marker.height, 1f)
                }
                capture("scan-${if (theme) "dark" else "light"}-$index")
            }
        }
        assertEquals(states.count { it.second } * 2, manual)
    }

    @Test fun objectNeedsMaterialBeforeConfirmationAndOnlyExplicitChoiceUpdatesIt() {
        var state by mutableStateOf(objects)
        var confirmed = 0
        compose.setContent { WasteNotTheme {
            Sheet(state, onCategory = { state = state.copy(category = it) }, onConfirm = { confirmed++ },
                onObjectKey = { state = state.copy(objectKey = it) })
        } }
        compose.onNodeWithText("Confirm object").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Choose a material").performScrollTo().performClick()
        compose.onNodeWithText("Plastic").performClick()
        compose.onNodeWithText("Confirm object").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Choose an item type").performScrollTo().performClick()
        compose.onNodeWithText("Plastic bottles").performClick()
        compose.onNodeWithText("Confirm object").performScrollTo().assertIsEnabled().performClick()
        assertEquals(WasteCategory.PLASTIC, state.category); assertEquals(1, confirmed)
    }

    @Test fun rawDetectionLabelStaysVisibleWhenTheCatalogHasNoTranslatedName() {
        compose.setContent { WasteNotTheme {
            Sheet(objects.copy(catalog = catalog.copy(supportedObjectLabels = emptyMap())))
        } }
        compose.waitUntil(5_000) {
            runCatching { compose.onAllNodesWithText("Object 1 · bottle").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
        }
        compose.onNodeWithText("Object 1 · bottle").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Choose a material").assertExists()
        compose.onNodeWithText("Confirm object").performScrollTo().assertIsNotEnabled()
        captureUiPreview(compose, "S22-raw-label", "backend-contract-preview")
    }

    @Test fun contextFieldsAreIndependentAndUnknownDoesNotImplyNo() {
        var state by mutableStateOf(objects.copy(phase = ScanPhase.CONTEXT, category = WasteCategory.PLASTIC, objectConfirmed = true))
        compose.setContent { WasteNotTheme {
            Sheet(state, onContext = { state = state.copy(context = it) })
        } }
        compose.onNodeWithContentDescription("Cleanliness")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Not sure"))
        compose.onAllNodesWithText("Not sure")[0].performScrollTo().performClick()
        compose.onNodeWithText("Clean").performClick()
        compose.onNodeWithContentDescription("Cleanliness")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Clean"))
        assertEquals(Cleanliness.CLEAN, state.context.cleanliness)
        assertEquals(Integrity.UNKNOWN, state.context.integrity)
        assertEquals(Reusability.UNKNOWN, state.context.reusable); assertNull(state.context.reuseIntention)
        compose.onNodeWithText("Haven't decided").performScrollTo().performClick()
        compose.onNodeWithText("I'll reuse it").performClick()
        assertEquals(true, state.context.reuseIntention)
        assertEquals(Integrity.UNKNOWN, state.context.integrity)
    }

    @Test fun adviceShowsStatusAfterSubmittingBelowTheFoldAndCanReturnToTheObject() {
        var state by mutableStateOf(objects.copy(phase = ScanPhase.CONTEXT, category = WasteCategory.PLASTIC,
            objectKey = "plastic_bottle", objectConfirmed = true, context = ScanContext(cleanliness = Cleanliness.CLEAN)))
        var requests = 0
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                WasteNotTheme {
                    Box(Modifier.size(320.dp, 280.dp)) {
                        ScanSheetContent(
                            state = state,
                            onDismiss = {}, onManual = {}, onRetake = {}, onDetect = {}, onSelect = {},
                            onCategory = {}, onConfirmObject = { state = state.copy(phase = ScanPhase.CONTEXT) },
                            onCorrectObject = { state = state.copy(phase = ScanPhase.OBJECT) },
                            onContext = {}, onRecommend = { requests++; state = state.copy(phase = ScanPhase.RECOMMENDING) },
                            onMethod = {}, onUse = {}, onRetry = {}, camera = {},
                        )
                    }
                }
            }
        }
        compose.onNodeWithText("See advice").performScrollTo().performClick()
        assertBusy("Preparing advice…")
        compose.onNodeWithContentDescription("Close scan").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("See advice").assertDoesNotExist()
        compose.onNodeWithText("Use this, add amount").assertDoesNotExist()
        val stop = compose.onNodeWithText("Stop and check object")
        if (!stop.isDisplayed()) stop.performScrollTo()
        assertBusy("Preparing advice…")
        stop.assertIsEnabled().performClick()
        compose.onNodeWithText("Confirm object").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Cleanliness").assertTextContains("Clean")
        compose.runOnIdle { assertEquals(1, requests) }
    }

    private fun assertBusy(label: String) {
        compose.onNodeWithText(label).assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate))
    }

    @Test fun recommendationRecoveryReturnsToAnswersWithoutStartingAnotherRequest() {
        val waiting = ApiFailure(ApiFailureKind.RATE_LIMITED, retryAt = Instant.MAX)
        var state by mutableStateOf(objects.copy(
            phase = ScanPhase.FAILURE, problem = ScanProblem.RECOMMENDATION, error = waiting,
            category = WasteCategory.PLASTIC, objectKey = "plastic_bottle", objectConfirmed = true,
            context = ScanContext(cleanliness = Cleanliness.CLEAN),
        ))
        var requests = 0
        compose.setContent { WasteNotTheme {
            Sheet(state,
                onRetry = { state = state.copy(phase = ScanPhase.CONTEXT, problem = null) },
                onRecommend = { requests++ },
            )
        } }
        compose.onNodeWithText("Too many attempts.", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Review your answers").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Cleanliness").assertTextContains("Clean")
        val advice = compose.onNodeWithText("See advice")
        advice.performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Wait a moment before sending another request.").performScrollTo().assertIsDisplayed()

        compose.runOnIdle {
            assertEquals(0, requests)
            state = state.copy(error = waiting.copy(retryAt = Instant.EPOCH))
        }
        advice.performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, requests) }
    }

    @Test fun recommendationAllowsThreeMethodsShowsBasisAndRequiresUseAction() {
        var dark by mutableStateOf(false)
        var state by mutableStateOf(objects.copy(phase = ScanPhase.RECOMMENDATION, category = WasteCategory.PLASTIC,
            objectKey = "plastic_bottle", objectConfirmed = true, recommendation = recommendation, method = DisposalMethod.RECYCLE))
        var used = 0
        compose.setContent { WasteNotTheme(dark) {
            Sheet(state, onMethod = { state = state.copy(method = it) }, onUse = { used++ })
        } }
        val use = compose.onNodeWithText("Use this, add amount")
        use.assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText("Log manually").assertDoesNotExist()
        compose.onAllNodesWithText("Recycle").assertCountEquals(1)
        compose.onNodeWithText("Keep it apart from other waste.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Why this advice").performScrollTo().performClick()
        compose.onNodeWithText("Plastic bottles").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Guide reference: fixture-pet · version 6").performScrollTo().assertIsDisplayed()
        captureUiPreview(compose, "guide-revision-light", "scan-review-fixes")
        compose.runOnIdle { dark = true }
        compose.onNodeWithText("Guide reference: fixture-pet · version 6").assertIsDisplayed()
        captureUiPreview(compose, "guide-revision-dark", "scan-review-fixes")
        compose.onNodeWithContentDescription("Change method").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        compose.onNode(hasText("Recycle") and hasAnyAncestor(isPopup())).assertIsSelected()
        compose.onNodeWithText("Reduce").assertDoesNotExist()
        compose.onNodeWithText("Trash").performClick()
        assertEquals(DisposalMethod.TRASH, state.method); assertEquals(0, used)
        compose.onNodeWithText("Your handling choice").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Trash").assertIsDisplayed()
        compose.onNodeWithText("Recycle").assertDoesNotExist()
        use.assertIsDisplayed().performClick()
        assertEquals(1, used)
    }

    @Test fun expiredChoicesExplainTheManualHandoffInBothThemes() {
        var dark by mutableStateOf(false)
        var manual = 0
        val state = objects.copy(phase = ScanPhase.FAILURE, problem = ScanProblem.EXPIRED,
            category = WasteCategory.PLASTIC, objectConfirmed = true, confirmedMethod = DisposalMethod.TRASH)
        compose.setContent { WasteNotTheme(dark) { Sheet(state, onManual = { manual++ }) } }
        for (theme in listOf(false, true)) {
            compose.runOnIdle { dark = theme }
            compose.onNodeWithText("The category and method you chose carry over to manual entry, without the AI reference.")
                .performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Use this, add amount").assertDoesNotExist()
            captureUiPreview(compose, "expired-choice-${if (theme) "dark" else "light"}", "scan-review-fixes")
            compose.onNodeWithText("Log manually").assertIsDisplayed().performClick()
        }
        assertEquals(2, manual)
    }

    @Test fun conditionalAdviceRequiresAcknowledgementAndSourceMetadataOpensOnlyItsUri() {
        var state by mutableStateOf(objects.copy(phase = ScanPhase.RECOMMENDATION, category = WasteCategory.PLASTIC,
            objectKey = "plastic_bottle", objectConfirmed = true, method = DisposalMethod.RECYCLE,
            recommendation = recommendation.copy(basis = AiRecommendationBasis.CONDITIONAL,
                conditionsToVerify = listOf(AiContextField.CLEANLINESS), specialHandling = true)))
        compose.setContent { WasteNotTheme {
            Sheet(state, onMethod = { state = state.copy(method = it) },
                onConfirmConditions = { state = state.copy(conditionsConfirmed = it) })
        } }
        val use = compose.onNodeWithText("Use this, add amount")
        use.assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithContentDescription("Change method").performScrollTo().performClick()
        compose.onNodeWithText("Reuse").performClick()
        use.assertIsNotEnabled()
        compose.onNodeWithText("Your handling choice").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Fixture source").assertDoesNotExist()
        compose.onNodeWithText("Needs special handling.", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("I've checked the conditions for this advice").performScrollTo().performClick()
        use.assertIsEnabled()
        val footer = use.fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("Why this advice").performScrollTo().performClick()
        compose.onNodeWithText("Fixture source").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("https://example.org/fixture"), openedSources) }
        compose.onNodeWithText("Reference checked: 2026-10-20").performScrollTo().assertIsDisplayed()
        capture("scan-conditional-source")
        compose.onNodeWithText("Hide the reasoning").performScrollTo().performClick()
        compose.onNodeWithText("Fixture source").assertDoesNotExist()
        assertEquals(footer, use.assertIsEnabled().fetchSemanticsNode().boundsInRoot)
    }

    @Test fun needsContextAndPrototypeRemainSeparateFromAUsableLiveRecommendation() {
        var state by mutableStateOf(objects.copy(phase = ScanPhase.RECOMMENDATION, category = WasteCategory.PLASTIC,
            objectKey = "plastic_bottle", objectConfirmed = true, method = null,
            recommendation = recommendation.copy(status = AiRecommendationStatus.NEEDS_CONTEXT, method = null,
                scope = null, basis = null, missingContext = listOf(AiContextField.RISK_TYPE))))
        var reviews = 0
        compose.setContent { WasteNotTheme { Sheet(state, onReviewContext = { reviews++ }) } }
        compose.onNodeWithText("Tell us more about the item").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Risk type").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Use this, add amount").assertDoesNotExist()
        compose.onNodeWithText("Check the item's condition").assertIsDisplayed().performClick()
        compose.onNodeWithText("Log manually").assertIsDisplayed()
        assertEquals(1, reviews)
        capture("scan-needs-context")
        compose.runOnIdle { state = state.copy(recommendation = recommendation.copy(prototypeOnly = true)) }
        compose.onNodeWithText("Prototype result. This advice can't be used for an AI log yet.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Use this, add amount").assertDoesNotExist()
        capture("scan-prototype-unusable")
    }

    @Test fun shortViewportAtDoubleFontScaleKeepsReviewAndActionReachable() {
        var state by mutableStateOf(objects.copy(phase = ScanPhase.RECOMMENDATION, category = WasteCategory.PLASTIC,
            objectKey = "plastic_bottle", objectConfirmed = true, method = DisposalMethod.RECYCLE,
            recommendation = recommendation.copy(basis = AiRecommendationBasis.CONDITIONAL,
                conditionsToVerify = listOf(AiContextField.CLEANLINESS))))
        var used = 0
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                WasteNotTheme {
                    Box(Modifier.size(320.dp, 280.dp)) {
                        ScanSheetContent(
                            state = state,
                            onDismiss = {}, onManual = {}, onRetake = {}, onDetect = {}, onSelect = {},
                            onCategory = {}, onConfirmObject = {}, onCorrectObject = {}, onContext = {},
                            onRecommend = {}, onMethod = {}, onUse = { used++ }, onRetry = {},
                            onConfirmConditions = { state = state.copy(conditionsConfirmed = it) },
                            camera = {},
                        )
                    }
                }
            }
        }
        val use = compose.onNodeWithText("Use this, add amount")
        use.performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp).assertIsNotEnabled()
        compose.onNodeWithText("Cleanliness").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("I've checked the conditions for this advice").performScrollTo().assertIsDisplayed().performClick()
        use.performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, used) }
        compose.onNodeWithText("Why this advice").performScrollTo().performClick()
        compose.onNodeWithText("Fixture source").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(listOf("https://example.org/fixture"), openedSources) }
    }

    @Test fun nonReadyGuidanceKeepsServerStepsSourcesAndManualWithoutOfferingAnAiSave() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val json = Json { ignoreUnknownKeys = true }
        fun data(name: String) = context.assets.open("ai/policy08/$name.json").bufferedReader().use {
            Json.parseToJsonElement(it.readText()).jsonObject.getValue("data").toString()
        }
        val currentCatalog = json.decodeFromString<AiCatalogDto>(data("catalog-response")).toModel()
        fun advice(name: String, key: String, category: WasteCategory) =
            json.decodeFromString<AiRecommendationDto>(data(name)).toModel(AiRecommendationRequest(
                "00000000-0000-0000-0000-000000000032", true, key, category, AiInputSource.MANUAL))
                .copy(expiresAt = Instant.now().plusSeconds(3600))
        val cases = listOf(
            advice("needs-context-response", "ceramic_cup", WasteCategory.OTHER),
            advice("provider-unavailable-response", "glass_bottle", WasteCategory.GLASS),
        )
        var current by mutableStateOf(cases.first())
        var dark by mutableStateOf(false)
        var manual = 0
        var reviews = 0
        compose.setContent { WasteNotTheme(dark) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                Sheet(ScanState(session = "policy08-ui", phase = ScanPhase.RECOMMENDATION,
                    catalog = currentCatalog, category = current.category, objectKey = current.objectKey,
                    objectConfirmed = true, recommendation = current), onManual = { manual++ }, onReviewContext = { reviews++ })
            }
        } }
        for (item in cases) for (theme in listOf(false, true)) {
            compose.runOnIdle { current = item; dark = theme }
            val name = "${item.status.name.lowercase()}-${if (theme) "dark" else "light"}"
            compose.onNodeWithText("Use this, add amount").assertDoesNotExist()
            compose.onNodeWithContentDescription("Change method").assertDoesNotExist()
            compose.onNodeWithText(if (item.specialHandling) "Specialist handling advice" else "Starter handling advice")
                .performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("1. ${item.steps.first()}").performScrollTo().assertIsDisplayed()
            captureUiPreview(compose, "$name-guidance", "policy08-ui")
            for ((index, step) in item.steps.withIndex())
                compose.onNodeWithText("${index + 1}. $step").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Prototype result. This advice can't be used for an AI log yet.")
                .assertExists()
            val source = item.sourceDetails.first()
            compose.onNodeWithText(source.title).performScrollTo().assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(source.url.toString(), openedSources.lastOrNull()) }
            val review = compose.onNodeWithText("Check the item's condition")
            if (!review.isDisplayed()) review.performScrollTo()
            review.assertIsDisplayed()
            captureUiPreview(compose, "$name-sources", "policy08-ui")
            review.performClick()
            compose.onNodeWithText("Log manually").assertIsDisplayed().performClick()
        }
        compose.runOnIdle { assertEquals(4, manual); assertEquals(4, reviews); assertEquals(4, openedSources.size) }
    }

    @Composable
    private fun Sheet(state: ScanState, onManual: () -> Unit = {}, onCategory: (WasteCategory) -> Unit = {},
        onConfirm: () -> Unit = {}, onContext: (ScanContext) -> Unit = {}, onMethod: (DisposalMethod) -> Unit = {}, onUse: () -> Unit = {},
        onObjectKey: (String) -> Unit = {}, onReviewContext: () -> Unit = {}, onConfirmConditions: (Boolean) -> Unit = {},
        onRetry: () -> Unit = {}, onRecommend: () -> Unit = {},
        camera: @Composable () -> Unit = { Text("Point at one item") }) {
        ScanSheet(state, {}, onManual, {}, {}, {}, onCategory, onConfirm, {}, onContext, onRecommend, onMethod, onUse, onRetry,
            onObjectKey = onObjectKey, onReviewContext = onReviewContext, onConfirmConditions = onConfirmConditions, camera = camera)
    }
    private fun capture(name: String) {
        captureUiPreview(compose, name, "scan-qa")
    }
}
