package com.example.wastenot.data.repository

import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.model.WasteCategory
import com.example.wastenot.model.WasteGuide

interface GuideStore {
    suspend fun search(query: String, category: WasteCategory? = null, cursor: String? = null): ApiResult<List<WasteGuide>>
    suspend fun get(id: String): ApiResult<WasteGuide>
}

class RemoteGuideStore(private val core: CoreRepository) : GuideStore {
    override suspend fun search(query: String, category: WasteCategory?, cursor: String?) = core.guides(query, category, cursor)
    override suspend fun get(id: String) = core.guide(id)
}
