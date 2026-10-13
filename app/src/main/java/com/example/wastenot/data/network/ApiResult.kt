package com.example.wastenot.data.network

import java.time.Instant

sealed interface ApiResult<out T> {
    data class Success<T>(val value: T, val page: PageInfo? = null) : ApiResult<T>

    @JvmInline
    value class Failure(val error: ApiFailure) : ApiResult<Nothing>
}

data class PageInfo(val nextCursor: String?, val hasMore: Boolean)

enum class ApiFailureKind {
    NOT_CONFIGURED, INVALID_REQUEST, UNAUTHENTICATED, NOT_FOUND, EMAIL_EXISTS,
    VERSION_CONFLICT, IDEMPOTENCY_CONFLICT, CONFLICT, DELETED, FILE_TOO_LARGE,
    UNSUPPORTED_MEDIA, VALIDATION, PRECONDITION_REQUIRED, RATE_LIMITED, SERVER,
    HTTP, MALFORMED_RESPONSE, TIMEOUT, NETWORK,
}

data class ApiFailure(
    val kind: ApiFailureKind,
    val status: Int? = null,
    val code: String? = null,
    val fields: Map<String, String> = emptyMap(),
    val requestId: String? = null,
    val retryAt: Instant? = null,
)
