package com.example.wastenot.data.network

import com.example.wastenot.data.repository.RequestCredential

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import okio.BufferedSink

data class ApiDiagnostic(val status: Int?, val durationMillis: Long, val requestId: String?)

class WasteNotNetwork private constructor(
    val client: OkHttpClient,
    val api: WasteNotApi,
    val executor: ApiExecutor,
) {
    companion object {
        fun create(
            baseUrl: String,
            accessToken: () -> String? = { null },
            diagnostics: (ApiDiagnostic) -> Unit = {},
            clock: Clock = Clock.systemUTC(),
            allowLocalHttp: Boolean = false,
            callTimeoutSeconds: Long = 30,
        ): WasteNotNetwork? {
            if (baseUrl.isBlank()) return null
            val base = baseUrl.toHttpUrl()
            require(base.username.isEmpty() && base.password.isEmpty() && base.query == null && base.fragment == null)
            require(base.encodedPath.endsWith('/'))
            require(base.isHttps || allowLocalHttp && base.host in setOf("localhost", "127.0.0.1", "10.0.2.2"))
            val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
            val client = OkHttpClient.Builder()
                .retryOnConnectionFailure(false)
                .followRedirects(false).followSslRedirects(false)
                .callTimeout(callTimeoutSeconds, TimeUnit.SECONDS)
                .connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
                .addInterceptor(BearerInterceptor(base, accessToken))
                .addInterceptor(WriteOnceInterceptor())
                .addInterceptor(DiagnosticsInterceptor(diagnostics))
                .addInterceptor(RetryAfterInterceptor(base, clock))
                .build()
            val api = Retrofit.Builder().baseUrl(base).client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build().create(WasteNotApi::class.java)
            return WasteNotNetwork(client, api, ApiExecutor(json, clock))
        }
    }
}

private fun HttpUrl.isApiUrl(base: HttpUrl): Boolean = scheme == base.scheme && host == base.host &&
    port == base.port && encodedPath.startsWith(base.encodedPath)

private class BearerInterceptor(private val base: HttpUrl, private val token: () -> String?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val builder = request.newBuilder().removeHeader("Authorization").removeHeader("Cookie")
        val publicAuth = request.url.encodedPath in setOf(
            "${base.encodedPath}auth/login", "${base.encodedPath}auth/register", "${base.encodedPath}auth/refresh")
        if (request.url.isApiUrl(base) && !publicAuth) {
            (request.tag(RequestCredential::class.java)?.token?.value ?: token())
                ?.let { builder.header("Authorization", "Bearer $it") }
        }
        return chain.proceed(builder.build())
    }
}

private class WriteOnceInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method !in setOf("POST", "PUT", "PATCH", "DELETE")) return chain.proceed(request)
        val body = request.body ?: "".toRequestBody()
        // OkHttp can replay 503/421 despite retryOnConnectionFailure(false).
        return chain.proceed(request.newBuilder().method(request.method, WriteOnceBody(body)).build())
    }
}

private class WriteOnceBody(private val delegate: RequestBody) : RequestBody() {
    override fun contentType() = delegate.contentType()
    override fun contentLength() = delegate.contentLength()
    override fun writeTo(sink: BufferedSink) = delegate.writeTo(sink)
    override fun isDuplex() = delegate.isDuplex()
    override fun isOneShot() = true
}

private class DiagnosticsInterceptor(private val record: (ApiDiagnostic) -> Unit) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val started = System.nanoTime()
        var response: Response? = null
        try {
            return chain.proceed(chain.request()).also { response = it }
        } finally {
            record(ApiDiagnostic(response?.code, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
                safeRequestId(response?.header("X-Request-ID"))))
        }
    }
}

private class RetryAfterInterceptor(private val base: HttpUrl, private val clock: Clock) : Interceptor {
    private val blockedUntil = AtomicReference<Instant?>(null)

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.url.isApiUrl(base)) return chain.proceed(request)
        val now = clock.instant()
        blockedUntil.get()?.takeIf { it > now }?.let { until ->
            val seconds = Duration.between(now, until).seconds.coerceAtLeast(1)
            return Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(429)
                .message("Rate limited").header("Retry-After", seconds.toString())
                .body("".toResponseBody("application/json".toMediaType())).build()
        }
        return chain.proceed(request).also { response ->
            if (response.code == 429) {
                retryAfter(response.header("Retry-After"), clock.instant())?.let { until ->
                    blockedUntil.updateAndGet { previous -> if (previous == null || until > previous) until else previous }
                }
            }
        }
    }
}
