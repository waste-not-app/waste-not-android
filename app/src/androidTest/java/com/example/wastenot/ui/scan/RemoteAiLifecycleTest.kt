package com.example.wastenot.ui.scan

import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wastenot.BuildConfig
import com.example.wastenot.LogEditor
import com.example.wastenot.data.local.*
import com.example.wastenot.data.network.*
import com.example.wastenot.data.repository.*
import com.example.wastenot.model.*
import com.example.wastenot.ui.main.*
import java.io.File
import java.security.MessageDigest
import java.time.ZoneId
import java.util.UUID
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class RemoteAiLifecycleTest {
    @Test fun publicPilotPreservesConfirmedAiReferenceThroughHandoffAndSaveReplay() = runTest(timeout = 4.minutes) {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("verifyLiveAiFixture") == "true")
        assertTrue(BuildConfig.DEBUG)
        assertTrue((androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as com.example.wastenot.WasteNotApplication).container.accountSession is com.example.wastenot.data.repository.RemoteAccountSession)
        val base = "https://pred-autoscaler.bccdev.id/wastenot/api/v1/"
        assertEquals(base, BuildConfig.API_BASE_URL)
        val runId = UUID.fromString(requireNotNull(args.getString("fixtureRunId"))).toString()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val evidence = JSONObject().put("run_id", runId).put("email", "android-$runId@example.test")
            .put("name", "Android transport probe").put("context_is_fixture", true)
        val steps = JSONArray()
        val output = File(context.cacheDir, "qa/backend/ai-fixture.json")
        fun record(step: String) {
            output.parentFile!!.mkdirs()
            output.writeText(evidence.put("passed_steps", steps.put(step)).toString(2))
        }
        val network = requireNotNull(WasteNotNetwork.create(base))
        var saved: RefreshToken? = null
        val tokens = object : RefreshTokenStore {
            override suspend fun read(): TokenReadResult = saved?.let(TokenReadResult::Available) ?: TokenReadResult.Absent
            override suspend fun write(token: RefreshToken) { saved = token }
            override suspend fun clear() { saved = null }
        }
        val session = SessionRepository(network, tokens)
        val core = CoreRepository(network, session)
        val ai = AiRepository(network, session)
        val activities = RemoteActivityStore(core)
        val views = ViewModelStore()
        var drafts: LocalEditorDraftStore? = null
        val directory = File(context.cacheDir, "ai-live-$runId")
        try {
            val user = success(session.register("Android transport probe", "android-$runId@example.test",
                UUID.randomUUID().toString() + "Az9!", ZoneId.of("Asia/Jakarta")))
            evidence.put("user_id", user.id)
            record("register_owned_fixture")
            val metadata = success(core.metadata())
            assertTrue(metadata.isEnabled("ai_scan") && metadata.isEnabled("ai_recommendation"))
            val catalog = success(ai.catalog())
            assertTrue(catalog.items.any { it.key == "pet_bottle" && it.guideStatus == AiGuideStatus.PUBLISHED })
            evidence.put("published_objects", JSONArray(catalog.items.filter { it.guideStatus == AiGuideStatus.PUBLISHED }.map { it.key }))
            record("live_capabilities_and_catalog")

            val bytes = instrumentation.context.assets.open("ai/pet-bottle.jpg").use { it.readBytes() }
            assertEquals("1f8bd2c97dc5321a22e8a052c32f6ecabd6789632517f173c9799072a23cc9fa", sha256(bytes))
            val photos = ScanPhotoCache(directory, user.id) {}
            val photo = withContext(Dispatchers.IO) { photos.save(bytes, 0) }
            evidence.put("uploaded_photo_sha256", sha256(photos.read(photo)))
            val remote = RemoteScanRepository(ai, photos)
            val requests = mutableListOf<AiRecommendationRequest>()
            val repository = object : ScanRepository by remote {
                override suspend fun recommend(input: AiRecommendationRequest): ApiResult<AiRecommendation> {
                    requests.add(input)
                    return remote.recommend(input)
                }
            }
            val model = withContext(Dispatchers.Main) {
                ScanViewModel(repository, photos).also {
                    views.put("scan", it); it.start(runId); it.photo(runId, photo); it.detect()
                }
            }
            await(model, ScanPhase.OBJECT)
            val detection = requireNotNull(model.state.value.detection)
            evidence.put("detection_id", detection.id).put("model_version", detection.modelVersion)
                .put("objects", JSONArray(detection.objects.map { JSONObject().put("raw_class", it.rawClass)
                    .put("band", it.band.name).put("confidence", it.confidence).put("supported", it.supportedForReview) }))
            record("received_real_detector_candidates")
            // The baseline calls this PET photo an uncertain vase; exercise explicit user correction.
            val candidate = requireNotNull(model.state.value.selected)
            assertNull(model.state.value.category)
            assertNull(model.state.value.objectKey)
            evidence.put("detection_id", detection.id).put("model_version", detection.modelVersion)
                .put("raw_class", candidate.rawClass).put("band", candidate.band.name).put("confidence", candidate.confidence)
                .put("user_corrected_candidate", candidate.rawClass != "bottle")
            record("real_yolo_band_preserved_before_explicit_user_material_confirmation")
            withContext(Dispatchers.Main) {
                model.select(candidate.id); model.category(WasteCategory.PLASTIC); model.objectKey("pet_bottle")
                model.confirmObject()
                model.context(ScanContext(cleanliness = Cleanliness.CLEAN, integrity = Integrity.INTACT,
                    reusable = AiAnswer.NO, reuseIntention = false, recyclingAccess = AiAnswer.YES))
                model.recommend()
            }
            await(model, ScanPhase.RECOMMENDATION)
            assertEquals(AiRecommendationStatus.NEEDS_CONTEXT, model.state.value.recommendation?.status)
            assertTrue(model.state.value.recommendation!!.missingContext.contains(AiContextField.FORMER_CONTENTS))
            withContext(Dispatchers.Main) {
                model.reviewContext()
                assertTrue(model.state.value.requestedContext.contains(AiContextField.FORMER_CONTENTS))
                model.context(model.state.value.context.copy(formerContents = AiFormerContents.FOOD_DRINK))
                model.recommend()
            }
            record("server_requested_former_contents_returns_to_context")
            await(model, ScanPhase.RECOMMENDATION)
            val recommendation = requireNotNull(model.state.value.recommendation)
            evidence.put("recommendation_id", recommendation.id).put("provider", recommendation.provider)
                .put("prototype_only", recommendation.prototypeOnly).put("recommendation_status", recommendation.status.name)
                .put("reason_code", recommendation.reasonCode).put("guide_refs", JSONArray(recommendation.guideRefs.map { "${it.id}:${it.version}" }))
            record("received_live_recommendation_response")
            assertEquals(AiRecommendationStatus.READY, recommendation.status)
            assertEquals("gemini", recommendation.provider)
            assertFalse(recommendation.prototypeOnly)
            assertEquals(DisposalMethod.RECYCLE, recommendation.method)
            assertTrue(recommendation.sources.isNotEmpty() && recommendation.guideRefs.isNotEmpty())
            assertEquals(2, requests.size)
            assertNotEquals(requests[0].requestId, requests[1].requestId)
            assertEquals(recommendation, success(ai.recommend(requests.last())))
            val before = success(core.dashboard())
            assertEquals(0L, before.summary.activityCount)
            assertEquals(0, before.progress.xp)
            record("gemini_replay_does_not_auto_save_or_award_xp")

            val prefill = withContext(Dispatchers.Main) { requireNotNull(model.handoff(true)).also { model.close() } }
            assertFalse(photos.exists(photo))
            assertEquals(recommendation.id, prefill.aiProvenance?.recommendationId)
            val requestId = UUID.randomUUID().toString()
            val key = LogEditor(null, requestId, scanCategory = prefill.category!!.name,
                scanMethod = prefill.method!!.name, scanRecommendationId = recommendation.id)
            val draft = EditorDraft.from(null, before.date).applyScan(key)
            assertEquals("", draft.values.quantity.rawText)
            draft.change(EditorField.QUANTITY, draft.values.copy(quantity = draft.values.quantity.copy(rawText = "250")))
            val input = requireNotNull(draft.prepareSubmission(before.date))
            val created = success(activities.create(input, requestId))
            assertTrue(created.created)
            evidence.put("log_id", created.log.id)
            assertEquals(10, success(core.dashboard()).progress.xp)
            record("handoff_keeps_detection_valid_until_explicit_core_save")

            val duplicate = activities.create(input, UUID.randomUUID().toString())
            assertEquals(ApiFailureKind.AI_ALREADY_LINKED, (duplicate as? ApiResult.Failure)?.error?.kind)
            success(ai.deleteDetection(detection.id))
            // Simulate loss of the successful acknowledgement; the server write above is real.
            draft.submissionFailed(ApiFailureKind.TIMEOUT)
            val disk = LocalEditorDraftStore.create(context, user.id)
            drafts = disk
            success(disk.write(draft.stored(key, MainTab.HOME)))
            val persisted = requireNotNull(success(disk.read()))
            val restored = persisted.restoreForm()
            assertTrue(restored.outcomeUnknown && restored.locked)
            val replay = success(activities.create(requireNotNull(restored.prepareSubmission(before.date)), persisted.requestId))
            assertFalse(replay.created)
            assertEquals(created.log, replay.log)
            val after = success(core.dashboard())
            assertEquals(QuantityTotals(250, 0), after.summary.handled)
            assertEquals(10, after.progress.xp)
            record("disk_restored_unknown_save_replays_once_after_detection_deletion")
            success(activities.delete(created.log.id, created.log.version))
            assertEquals(0, success(core.dashboard()).progress.xp)
            assertTrue(success(activities.load()).isEmpty())
            success(disk.clear())
            record("delete_reverses_log_and_derived_xp")
        } finally {
            output.parentFile!!.mkdirs()
            output.writeText(evidence.put("passed_steps", steps).toString(2))
            withContext(Dispatchers.Main) { views.clear() }
            drafts?.clear()
            session.logout()
            directory.deleteRecursively()
            network.client.dispatcher.cancelAll()
            network.client.connectionPool.evictAll()
            network.client.dispatcher.executorService.shutdown()
        }
    }

    private suspend fun await(model: ScanViewModel, phase: ScanPhase) = withContext(Dispatchers.Default) {
        withTimeout(45_000) {
            while (model.state.value.phase !in setOf(phase, ScanPhase.FAILURE)) delay(50)
        }
        assertEquals("Live scan failure: ${model.state.value.problem}, ${model.state.value.error?.kind}", phase, model.state.value.phase)
    }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun <T> success(result: ApiResult<T>): T {
        assertTrue("API failure: ${(result as? ApiResult.Failure)?.error?.kind}", result is ApiResult.Success)
        return (result as ApiResult.Success).value
    }
}
