package com.example.wastenot.data.dummy

import android.content.SharedPreferences
import com.example.wastenot.data.network.ApiFailure
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.repository.ActivityInput
import com.example.wastenot.data.repository.ActivityCreation
import com.example.wastenot.data.repository.ActivityStore
import com.example.wastenot.model.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Persists only fixture records; never supplies access tokens or calls a network service. */
class DummyActivityStore(
    private val preferences: SharedPreferences,
    private val zone: ZoneId,
    private val seedInitialRecords: Boolean = true,
    private val today: () -> LocalDate = { LocalDate.now(zone) },
) : ActivityStore {

    override val referenceDate: LocalDate get() = today()

    override suspend fun dashboard(): ApiResult<DashboardSnapshot> = transact { records, _, _ ->
        ApiResult.Success(dummyDashboard(records, today(), zone))
    }

    override suspend fun statistics(range: ActivityDateRange): ApiResult<StatisticsSnapshot> = transact { records, _, _ ->
        ApiResult.Success(dummyStatistics(records, range, zone))
    }

    override suspend fun load(): ApiResult<List<WasteLog>> = transact { records, _, _ -> ApiResult.Success(records) }

    override suspend fun get(id: String): ApiResult<WasteLog> = transact { records, _, _ ->
        records.find { it.id == id }?.let { ApiResult.Success(it) } ?: failure(ApiFailureKind.NOT_FOUND)
    }

    override suspend fun create(input: ActivityInput, clientRequestId: String): ApiResult<ActivityCreation> = transact { records, requests, deleted ->
        val previous = requests.optJSONObject(clientRequestId)
        if (previous != null) {
            val old = records.find { it.id == previous.getString("id") }
            when {
                previous.getString("payload") != encodeInput(input) -> failure(ApiFailureKind.IDEMPOTENCY_CONFLICT)
                previous.getString("id") in deleted -> failure(ApiFailureKind.DELETED)
                old != null -> ApiResult.Success(ActivityCreation(old, created = false))
                else -> failure(ApiFailureKind.STORAGE)
            }
        } else if (!valid(input)) failure(ApiFailureKind.VALIDATION)
        else {
            val now = Instant.now()
            val log = WasteLog(UUID.randomUUID().toString(), input.category, input.method, input.quantity,
                input.date, zone, input.notes, 1, now, now)
            requests.put(clientRequestId, JSONObject().put("id", log.id).put("payload", encodeInput(input)))
            persist(records + log, requests, deleted, ActivityCreation(log, created = true))
        }
    }

    override suspend fun update(id: String, version: Int, input: ActivityInput): ApiResult<WasteLog> = transact { records, requests, deleted ->
        val old = records.find { it.id == id }
        when {
            old == null -> failure(ApiFailureKind.NOT_FOUND)
            old.version != version -> failure(ApiFailureKind.VERSION_CONFLICT)
            !valid(input, old.activityDate) -> failure(ApiFailureKind.VALIDATION)
            else -> {
                val log = old.copy(category = input.category, method = input.method, quantity = input.quantity,
                    activityDate = input.date, notes = input.notes, version = version + 1,
                    updatedAt = maxOf(Instant.now(), old.updatedAt.plusMillis(1)))
                persist(records.filterNot { it.id == id } + log, requests, deleted, log)
            }
        }
    }

    override suspend fun delete(id: String, version: Int): ApiResult<Unit> = transact { records, requests, deleted ->
        val old = records.find { it.id == id }
        when {
            id in deleted -> ApiResult.Success(Unit)
            old == null -> failure(ApiFailureKind.NOT_FOUND)
            old.version != version -> failure(ApiFailureKind.VERSION_CONFLICT)
            else -> persist(records.filterNot { it.id == id }, requests, deleted + id, Unit)
        }
    }

    // Session disposal drops the ViewModel; account-scoped fixture data stays available after restart/login.
    override fun clear() = Unit

    private suspend fun <T> transact(block: (List<WasteLog>, JSONObject, Set<String>) -> ApiResult<T>): ApiResult<T> =
        withContext(Dispatchers.IO) {
            // SharedPreferences instances are cached per account; serialize recreated adapters as well.
            synchronized(preferences) {
                try {
                    val raw = preferences.getString("records", null)
                    val requestSnapshot = preferences.getString("requests", null)
                    val deletedSnapshot = preferences.getStringSet("deleted", null)?.toSet()
                    if (raw == null && (requestSnapshot != null || deletedSnapshot != null)) {
                        failure(ApiFailureKind.STORAGE)
                    } else try {
                        val records = if (raw == null) { if (seedInitialRecords) seed() else emptyList() } else JSONArray(raw).let { array ->
                            (0 until array.length()).map { decode(array.getJSONObject(it)) }
                        }
                        if (raw == null) commit(preferences.edit().putString("records", encode(records)))
                        block(records.sortedWith(compareByDescending<WasteLog> { it.activityDate }.thenByDescending { it.createdAt }),
                            JSONObject(requestSnapshot ?: "{}"), deletedSnapshot ?: emptySet())
                    } catch (_: StorageCommitFailure) {
                        // commit() updates Android's cache before reporting disk failure; restore the last confirmed state.
                        preferences.edit().putString("records", raw).putString("requests", requestSnapshot)
                            .putStringSet("deleted", deletedSnapshot).commit()
                        failure(ApiFailureKind.STORAGE)
                    }
                } catch (_: org.json.JSONException) {
                    failure(ApiFailureKind.STORAGE)
                } catch (_: IllegalArgumentException) {
                    failure(ApiFailureKind.STORAGE)
                } catch (_: java.time.DateTimeException) {
                    failure(ApiFailureKind.STORAGE)
                } catch (_: ClassCastException) {
                    failure(ApiFailureKind.STORAGE)
                }
            }
        }

    private fun <T> persist(records: List<WasteLog>, requests: JSONObject, deleted: Set<String>, value: T): ApiResult<T> {
        commit(preferences.edit().putString("records", encode(records)).putString("requests", requests.toString())
            .putStringSet("deleted", deleted))
        return ApiResult.Success(value)
    }

    private fun commit(editor: SharedPreferences.Editor) {
        if (!editor.commit()) throw StorageCommitFailure()
    }

    private class StorageCommitFailure : RuntimeException()

    private fun valid(input: ActivityInput, original: LocalDate? = null) =
        input.notes.length <= 500 && ActivityDates.canSave(input.date, today(), original)

    private fun seed(): List<WasteLog> = listOf(
        Triple(WasteCategory.PLASTIC, DisposalMethod.RECYCLE, Quantity.Weight.of(250)),
        Triple(WasteCategory.GLASS, DisposalMethod.REUSE, Quantity.Weight.of(400)),
        Triple(WasteCategory.PLASTIC, DisposalMethod.REDUCE, Quantity.Count.of(2)),
        Triple(WasteCategory.ORGANIC, DisposalMethod.TRASH, Quantity.Weight.of(600)),
        Triple(WasteCategory.PAPER, DisposalMethod.RECYCLE, Quantity.Weight.of(150)),
    ).mapIndexed { index, (category, method, quantity) ->
        val date = if (index < 3) today() else today().minusDays(1)
        val instant = minOf(date.atTime(8 + index, 0).atZone(zone).toInstant(), Instant.now())
        WasteLog(UUID.randomUUID().toString(), category, method, quantity, date, zone, "", 1, instant, instant)
    }

    private fun encodeInput(input: ActivityInput) = listOf(input.category.name, input.method.name,
        input.quantity.kind.name, quantityValue(input.quantity).toString(), input.date.toString(), input.notes).joinToString("\u0000")

    private fun encode(records: List<WasteLog>) = JSONArray().apply {
        records.forEach { log -> put(JSONObject().put("id", log.id).put("category", log.category.name)
            .put("method", log.method.name).put("kind", log.quantity.kind.name).put("value", quantityValue(log.quantity))
            .put("date", log.activityDate.toString()).put("zone", log.activityTimezone.id).put("notes", log.notes)
            .put("version", log.version).put("created", log.createdAt.toString()).put("updated", log.updatedAt.toString())) }
    }.toString()

    private fun decode(json: JSONObject) = WasteLog(json.getString("id"), WasteCategory.valueOf(json.getString("category")),
        DisposalMethod.valueOf(json.getString("method")), when (QuantityKind.valueOf(json.getString("kind"))) {
            QuantityKind.WEIGHT -> Quantity.Weight.of(json.getInt("value"))
            QuantityKind.COUNT -> Quantity.Count.of(json.getInt("value"))
        }, LocalDate.parse(json.getString("date")), ZoneId.of(json.getString("zone")), json.getString("notes"),
        json.getInt("version"), Instant.parse(json.getString("created")), Instant.parse(json.getString("updated"))).also { require(it.version > 0) }

    private fun quantityValue(quantity: Quantity) = when (quantity) {
        is Quantity.Weight -> quantity.grams
        is Quantity.Count -> quantity.items
    }

    private fun failure(kind: ApiFailureKind) = ApiResult.Failure(ApiFailure(kind))
}
