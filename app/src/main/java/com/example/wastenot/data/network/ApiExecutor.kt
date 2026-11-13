package com.example.wastenot.data.network

import com.example.wastenot.data.network.dto.DataEnvelope
import com.example.wastenot.data.network.dto.ErrorEnvelope
import java.io.IOException
import java.io.InterruptedIOException
import java.time.Clock
import java.time.DateTimeException
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.Response

class ApiExecutor(private val json: Json, private val clock: Clock = Clock.systemUTC()) {
    suspend fun <D, T> execute(map: (D) -> T, call: suspend () -> Response<DataEnvelope<D>>): ApiResult<T> =
        executeWithStatus({ data, _ -> map(data) }, call)

    suspend fun <D, T> executeWithStatus(map: (D, Int) -> T, call: suspend () -> Response<DataEnvelope<D>>): ApiResult<T> =
        respond(call) { response ->
            val envelope = response.body() ?: return@respond malformed(response)
            ApiResult.Success(map(envelope.data, response.code()), envelope.page?.toModel())
        }

    suspend fun executeEmpty(call: suspend () -> Response<Unit>): ApiResult<Unit> = respond(call) { response ->
        if (response.code() == 204) ApiResult.Success(Unit) else malformed(response)
    }

    private suspend fun <D, T> respond(call: suspend () -> Response<D>, map: (Response<D>) -> ApiResult<T>): ApiResult<T> {
        try {
            val response = call()
            if (!response.isSuccessful) {
                val envelope = response.errorBody()?.use { body ->
                    try { json.decodeFromString<ErrorEnvelope>(body.string()) }
                    catch (_: SerializationException) { null }
                    catch (_: IllegalArgumentException) { null }
                }
                return ApiResult.Failure(ApiFailure(
                    kind = classify(response.code(), envelope?.error?.code),
                    status = response.code(), code = envelope?.error?.code?.takeIf { errorCode.matches(it) },
                    fields = if (response.code() in setOf(409, 422)) envelope?.error?.fields.orEmpty() else emptyMap(),
                    requestId = safeRequestId(envelope?.requestId ?: response.headers()["X-Request-ID"]),
                    retryAt = retryAfter(response.headers()["Retry-After"], clock.instant()),
                ))
            }
            return try {
                map(response)
            } catch (_: IllegalArgumentException) {
                malformed(response)
            } catch (_: DateTimeException) {
                malformed(response)
            }
        } catch (error: IOException) {
            currentCoroutineContext().ensureActive()
            return ApiResult.Failure(ApiFailure(
                if (error is InterruptedIOException) ApiFailureKind.TIMEOUT else ApiFailureKind.NETWORK))
        } catch (_: SerializationException) {
            return ApiResult.Failure(ApiFailure(ApiFailureKind.MALFORMED_RESPONSE))
        }
    }

    private fun malformed(response: Response<*>): ApiResult.Failure = ApiResult.Failure(ApiFailure(
        ApiFailureKind.MALFORMED_RESPONSE, response.code(), requestId = safeRequestId(response.headers()["X-Request-ID"])))
}

private val errorCode = Regex("[A-Z][A-Z0-9_]{0,79}")
private val requestId = Regex("[A-Za-z0-9_-]{1,80}")
internal fun safeRequestId(value: String?): String? = value?.takeIf { requestId.matches(it) }

internal fun retryAfter(value: String?, now: Instant): Instant? {
    if (value == null) return null
    val seconds = value.toLongOrNull()
    if (seconds != null) {
        if (seconds < 0) return null
        return try { now.plusSeconds(seconds) } catch (_: DateTimeException) { Instant.MAX }
    }
    if (value.isNotEmpty() && value.all { it in '0'..'9' }) return Instant.MAX
    return try {
        ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().takeIf { it >= now }
    } catch (_: DateTimeException) { null }
}

private fun classify(status: Int, code: String?): ApiFailureKind = when (status) {
    400 -> ApiFailureKind.INVALID_REQUEST
    401 -> ApiFailureKind.UNAUTHENTICATED
    404 -> ApiFailureKind.NOT_FOUND
    409 -> when (code) {
        "EMAIL_EXISTS" -> ApiFailureKind.EMAIL_EXISTS
        "VERSION_CONFLICT" -> ApiFailureKind.VERSION_CONFLICT
        "IDEMPOTENCY_CONFLICT" -> ApiFailureKind.IDEMPOTENCY_CONFLICT
        "AI_ALREADY_LINKED" -> ApiFailureKind.AI_ALREADY_LINKED
        "PHOTO_ALREADY_LINKED" -> ApiFailureKind.PHOTO_ALREADY_LINKED
        "PHOTO_IN_USE" -> ApiFailureKind.PHOTO_IN_USE
        else -> ApiFailureKind.CONFLICT
    }
    410 -> if (code == "AI_SESSION_EXPIRED") ApiFailureKind.AI_EXPIRED else ApiFailureKind.DELETED
    413 -> ApiFailureKind.FILE_TOO_LARGE
    415 -> ApiFailureKind.UNSUPPORTED_MEDIA
    422 -> if (code == "AI_REFERENCE_INVALID") ApiFailureKind.AI_REFERENCE_INVALID else ApiFailureKind.VALIDATION
    428 -> ApiFailureKind.PRECONDITION_REQUIRED
    429 -> ApiFailureKind.RATE_LIMITED
    in 500..599 -> ApiFailureKind.SERVER
    else -> ApiFailureKind.HTTP
}
