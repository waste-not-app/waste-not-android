package com.example.wastenot.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.wastenot.data.network.ApiFailure
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.repository.EditorDraftStore
import com.example.wastenot.data.repository.StoredEditorDraft
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

@Serializable
internal data class DraftEnvelope(val ownerId: String, val schema: Int = 1, val draft: StoredEditorDraft)

internal object DraftDiskCodec {
    private val json = Json { encodeDefaults = true }
    fun encode(ownerId: String, draft: StoredEditorDraft): String {
        checkDraft(draft)
        return json.encodeToString(DraftEnvelope.serializer(), DraftEnvelope(ownerId, draft = draft))
            .also { require(it.toByteArray(Charsets.UTF_8).size <= 1_048_576) }
    }
    fun decode(ownerId: String, raw: String): StoredEditorDraft {
        require(raw.toByteArray(Charsets.UTF_8).size <= 1_048_576)
        val envelope = json.decodeFromString(DraftEnvelope.serializer(), raw)
        require(envelope.schema == 1 && envelope.ownerId == ownerId)
        return envelope.draft.also(::checkDraft)
    }
    private fun checkDraft(draft: StoredEditorDraft) {
        require(draft.requestId.isNotBlank() && draft.requestId.length <= 128)
        require(draft.origin in setOf("HOME", "HISTORY", "GUIDE", "STATISTICS", "PROFILE"))
        require(draft.fields.size in setOf(22, 28))
        require(listOfNotNull(draft.logId, draft.guideId, draft.scanCategory, draft.scanMethod,
            draft.scanRecommendationId).all { it.length in 1..256 })
    }
}

class LocalEditorDraftStore(private val preferences: SharedPreferences, private val ownerId: String) : EditorDraftStore {
    init { require(ownerId.isNotBlank()) }
    override suspend fun read(): ApiResult<StoredEditorDraft?> = access {
        ApiResult.Success(preferences.getString("draft", null)?.let { DraftDiskCodec.decode(ownerId, it) })
    }

    override suspend fun write(draft: StoredEditorDraft): ApiResult<Unit> = access {
        persist(DraftDiskCodec.encode(ownerId, draft))
    }

    override suspend fun clear(requestId: String?): ApiResult<Unit> = access {
        val raw = preferences.all["draft"]
        if (requestId != null && raw != null && DraftDiskCodec.decode(ownerId, raw as String).requestId != requestId) ApiResult.Success(Unit)
        else persist(null)
    }

    private fun persist(value: String?): ApiResult<Unit> {
        val before = preferences.all["draft"]
        if (preferences.edit().putString("draft", value).commit()) return ApiResult.Success(Unit)
        // Android updates its preference cache even when commit reports a disk failure.
        val restore = preferences.edit()
        when (before) {
            null -> restore.remove("draft")
            is String -> restore.putString("draft", before)
            is Int -> restore.putInt("draft", before)
            is Long -> restore.putLong("draft", before)
            is Float -> restore.putFloat("draft", before)
            is Boolean -> restore.putBoolean("draft", before)
            is Set<*> -> restore.putStringSet("draft", before.filterIsInstance<String>().toSet())
        }
        restore.commit()
        return failure()
    }

    private suspend fun <T> access(block: () -> ApiResult<T>): ApiResult<T> = withContext(Dispatchers.IO) {
        val operation = currentCoroutineContext()
        synchronized(preferences) {
            operation.ensureActive()
            try { block() }
            catch (_: SerializationException) { failure() }
            catch (_: IllegalArgumentException) { failure() }
            catch (_: ClassCastException) { failure() }
        }
    }

    private fun failure() = ApiResult.Failure(ApiFailure(ApiFailureKind.STORAGE))

    companion object {
        fun create(context: Context, ownerId: String): LocalEditorDraftStore {
            require(ownerId.isNotBlank())
            return LocalEditorDraftStore(context.applicationContext.getSharedPreferences(preferencesName(ownerId), Context.MODE_PRIVATE), ownerId)
        }
        internal fun preferencesName(ownerId: String): String {
            val hash = MessageDigest.getInstance("SHA-256").digest(ownerId.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            return "editor_draft_$hash"
        }
    }
}
