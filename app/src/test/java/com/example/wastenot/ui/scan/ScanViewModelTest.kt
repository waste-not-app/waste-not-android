package com.example.wastenot.ui.scan

import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.wastenot.LogEditor
import com.example.wastenot.data.dummy.DummyScanRepository
import com.example.wastenot.data.network.*
import com.example.wastenot.data.repository.*
import com.example.wastenot.model.*
import com.example.wastenot.ui.main.EditorDraft
import com.example.wastenot.ui.main.EditorField
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScanViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val owners = mutableListOf<ViewModelStoreOwner>()
    private val deleted = mutableListOf<ScanPhoto>()
    private var exists = true
    private var now = 10_000L
    private val photo = ScanPhoto("private/photo.jpg", 800, 600, 10_000)
    private val photos = object : ScanPhotoStore {
        override fun exists(photo: ScanPhoto) = exists && photo !in deleted
        override fun delete(photo: ScanPhoto) { deleted.add(photo) }
        override fun read(photo: ScanPhoto) = byteArrayOf()
    }
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { owners.forEach { it.viewModelStore.clear() }; Dispatchers.resetMain() }
    private fun model(repo: ScanRepository = Fake()): ScanViewModel {
        val owner = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
        owners.add(owner)
        return ViewModelProvider.create(owner, viewModelFactory { initializer { ScanViewModel(repo, photos) { now } } })[ScanViewModel::class]
            .apply { start("session"); photo("session", photo) }
    }
    private suspend fun TestScope.confirm(vm: ScanViewModel) {
        vm.detect(); advanceUntilIdle(); vm.category(WasteCategory.PLASTIC); vm.objectKey("plastic_bottle"); vm.confirmObject()
    }

    @Test fun detectionNeedsExplicitCatalogObjectAndMaterialBeforeConfirmation() = runTest {
        val vm = model(); vm.detect(); advanceUntilIdle()
        assertEquals(ScanPhase.OBJECT, vm.state.value.phase)
        assertNull(vm.state.value.category); assertNull(vm.state.value.objectKey)
        vm.confirmObject(); assertEquals(ScanPhase.OBJECT, vm.state.value.phase)
        vm.category(WasteCategory.PLASTIC); vm.confirmObject()
        assertEquals(ScanPhase.OBJECT, vm.state.value.phase)
        vm.objectKey("glass_bottle"); assertNull(vm.state.value.objectKey)
        vm.objectKey("plastic_bottle"); vm.confirmObject()
        assertEquals(ScanPrefill(WasteCategory.PLASTIC, null), vm.prefill())
    }

    @Test fun contextFieldsStayIndependentAndChangingObjectInvalidatesTheOldDecision() = runTest {
        val vm = model(); confirm(vm)
        val context = ScanContext(Cleanliness.DIRTY, Integrity.DAMAGED, AiAnswer.YES, null,
            cleanable = AiAnswer.YES, soiling = AiSoiling.HAZARDOUS, formerContents = AiFormerContents.CHEMICAL,
            recyclingAccess = AiAnswer.NO, riskFlag = true, riskType = AiRiskType.CHEMICAL)
        vm.context(context); vm.correctObject()
        assertEquals(context, vm.state.value.context)
        assertFalse(vm.state.value.objectConfirmed)
        vm.confirmObject(); assertEquals(context, vm.state.value.context)
        vm.correctObject(); vm.category(WasteCategory.GLASS)
        assertEquals(ScanContext(), vm.state.value.context); assertNull(vm.state.value.objectKey)
        assertNull(vm.prefill().category)
    }

    @Test fun recommendationUsesAllConfirmedContextAndAUniqueExplicitOperationId() = runTest {
        val fake = Fake(); val vm = model(fake); confirm(vm)
        val context = ScanContext(Cleanliness.DIRTY, Integrity.INTACT, AiAnswer.YES, true,
            AiAnswer.YES, AiSoiling.FOOD_DRINK, AiFormerContents.FOOD_DRINK, AiAnswer.YES, true, AiRiskType.SHARP)
        vm.context(context); vm.recommend(); advanceUntilIdle()
        val input = fake.inputs.single()
        assertEquals(context.condition(), input.condition)
        assertEquals(context.reuseIntention, input.reuseIntention)
        assertEquals(context.recyclingAccess, input.recyclingAccess)
        assertEquals(context.riskType, input.riskType); assertTrue(input.riskFlag)
        assertEquals(vm.state.value.detection!!.id, input.detectionId)
        assertEquals(vm.state.value.selectedId, input.objectId)
        assertEquals(vm.state.value.revision, input.contextRevision)
        assertEquals("plastic_bottle", input.objectKey); assertTrue(input.confirmedObject)
        assertEquals(AiInputSource.DETECTION, input.inputSource)
        UUID.fromString(input.requestId)
        vm.reviewContext(); vm.recommend(); advanceUntilIdle()
        assertNotEquals(input.requestId, fake.inputs.last().requestId)
        assertNotEquals(fake.uploadIds.single(), input.requestId)
    }

    @Test fun methodCorrectionRetainsReferenceAndTheEditorStillNeedsActualQuantity() = runTest {
        val vm = model(); confirm(vm); vm.recommend(); advanceUntilIdle()
        assertEquals(DisposalMethod.RECYCLE, vm.state.value.method)
        assertNull(vm.prefill().method)
        vm.method(DisposalMethod.TRASH)
        val prefill = vm.prefill(true)
        assertEquals(DisposalMethod.TRASH, prefill.method)
        assertEquals(vm.state.value.recommendation!!.id, prefill.aiProvenance!!.recommendationId)
        val date = LocalDate.of(2026, 10, 23)
        val draft = EditorDraft.from(null, date).applyScan(LogEditor(null, "request", scanCategory = prefill.category?.name,
            scanMethod = prefill.method?.name, scanRecommendationId = prefill.aiProvenance.recommendationId))
        assertNull(draft.prepareSubmission(date)); assertTrue(EditorField.QUANTITY in draft.errors)
        assertEquals("", draft.values.quantity.rawText); assertEquals(date, draft.values.date)
        vm.method(DisposalMethod.REDUCE); assertEquals(DisposalMethod.TRASH, vm.state.value.method)
    }

    @Test fun serverUncertainBandBelowPointFiveIsOfferedWithoutAutomaticMaterial() = runTest {
        val fake = Fake().apply { detector = { id -> ApiResult.Success(detection(id).copy(objects = listOf(
            candidate().copy(confidence = .4, band = AiConfidenceBand.UNCERTAIN, reviewThresholds = AiThresholds(.3, .725)))) ) } }
        val vm = model(fake); vm.detect(); advanceUntilIdle()
        assertEquals(ScanPhase.OBJECT, vm.state.value.phase)
        assertEquals(.4, vm.state.value.objects.single().confidence, 0.0)
        assertNull(vm.prefill().category)
    }

    @Test fun noObjectsOrUnsupportedCandidatesGiveManualStateAndKnownSessionsAreRetired() = runTest {
        for (rows in listOf(emptyList(), listOf(candidate().copy(supportedForReview = false, band = AiConfidenceBand.MANUAL_REQUIRED)))) {
            deleted.clear()
            val fake = Fake().apply { detector = { id -> ApiResult.Success(detection(id).copy(objects = rows)) } }
            val vm = model(fake); vm.detect(); advanceUntilIdle()
            assertEquals(ScanProblem.NO_OBJECT, vm.state.value.problem)
            assertEquals(ScanPrefill(null, null), vm.prefill(true))
            val id = vm.state.value.detection!!.id
            vm.close(); advanceUntilIdle(); assertEquals(listOf(id), fake.retired)
        }
    }

    @Test fun catalogFailureDoesNotUploadOrInventDemoOptions() = runTest {
        val fake = Fake().apply { catalogResult = ApiResult.Failure(ApiFailure(ApiFailureKind.NETWORK)) }
        val vm = model(fake); vm.detect(); advanceUntilIdle()
        assertEquals(ScanProblem.CATALOG, vm.state.value.problem)
        assertTrue(fake.uploadIds.isEmpty()); assertNull(vm.state.value.catalog)
    }

    @Test fun lateDetectionAfterRetakeCannotResurrectTheScreenAndIsRetired() = runTest {
        val reply = CompletableDeferred<ApiResult<AiDetection>>()
        val fake = Fake().apply { detector = { withContext(NonCancellable) { reply.await() } } }
        val vm = model(fake); vm.detect(); runCurrent(); vm.retake()
        val late = detection(UUID.randomUUID().toString())
        reply.complete(ApiResult.Success(late)); advanceUntilIdle()
        assertEquals(ScanPhase.CAMERA, vm.state.value.phase); assertTrue(vm.state.value.objects.isEmpty())
        assertTrue(photo in deleted); assertEquals(listOf(late.id), fake.retired)
    }

    @Test fun correctingObjectRejectsALateRecommendationEvenIfCancellationIsIgnored() = runTest {
        val reply = CompletableDeferred<ApiResult<AiRecommendation>>()
        val fake = Fake().apply { recommender = { withContext(NonCancellable) { reply.await() } } }
        val vm = model(fake); confirm(vm); vm.recommend(); runCurrent()
        val old = recommendation(fake.inputs.single())
        vm.correctObject(); vm.category(WasteCategory.GLASS)
        reply.complete(ApiResult.Success(old)); advanceUntilIdle()
        assertEquals(ScanPhase.OBJECT, vm.state.value.phase); assertNull(vm.state.value.recommendation)
        assertNull(vm.prefill(true).method)
    }

    @Test fun closedSessionRejectsLateCameraCallbackAndClearsTheOwnerState() {
        val vm = model(); vm.close(); vm.start("new"); vm.photo("session", photo)
        assertEquals("new", vm.state.value.session); assertNull(vm.state.value.photo)
        assertNull(vm.state.value.catalog); assertNull(vm.state.value.recommendation)
        assertTrue(photo in deleted)
    }

    @Test fun expiredOrMissingLocalPhotoDoesNotCallTheApi() = runTest {
        val fake = Fake(); val vm = model(fake)
        now += 3_600_000; vm.detect(); advanceUntilIdle()
        assertTrue(fake.uploadIds.isEmpty()); assertEquals(ScanProblem.EXPIRED, vm.state.value.problem)
        vm.retry(); vm.photo("session", photo.copy(createdAt = now)); exists = false
        vm.detect(); advanceUntilIdle(); assertTrue(fake.uploadIds.isEmpty())
    }

    @Test fun detectionAndRecommendationExpiryCannotBecomeAnAiDraft() = runTest {
        val fake = Fake(); val vm = model(fake); confirm(vm)
        now += 60_000; vm.recommend(); advanceUntilIdle()
        assertTrue(fake.inputs.isEmpty()); assertEquals(ScanProblem.EXPIRED, vm.state.value.problem)
        vm.close(); deleted.clear(); now = 10_000
        vm.start("second"); vm.photo("second", photo); confirm(vm); vm.recommend(); advanceUntilIdle()
        now += 60_000
        assertNull(vm.handoff(true)); assertEquals(ScanProblem.EXPIRED, vm.state.value.problem)
        assertNull(vm.prefill(true).aiProvenance)
    }

    @Test fun timeoutKeepsContextAndOnlyTheUserCanStartANewInference() = runTest {
        val fake = Fake().apply { recommender = { delay(30_000); ApiResult.Success(recommendation(it)) } }
        val vm = model(fake); confirm(vm); val context = ScanContext(cleanliness = Cleanliness.DIRTY); vm.context(context)
        vm.recommend(); advanceUntilIdle()
        assertEquals(ScanProblem.RECOMMENDATION, vm.state.value.problem)
        assertEquals(context, vm.state.value.context)
        vm.retry(); advanceUntilIdle(); assertEquals(1, fake.inputs.size)
        vm.recommend(); advanceUntilIdle(); assertEquals(2, fake.inputs.size)
        assertNotEquals(fake.inputs[0].requestId, fake.inputs[1].requestId)
    }

    @Test fun retryAfterDoesNotFlashAnotherRequestOrSpendInferenceDuringTheDeadline() = runTest {
        val fake = Fake().apply { recommender = { ApiResult.Failure(ApiFailure(ApiFailureKind.RATE_LIMITED,
            retryAt = Instant.ofEpochMilli(now + 5_000))) } }
        val vm = model(fake); confirm(vm); vm.recommend(); advanceUntilIdle(); vm.retry()
        vm.recommend(); advanceUntilIdle()
        assertEquals(1, fake.inputs.size); assertEquals(ScanPhase.CONTEXT, vm.state.value.phase)
        now += 5_000; vm.recommend(); advanceUntilIdle(); assertEquals(2, fake.inputs.size)
    }

    @Test fun specialHandlingKeepsAValidReadyMethodButUnavailableAndPrototypeCannotPrefill() = runTest {
        val fake = Fake().apply { recommender = { ApiResult.Success(recommendation(it).copy(specialHandling = true)) } }
        val vm = model(fake); confirm(vm); vm.recommend(); advanceUntilIdle()
        assertEquals(DisposalMethod.RECYCLE, vm.prefill(true).method)
        for (change in listOf<(AiRecommendation) -> AiRecommendation>(
            { it.copy(prototypeOnly = true) },
            { it.copy(status = AiRecommendationStatus.UNAVAILABLE, method = null, scope = null, basis = null) },
            { it.copy(method = DisposalMethod.REDUCE, scope = AiRecommendationScope.PREVENTION) })) {
            fake.recommender = { ApiResult.Success(change(recommendation(it))) }
            vm.reviewContext(); vm.recommend(); advanceUntilIdle()
            assertNull(vm.prefill(true).method); assertNull(vm.handoff(true))
        }
    }

    @Test fun missingContextReturnsToIndependentControlsAndAConditionalSuggestionNeedsAnotherConfirmation() = runTest {
        val fake = Fake().apply { recommender = { ApiResult.Success(recommendation(it).copy(
            status = AiRecommendationStatus.NEEDS_CONTEXT, method = null, scope = null, basis = null,
            missingContext = listOf(AiContextField.RISK_TYPE))) } }
        val vm = model(fake); confirm(vm); vm.recommend(); advanceUntilIdle()
        assertFalse(vm.state.value.canUseRecommendation); vm.reviewContext()
        vm.context(ScanContext(riskFlag = true, riskType = AiRiskType.SHARP))
        fake.recommender = { ApiResult.Success(recommendation(it).copy(basis = AiRecommendationBasis.CONDITIONAL,
            conditionsToVerify = listOf(AiContextField.CLEANLINESS))) }
        vm.recommend(); advanceUntilIdle()
        assertNull(vm.handoff(true)); assertFalse(vm.state.value.canUseRecommendation)
        vm.confirmConditions(true); assertNotNull(vm.prefill(true).aiProvenance)
        vm.reviewContext(); assertFalse(vm.state.value.conditionsConfirmed)
    }

    @Test fun handoffPreservesServerDetectionForCoreSaveButDeletesTheLocalPhoto() = runTest {
        val fake = Fake(); val vm = model(fake); confirm(vm); vm.recommend(); advanceUntilIdle()
        val prefill = vm.handoff(true)!!
        vm.close(); advanceUntilIdle()
        assertNotNull(prefill.aiProvenance); assertTrue(photo in deleted)
        assertTrue("DELETE would invalidate the recommendation before core POST", fake.retired.isEmpty())
    }

    @Test fun manualHandoffDoesNotClaimAiConfirmationAndRetiresKnownDetection() = runTest {
        val fake = Fake(); val vm = model(fake); confirm(vm); vm.recommend(); advanceUntilIdle()
        val id = vm.state.value.detection!!.id
        val prefill = vm.handoff(false)!!; vm.close(); advanceUntilIdle()
        assertNull(prefill.method); assertNull(prefill.aiProvenance)
        assertEquals(WasteCategory.PLASTIC, prefill.category); assertEquals(listOf(id), fake.retired)
    }

    @Test fun duplicateObjectsAndInvalidRecommendationFailInsteadOfCrashingTheScreen() = runTest {
        val fake = Fake().apply { detector = { id -> ApiResult.Success(detection(id).copy(objects = listOf(candidate(), candidate()))) } }
        val vm = model(fake); vm.detect(); advanceUntilIdle()
        assertEquals(ScanProblem.DETECTION, vm.state.value.problem)
        fake.detector = { ApiResult.Success(detection(it)) }; vm.retry(); confirm(vm)
        fake.recommender = { throw IllegalArgumentException("Invalid response") }
        vm.recommend(); advanceUntilIdle()
        assertEquals(ScanProblem.RECOMMENDATION, vm.state.value.problem); assertNull(vm.prefill(true).method)
    }

    @Test fun anotherObjectResetsContextAndRequiresAnotherCatalogConfirmation() = runTest {
        val fake = Fake().apply { detector = { id -> ApiResult.Success(detection(id).copy(objects = listOf(candidate(), candidate().copy(id = "second")))) } }
        val vm = model(fake); confirm(vm); vm.context(ScanContext(reuseIntention = true)); vm.correctObject(); vm.select("second")
        assertEquals(ScanContext(), vm.state.value.context); assertNull(vm.state.value.category); assertNull(vm.state.value.objectKey)
    }

    @Test fun demoCanExerciseTheEditorWithoutClaimingLiveProvenance() = runTest {
        val vm = model(DummyScanRepository()); confirm(vm); vm.recommend(); advanceUntilIdle()
        assertTrue(vm.state.value.recommendation!!.prototypeOnly)
        assertEquals(DisposalMethod.RECYCLE, vm.prefill(true).method); assertNull(vm.prefill(true).aiProvenance)
    }

    @Test fun boundingBoxesRejectInvalidCoordinatesAndFitALetterboxedImage() {
        assertThrows(IllegalArgumentException::class.java) { ScanBounds(Float.NaN, 0f, 1f, 1f) }
        assertThrows(IllegalArgumentException::class.java) { ScanBounds(.5f, 0f, .1f, 1f) }
        val box = fitScanBounds(800, 400, 300f, 300f, ScanBounds(.25f, .25f, .75f, .75f))
        assertEquals(75f, box.left); assertEquals(112.5f, box.top); assertEquals(150f, box.width); assertEquals(75f, box.height)
    }

    private fun candidate() = AiDetectionObject("object-1", 0, "bottle", .94, ScanBounds(.18f, .1f, .82f, .9f),
        AiConfidenceBand.OFFERED, true, true, listOf("plastic_bottle"), emptyList(), emptyList(), null)
    private fun detection(id: String) = AiDetection(id, Instant.ofEpochMilli(now + 60_000), AiDetectionStatus.REVIEW_REQUIRED,
        "fixture", "fixture", "fixture", AiThresholds(.5, .7), 800, 600, listOf(candidate()))
    private fun recommendation(input: AiRecommendationRequest) = AiRecommendation(UUID.randomUUID().toString(),
        Instant.ofEpochMilli(now + 60_000), AiRecommendationStatus.READY, input.detectionId, input.objectId,
        input.contextRevision, input.objectKey, input.category, DisposalMethod.RECYCLE, AiRecommendationScope.HANDLING,
        AiRecommendationBasis.CONFIRMED, emptyList(), emptyList(), "Fixture only", null, false, "", "fixture", false,
        "fixture", listOf("fixture"), emptyList(), emptyList(), emptyList(), emptyList())
    private inner class Fake : ScanRepository {
        override val isDemo = false
        val inputs = mutableListOf<AiRecommendationRequest>()
        val uploadIds = mutableListOf<String>()
        val retired = mutableListOf<String>()
        var catalogResult: ApiResult<AiCatalog> = ApiResult.Success(AiCatalog("fixture", listOf(
            AiCatalogItem("plastic_bottle", "Botol plastik", WasteCategory.PLASTIC, false, AiGuideStatus.PUBLISHED),
            AiCatalogItem("glass_bottle", "Botol kaca", WasteCategory.GLASS, false, AiGuideStatus.PUBLISHED),
        ), "fixture", "fixture", "fixture", emptyMap(), emptySet(), mapOf("bottle" to "Botol")))
        var detector: suspend (String) -> ApiResult<AiDetection> = { ApiResult.Success(detection(it)) }
        var recommender: suspend (AiRecommendationRequest) -> ApiResult<AiRecommendation> = { ApiResult.Success(recommendation(it)) }
        override suspend fun catalog() = catalogResult
        override suspend fun detect(photo: ScanPhoto, requestId: String): ApiResult<AiDetection> {
            uploadIds.add(requestId); return detector(requestId)
        }
        override suspend fun recommend(input: AiRecommendationRequest): ApiResult<AiRecommendation> {
            inputs.add(input); return recommender(input)
        }
        override suspend fun deleteDetection(id: String): ApiResult<Unit> { retired.add(id); return ApiResult.Success(Unit) }
    }
}
