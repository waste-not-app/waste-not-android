package com.example.wastenot.data.repository

import android.app.Application
import androidx.room.Room
import com.example.wastenot.data.local.ActivityDatabase
import com.example.wastenot.data.network.*
import com.example.wastenot.model.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class OfflineActivityStoreTest {
    private val owner = User(UUID.randomUUID().toString(), "Owner", "owner@example.com", ZoneId.of("Asia/Jakarta"), null)
    private val filename = "offline-${UUID.randomUUID()}.db"
    private lateinit var database: ActivityDatabase
    private val server = TestServer()
    private var active = true
    private var scheduled = 0
    private val input = ActivityInput(WasteCategory.PLASTIC, DisposalMethod.RECYCLE, Quantity.Count.of(2), LocalDate.of(2026, 1, 2), "botol")

    @Before fun open() { database = openDatabase() }
    @After fun close() { database.close(); RuntimeEnvironment.getApplication().deleteDatabase(filename) }
    private fun openDatabase() = Room.databaseBuilder(RuntimeEnvironment.getApplication(), ActivityDatabase::class.java, filename).build()
    private fun store(user: User = owner) = OfflineActivityStore(user, database, server, { active }, { scheduled++ })
    private fun <T> ApiResult<T>.value(): T = (this as ApiResult.Success).value
    private fun ApiResult<*>.kind() = (this as ApiResult.Failure).error.kind
    private fun request() = UUID.randomUUID().toString()

    @Test fun createEditAndReadSurviveDatabaseAndRepositoryRestart() = runBlocking {
        val store = store()
        val id = store.create(input, request()).value().log.id
        val updated = store.update(id, 1, input.copy(notes = "dipilah")).value()
        assertEquals(ActivitySyncState.PENDING, updated.syncState)
        assertEquals(0, server.created)
        database.close(); database = openDatabase()
        assertEquals("dipilah", store().get(id).value().notes)
        assertEquals(2, database.activities().mutations(owner.id).size)
        assertEquals(2, scheduled)
    }

    @Test fun lostCreateResponseRetriesSameRequestWithoutDuplicateXpOrRows() = runBlocking {
        val store = store()
        val request = request()
        val localId = store.create(input, request).value().log.id
        server.loseCreate = true
        assertFalse(store.synchronize())
        assertEquals(1, server.created)
        database.close(); database = openDatabase()
        assertTrue(store().synchronize())
        assertEquals(listOf(request, request), server.requests)
        assertEquals(1, server.created)
        assertEquals(1, server.rows.size)
        assertEquals(ActivitySyncState.SYNCED, store().get(localId).value().syncState)
        assertTrue(database.activities().mutations(owner.id).isEmpty())
    }

    @Test fun createEditDeleteSynchronizeInOrderEvenBeforeFirstServerAcknowledgement() = runBlocking {
        val store = store()
        val id = store.create(input, request()).value().log.id
        val edited = store.update(id, 1, input.copy(notes = "edit")).value()
        assertTrue(store.delete(id, edited.version) is ApiResult.Success)
        assertTrue(store.page().value().isEmpty())
        assertTrue(store.synchronize())
        assertEquals(listOf("CREATE", "UPDATE", "DELETE"), server.operations)
        assertTrue(server.rows.isEmpty())
        assertTrue(database.activities().mutations(owner.id).isEmpty())
    }

    @Test fun createEditDeleteCommittedPostAndPatchResponseLossRecoverAcrossRestart() = runBlocking {
        val store = store()
        val id = store.create(input, request()).value().log.id
        store.update(id, 1, input.copy(notes = "edit"))
        server.loseCreate = true
        assertFalse(store.synchronize())
        server.loseUpdate = true
        assertFalse(store.synchronize())
        database.close(); database = openDatabase()
        assertTrue(store().synchronize())
        val record = store().get(id).value()
        assertEquals("edit", record.notes)
        assertEquals(ActivitySyncState.SYNCED, record.syncState)
        assertEquals(1, server.created)
        assertEquals(2, server.rows.getValue(record.id).version)
    }

    @Test fun pullNeverOverwritesPendingFieldsOrResurrectsLocalDeletion() = runBlocking {
        val store = store()
        val a = store.create(input, request()).value().log.id
        val b = store.create(input, request()).value().log.id
        store.synchronize()
        val first = store.get(a).value()
        val second = store.get(b).value()
        store.update(a, first.version, input.copy(notes = "lokal"))
        store.delete(b, second.version)
        server.rejectWrites = ApiFailureKind.VERSION_CONFLICT
        store.synchronize()
        assertEquals("lokal", store.get(a).value().notes)
        assertEquals(ActivitySyncState.CONFLICT, store.get(a).value().syncState)
        assertEquals(ApiFailureKind.NOT_FOUND, store.get(b).kind())
        assertEquals(1, store.page().value().size)
    }

    @Test fun conflictPreservesLocalDraftAndRequiresExplicitDecisionAtRemoteVersion() = runBlocking {
        val store = store()
        val id = store.create(input, request()).value().log.id
        store.synchronize()
        val record = store.get(id).value()
        server.externalEdit(record.id, "server")
        store.update(id, record.version, input.copy(notes = "lokal"))
        store.synchronize()
        assertEquals("lokal", store.get(id).value().notes)
        assertEquals("server", server.rows.getValue(record.id).notes)
        assertEquals(ApiFailureKind.VERSION_CONFLICT, store.update(id, 2, input).kind())
        assertTrue(store.resolveSync(id, keepLocal = true) is ApiResult.Success)
        store.synchronize()
        assertEquals("lokal", server.rows.getValue(record.id).notes)
        assertEquals(3, server.rows.getValue(record.id).version)
    }

    @Test fun discardingConflictRestoresServerFieldsWithoutAnotherMutation() = runBlocking {
        val store = store()
        val id = store.create(input, request()).value().log.id
        store.synchronize()
        val record = store.get(id).value()
        server.externalEdit(record.id, "server")
        store.delete(id, record.version)
        store.synchronize()
        assertTrue(store.resolveSync(id, keepLocal = false) is ApiResult.Success)
        assertEquals("server", store.get(id).value().notes)
        assertEquals(ActivitySyncState.SYNCED, store.get(id).value().syncState)
        assertTrue(database.activities().mutations(owner.id).isEmpty())
    }

    @Test fun otherAccountCannotReadOrDrainPendingChangesAndInactiveSessionCannotWrite() = runBlocking {
        val id = store().create(input, request()).value().log.id
        val other = store(owner.copy(id = request()))
        assertEquals(ApiFailureKind.NOT_FOUND, other.get(id).kind())
        assertTrue(other.page().value().isEmpty())
        other.synchronize()
        assertEquals(0, server.created)
        active = false
        assertEquals(ApiFailureKind.UNAUTHENTICATED, store().get(id).kind())
        assertEquals(ApiFailureKind.UNAUTHENTICATED, store().create(input, request()).kind())
        store().synchronize()
        assertEquals(1, database.activities().mutations(owner.id).size)
    }

    @Test fun repeatedClientIdCannotReplaceOriginalPayloadAfterAcknowledgement() = runBlocking {
        val store = store()
        val id = request()
        store.create(input, id)
        store.synchronize()
        assertEquals(ApiFailureKind.IDEMPOTENCY_CONFLICT, store.create(input.copy(notes = "other"), id).kind())
        assertFalse(store.create(input, id).value().created)
        val record = store.get(id).value()
        store.delete(id, record.version)
        store.synchronize()
        assertEquals(ApiFailureKind.DELETED, store.create(input, id).kind())
    }

    @Test fun historyPagesAndFiltersReadRoomWithoutRequests() = runBlocking {
        repeat(42) { index -> store().create(input.copy(category = if (index < 22) WasteCategory.PLASTIC else WasteCategory.METAL), request()) }
        val first = store().page(WasteCategory.PLASTIC)
        assertEquals(20, first.value().size)
        val cursor = (first as ApiResult.Success).page!!.nextCursor
        assertEquals(2, store().page(WasteCategory.PLASTIC, cursor = cursor).value().size)
        assertEquals(0, server.created)
    }

    @Test fun cacheRefreshReturnsVisibleWindowWithoutDroppingPreviouslyLoadedPages() = runBlocking {
        repeat(42) { store().create(input, request()) }
        val visible = store().visiblePage(null, null, null, 40) as ApiResult.Success
        assertEquals(40, visible.value.size)
        assertEquals("local:40", visible.page!!.nextCursor)
        assertEquals(2, store().page(cursor = visible.page!!.nextCursor).value().size)
        assertEquals(0, server.created)
    }

    @Test fun expiredAiReferenceIsRetainedUntilExplicitConversionToManual() = runBlocking {
        val store = store()
        val original = request()
        store.create(input.copy(aiProvenance = AiLogProvenance(request(), input.category)), original)
        server.rejectCreate = ApiFailureKind.AI_EXPIRED
        store.synchronize()
        assertEquals(ActivitySyncState.FAILED, store.get(original).value().syncState)
        assertEquals(1, database.activities().mutations(owner.id).size)
        assertEquals(0, server.created)
        store.resolveSync(original, keepLocal = true)
        assertEquals(ApiFailureKind.NOT_FOUND, store.get(original).kind())
        server.rejectCreate = null
        store.synchronize()
        assertEquals(1, server.created)
        assertEquals("botol", server.rows.values.single().notes)
    }

    private inner class TestServer : ActivityStore {
        override val referenceDate: LocalDate? = null
        val rows = linkedMapOf<String, WasteLog>()
        private val receipts = mutableMapOf<String, String>()
        val requests = mutableListOf<String>()
        val operations = mutableListOf<String>()
        var created = 0
        var loseCreate = false
        var loseUpdate = false
        var rejectWrites: ApiFailureKind? = null
        var rejectCreate: ApiFailureKind? = null
        private fun failure(kind: ApiFailureKind) = ApiResult.Failure(ApiFailure(kind))
        override suspend fun dashboard(): ApiResult<DashboardSnapshot> = failure(ApiFailureKind.NOT_CONFIGURED)
        override suspend fun statistics(range: ActivityDateRange): ApiResult<StatisticsSnapshot> = failure(ApiFailureKind.NOT_CONFIGURED)
        override suspend fun load() = page()
        override suspend fun page(category: WasteCategory?, method: DisposalMethod?, dates: LogDateRange?, cursor: String?): ApiResult<List<WasteLog>> {
            val offset = cursor?.toInt() ?: 0
            val all = rows.values.toList()
            val more = all.size > offset + 20
            return ApiResult.Success(all.drop(offset).take(20), PageInfo(if (more) (offset + 20).toString() else null, more))
        }
        override suspend fun get(id: String): ApiResult<WasteLog> = rows[id]?.let { ApiResult.Success(it) } ?: failure(ApiFailureKind.NOT_FOUND)
        override suspend fun create(input: ActivityInput, clientRequestId: String): ApiResult<ActivityCreation> {
            rejectCreate?.let { return failure(it) }
            requests += clientRequestId
            receipts[clientRequestId]?.let { return ApiResult.Success(ActivityCreation(rows.getValue(it), false)) }
            val id = request()
            val now = Instant.now()
            val record = WasteLog(id, input.category, input.method, input.quantity, input.date, owner.timezone, input.notes, 1, now, now)
            rows[id] = record; receipts[clientRequestId] = id; created++; operations += "CREATE"
            if (loseCreate) { loseCreate = false; return failure(ApiFailureKind.NETWORK) }
            return ApiResult.Success(ActivityCreation(record, true))
        }
        override suspend fun update(id: String, version: Int, input: ActivityInput): ApiResult<WasteLog> {
            rejectWrites?.let { return failure(it) }
            val current = rows[id] ?: return failure(ApiFailureKind.NOT_FOUND)
            if (current.version != version) return failure(ApiFailureKind.VERSION_CONFLICT)
            val result = current.copy(category = input.category, method = input.method, quantity = input.quantity,
                notes = input.notes, activityDate = input.date, version = version + 1)
            rows[id] = result; operations += "UPDATE"
            if (loseUpdate) { loseUpdate = false; return failure(ApiFailureKind.NETWORK) }
            return ApiResult.Success(result)
        }
        override suspend fun delete(id: String, version: Int): ApiResult<Unit> {
            rejectWrites?.let { return failure(it) }
            val current = rows[id] ?: return ApiResult.Success(Unit)
            if (current.version != version) return failure(ApiFailureKind.VERSION_CONFLICT)
            rows.remove(id); operations += "DELETE"
            return ApiResult.Success(Unit)
        }
        fun externalEdit(id: String, notes: String) { rows[id] = rows.getValue(id).let { it.copy(notes = notes, version = it.version + 1) } }
        override fun clear() = Unit
    }
}
