package com.example.wastenot.data.network

import com.example.wastenot.data.network.dto.DataEnvelope
import com.example.wastenot.data.network.dto.MetadataDto
import com.example.wastenot.data.network.dto.WasteLogDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface WasteNotApi {
    @GET("metadata")
    suspend fun metadata(): Response<DataEnvelope<MetadataDto>>

    @GET("waste-logs")
    suspend fun wasteLogs(
        @Query("category") category: String? = null,
        @Query("method") method: String? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 20,
    ): Response<DataEnvelope<List<WasteLogDto>>>
}
