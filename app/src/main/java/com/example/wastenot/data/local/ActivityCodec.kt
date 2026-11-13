package com.example.wastenot.data.local

import com.example.wastenot.data.network.dto.*
import com.example.wastenot.data.repository.ActivityInput
import com.example.wastenot.model.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

internal val activityJson = Json { ignoreUnknownKeys = true }

@Serializable
internal data class StoredInput(
    val category: String, val method: String, val weight: Int?, val count: Int?,
    val date: String, val notes: String, val recommendation: String? = null,
    val photoId: String? = null, val clearPhoto: Boolean = false,
) {
    fun input() = ActivityInput(WasteCategory.valueOf(category), DisposalMethod.valueOf(method),
        requireNotNull(Quantity.fromFields(if (weight != null) QuantityKind.WEIGHT else QuantityKind.COUNT, weight, count)),
        LocalDate.parse(date), notes, recommendation?.let { AiLogProvenance(it, WasteCategory.valueOf(category)) },
        photoId = photoId, clearPhoto = clearPhoto)
}

internal fun ActivityInput.encode(): String = activityJson.encodeToString(StoredInput.serializer(),
    StoredInput(category.name, method.name, (quantity as? Quantity.Weight)?.grams,
        (quantity as? Quantity.Count)?.items, date.toString(), notes, aiProvenance?.recommendationId, photoId, clearPhoto))

internal fun String.activityInput() = activityJson.decodeFromString(StoredInput.serializer(), this).input()

internal fun LocalActivity.log(zone: ZoneId): WasteLog {
    val input = payload.activityInput()
    return WasteLog(serverId ?: localId, input.category, input.method, input.quantity, input.date,
        zone, input.notes, revision, Instant.parse(createdAt), Instant.parse(updatedAt),
        ActivitySyncState.valueOf(syncState), photoId = input.photoId)
}

internal fun WasteLog.input() = ActivityInput(category, method, quantity, activityDate, notes, photoId = photoId)
internal fun WasteLog.dto() = WasteLogDto(id, category.name, method.name, quantity.kind.name,
    (quantity as? Quantity.Weight)?.grams, (quantity as? Quantity.Count)?.items,
    activityDate.toString(), activityTimezone.id, notes, version, createdAt.toString(), updatedAt.toString(), photoId = photoId)
internal fun UserProgress.dto() = ProgressDto(activeLogCount, xp, level, xpIntoLevel, xpToNextLevel,
    currentStreak, bestStreak, hasLoggedToday)
internal fun WasteSummary.dto() = SummaryDto(handled.weightGrams, handled.itemCount, avoided.weightGrams,
    avoided.itemCount, activityCount, handledActivityCount, recycledActivityCount,
    recyclingRatePercent?.let(::JsonPrimitive), "HANDLED_ACTIVITY_COUNT")
internal fun DashboardSnapshot.encode() = activityJson.encodeToString(DashboardDto.serializer(),
    DashboardDto(date.toString(), timezone.id, summary.dto(), recentLogs.map { it.dto() }, progress.dto()))
internal fun StatisticsSnapshot.encode() = activityJson.encodeToString(StatisticsDto.serializer(),
    StatisticsDto(range.from.toString(), range.to.toString(), timezone.id, summary.dto(),
        methods.mapKeys { it.key.name }, daily.map { StatisticsDayDto(it.date.toString(), it.handled.weightGrams, it.handled.itemCount, it.activityCount) },
        categories.map { CategoryTotalDto(it.key.name, it.value.weightGrams, it.value.itemCount) }))

@Serializable
internal data class StoredIdentity(val id: String, val name: String, val email: String,
    val timezone: String, val progress: ProgressDto?) {
    fun user() = User(id, name, email, ZoneId.of(timezone), progress?.toModel())
}

interface SessionIdentityStore {
    suspend fun read(): User?
    suspend fun write(user: User)
    suspend fun clear()
}

class RoomSessionIdentityStore(private val dao: ActivityDao) : SessionIdentityStore {
    override suspend fun read(): User? = storage { dao.identity()?.let {
        activityJson.decodeFromString(StoredIdentity.serializer(), it.payload).user()
    } }
    override suspend fun write(user: User) = storage { dao.identify(AccountIdentity(owner = user.id,
        payload = activityJson.encodeToString(StoredIdentity.serializer(),
            StoredIdentity(user.id, user.name, user.email, user.timezone.id, user.progress?.dto())))) }
    override suspend fun clear() = storage { dao.clearIdentity() }

    private suspend fun <T> storage(block: suspend () -> T): T = try { block() }
        catch (error: android.database.sqlite.SQLiteException) { throw java.io.IOException("Account cache unavailable", error) }
        catch (error: kotlinx.serialization.SerializationException) { throw java.io.IOException("Account cache unreadable", error) }
}
