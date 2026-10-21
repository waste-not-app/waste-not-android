package com.example.wastenot.data.repository

import com.example.wastenot.data.network.ApiFailure
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.model.WasteCategory
import com.example.wastenot.model.WasteGuide

interface GuideStore {
    suspend fun search(query: String, category: WasteCategory? = null, cursor: String? = null): ApiResult<List<WasteGuide>>
    suspend fun get(id: String): ApiResult<WasteGuide>
}

class RemoteGuideStore : GuideStore {
    override suspend fun search(query: String, category: WasteCategory?, cursor: String?) = unavailable()
    override suspend fun get(id: String) = unavailable()
    private fun unavailable() = ApiResult.Failure(ApiFailure(ApiFailureKind.NOT_CONFIGURED))
}
