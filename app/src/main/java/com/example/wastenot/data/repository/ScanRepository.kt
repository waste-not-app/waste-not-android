package com.example.wastenot.data.repository

import com.example.wastenot.data.dummy.DummyGuideStore
import com.example.wastenot.data.network.ApiFailure
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.model.*

interface ScanRepository {
    suspend fun detect(photo: ScanPhoto): ApiResult<List<ScanObject>>
    suspend fun recommend(candidate: ScanObject, category: WasteCategory, context: ScanContext): ApiResult<ScanRecommendation>
}

class DummyScanRepository : ScanRepository {
    override suspend fun detect(photo: ScanPhoto) = ApiResult.Success(listOf(
        ScanObject("bottle-1", "Botol", .94f, ScanBounds(.18f, .1f, .82f, .9f)),
    ))

    override suspend fun recommend(candidate: ScanObject, category: WasteCategory, context: ScanContext): ApiResult<ScanRecommendation> {
        // Dummy responses use the kit's editorial fixtures, not inference or a published knowledge base.
        val guide = DummyGuideStore.fixtures.firstOrNull { it.category == category }
            ?.takeIf { candidate.id == "bottle-1" && category == WasteCategory.PLASTIC }
        if (candidate.specialHandling || guide == null) return ApiResult.Success(ScanRecommendation(null,
            "Belum ada panduan yang cocok untuk objek dan material ini. Lanjutkan dengan pilihan manual.", "", "", emptyList(), emptyList()))
        val reuse = context.integrity == Integrity.INTACT && context.cleanliness == Cleanliness.CLEAN &&
            context.reusable == Reusability.YES && context.reuseIntention == true
        val method = if (reuse) DisposalMethod.REUSE else DisposalMethod.RECYCLE
        return ApiResult.Success(ScanRecommendation(method,
            if (reuse) "Kamu mengonfirmasi benda bersih, utuh, dapat dipakai ulang, dan akan menggunakannya lagi."
            else "Periksa material dan penerimaan fasilitas sebelum menyalurkan benda untuk daur ulang.",
            guide.name, "kit-v1.3", guide.steps, guide.cautions))
    }
}

class UnconfiguredScanRepository : ScanRepository {
    override suspend fun detect(photo: ScanPhoto) = ApiResult.Failure(ApiFailure(ApiFailureKind.NOT_CONFIGURED))
    override suspend fun recommend(candidate: ScanObject, category: WasteCategory, context: ScanContext) =
        ApiResult.Failure(ApiFailure(ApiFailureKind.NOT_CONFIGURED))
}
