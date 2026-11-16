package com.example.wastenot.data.repository

import androidx.room.withTransaction
import com.example.wastenot.data.local.*
import com.example.wastenot.data.network.*
import com.example.wastenot.data.network.dto.DashboardDto
import com.example.wastenot.data.network.dto.StatisticsDto
import com.example.wastenot.model.*
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** All activity reads come from Room; the server is reconciled by account-scoped work. */
class OfflineActivityStore(
    private val user: User,
    private val database: ActivityDatabase,
    private val remote: ActivityStore,
    private val isOwnerActive: () -> Boolean,
    private val schedule: () -> Unit,
    private val now: () -> Instant = Instant::now,
) : ActivityStore {
    private val dao = database.activities()
    private val synchronization = Mutex()
    override val referenceDate = null
    override val changes = database.invalidationTracker.createFlow("activities", "server_snapshots", emitInitialState = false).map { Unit }
    override val synchronizationAcknowledgements = dao.observeSnapshots(user.id).map { snapshots ->
        snapshots.firstOrNull { it.key == "acknowledgement" }?.payload?.takeIf { isOwnerActive() }
    }.filterNotNull().distinctUntilChanged()
    override val historyReady = dao.observeSnapshots(user.id).map { snapshots -> snapshots.any { it.key == "history" } }
    override val pending = dao.observePending(user.id).map { rows -> rows.map { row ->
        SyncIssue(row.localId, row.log(user.timezone), row.remotePayload?.activityInput()?.let { input ->
            row.log(user.timezone).copy(category = input.category, method = input.method, quantity = input.quantity,
                activityDate = input.date, notes = input.notes, version = row.remoteVersion ?: 1,
                syncState = ActivitySyncState.SYNCED)
        }, row.deleted, row.error?.let(ApiFailureKind::valueOf))
    } }

    override fun requestSync() { if (isOwnerActive()) schedule() }

    suspend fun deleteLocalData() = synchronization.withLock { dao.clearOwner(user.id) }
    override suspend fun isPhotoScanDisabled() = remote.isPhotoScanDisabled()

    override suspend fun dashboard(): ApiResult<DashboardSnapshot> = read {
        val snapshot = dao.snapshot(user.id, "dashboard") ?: return@read missingSnapshot()
        val confirmed = activityJson.decodeFromString(DashboardDto.serializer(), snapshot.payload).toModel()
        val recent = dao.page(user.id, null, null, null, null, 3, 0).map { it.log(user.timezone) }
        ApiResult.Success(confirmed.copy(recentLogs = recent, freshness = freshness(snapshot)))
    }

    override suspend fun statistics(range: ActivityDateRange): ApiResult<StatisticsSnapshot> = read {
        val snapshot = dao.snapshot(user.id, statisticsKey(range))
            ?: dao.statisticsSnapshots(user.id).firstOrNull {
                val saved = activityJson.decodeFromString(StatisticsDto.serializer(), it.payload).toModel()
                java.time.temporal.ChronoUnit.DAYS.between(saved.range.from, saved.range.to) ==
                    java.time.temporal.ChronoUnit.DAYS.between(range.from, range.to)
            } ?: return@read missingSnapshot()
        val confirmed = activityJson.decodeFromString(StatisticsDto.serializer(), snapshot.payload).toModel()
        ApiResult.Success(confirmed.copy(freshness = freshness(snapshot)))
    }

    private suspend fun missingSnapshot(): ApiResult.Failure {
        val status = dao.snapshot(user.id, "summary-refresh")?.payload
        return failure(status?.takeUnless { it == "OK" }?.let(ApiFailureKind::valueOf) ?: ApiFailureKind.NETWORK)
    }

    private suspend fun freshness(snapshot: ServerSnapshot): SnapshotFreshness {
        val status = dao.snapshot(user.id, "summary-refresh")
        val error = status?.payload?.takeUnless { it == "OK" }?.let(ApiFailureKind::valueOf)
        return SnapshotFreshness(cached = true, confirmedAt = Instant.ofEpochMilli(snapshot.savedAt), refreshError = error)
    }

    private suspend fun refreshFailed(kind: ApiFailureKind): Boolean {
        if (isOwnerActive()) dao.snapshot(ServerSnapshot(user.id, "summary-refresh", kind.name, now().toEpochMilli()))
        return kind !in transientFailures
    }

    override suspend fun load() = page()
    override suspend fun visiblePage(category: WasteCategory?, method: DisposalMethod?, dates: LogDateRange?, limit: Int): ApiResult<List<WasteLog>> = read {
        require(limit in 20 until Int.MAX_VALUE)
        val rows = dao.page(user.id, category?.name, method?.name, dates?.from?.toString(), dates?.to?.toString(), limit + 1, 0)
        ApiResult.Success(rows.take(limit).map { it.log(user.timezone) },
            PageInfo(if (rows.size > limit) "local:$limit" else null, rows.size > limit))
    }
    override suspend fun page(category: WasteCategory?, method: DisposalMethod?, dates: LogDateRange?, cursor: String?): ApiResult<List<WasteLog>> = read {
        val offset = if (cursor == null) 0 else cursor.removePrefix("local:").toIntOrNull()
            ?.takeIf { it >= 0 && cursor == "local:$it" } ?: return@read failure(ApiFailureKind.INVALID_REQUEST)
        val rows = dao.page(user.id, category?.name, method?.name, dates?.from?.toString(), dates?.to?.toString(), 21, offset)
        ApiResult.Success(rows.take(20).map { it.log(user.timezone) },
            PageInfo(if (rows.size > 20) "local:${offset + 20}" else null, rows.size > 20))
    }

    override suspend fun get(id: String): ApiResult<WasteLog> = read {
        dao.find(user.id, id)?.takeUnless { it.deleted }?.let { ApiResult.Success(it.log(user.timezone)) }
            ?: failure(ApiFailureKind.NOT_FOUND)
    }

    override suspend fun isSameActivity(requestedId: String, actualId: String): Boolean {
        if (!isOwnerActive()) return false
        val row = dao.find(user.id, requestedId) ?: return false
        return actualId == row.localId || actualId == row.serverId
    }

    override suspend fun create(input: ActivityInput, clientRequestId: String): ApiResult<ActivityCreation> = write {
        if (!valid(input) || runCatching { UUID.fromString(clientRequestId).toString().equals(clientRequestId, ignoreCase = true) }.getOrDefault(false).not())
            return@write failure(ApiFailureKind.INVALID_REQUEST)
        val requestId = UUID.fromString(clientRequestId).toString()
        val encoded = input.encode()
        val receipt = dao.receipt(user.id, requestId)
        if (receipt != null) {
            val existing = dao.find(user.id, receipt.localId)
            return@write if (existing == null || existing.deleted) failure(ApiFailureKind.DELETED)
                else if (receipt.payload != encoded) failure(ApiFailureKind.IDEMPOTENCY_CONFLICT)
                else ApiResult.Success(ActivityCreation(existing.log(user.timezone), created = false))
        }
        if (dao.find(user.id, requestId) != null) return@write failure(ApiFailureKind.IDEMPOTENCY_CONFLICT)
        val timestamp = now().toString()
        val row = LocalActivity(user.id, requestId, null, input.category.name, input.method.name,
            input.date.toString(), encoded, 1, 0, timestamp, timestamp, syncState = "PENDING")
        dao.put(row)
        dao.receipt(ActivityReceipt(user.id, requestId, encoded, row.localId))
        dao.enqueue(ActivityMutation(owner = user.id, localId = row.localId, kind = "CREATE", payload = encoded,
            requestId = requestId, revision = row.revision))
        ApiResult.Success(ActivityCreation(row.log(user.timezone), created = false))
    }

    override suspend fun update(id: String, version: Int, input: ActivityInput): ApiResult<WasteLog> = write {
        if (!valid(input) || input.aiProvenance != null) return@write failure(ApiFailureKind.INVALID_REQUEST)
        val row = dao.find(user.id, id)?.takeUnless { it.deleted } ?: return@write failure(ApiFailureKind.NOT_FOUND)
        if (row.revision != version || row.syncState == "CONFLICT") return@write failure(ApiFailureKind.VERSION_CONFLICT)
        if (row.syncState == "FAILED") return@write failure(ApiFailureKind.PRECONDITION_REQUIRED)
        val changed = row.copy(category = input.category.name, method = input.method.name, date = input.date.toString(),
            payload = input.encode(), revision = row.revision + 1, updatedAt = now().toString(), syncState = "PENDING")
        dao.put(changed)
        dao.enqueue(ActivityMutation(owner = user.id, localId = row.localId, kind = "UPDATE", payload = changed.payload,
            requestId = UUID.randomUUID().toString(), revision = changed.revision))
        ApiResult.Success(changed.log(user.timezone))
    }

    override suspend fun delete(id: String, version: Int): ApiResult<Unit> = write {
        val row = dao.find(user.id, id)?.takeUnless { it.deleted } ?: return@write failure(ApiFailureKind.NOT_FOUND)
        if (row.revision != version || row.syncState == "CONFLICT") return@write failure(ApiFailureKind.VERSION_CONFLICT)
        if (row.syncState == "FAILED") return@write failure(ApiFailureKind.PRECONDITION_REQUIRED)
        dao.put(row.copy(deleted = true, revision = row.revision + 1, syncState = "PENDING", updatedAt = now().toString()))
        dao.enqueue(ActivityMutation(owner = user.id, localId = row.localId, kind = "DELETE", payload = row.payload,
            requestId = UUID.randomUUID().toString(), revision = row.revision + 1))
        ApiResult.Success(Unit)
    }

    override suspend fun resolveSync(id: String, keepLocal: Boolean): ApiResult<Unit> = write {
        val row = dao.find(user.id, id) ?: return@write failure(ApiFailureKind.NOT_FOUND)
        if (row.syncState !in setOf("CONFLICT", "FAILED")) return@write failure(ApiFailureKind.PRECONDITION_REQUIRED)
        if (keepLocal && !row.deleted && row.serverId == null && row.error in manualConversionErrors) {
            val request = UUID.randomUUID().toString()
            val payload = row.payload.activityInput().copy(aiProvenance = null).encode()
            val manual = row.copy(payload = payload, syncState = "PENDING", error = null,
                remotePayload = null, remoteVersion = null)
            dao.discard(user.id, row.localId)
            dao.put(manual)
            dao.receipt(ActivityReceipt(user.id, request, payload, row.localId))
            dao.enqueue(ActivityMutation(owner = user.id, localId = row.localId, kind = "CREATE", payload = payload,
                requestId = request, revision = manual.revision))
            return@write ApiResult.Success(Unit)
        }
        // A failed CREATE may have committed remotely. Its original request must never be replaced silently.
        if (keepLocal && (row.serverId == null || row.remoteVersion == null || row.remotePayload == null)) {
            return@write failure(ApiFailureKind.PRECONDITION_REQUIRED)
        }
        dao.discard(user.id, row.localId)
        if (!keepLocal) {
            if (row.remotePayload == null || row.remoteVersion == null) dao.remove(user.id, row.localId)
            else dao.put(withInput(row, row.remotePayload.activityInput()).copy(deleted = false,
                payload = row.remotePayload, serverVersion = row.remoteVersion, revision = row.revision + 1,
                syncState = "SYNCED", remotePayload = null, remoteVersion = null, error = null))
        } else {
            val next = row.copy(serverVersion = checkNotNull(row.remoteVersion), revision = row.revision + 1,
                syncState = "PENDING", remotePayload = null, remoteVersion = null, error = null)
            dao.put(next)
            dao.enqueue(ActivityMutation(owner = user.id, localId = row.localId,
                kind = if (row.deleted) "DELETE" else "UPDATE", payload = row.payload,
                requestId = UUID.randomUUID().toString(), revision = next.revision))
        }
        ApiResult.Success(Unit)
    }

    /** Returns false only when network/backoff should retry. Permanent issues remain visible in Room. */
    suspend fun synchronize(): Boolean = synchronization.withLock {
        if (!isOwnerActive()) return@withLock true
        val blocked = mutableSetOf<String>()
        for (queued in dao.mutations(user.id)) {
            if (!isOwnerActive()) return@withLock true
            val prepared = database.withTransaction {
                val row = dao.find(user.id, queued.localId) ?: return@withTransaction null
                val pending = dao.request(user.id, queued.requestId) ?: return@withTransaction null
                if (pending.sequence != queued.sequence || row.localId in blocked || row.syncState in setOf("CONFLICT", "FAILED")) return@withTransaction null
                row to pending.copy(attemptVersion = pending.attemptVersion ?: row.serverVersion).also { dao.put(it) }
            } ?: continue
            val (row, operation) = prepared
            val result: ApiResult<WasteLog?> = when (operation.kind) {
                "CREATE" -> when (val response = remote.create(operation.payload.activityInput(), operation.requestId)) {
                    is ApiResult.Success -> ApiResult.Success(response.value.log)
                    is ApiResult.Failure -> response
                }
                "UPDATE" -> remote.update(checkNotNull(row.serverId), checkNotNull(operation.attemptVersion), operation.payload.activityInput())
                "DELETE" -> when (val response = remote.delete(checkNotNull(row.serverId), checkNotNull(operation.attemptVersion))) {
                    is ApiResult.Success -> ApiResult.Success(null)
                    is ApiResult.Failure -> response
                }
                else -> error("Unknown mutation kind")
            }
            if (!isOwnerActive()) return@withLock true
            when (result) {
                is ApiResult.Success -> acknowledge(operation, result.value)
                is ApiResult.Failure -> {
                    if (result.error.kind in transientFailures || result.error.kind == ApiFailureKind.UNAUTHENTICATED)
                        return@withLock refreshFailed(result.error.kind)
                    val latest = row.serverId?.let { remote.get(it) }
                    if (latest is ApiResult.Failure && latest.error.kind in transientFailures)
                        return@withLock refreshFailed(latest.error.kind)
                    val server = (latest as? ApiResult.Success)?.value
                    // A lost PATCH response is successful only if precisely that next version has the same fields.
                    if (operation.kind == "UPDATE" && result.error.kind == ApiFailureKind.VERSION_CONFLICT &&
                        server?.version == operation.attemptVersion!! + 1 && server.input() == operation.payload.activityInput()) {
                        acknowledge(operation, server)
                    } else if (operation.kind == "DELETE" && result.error.kind in setOf(ApiFailureKind.DELETED, ApiFailureKind.NOT_FOUND)) {
                        acknowledge(operation, null)
                    } else {
                        database.withTransaction {
                            val current = dao.find(user.id, row.localId) ?: return@withTransaction
                            dao.put(current.copy(syncState = if (result.error.kind in conflictFailures) "CONFLICT" else "FAILED",
                                remotePayload = server?.input()?.encode(), remoteVersion = server?.version, error = result.error.kind.name))
                        }
                        blocked += row.localId
                    }
                }
            }
        }
        if (!pull()) return@withLock false
        refreshSnapshots()
    }

    private suspend fun acknowledge(operation: ActivityMutation, server: WasteLog?) = database.withTransaction {
        val current = dao.find(user.id, operation.localId) ?: return@withTransaction
        dao.acknowledge(user.id, operation.sequence)
        dao.snapshot(ServerSnapshot(user.id, "acknowledgement", UUID.randomUUID().toString(), now().toEpochMilli()))
        val remaining = dao.outstanding(user.id, current.localId) > 0
        if (server == null) {
            if (!remaining) dao.remove(user.id, current.localId)
        } else if (remaining) {
            dao.put(current.copy(serverId = server.id, serverVersion = server.version, seen = null))
        } else {
            dao.put(withInput(current, server.input()).copy(serverId = server.id, serverVersion = server.version,
                revision = maxOf(current.revision, server.version), syncState = "SYNCED", deleted = false,
                createdAt = server.createdAt.toString(), updatedAt = server.updatedAt.toString(),
                remotePayload = null, remoteVersion = null, error = null, seen = null))
        }
    }

    private suspend fun pull(): Boolean {
        var cursor: String? = null
        val cursors = mutableSetOf<String>()
        val generation = UUID.randomUUID().toString()
        do {
            if (!isOwnerActive()) return true
            val result = remote.page(cursor = cursor)
            if (!isOwnerActive()) return true
            if (result is ApiResult.Failure) return refreshFailed(result.error.kind)
            result as ApiResult.Success
            val page = result.page ?: return refreshFailed(ApiFailureKind.MALFORMED_RESPONSE)
            if (result.value.size > 20 || page.hasMore != (page.nextCursor != null) ||
                (page.hasMore && result.value.isEmpty()) || page.nextCursor?.let { !cursors.add(it) } == true)
                return refreshFailed(ApiFailureKind.MALFORMED_RESPONSE)
            database.withTransaction {
                for (log in result.value) {
                    val existing = dao.find(user.id, log.id)
                    if (existing != null && existing.syncState != "SYNCED") continue
                    val initial = existing ?: LocalActivity(user.id, log.id, log.id, log.category.name, log.method.name,
                        log.activityDate.toString(), log.input().encode(), log.version, log.version,
                        log.createdAt.toString(), log.updatedAt.toString())
                    if (log.version < initial.serverVersion) continue
                    dao.put(withInput(initial, log.input()).copy(serverId = log.id, serverVersion = log.version,
                        revision = maxOf(initial.revision, log.version), updatedAt = log.updatedAt.toString(), seen = generation))
                }
            }
            cursor = page.nextCursor
        } while (cursor != null)
        // Pagination is not a server snapshot: unseen rows are verified individually before removing them.
        val cached = dao.page(user.id, null, null, null, null, Int.MAX_VALUE, 0)
        for (row in cached.filter { it.seen != generation && it.syncState == "SYNCED" && it.serverId != null }) {
            if (!isOwnerActive()) return true
            when (val result = remote.get(checkNotNull(row.serverId))) {
                is ApiResult.Success -> database.withTransaction {
                    val current = dao.find(user.id, row.localId)
                    if (current?.syncState == "SYNCED" && result.value.version >= current.serverVersion) {
                        dao.put(withInput(current, result.value.input()).copy(serverVersion = result.value.version,
                            revision = maxOf(current.revision, result.value.version), updatedAt = result.value.updatedAt.toString(), seen = generation))
                    }
                }
                is ApiResult.Failure -> if (result.error.kind in setOf(ApiFailureKind.DELETED, ApiFailureKind.NOT_FOUND)) {
                    database.withTransaction { if (dao.find(user.id, row.localId)?.syncState == "SYNCED") dao.remove(user.id, row.localId) }
                } else if (result.error.kind in transientFailures) return refreshFailed(result.error.kind)
            }
        }
        dao.snapshot(ServerSnapshot(user.id, "history", "{}", now().toEpochMilli()))
        return true
    }

    private suspend fun refreshSnapshots(): Boolean {
        if (!isOwnerActive()) return true
        val dashboard = remote.dashboard()
        if (!isOwnerActive()) return true
        if (dashboard is ApiResult.Failure) return refreshFailed(dashboard.error.kind)
        dashboard as ApiResult.Success
        dao.snapshot(ServerSnapshot(user.id, "dashboard", dashboard.value.encode(), now().toEpochMilli()))
        for (days in listOf(7, 30)) {
            val range = ActivityDateRange.lastDays(dashboard.value.date, days)
            val result = remote.statistics(range)
            if (!isOwnerActive()) return true
            if (result is ApiResult.Failure) return refreshFailed(result.error.kind)
            result as ApiResult.Success
            dao.snapshot(ServerSnapshot(user.id, statisticsKey(range), result.value.encode(), now().toEpochMilli()))
        }
        dao.snapshot(ServerSnapshot(user.id, "summary-refresh", "OK", now().toEpochMilli()))
        return true
    }

    private suspend fun <T> read(block: suspend () -> ApiResult<T>): ApiResult<T> {
        if (!isOwnerActive()) return failure(ApiFailureKind.UNAUTHENTICATED)
        return try { block().let { if (isOwnerActive()) it else failure(ApiFailureKind.UNAUTHENTICATED) } }
        catch (error: CancellationException) { throw error }
        catch (_: android.database.sqlite.SQLiteException) { failure(ApiFailureKind.STORAGE) }
    }

    private suspend fun <T> write(block: suspend () -> ApiResult<T>): ApiResult<T> {
        val result = read { database.withTransaction { block() } }
        if (result is ApiResult.Success) requestSync()
        return result
    }

    private fun valid(input: ActivityInput) = input.notes.length <= 500 && !input.date.isAfter(java.time.LocalDate.now(user.timezone))
    private fun withInput(row: LocalActivity, input: ActivityInput) = row.copy(category = input.category.name,
        method = input.method.name, date = input.date.toString(), payload = input.encode())
    private fun statisticsKey(range: ActivityDateRange) = "statistics:${range.from}:${range.to}"
    private fun failure(kind: ApiFailureKind) = ApiResult.Failure(ApiFailure(kind))
    override fun clear() = Unit

    companion object {
        private val transientFailures = setOf(ApiFailureKind.NETWORK, ApiFailureKind.TIMEOUT, ApiFailureKind.SERVER,
            ApiFailureKind.HTTP, ApiFailureKind.RATE_LIMITED, ApiFailureKind.MALFORMED_RESPONSE)
        private val conflictFailures = setOf(ApiFailureKind.VERSION_CONFLICT, ApiFailureKind.CONFLICT,
            ApiFailureKind.DELETED, ApiFailureKind.NOT_FOUND)
        private val manualConversionErrors = setOf(ApiFailureKind.AI_EXPIRED.name, ApiFailureKind.AI_REFERENCE_INVALID.name)
    }
}
