package com.example.wastenot.data.repository

import com.example.wastenot.data.network.ApiFailure
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.network.WasteNotNetwork
import com.example.wastenot.data.network.dto.toRequestFields
import com.example.wastenot.model.ActivityDateRange
import com.example.wastenot.model.DashboardSnapshot
import com.example.wastenot.model.StatisticsSnapshot
import com.example.wastenot.model.DisposalMethod
import com.example.wastenot.model.Metadata
import com.example.wastenot.model.WasteCategory
import com.example.wastenot.model.WasteLog
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class CoreRepository(private val network: WasteNotNetwork?, private val session: SessionRepository? = null) {
    suspend fun dashboard(): ApiResult<DashboardSnapshot> = protected { credential ->
        network?.executor?.executeWithStatus({ dto, status ->
            require(status == 200)
            dto.toModel()
        }) { network.api.dashboard(credential) } ?: notConfigured()
    }

    suspend fun statistics(range: ActivityDateRange): ApiResult<StatisticsSnapshot> = protected { credential ->
        network?.executor?.executeWithStatus({ dto, status ->
            require(status == 200)
            dto.toModel().also { require(it.range == range) }
        }) { network.api.statistics(range.from.toString(), range.to.toString(), credential) } ?: notConfigured()
    }

    suspend fun metadata(): ApiResult<Metadata> = protected { credential ->
        network?.executor?.execute({ it.toModel() }) { network.api.metadata(credential) } ?: notConfigured()
    }

    suspend fun wasteLogs(
        category: WasteCategory? = null,
        method: DisposalMethod? = null,
        dates: ActivityDateRange? = null,
        cursor: String? = null,
    ): ApiResult<List<WasteLog>> {
        val result = protected { credential ->
            network?.executor?.execute({ dtos -> dtos.map { it.toModel() } }) {
                network.api.wasteLogs(category?.name, method?.name, dates?.from?.toString(), dates?.to?.toString(),
                    cursor, credential = credential)
            } ?: notConfigured()
        }
        return if (result is ApiResult.Success && result.page == null) {
            ApiResult.Failure(ApiFailure(ApiFailureKind.MALFORMED_RESPONSE, status = 200))
        } else result
    }

    suspend fun wasteLog(id: String): ApiResult<WasteLog> {
        if (!uuid.matches(id)) return invalidRequest()
        return protected { credential ->
            network?.executor?.executeWithStatus({ dto, status ->
                require(status == 200 && dto.id.equals(id, ignoreCase = true))
                dto.toModel()
            }) { network.api.wasteLog(id, credential) } ?: notConfigured()
        }
    }

    suspend fun createLog(input: ActivityInput, requestId: String): ApiResult<ActivityCreation> {
        if (!uuid.matches(requestId)) return invalidRequest()
        val body = buildJsonObject {
            input.toRequestFields().forEach { (name, value) -> put(name, value) }
            put("client_request_id", requestId)
        }
        return protected { credential ->
            network?.executor?.executeWithStatus({ dto, status ->
                require(status == 200 || status == 201)
                ActivityCreation(dto.toModel(), created = status == 201)
            }) { network.api.createLog(body, credential) } ?: notConfigured()
        }
    }

    suspend fun updateLog(id: String, version: Int, input: ActivityInput): ApiResult<WasteLog> {
        if (!uuid.matches(id) || version < 1) return invalidRequest()
        val body = buildJsonObject {
            input.toRequestFields().forEach { (name, value) -> put(name, value) }
            put("version", version)
        }
        return protected { credential ->
            network?.executor?.executeWithStatus({ dto, status ->
                require(status == 200 && dto.id.equals(id, ignoreCase = true) && dto.version > version)
                dto.toModel()
            }) { network.api.updateLog(id, body, credential) } ?: notConfigured()
        }
    }

    suspend fun deleteLog(id: String, version: Int): ApiResult<Unit> {
        if (!uuid.matches(id) || version < 1) return invalidRequest()
        return protected { credential ->
            network?.executor?.executeEmpty { network.api.deleteLog(id, "\"$version\"", credential) } ?: notConfigured()
        }
    }

    private fun notConfigured() = ApiResult.Failure(ApiFailure(ApiFailureKind.NOT_CONFIGURED))
    private fun invalidRequest() = ApiResult.Failure(ApiFailure(ApiFailureKind.INVALID_REQUEST))

    private suspend fun <T> protected(call: suspend (RequestCredential?) -> ApiResult<T>): ApiResult<T> =
        session?.authenticated { call(it) } ?: call(null)
}

private val uuid = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
