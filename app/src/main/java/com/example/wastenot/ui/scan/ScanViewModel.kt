package com.example.wastenot.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.repository.ScanRepository
import com.example.wastenot.model.*
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

enum class ScanPhase { CAMERA, PHOTO, DETECTING, OBJECT, CONTEXT, RECOMMENDING, RECOMMENDATION, FAILURE }
enum class ScanProblem { CAMERA, PERMISSION, PHOTO, NO_OBJECT, DETECTION, RECOMMENDATION, EXPIRED }
data class ScanState(val session: String = "", val phase: ScanPhase = ScanPhase.CAMERA,
    val photo: ScanPhoto? = null, val objects: List<ScanObject> = emptyList(), val selectedId: String? = null,
    val category: WasteCategory? = null, val context: ScanContext = ScanContext(), val objectConfirmed: Boolean = false,
    val recommendation: ScanRecommendation? = null, val method: DisposalMethod? = null,
    val problem: ScanProblem? = null) {
    val selected: ScanObject? get() = objects.find { it.id == selectedId }
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
    private val mutable = MutableStateFlow(ScanState())
    val state = mutable.asStateFlow()
    private var generation = 0
    private var job: Job? = null

    fun start(session: String) {
        if (state.value.session == session) return
        close(); mutable.value = ScanState(session = session)
    }

    fun close(session: String = state.value.session) {
        if (session != state.value.session) return
        invalidate(); state.value.photo?.let(photos::delete); mutable.value = ScanState()
    }

    fun photo(session: String, photo: ScanPhoto) {
        if (session != state.value.session || state.value.phase != ScanPhase.CAMERA) { photos.delete(photo); return }
        invalidate(); state.value.photo?.let(photos::delete)
        mutable.value = ScanState(session, ScanPhase.PHOTO, photo)
    }

    fun problem(session: String, problem: ScanProblem) {
        if (session != state.value.session) return
        invalidate(); mutable.value = state.value.copy(phase = ScanPhase.FAILURE, problem = problem)
    }

    fun retake() {
        val session = state.value.session
        invalidate(); state.value.photo?.let(photos::delete); mutable.value = ScanState(session)
    }

    fun detect() {
        val snapshot = state.value
        if (snapshot.phase != ScanPhase.PHOTO) return
        if (!validPhoto(snapshot)) return
        invalidate(); val ticket = generation
        mutable.value = snapshot.copy(phase = ScanPhase.DETECTING, problem = null)
        job = viewModelScope.launch {
            val result = request { repository.detect(checkNotNull(snapshot.photo)) }
            if (ticket != generation) return@launch
            val candidates = (result as? ApiResult.Success)?.value
            if (candidates == null || candidates.map { it.id }.distinct().size != candidates.size) {
                mutable.value = state.value.copy(phase = ScanPhase.FAILURE, problem = ScanProblem.DETECTION)
            } else {
                val offered = candidates.filter { it.score >= .5f }
                mutable.value = snapshot.copy(phase = if (offered.isEmpty()) ScanPhase.FAILURE else ScanPhase.OBJECT,
                    objects = offered, selectedId = offered.firstOrNull()?.id, category = offered.firstOrNull()?.category,
                    problem = ScanProblem.NO_OBJECT.takeIf { offered.isEmpty() })
            }
        }
    }

    fun select(id: String) {
        val current = state.value
        if (current.phase != ScanPhase.OBJECT || current.selectedId == id) return
        val candidate = current.objects.find { it.id == id } ?: return
        invalidate(); mutable.value = current.copy(selectedId = id, category = candidate.category,
            context = ScanContext(), objectConfirmed = false, recommendation = null, method = null)
    }

    fun category(category: WasteCategory) {
        val current = state.value
        if (current.phase != ScanPhase.OBJECT || current.category == category) return
        invalidate(); mutable.value = current.copy(category = category, context = ScanContext(),
            objectConfirmed = false, recommendation = null, method = null)
    }

    fun confirmObject() {
        val current = state.value
        if (current.phase != ScanPhase.OBJECT || current.selected == null || current.category == null) return
        mutable.value = current.copy(phase = ScanPhase.CONTEXT, objectConfirmed = true)
    }

    fun correctObject() {
        if (!state.value.objectConfirmed) return
        invalidate(); mutable.value = state.value.copy(phase = ScanPhase.OBJECT, recommendation = null, method = null)
    }

    fun context(context: ScanContext) {
        if (state.value.phase != ScanPhase.CONTEXT) return
        invalidate(); mutable.value = state.value.copy(context = context, recommendation = null, method = null)
    }

    fun recommend() {
        val snapshot = state.value
        val selected = snapshot.selected ?: return
        val category = snapshot.category ?: return
        if (snapshot.phase != ScanPhase.CONTEXT || !snapshot.objectConfirmed || !validPhoto(snapshot)) return
        invalidate(); val ticket = generation
        mutable.value = snapshot.copy(phase = ScanPhase.RECOMMENDING)
        job = viewModelScope.launch {
            val result = request { repository.recommend(selected, category, snapshot.context) }
            if (ticket != generation) return@launch
            val recommendation = (result as? ApiResult.Success)?.value
            mutable.value = if (recommendation == null) snapshot.copy(phase = ScanPhase.FAILURE, problem = ScanProblem.RECOMMENDATION)
            else snapshot.copy(phase = ScanPhase.RECOMMENDATION, recommendation = recommendation,
                method = recommendation.method.takeUnless { selected.specialHandling })
        }
    }

    fun method(method: DisposalMethod) {
        if (state.value.phase == ScanPhase.RECOMMENDATION && state.value.recommendation?.method != null &&
            state.value.selected?.specialHandling == false && method != DisposalMethod.REDUCE) {
            mutable.value = state.value.copy(method = method)
        }
    }

    fun prefill(confirmRecommendation: Boolean = false): ScanPrefill {
        val current = state.value
        val category = current.category.takeIf { current.objectConfirmed }
        val method = current.method.takeIf { confirmRecommendation && category != null &&
            current.phase == ScanPhase.RECOMMENDATION && current.recommendation?.method != null && current.selected?.specialHandling == false }
        return ScanPrefill(category, method)
    }

    fun retry() {
        if (state.value.phase != ScanPhase.FAILURE) return
        if (state.value.problem == ScanProblem.RECOMMENDATION) {
            mutable.value = state.value.copy(phase = ScanPhase.CONTEXT, problem = null)
        } else if (state.value.problem in listOf(ScanProblem.DETECTION, ScanProblem.NO_OBJECT) && validPhoto(state.value)) {
            mutable.value = state.value.copy(phase = ScanPhase.PHOTO, problem = null)
        } else retake()
    }

    private fun validPhoto(snapshot: ScanState): Boolean {
        val photo = snapshot.photo
        if (photo != null && now() - photo.createdAt in 0 until 3_600_000 && photos.exists(photo)) return true
        invalidate(); photo?.let(photos::delete)
        mutable.value = snapshot.copy(phase = ScanPhase.FAILURE, photo = null, problem = ScanProblem.EXPIRED)
        return false
    }

    private fun invalidate() { generation++; job?.cancel(); job = null }
    private suspend fun <T> request(action: suspend () -> ApiResult<T>): ApiResult<T>? = try {
        withTimeout(25_000) { action() }
    } catch (_: TimeoutCancellationException) { null }
      catch (cancelled: CancellationException) { throw cancelled }
      catch (_: IOException) { null }
      catch (_: IllegalArgumentException) { null }

    override fun onCleared() { close() }
}
