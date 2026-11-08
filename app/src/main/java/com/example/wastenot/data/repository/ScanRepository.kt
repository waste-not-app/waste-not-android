package com.example.wastenot.data.repository

import com.example.wastenot.data.network.ApiFailure
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.model.*
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface ScanRepository {
    suspend fun catalog(): ApiResult<AiCatalog>
    suspend fun detect(photo: ScanPhoto, requestId: String): ApiResult<AiDetection>
    suspend fun recommend(input: AiRecommendationRequest): ApiResult<AiRecommendation>
    suspend fun deleteDetection(id: String): ApiResult<Unit>
}

class RemoteScanRepository(private val ai: AiRepository, private val photos: ScanPhotoStore) : ScanRepository {

    override suspend fun catalog() = ai.catalog()
    override suspend fun detect(photo: ScanPhoto, requestId: String): ApiResult<AiDetection> {
        val bytes = try { withContext(Dispatchers.IO) { photos.read(photo) } }
        catch (_: IOException) { return ApiResult.Failure(ApiFailure(ApiFailureKind.STORAGE)) }
        catch (_: IllegalArgumentException) { return ApiResult.Failure(ApiFailure(ApiFailureKind.INVALID_REQUEST)) }
        return ai.detect(bytes, requestId)
    }
    override suspend fun recommend(input: AiRecommendationRequest) = ai.recommend(input)
    override suspend fun deleteDetection(id: String) = ai.deleteDetection(id)
}
