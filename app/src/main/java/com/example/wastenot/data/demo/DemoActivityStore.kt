package com.example.wastenot.data.demo

import com.example.wastenot.data.network.ApiFailure
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.repository.ActivityInput
import com.example.wastenot.data.repository.ActivityStore
import com.example.wastenot.model.ActivityDates
import com.example.wastenot.model.DisposalMethod
import com.example.wastenot.model.Quantity
import com.example.wastenot.model.QuantityTotals
import com.example.wastenot.model.WasteCategory
import com.example.wastenot.model.WasteLog
import com.example.wastenot.model.WasteSummary
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class DemoActivityStore(private val zone: ZoneId) : ActivityStore {
    override val isDemo = true
    override val referenceDate: LocalDate = LocalDate.of(2026, 9, 21)
    private val instant = referenceDate.atTime(12, 0).atZone(zone).toInstant()
    private val records = linkedMapOf<String, WasteLog>()
    private val requests = mutableMapOf<String, Pair<ActivityInput, String>>()

    init {
        listOf(
            Triple(WasteCategory.PLASTIC, DisposalMethod.RECYCLE, Quantity.Weight.of(250)),
            Triple(WasteCategory.GLASS, DisposalMethod.REUSE, Quantity.Weight.of(400)),
            Triple(WasteCategory.PLASTIC, DisposalMethod.REDUCE, Quantity.Count.of(2)),
            Triple(WasteCategory.ORGANIC, DisposalMethod.TRASH, Quantity.Weight.of(600)),
            Triple(WasteCategory.PAPER, DisposalMethod.RECYCLE, Quantity.Weight.of(150)),
        ).forEachIndexed { index, (category, method, quantity) ->
            val id = "00000000-0000-4000-8000-${(101 + index).toString().padStart(12, '0')}"
            val date = if (index < 3) referenceDate else referenceDate.minusDays(1)
            val created = date.atTime(8 + index, 0).atZone(zone).toInstant()
            records[id] = WasteLog(id, category, method, quantity, date, zone, "", 1, created, created)
        }
    }

    override suspend fun load() = ApiResult.Success(records.values.sortedWith(compareByDescending<WasteLog> { it.activityDate }.thenByDescending { it.createdAt }))

    override suspend fun create(input: ActivityInput, clientRequestId: String): ApiResult<WasteLog> {
        requests[clientRequestId]?.let { (previous, id) ->
            return if (previous == input) ApiResult.Success(checkNotNull(records[id])) else failure(ApiFailureKind.IDEMPOTENCY_CONFLICT)
        }
        if (!valid(input)) return failure(ApiFailureKind.VALIDATION)
        val id = UUID.randomUUID().toString()
        val created = instant.plusSeconds(records.size.toLong())
        val log = WasteLog(id, input.category, input.method, input.quantity, input.date, zone, input.notes, 1, created, created)
        records[id] = log
        requests[clientRequestId] = input to id
        return ApiResult.Success(log)
    }

    override suspend fun update(id: String, version: Int, input: ActivityInput): ApiResult<WasteLog> {
        val old = records[id] ?: return failure(ApiFailureKind.NOT_FOUND)
        if (old.version != version) return failure(ApiFailureKind.VERSION_CONFLICT)
        if (!valid(input, old.activityDate)) return failure(ApiFailureKind.VALIDATION)
        val updated = old.copy(category = input.category, method = input.method, quantity = input.quantity,
            activityDate = input.date, notes = input.notes, version = old.version + 1, updatedAt = maxOf(instant, old.updatedAt).plusSeconds(1))
        records[id] = updated
        return ApiResult.Success(updated)
    }

    override fun clear() { records.clear(); requests.clear() }

    private fun valid(input: ActivityInput, originalDate: LocalDate? = null) =
        input.notes.length <= 500 && ActivityDates.canSave(input.date, referenceDate, originalDate)

    private fun failure(kind: ApiFailureKind) = ApiResult.Failure(ApiFailure(kind))
}

// Only the explicit demo source aggregates locally; live totals require the summary endpoint.
fun demoSummary(records: List<WasteLog>): WasteSummary {
    fun totals(items: List<WasteLog>) = QuantityTotals(
        items.sumOf { (it.quantity as? Quantity.Weight)?.grams?.toLong() ?: 0L },
        items.sumOf { (it.quantity as? Quantity.Count)?.items?.toLong() ?: 0L },
    )
    val handled = records.filter { it.method != DisposalMethod.REDUCE }
    val avoided = records.filter { it.method == DisposalMethod.REDUCE }
    val recycled = handled.count { it.method == DisposalMethod.RECYCLE }.toLong()
    val rate = if (handled.isEmpty()) null else BigDecimal(recycled * 100)
        .divide(BigDecimal(handled.size), 1, RoundingMode.HALF_UP)
    return WasteSummary(totals(handled), totals(avoided), records.size.toLong(), handled.size.toLong(), recycled, rate)
}
