package com.example.wastenot.data.repository

import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.model.DisposalMethod
import com.example.wastenot.model.Quantity
import com.example.wastenot.model.WasteCategory
import com.example.wastenot.model.WasteLog
import java.time.LocalDate

data class ActivityInput(
    val category: WasteCategory,
    val method: DisposalMethod,
    val quantity: Quantity,
    val date: LocalDate,
    val notes: String,
)

data class ActivityCreation(val log: WasteLog, val created: Boolean)

interface ActivityStore {
    val isDemo: Boolean
    val referenceDate: LocalDate?
    suspend fun load(): ApiResult<List<WasteLog>>
    suspend fun get(id: String): ApiResult<WasteLog>
    suspend fun create(input: ActivityInput, clientRequestId: String): ApiResult<ActivityCreation>
    suspend fun update(id: String, version: Int, input: ActivityInput): ApiResult<WasteLog>
    suspend fun delete(id: String, version: Int): ApiResult<Unit>
    fun clear()
}

class RemoteActivityStore(private val core: CoreRepository) : ActivityStore {
    override val isDemo = false
    override val referenceDate: LocalDate? = null
    override suspend fun load() = core.wasteLogs()
    override suspend fun get(id: String) = core.wasteLog(id)
    override suspend fun create(input: ActivityInput, clientRequestId: String) = core.createLog(input, clientRequestId)
    override suspend fun update(id: String, version: Int, input: ActivityInput) = core.updateLog(id, version, input)
    override suspend fun delete(id: String, version: Int) = core.deleteLog(id, version)
    override fun clear() = Unit
}
