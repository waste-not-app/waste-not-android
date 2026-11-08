package com.example.wastenot.data.repository

import com.example.wastenot.data.network.*
import com.example.wastenot.data.network.dto.toRequestFields
import com.example.wastenot.model.*
import java.time.Clock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class AiRepository(private val network: WasteNotNetwork?, private val session: SessionRepository? = null,
    private val clock: Clock = Clock.systemUTC()) {
    suspend fun catalog(): ApiResult<AiCatalog> = protected { credential ->
        network?.executor?.executeWithStatus({ dto, status ->
            require(status == 200); dto.toModel()
        }) { network.aiApi.catalog(credential) } ?: notConfigured()
    }

    suspend fun detect(jpeg: ByteArray, requestId: String): ApiResult<AiDetection> {
        if (!validId(requestId) || jpeg.size !in 3..5 * 1024 * 1024 ||
            jpeg[0] != 0xff.toByte() || jpeg[1] != 0xd8.toByte() || jpeg[2] != 0xff.toByte()) return invalid()
        val image = MultipartBody.Part.createFormData("image", "capture.jpg", jpeg.copyOf().toRequestBody("image/jpeg".toMediaType()))
        val id = requestId.toRequestBody("text/plain".toMediaType())
        val result = protected { credential ->
            network?.executor?.executeWithStatus({ dto, status ->
                require(status == 200 || status == 201); dto.toModel()
            }) { network.aiApi.detect(image, id, credential) } ?: notConfigured()
        }
        return if (result is ApiResult.Success && result.value.expiresAt <= clock.instant()) expired() else result
    }

    suspend fun recommend(input: AiRecommendationRequest): ApiResult<AiRecommendation> {
        val body = input.toRequestFields()
        val result = protected { credential ->
            network?.executor?.executeWithStatus({ dto, status ->
                require(status == 200 || status == 201); dto.toModel(input)
            }) { network.aiApi.recommend(body, credential) } ?: notConfigured()
        }
        return if (result is ApiResult.Success && result.value.expiresAt <= clock.instant()) expired() else result
    }

    suspend fun deleteDetection(id: String): ApiResult<Unit> {
        if (!validId(id)) return invalid()
        return protected { credential ->
            network?.executor?.executeEmpty { network.aiApi.deleteDetection(id, credential) } ?: notConfigured()
        }
    }

    private suspend fun <T> protected(call: suspend (RequestCredential?) -> ApiResult<T>): ApiResult<T> =
        session?.authenticated { call(it) } ?: call(null)

    private fun validId(value: String) = try { requireAiUuid(value); true } catch (_: IllegalArgumentException) { false }
    private fun invalid() = ApiResult.Failure(ApiFailure(ApiFailureKind.INVALID_REQUEST))
    private fun notConfigured() = ApiResult.Failure(ApiFailure(ApiFailureKind.NOT_CONFIGURED))
    private fun expired() = ApiResult.Failure(ApiFailure(ApiFailureKind.AI_EXPIRED, status = 410, code = "AI_SESSION_EXPIRED"))
}
