package com.example.wastenot.ui.scan

import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.wastenot.LogEditor
import com.example.wastenot.data.network.*
import com.example.wastenot.data.repository.*
import com.example.wastenot.model.*
import com.example.wastenot.ui.main.EditorDraft
import com.example.wastenot.ui.main.EditorField
import java.time.LocalDate
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
    private val source = DummyScanRepository()
    private val photos = object : ScanPhotoStore {
        override fun exists(photo: ScanPhoto) = exists && photo !in deleted
        override fun delete(photo: ScanPhoto) { deleted.add(photo) }
    }
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { owners.forEach { it.viewModelStore.clear() }; Dispatchers.resetMain() }
    private fun model(repo: ScanRepository = source): ScanViewModel {
        val owner = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
        owners.add(owner)
        return ViewModelProvider.create(owner, viewModelFactory { initializer { ScanViewModel(repo, photos) { now } } })[ScanViewModel::class]
            .apply { start("session"); photo("session", photo) }
    }
    private suspend fun TestScope.confirm(vm: ScanViewModel) {
        vm.detect(); advanceUntilIdle(); vm.category(WasteCategory.PLASTIC); vm.confirmObject()
    }

    @Test fun detectionRequiresMaterialAndNeverPrefillsAnUnconfirmedObject() = runTest {
        val vm = model(); vm.detect(); advanceUntilIdle()
        assertEquals(ScanPhase.OBJECT, vm.state.value.phase)
        assertNull(vm.state.value.category)
        vm.confirmObject(); assertEquals(ScanPhase.OBJECT, vm.state.value.phase)
        vm.category(WasteCategory.GLASS)
        assertEquals(ScanPrefill(null, null), vm.prefill())
        vm.confirmObject(); assertEquals(ScanPrefill(WasteCategory.GLASS, null), vm.prefill())
    }

    @Test fun contextIsIndependentAndCategoryChangeResetsItWhileGoingBackPreservesIt() = runTest {
        val vm = model(); confirm(vm)
        val context = ScanContext(Cleanliness.CLEAN, Integrity.DAMAGED, Reusability.YES, null)
        vm.context(context); vm.correctObject()
        assertEquals(context, vm.state.value.context)
        vm.category(WasteCategory.PLASTIC); assertEquals(context, vm.state.value.context)
        vm.confirmObject(); assertEquals(context, vm.state.value.context)
        vm.correctObject(); vm.category(WasteCategory.GLASS)
        assertEquals(ScanContext(), vm.state.value.context)
        assertNull(vm.prefill().category)
    }

    @Test fun recommendationIsConfirmedSeparatelyAndEditorStillRequiresManualQuantity() = runTest {
        val vm = model(); confirm(vm); vm.recommend(); advanceUntilIdle()
        assertEquals(DisposalMethod.RECYCLE, vm.state.value.method)
        assertNull(vm.prefill().method)
        vm.method(DisposalMethod.TRASH)
        val prefill = vm.prefill(true)
        assertEquals(DisposalMethod.TRASH, prefill.method)
        val date = LocalDate.of(2026, 10, 23)
        val draft = EditorDraft.from(null, date).applyScan(LogEditor(null, "request", scanCategory = prefill.category?.name, scanMethod = prefill.method?.name))
        assertTrue(draft.dirty); assertEquals("", draft.values.quantity.rawText)
        assertEquals(QuantityUnit.GRAM, draft.values.quantity.unit)
        assertEquals(date, draft.values.date); assertNull(draft.values.version)
        assertNull(draft.prepareSubmission(date)); assertTrue(EditorField.QUANTITY in draft.errors)
        vm.method(DisposalMethod.REDUCE); assertEquals(DisposalMethod.TRASH, vm.state.value.method)
    }

    @Test fun weakOrMissingCandidatesRequireManualWithoutPrefill() = runTest {
        for (candidates in listOf(emptyList(), listOf(ScanObject("weak", "Botol", .49f, ScanBounds(0f, 0f, 1f, 1f))))) {
            val vm = model(object : ScanRepository by source { override suspend fun detect(photo: ScanPhoto) = ApiResult.Success(candidates) })
            vm.detect(); advanceUntilIdle()
            assertEquals(ScanProblem.NO_OBJECT, vm.state.value.problem)
            assertEquals(ScanPrefill(null, null), vm.prefill(true))
        }
    }

    @Test fun uncertainCandidateStillNeedsExplicitConfirmation() = runTest {
        val vm = model(object : ScanRepository by source {
            override suspend fun detect(photo: ScanPhoto) = ApiResult.Success(listOf(ScanObject("uncertain", "Botol", .5f, ScanBounds(0f, 0f, 1f, 1f))))
        })
        vm.detect(); advanceUntilIdle(); assertEquals(ScanPhase.OBJECT, vm.state.value.phase)
        assertNull(vm.prefill().category)
    }

    @Test fun cancelledDetectionCannotResurrectAfterRetakeEvenWhenRepositoryIgnoresCancellation() = runTest {
        val reply = CompletableDeferred<ApiResult<List<ScanObject>>>()
        val vm = model(object : ScanRepository by source {
            override suspend fun detect(photo: ScanPhoto) = withContext(NonCancellable) { reply.await() }
        })
        vm.detect(); runCurrent(); vm.retake()
        reply.complete(source.detect(photo)); advanceUntilIdle()
        assertEquals(ScanPhase.CAMERA, vm.state.value.phase); assertTrue(vm.state.value.objects.isEmpty())
        assertTrue(photo in deleted)
    }

    @Test fun correctedObjectRejectsLateRecommendationAndClearsOldMethod() = runTest {
        val reply = CompletableDeferred<ApiResult<ScanRecommendation>>()
        val vm = model(object : ScanRepository by source {
            override suspend fun recommend(candidate: ScanObject, category: WasteCategory, context: ScanContext) =
                withContext(NonCancellable) { reply.await() }
        })
        confirm(vm); vm.recommend(); runCurrent(); vm.correctObject(); vm.category(WasteCategory.GLASS)
        reply.complete(source.recommend(vm.state.value.selected!!, WasteCategory.PLASTIC, ScanContext())); advanceUntilIdle()
        assertEquals(ScanPhase.OBJECT, vm.state.value.phase); assertNull(vm.state.value.recommendation)
        assertNull(vm.prefill(true).method)
    }

    @Test fun cameraCallbackFromClosedSessionDeletesPhotoAndCannotEnterNewSession() {
        val vm = model(); vm.close(); vm.start("new"); vm.photo("session", photo)
        assertEquals("new", vm.state.value.session); assertNull(vm.state.value.photo)
        assertTrue(photo in deleted)
    }

    @Test fun expiredOrMissingPhotoRequiresRetakeAndNeverCallsRepository() = runTest {
        var calls = 0
        val vm = model(object : ScanRepository by source {
            override suspend fun detect(photo: ScanPhoto): ApiResult<List<ScanObject>> { calls++; return source.detect(photo) }
        })
        now += 3_600_000; vm.detect(); advanceUntilIdle()
        assertEquals(0, calls); assertEquals(ScanProblem.EXPIRED, vm.state.value.problem)
        assertNull(vm.state.value.photo)
        vm.retry(); vm.photo("session", photo.copy(createdAt = now)); exists = false
        vm.detect(); advanceUntilIdle(); assertEquals(0, calls)
    }

    @Test fun recommendationTimeoutPreservesContextAndRetryDoesNotSendAutomatically() = runTest {
        var calls = 0
        val vm = model(object : ScanRepository by source {
            override suspend fun recommend(candidate: ScanObject, category: WasteCategory, context: ScanContext): ApiResult<ScanRecommendation> {
                calls++; delay(30_000); return source.recommend(candidate, category, context)
            }
        })
        confirm(vm); val context = ScanContext(cleanliness = Cleanliness.DIRTY); vm.context(context)
        vm.recommend(); advanceUntilIdle()
        assertEquals(ScanProblem.RECOMMENDATION, vm.state.value.problem)
        assertEquals(context, vm.state.value.context)
        vm.retry(); advanceUntilIdle(); assertEquals(1, calls); assertEquals(ScanPhase.CONTEXT, vm.state.value.phase)
    }

    @Test fun specialHandlingAndUnavailableGuideNeverOfferAMethod() = runTest {
        val special = ScanObject("special", "Baterai", .94f, ScanBounds(0f, 0f, 1f, 1f), WasteCategory.ELECTRONIC, true)
        val vm = model(object : ScanRepository by source { override suspend fun detect(photo: ScanPhoto) = ApiResult.Success(listOf(special)) })
        vm.detect(); advanceUntilIdle(); vm.confirmObject(); vm.recommend(); advanceUntilIdle()
        assertNull(vm.state.value.method); vm.method(DisposalMethod.TRASH); assertNull(vm.prefill(true).method)
        assertThrows(IllegalArgumentException::class.java) { ScanRecommendation(DisposalMethod.REDUCE, "", "", "", emptyList(), emptyList()) }
    }

    @Test fun unmatchedObjectDoesNotReceiveAGuideBasedOnlyOnItsCategory() = runTest {
        val unknown = ScanObject("unmapped", "Kemasan", .94f, ScanBounds(0f, 0f, 1f, 1f))
        val result = source.recommend(unknown, WasteCategory.PLASTIC, ScanContext()) as ApiResult.Success
        assertNull(result.value.method)
        val bottle = (source.detect(photo) as ApiResult.Success).value.single()
        assertNull((source.recommend(bottle, WasteCategory.GLASS, ScanContext()) as ApiResult.Success).value.method)
    }

    @Test fun invalidRecommendationBecomesUnavailableRatherThanCrashingTheScreen() = runTest {
        val vm = model(object : ScanRepository by source {
            override suspend fun recommend(candidate: ScanObject, category: WasteCategory, context: ScanContext): ApiResult<ScanRecommendation> =
                throw IllegalArgumentException("Invalid enum in response")
        })
        confirm(vm); vm.recommend(); advanceUntilIdle()
        assertEquals(ScanProblem.RECOMMENDATION, vm.state.value.problem)
        assertNull(vm.state.value.recommendation); assertNull(vm.prefill(true).method)
    }

    @Test fun selectingAnotherObjectResetsContextAndDuplicateIdsAreRejected() = runTest {
        val items = listOf(ScanObject("one", "Botol", .9f, ScanBounds(0f, 0f, .5f, 1f)),
            ScanObject("two", "Botol", .8f, ScanBounds(.5f, 0f, 1f, 1f)))
        val vm = model(object : ScanRepository by source { override suspend fun detect(photo: ScanPhoto) = ApiResult.Success(items) })
        confirm(vm); vm.context(ScanContext(reuseIntention = true)); vm.correctObject(); vm.select("two")
        assertEquals(ScanContext(), vm.state.value.context); assertNull(vm.state.value.category)
        val duplicate = model(object : ScanRepository by source { override suspend fun detect(photo: ScanPhoto) = ApiResult.Success(listOf(items[0], items[0])) })
        duplicate.detect(); advanceUntilIdle(); assertEquals(ScanProblem.DETECTION, duplicate.state.value.problem)
    }

    @Test fun boundingBoxesRejectInvalidCoordinatesAndFitLetterboxedImage() {
        assertThrows(IllegalArgumentException::class.java) { ScanBounds(Float.NaN, 0f, 1f, 1f) }
        assertThrows(IllegalArgumentException::class.java) { ScanBounds(.5f, 0f, .1f, 1f) }
        val box = fitScanBounds(800, 400, 300f, 300f, ScanBounds(.25f, .25f, .75f, .75f))
        assertEquals(75f, box.left); assertEquals(112.5f, box.top)
        assertEquals(150f, box.width); assertEquals(75f, box.height)
    }
}
