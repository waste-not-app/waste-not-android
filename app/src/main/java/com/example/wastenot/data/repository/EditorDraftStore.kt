package com.example.wastenot.data.repository

import com.example.wastenot.data.network.ApiResult
import kotlinx.serialization.Serializable

@Serializable
data class StoredEditorDraft(
    val requestId: String,
    val origin: String,
    val fields: List<String>,
    val logId: String? = null,
    val guideId: String? = null,
    val scanCategory: String? = null,
    val scanMethod: String? = null,
    val paused: Boolean = false,
)

interface EditorDraftStore {
    suspend fun read(): ApiResult<StoredEditorDraft?>
    suspend fun write(draft: StoredEditorDraft): ApiResult<Unit>
    suspend fun clear(requestId: String? = null): ApiResult<Unit>
}
