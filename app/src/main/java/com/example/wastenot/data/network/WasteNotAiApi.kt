package com.example.wastenot.data.network

import com.example.wastenot.data.network.dto.*
import com.example.wastenot.data.repository.RequestCredential
import kotlinx.serialization.json.JsonObject
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface WasteNotAiApi {
    @GET("ai/objects")
    suspend fun catalog(@Tag credential: RequestCredential? = null): Response<DataEnvelope<AiCatalogDto>>

    @Multipart
    @POST("ai/detections")
    suspend fun detect(@Part image: MultipartBody.Part, @Part("client_request_id") requestId: RequestBody,
        @Tag credential: RequestCredential? = null): Response<DataEnvelope<AiDetectionDto>>

    @POST("ai/recommendations")
    suspend fun recommend(@Body input: JsonObject, @Tag credential: RequestCredential? = null): Response<DataEnvelope<AiRecommendationDto>>

    @DELETE("ai/detections/{id}")
    suspend fun deleteDetection(@Path("id") id: String, @Tag credential: RequestCredential? = null): Response<Unit>
}
