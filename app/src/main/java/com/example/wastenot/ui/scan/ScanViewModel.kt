package com.example.wastenot.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wastenot.data.network.*
import com.example.wastenot.data.repository.ScanRepository
import com.example.wastenot.model.*
import java.io.IOException
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

enum class ScanPhase { CAMERA, PHOTO, DETECTING, OBJECT, CONTEXT, RECOMMENDING, RECOMMENDATION, FAILURE }
enum class ScanProblem { CAMERA, PERMISSION, PHOTO, CATALOG, NO_OBJECT, DETECTION, RECOMMENDATION, EXPIRED }
data class ScanState(val session: String = "", val phase: ScanPhase = ScanPhase.CAMERA,
    val photo: ScanPhoto? = null, val objects: List<AiDetectionObject> = emptyList(), val selectedId: String? = null,
    val category: WasteCategory? = null, val context: ScanContext = ScanContext(), val objectConfirmed: Boolean = false,
    val recommendation: AiRecommendation? = null, val method: DisposalMethod? = null,
    val problem: ScanProblem? = null, val catalog: AiCatalog? = null, val detection: AiDetection? = null,
    val objectKey: String? = null, val revision: Int = 1, val conditionsConfirmed: Boolean = false,
    val error: ApiFailure? = null, val demo: Boolean = false,
    val requestedContext: List<AiContextField> = emptyList()) {
    val selected: AiDetectionObject? get() = objects.find { it.id == selectedId }
    val item: AiCatalogItem? get() = catalog?.items?.find { it.key == objectKey && it.category == category }
    val canChooseMethod: Boolean get() = recommendation?.let {
        it.status == AiRecommendationStatus.READY && it.scope == AiRecommendationScope.HANDLING &&
            it.method != null && it.method != DisposalMethod.REDUCE && (demo || !it.prototypeOnly)
    } == true
    val canUseRecommendation: Boolean get() = objectConfirmed && item != null && canChooseMethod && method != null &&
        (recommendation?.conditionsToVerify.isNullOrEmpty() || conditionsConfirmed)
    val step: Int get() = when (phase) {
        ScanPhase.CAMERA, ScanPhase.PHOTO, ScanPhase.DETECTING -> 0
        ScanPhase.OBJECT -> 1
        ScanPhase.CONTEXT, ScanPhase.RECOMMENDING -> 2
        ScanPhase.RECOMMENDATION -> 3
        ScanPhase.FAILURE -> if (problem == ScanProblem.RECOMMENDATION) 2 else 0
    }
}

class ScanViewModel(private val repository: ScanRepository, private val photos: ScanPhotoStore,
    private val now: () -> Long = System::currentTimeMillis) : ViewModel() {
    private val mutable = MutableStateFlow(ScanState(demo = repository.isDemo))
    val state = mutable.asStateFlow()
    private var generation = 0
    private var job: Job? = null
    private var transferred = false

    fun start(session: String) {
        if (state.value.session == session) return
        close(); transferred = false
        mutable.value = ScanState(session = session, demo = repository.isDemo)
    }

    fun close(session: String = state.value.session) {
        if (session != state.value.session) return
        val current = state.value
        invalidate(); current.photo?.let(photos::delete)
        if (!transferred) current.detection?.id?.let(::retire)
        mutable.value = ScanState(demo = repository.isDemo)
        transferred = false
    }

    fun photo(session: String, photo: ScanPhoto) {
        if (session != state.value.session || state.value.phase != ScanPhase.CAMERA) { photos.delete(photo); return }
        invalidate(); state.value.photo?.let(photos::delete)
        mutable.value = state.value.copy(phase = ScanPhase.PHOTO, photo = photo, problem = null, error = null)
    }

    fun problem(session: String, problem: ScanProblem) {
        if (session != state.value.session) return
        invalidate(); mutable.value = state.value.copy(phase = ScanPhase.FAILURE, problem = problem)
    }

    fun retake() {
        val current = state.value
        invalidate(); current.photo?.let(photos::delete); current.detection?.id?.let(::retire)
        mutable.value = ScanState(session = current.session, catalog = current.catalog, demo = repository.isDemo)
    }

    fun detect() {
        val snapshot = state.value
        if (snapshot.phase != ScanPhase.PHOTO || blocked(snapshot) || !validPhoto(snapshot)) return
        invalidate(); val ticket = generation
        val requestId = UUID.randomUUID().toString()
        mutable.value = snapshot.copy(phase = ScanPhase.DETECTING, problem = null, error = null)
        job = viewModelScope.launch {
            val catalogResult = snapshot.catalog?.let { ApiResult.Success(it) } ?: request { repository.catalog() }
            if (ticket != generation) return@launch
            val catalog = (catalogResult as? ApiResult.Success)?.value
            if (catalog == null) { failed(snapshot, ScanProblem.CATALOG, catalogResult); return@launch }
            val result = request {
                repository.detect(checkNotNull(snapshot.photo), requestId).also { response ->
                    // Observe late known IDs before cancellation discards the request's return value.
                    if (ticket != generation && response is ApiResult.Success) retire(response.value.id)
                }
            }
            val detection = (result as? ApiResult.Success)?.value
            if (ticket != generation) return@launch
            if (detection == null) failed(snapshot.copy(catalog = catalog), ScanProblem.DETECTION, result)
            else if (detection.expiresAt <= instant()) failed(snapshot.copy(catalog = catalog), ScanProblem.EXPIRED, expired())
            else {
                val offered = detection.objects.filter { it.supportedForReview && it.band != AiConfidenceBand.MANUAL_REQUIRED }
                if (detection.objects.map { it.id }.distinct().size != detection.objects.size) {
                    retire(detection.id)
                    failed(snapshot.copy(catalog = catalog), ScanProblem.DETECTION,
                        ApiResult.Failure(ApiFailure(ApiFailureKind.MALFORMED_RESPONSE)))
                } else mutable.value = snapshot.copy(catalog = catalog, detection = detection,
                    phase = if (offered.isEmpty()) ScanPhase.FAILURE else ScanPhase.OBJECT,
                    objects = offered, selectedId = offered.firstOrNull()?.id, category = null, objectKey = null,
                    problem = ScanProblem.NO_OBJECT.takeIf { offered.isEmpty() }, error = null)
            }
        }
    }

    fun select(id: String) {
        val current = state.value
        if (current.phase != ScanPhase.OBJECT || current.selectedId == id || current.objects.none { it.id == id }) return
        invalidate(); mutable.value = current.copy(selectedId = id, category = null, objectKey = null,
            context = ScanContext(), objectConfirmed = false, recommendation = null, method = null,
            conditionsConfirmed = false, requestedContext = emptyList(), revision = current.revision + 1)
    }

    fun category(category: WasteCategory) {
        val current = state.value
        if (current.phase != ScanPhase.OBJECT || current.category == category) return
        invalidate(); mutable.value = current.copy(category = category, objectKey = null, context = ScanContext(),
            objectConfirmed = false, recommendation = null, method = null, conditionsConfirmed = false,
            requestedContext = emptyList(), revision = current.revision + 1)
    }

    fun objectKey(key: String) {
        val current = state.value
        if (current.phase != ScanPhase.OBJECT || current.objectKey == key ||
            current.catalog?.items?.none { it.key == key && it.category == current.category } != false) return
        invalidate(); mutable.value = current.copy(objectKey = key, context = ScanContext(), objectConfirmed = false,
            recommendation = null, method = null, conditionsConfirmed = false,
            requestedContext = emptyList(), revision = current.revision + 1)
    }

    fun confirmObject() {
        val current = state.value
        if (current.phase != ScanPhase.OBJECT || current.selected == null || current.item == null || !validDetection(current)) return
        mutable.value = current.copy(phase = ScanPhase.CONTEXT, objectConfirmed = true)
    }

    fun correctObject() {
        if (!state.value.objectConfirmed) return
        invalidate(); mutable.value = state.value.copy(phase = ScanPhase.OBJECT, objectConfirmed = false,
            recommendation = null, method = null, conditionsConfirmed = false)
    }

    fun context(context: ScanContext) {
        val current = state.value
        if (current.phase != ScanPhase.CONTEXT || current.context == context) return
        invalidate(); mutable.value = current.copy(context = context, recommendation = null, method = null,
            conditionsConfirmed = false, revision = current.revision + 1)
    }

    fun reviewContext() {
        if (state.value.phase != ScanPhase.RECOMMENDATION) return
        val requested = state.value.recommendation?.let { it.missingContext.ifEmpty { it.conditionsToVerify } }.orEmpty()
        invalidate(); mutable.value = state.value.copy(phase = ScanPhase.CONTEXT, recommendation = null,
            method = null, conditionsConfirmed = false, requestedContext = requested)
    }

    fun recommend() {
        val snapshot = state.value
        val selected = snapshot.selected ?: return
        val item = snapshot.item ?: return
        if (snapshot.phase != ScanPhase.CONTEXT || !snapshot.objectConfirmed || blocked(snapshot) ||
            !validPhoto(snapshot) || !validDetection(snapshot)) return
        val input = AiRecommendationRequest(UUID.randomUUID().toString(), true, item.key, item.category,
            AiInputSource.DETECTION, snapshot.detection!!.id, selected.id, snapshot.revision,
            snapshot.context.condition(), snapshot.context.reuseIntention, snapshot.context.recyclingAccess,
            snapshot.context.riskFlag, snapshot.context.riskType)
        invalidate(); val ticket = generation
        mutable.value = snapshot.copy(phase = ScanPhase.RECOMMENDING, error = null)
        job = viewModelScope.launch {
            val result = request { repository.recommend(input) }
            if (ticket != generation) return@launch
            val recommendation = (result as? ApiResult.Success)?.value
            if (recommendation == null) failed(snapshot, ScanProblem.RECOMMENDATION, result)
            else if (recommendation.expiresAt <= instant()) failed(snapshot, ScanProblem.EXPIRED, expired())
            else {
                val updated = snapshot.copy(phase = ScanPhase.RECOMMENDATION, recommendation = recommendation,
                    conditionsConfirmed = false, error = null)
                mutable.value = updated.copy(method = recommendation.method.takeIf { updated.canChooseMethod })
            }
        }
    }

    fun method(method: DisposalMethod) {
        if (state.value.phase == ScanPhase.RECOMMENDATION && state.value.canChooseMethod && method != DisposalMethod.REDUCE)
            mutable.value = state.value.copy(method = method)
    }

    fun confirmConditions(confirmed: Boolean) {
        if (state.value.phase == ScanPhase.RECOMMENDATION) mutable.value = state.value.copy(conditionsConfirmed = confirmed)
    }

    fun prefill(confirmRecommendation: Boolean = false): ScanPrefill {
        val current = state.value
        val category = current.category.takeIf { current.objectConfirmed }
        val recommendation = current.recommendation
        val method = current.method.takeIf { confirmRecommendation && category != null && current.canUseRecommendation &&
            current.phase == ScanPhase.RECOMMENDATION && recommendation!!.expiresAt > instant() &&
            current.detection?.expiresAt?.let { it > instant() } == true }
        val provenance = if (method != null && !repository.isDemo) AiLogProvenance(recommendation!!.id, checkNotNull(category)) else null
        return ScanPrefill(category, method, provenance)
    }

    fun handoff(confirmRecommendation: Boolean): ScanPrefill? {
        if (confirmRecommendation && (!validPhoto(state.value) || !validDetection(state.value))) return null
        if (confirmRecommendation && state.value.recommendation?.expiresAt?.let { it <= instant() } == true) {
            failed(state.value, ScanProblem.EXPIRED, expired()); return null
        }
        val result = prefill(confirmRecommendation)
        if (confirmRecommendation && result.method == null) return null
        // Core validates the recommendation when saving; deleting its detection here would invalidate the draft.
        transferred = result.aiProvenance != null
        return result
    }

    fun retry() {
        val current = state.value
        if (current.phase != ScanPhase.FAILURE) return
        if (current.problem == ScanProblem.RECOMMENDATION) mutable.value = current.copy(phase = ScanPhase.CONTEXT, problem = null)
        else if (current.problem in listOf(ScanProblem.CATALOG, ScanProblem.DETECTION, ScanProblem.NO_OBJECT) && validPhoto(current)) {
            current.detection?.id?.let(::retire)
            mutable.value = current.copy(phase = ScanPhase.PHOTO, problem = null, detection = null,
                objects = emptyList(), selectedId = null, category = null, objectKey = null, objectConfirmed = false)
        } else retake()
    }

    private fun blocked(snapshot: ScanState) = snapshot.error?.retryAt?.let { it > instant() } == true
    private fun instant() = Instant.ofEpochMilli(now())
    private fun expired() = ApiResult.Failure(ApiFailure(ApiFailureKind.AI_EXPIRED))
    private fun failed(snapshot: ScanState, problem: ScanProblem, result: ApiResult<*>) {
        val error = (result as? ApiResult.Failure)?.error
        mutable.value = snapshot.copy(phase = ScanPhase.FAILURE,
            problem = if (error?.kind == ApiFailureKind.AI_EXPIRED) ScanProblem.EXPIRED else problem,
            recommendation = null, method = null, error = error)
    }

    private fun validDetection(snapshot: ScanState): Boolean {
        if (snapshot.detection?.expiresAt?.let { it > instant() } == true) return true
        invalidate(); failed(snapshot, ScanProblem.EXPIRED, expired()); return false
    }

    private fun validPhoto(snapshot: ScanState): Boolean {
        val photo = snapshot.photo
        if (photo != null && now() - photo.createdAt in 0 until 3_600_000 && photos.exists(photo)) return true
        invalidate(); photo?.let(photos::delete)
        mutable.value = snapshot.copy(phase = ScanPhase.FAILURE, photo = null, problem = ScanProblem.EXPIRED,
            recommendation = null, method = null)
        return false
    }

    private fun retire(id: String) {
        viewModelScope.launch { request { repository.deleteDetection(id) } }
    }
    private fun invalidate() { generation++; job?.cancel(); job = null }
    private suspend fun <T> request(action: suspend () -> ApiResult<T>): ApiResult<T> = try {
        withTimeout(25_000) { action() }
    } catch (_: TimeoutCancellationException) { ApiResult.Failure(ApiFailure(ApiFailureKind.TIMEOUT)) }
      catch (cancelled: CancellationException) { throw cancelled }
      catch (_: IOException) { ApiResult.Failure(ApiFailure(ApiFailureKind.NETWORK)) }
      catch (_: IllegalArgumentException) { ApiResult.Failure(ApiFailure(ApiFailureKind.MALFORMED_RESPONSE)) }

    override fun onCleared() { close() }
}
