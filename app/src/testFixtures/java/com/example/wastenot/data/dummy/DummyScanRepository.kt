package com.example.wastenot.data.dummy

import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.repository.ScanRepository
import com.example.wastenot.model.*
import java.time.Instant
import java.util.UUID

class DummyScanRepository : ScanRepository {

    override suspend fun catalog() = ApiResult.Success(AiCatalog("kit-v1.3", listOf(
        AiCatalogItem("plastic_bottle", "Botol plastik (contoh)", WasteCategory.PLASTIC, false, AiGuideStatus.DRAFT),
        AiCatalogItem("glass_bottle", "Botol kaca (contoh)", WasteCategory.GLASS, false, AiGuideStatus.DRAFT),
    ), "demo", "demo", "demo", emptyMap(), emptySet(), mapOf("bottle" to "Botol")))

    override suspend fun detect(photo: ScanPhoto, requestId: String) = ApiResult.Success(AiDetection(
        UUID.randomUUID().toString(), Instant.now().plusSeconds(3600), AiDetectionStatus.REVIEW_REQUIRED,
        "demo", "demo", "demo", AiThresholds(.5, .7), photo.width, photo.height, listOf(
            AiDetectionObject("bottle-1", 0, "bottle", .94, ScanBounds(.18f, .1f, .82f, .9f),
                AiConfidenceBand.OFFERED, true, true, listOf("plastic_bottle", "glass_bottle"), emptyList(), emptyList(), null))))

    override suspend fun recommend(input: AiRecommendationRequest): ApiResult<AiRecommendation> {
        val guide = DummyGuideStore.fixtures.firstOrNull { it.category == input.category }
            ?.takeIf { input.objectKey == "plastic_bottle" }
        val reuse = input.condition.integrity == Integrity.INTACT && input.condition.cleanliness == Cleanliness.CLEAN &&
            input.condition.reusable == AiAnswer.YES && input.reuseIntention == true
        val method = guide?.let { if (reuse) DisposalMethod.REUSE else DisposalMethod.RECYCLE }
        return ApiResult.Success(AiRecommendation(UUID.randomUUID().toString(), Instant.now().plusSeconds(3600),
            if (method != null) AiRecommendationStatus.READY else AiRecommendationStatus.UNAVAILABLE,
            input.detectionId, input.objectId, input.contextRevision, input.objectKey, input.category, method,
            AiRecommendationScope.HANDLING.takeIf { method != null }, AiRecommendationBasis.CONFIRMED.takeIf { method != null },
            emptyList(), emptyList(), if (guide != null && reuse) "Contoh: gunakan kembali bila kondisi benda dan rencana penggunaan ulangmu sudah sesuai."
            else if (guide != null) "Contoh: periksa material dan penerimaan pengelola sebelum menyalurkan benda untuk daur ulang."
            else "Belum ada contoh panduan yang cocok. Lanjutkan dengan pilihan manual.", null, false, "",
            "demo", true, "kit-v1.3", emptyList(), emptyList(), emptyList(), emptyList(), guide?.steps.orEmpty()))
    }

    override suspend fun deleteDetection(id: String) = ApiResult.Success(Unit)
}
