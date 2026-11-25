package com.example.wastenot.data.network

import com.example.wastenot.data.network.dto.DataEnvelope
import com.example.wastenot.data.network.dto.MetadataDto
import com.example.wastenot.data.network.dto.WasteLogDto
import com.example.wastenot.data.network.dto.LoginDto
import com.example.wastenot.data.network.dto.RegisterDto
import com.example.wastenot.data.network.dto.RefreshDto
import com.example.wastenot.data.network.dto.SessionDto
import com.example.wastenot.data.network.dto.UserDto
import com.example.wastenot.data.network.dto.DashboardDto
import com.example.wastenot.data.network.dto.StatisticsDto
import com.example.wastenot.data.network.dto.GuideDto
import com.example.wastenot.data.repository.RequestCredential
import com.example.wastenot.data.network.dto.AchievementDto
import com.example.wastenot.data.network.dto.PhotoDto
import com.example.wastenot.data.network.dto.PasswordResetRequestDto
import com.example.wastenot.data.network.dto.PasswordResetConfirmationDto
import com.example.wastenot.data.network.dto.PasswordResetAcknowledgementDto
import com.example.wastenot.data.network.dto.DeleteAccountDto
import com.example.wastenot.data.network.dto.GoogleChallengeDto
import com.example.wastenot.data.network.dto.GoogleLoginDto
import okhttp3.MultipartBody
import retrofit2.http.Multipart
import retrofit2.http.Part
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.POST
import retrofit2.http.Body
import retrofit2.http.Tag
import retrofit2.http.DELETE
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.Path
import kotlinx.serialization.json.JsonObject

interface WasteNotApi {
    @GET("waste-guide")
    suspend fun guides(@Query("q") query: String? = null, @Query("category") category: String? = null,
        @Query("cursor") cursor: String? = null, @Query("limit") limit: Int = 20,
        @Tag credential: RequestCredential? = null): Response<DataEnvelope<List<GuideDto>>>

    @GET("waste-guide/{id}")
    suspend fun guide(@Path("id") id: String, @Tag credential: RequestCredential? = null): Response<DataEnvelope<GuideDto>>

    @GET("dashboard")
    suspend fun dashboard(@Tag credential: RequestCredential? = null): Response<DataEnvelope<DashboardDto>>

    @GET("statistics")
    suspend fun statistics(@Query("from") from: String, @Query("to") to: String,
        @Tag credential: RequestCredential? = null): Response<DataEnvelope<StatisticsDto>>

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

    @GET("waste-logs/{id}")
    suspend fun wasteLog(@Path("id") id: String, @Tag credential: RequestCredential? = null): Response<DataEnvelope<WasteLogDto>>

    @POST("waste-logs")
    suspend fun createLog(@Body input: JsonObject, @Tag credential: RequestCredential? = null): Response<DataEnvelope<WasteLogDto>>

    @PATCH("waste-logs/{id}")
    suspend fun updateLog(@Path("id") id: String, @Body input: JsonObject,
        @Tag credential: RequestCredential? = null): Response<DataEnvelope<WasteLogDto>>

    @DELETE("waste-logs/{id}")
    suspend fun deleteLog(@Path("id") id: String, @Header("If-Match") version: String,
        @Tag credential: RequestCredential? = null): Response<Unit>

    @POST("auth/login")
    suspend fun login(@Body input: LoginDto): Response<DataEnvelope<SessionDto>>

    @POST("auth/register")
    suspend fun register(@Body input: RegisterDto): Response<DataEnvelope<SessionDto>>

    @POST("auth/google/challenge")
    suspend fun googleChallenge(): Response<DataEnvelope<GoogleChallengeDto>>

    @POST("auth/google")
    suspend fun googleLogin(@Body input: GoogleLoginDto): Response<DataEnvelope<SessionDto>>

    @POST("auth/refresh")
    suspend fun refresh(@Body input: RefreshDto): Response<DataEnvelope<SessionDto>>

    @GET("me")
    suspend fun me(@Tag credential: RequestCredential): Response<DataEnvelope<UserDto>>

    @POST("auth/logout")
    suspend fun logout(@Tag credential: RequestCredential): Response<Unit>

    @POST("auth/password-reset")
    suspend fun requestPasswordReset(@Body input: PasswordResetRequestDto): Response<DataEnvelope<PasswordResetAcknowledgementDto>>

    @POST("auth/password-reset/confirm")
    suspend fun confirmPasswordReset(@Body input: PasswordResetConfirmationDto): Response<Unit>

    @retrofit2.http.HTTP(method = "DELETE", path = "me", hasBody = true)
    suspend fun deleteAccount(@Body input: DeleteAccountDto, @Tag credential: RequestCredential): Response<Unit>

    @GET("achievements")
    suspend fun achievements(@Tag credential: RequestCredential? = null): Response<DataEnvelope<List<AchievementDto>>>

    @Multipart
    @POST("photos")
    suspend fun uploadPhoto(@Part image: MultipartBody.Part, @Tag credential: RequestCredential? = null): Response<DataEnvelope<PhotoDto>>

    @DELETE("photos/{id}")
    suspend fun deletePhoto(@Path("id") id: String, @Tag credential: RequestCredential? = null): Response<Unit>
}
