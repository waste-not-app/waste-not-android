package com.example.wastenot.data.repository

import com.example.wastenot.data.network.ApiFailure
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.network.WasteNotNetwork
import com.example.wastenot.model.ActivityDateRange
import com.example.wastenot.model.DisposalMethod
import com.example.wastenot.model.Metadata
import com.example.wastenot.model.WasteCategory
import com.example.wastenot.model.WasteLog

class CoreRepository(private val network: WasteNotNetwork?) {
    suspend fun metadata(): ApiResult<Metadata> = network?.executor?.execute({ it.toModel() }) {
        network.api.metadata()
    } ?: notConfigured()

    suspend fun wasteLogs(
        category: WasteCategory? = null,
        method: DisposalMethod? = null,
        dates: ActivityDateRange? = null,
        cursor: String? = null,
    ): ApiResult<List<WasteLog>> {
        val result = network?.executor?.execute({ dtos -> dtos.map { it.toModel() } }) {
            network.api.wasteLogs(category?.name, method?.name, dates?.from?.toString(), dates?.to?.toString(), cursor)
        } ?: return notConfigured()
        return if (result is ApiResult.Success && result.page == null) {
            ApiResult.Failure(ApiFailure(ApiFailureKind.MALFORMED_RESPONSE, status = 200))
        } else result
    }

    private fun notConfigured() = ApiResult.Failure(ApiFailure(ApiFailureKind.NOT_CONFIGURED))
}
