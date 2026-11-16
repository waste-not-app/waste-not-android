package com.example.wastenot.data.repository

import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.model.DisposalMethod
import com.example.wastenot.model.AiLogProvenance
import com.example.wastenot.model.Quantity
import com.example.wastenot.model.WasteCategory
import com.example.wastenot.model.WasteLog
import com.example.wastenot.model.ActivityDateRange
import com.example.wastenot.model.LogDateRange
import com.example.wastenot.model.DashboardSnapshot
import com.example.wastenot.model.StatisticsSnapshot
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

data class ActivityInput(
    val category: WasteCategory,
    val method: DisposalMethod,
    val quantity: Quantity,
    val date: LocalDate,
    val notes: String,
    val aiProvenance: AiLogProvenance? = null,
    val photoId: String? = null,
    val clearPhoto: Boolean = false,
) {
    init {
        require(aiProvenance == null || (aiProvenance.category == category && method != DisposalMethod.REDUCE))
        require(!(photoId != null && clearPhoto))
    }
}

data class ActivityCreation(val log: WasteLog, val created: Boolean)

interface ActivityStore {
    val referenceDate: LocalDate?
    val changes: Flow<Unit> get() = emptyFlow()
    /** Durable, account-scoped revision changed only after a server mutation acknowledgement. */
    val synchronizationAcknowledgements: Flow<String> get() = emptyFlow()
    val pending: Flow<List<SyncIssue>> get() = emptyFlow()
    val historyReady: Flow<Boolean> get() = kotlinx.coroutines.flow.flowOf(true)
    fun requestSync() = Unit
    suspend fun resolveSync(id: String, keepLocal: Boolean): ApiResult<Unit> =
        ApiResult.Failure(com.example.wastenot.data.network.ApiFailure(com.example.wastenot.data.network.ApiFailureKind.NOT_CONFIGURED))
    suspend fun isPhotoScanDisabled(): Boolean = false
    suspend fun dashboard(): ApiResult<DashboardSnapshot>
    suspend fun statistics(range: ActivityDateRange): ApiResult<StatisticsSnapshot>
    suspend fun load(): ApiResult<List<WasteLog>>
    suspend fun page(category: WasteCategory? = null, method: DisposalMethod? = null,
        dates: LogDateRange? = null, cursor: String? = null): ApiResult<List<WasteLog>>
    suspend fun visiblePage(category: WasteCategory?, method: DisposalMethod?, dates: LogDateRange?,
        limit: Int): ApiResult<List<WasteLog>> = page(category, method, dates)
    suspend fun get(id: String): ApiResult<WasteLog>
    suspend fun isSameActivity(requestedId: String, actualId: String): Boolean = requestedId == actualId
    suspend fun create(input: ActivityInput, clientRequestId: String): ApiResult<ActivityCreation>
    suspend fun update(id: String, version: Int, input: ActivityInput): ApiResult<WasteLog>
    suspend fun delete(id: String, version: Int): ApiResult<Unit>
    fun clear()
}

data class SyncIssue(val localId: String, val local: WasteLog, val remote: WasteLog?,
    val deleting: Boolean, val error: com.example.wastenot.data.network.ApiFailureKind?)

class RemoteActivityStore(private val core: CoreRepository) : ActivityStore {

    override val referenceDate: LocalDate? = null
    override suspend fun isPhotoScanDisabled(): Boolean =
        (core.metadata() as? ApiResult.Success)?.value?.capabilities?.get("ai_scan") == false
    override suspend fun dashboard() = core.dashboard()
    override suspend fun statistics(range: ActivityDateRange) = core.statistics(range)
    override suspend fun load() = core.wasteLogs()
    override suspend fun page(category: WasteCategory?, method: DisposalMethod?, dates: LogDateRange?, cursor: String?) =
        core.wasteLogs(category, method, dates, cursor)
    override suspend fun get(id: String) = core.wasteLog(id)
    override suspend fun create(input: ActivityInput, clientRequestId: String) = core.createLog(input, clientRequestId)
    override suspend fun update(id: String, version: Int, input: ActivityInput) = core.updateLog(id, version, input)
    override suspend fun delete(id: String, version: Int) = core.deleteLog(id, version)
    override fun clear() = Unit
}
