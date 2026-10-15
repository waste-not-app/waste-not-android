package com.example.wastenot.data.network

import com.example.wastenot.data.network.dto.DataEnvelope
import com.example.wastenot.data.network.dto.MetadataDto
import com.example.wastenot.data.network.dto.WasteLogDto
import com.example.wastenot.data.network.dto.LoginDto
import com.example.wastenot.data.network.dto.RegisterDto
import com.example.wastenot.data.network.dto.RefreshDto
import com.example.wastenot.data.network.dto.SessionDto
import com.example.wastenot.data.network.dto.UserDto
import com.example.wastenot.data.repository.RequestCredential
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.POST
import retrofit2.http.Body
import retrofit2.http.Tag

interface WasteNotApi {
    @GET("metadata")
    suspend fun metadata(@Tag credential: RequestCredential? = null): Response<DataEnvelope<MetadataDto>>

    @GET("waste-logs")
    suspend fun wasteLogs(
        @Query("category") category: String? = null,
        @Query("method") method: String? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 20,
        @Tag credential: RequestCredential? = null,
    ): Response<DataEnvelope<List<WasteLogDto>>>

    @POST("auth/login")
    suspend fun login(@Body input: LoginDto): Response<DataEnvelope<SessionDto>>

    @POST("auth/register")
    suspend fun register(@Body input: RegisterDto): Response<DataEnvelope<SessionDto>>

    @POST("auth/refresh")
    suspend fun refresh(@Body input: RefreshDto): Response<DataEnvelope<SessionDto>>

    @GET("me")
    suspend fun me(@Tag credential: RequestCredential): Response<DataEnvelope<UserDto>>

    @POST("auth/logout")
    suspend fun logout(@Tag credential: RequestCredential): Response<Unit>
}
