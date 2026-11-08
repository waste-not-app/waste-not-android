package com.example.wastenot.data.dummy

import com.example.wastenot.data.demo.DemoActivityStore
import com.example.wastenot.model.*
import java.math.BigDecimal
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class DummyStatisticsTest {
    private val store = DemoActivityStore(ZoneId.of("Asia/Jakarta"))
    private val today = store.referenceDate
    private fun statistics(records: List<WasteLog>, days: Int) =
        dummyStatistics(records, ActivityDateRange.lastDays(today, days), ZoneId.of("Asia/Jakarta"))

    @Test fun canonicalWeekHasZeroFilledDaysAndAllMethodsAndCategories() = runTest {
        val stats = statistics(store.load().value, 7)
        assertEquals(1_400L, stats.summary.handled.weightGrams)
        assertEquals(2L, stats.summary.avoided.itemCount)
        assertEquals(BigDecimal("50.0"), stats.summary.recyclingRatePercent)
        assertEquals(7, stats.daily.size)
        assertEquals(today.minusDays(6), stats.daily.first().date)
        assertTrue(stats.daily.take(5).all { it.handled == QuantityTotals(0, 0) })
        assertEquals(650L, stats.daily.last().handled.weightGrams)
        assertEquals(0L, stats.daily.last().handled.itemCount)
        assertEquals(5L, stats.methods.values.sum())
        assertEquals(DisposalMethod.entries.toSet(), stats.methods.keys)
        assertEquals(WasteCategory.entries.toSet(), stats.categories.keys)
        assertEquals(250L, stats.categories.getValue(WasteCategory.PLASTIC).weightGrams)
        assertEquals(0L, stats.categories.getValue(WasteCategory.PLASTIC).itemCount)
    }

    @Test fun rangesAreInclusiveAndExcludeFutureAndOldDates() = runTest {
        val record = store.load().value.first { it.method == DisposalMethod.RECYCLE }
        val logs = listOf(-30L, -29L, -7L, -6L, 0L, 1L).mapIndexed { i, offset ->
            record.copy(id = i.toString(), activityDate = today.plusDays(offset), quantity = Quantity.Weight.of(1))
        }
        val week = statistics(logs, 7)
        assertEquals(2L, week.summary.activityCount)
        assertEquals(2L, week.daily.sumOf { it.handled.weightGrams })
        val month = statistics(logs, 30)
        assertEquals(4L, month.summary.activityCount)
        assertEquals(30, month.daily.size)
        assertEquals(1L, month.daily.first().handled.weightGrams)
        assertEquals(today, month.daily.last().date)
    }

    @Test fun countSeriesAndReduceStaySeparateWithoutRoundingBeforeAggregation() = runTest {
        val record = store.load().value.first { it.method == DisposalMethod.RECYCLE }
        val logs = listOf(
            record.copy(id = "1", quantity = Quantity.Count.of(20), method = DisposalMethod.RECYCLE),
            record.copy(id = "2", quantity = Quantity.Weight.of(1_655), method = DisposalMethod.REUSE),
            record.copy(id = "3", quantity = Quantity.Weight.of(1_655), method = DisposalMethod.REDUCE),
            record.copy(id = "4", quantity = Quantity.Count.of(3), method = DisposalMethod.REDUCE),
        )
        val stats = statistics(logs, 7)
        assertEquals(QuantityTotals(1_655, 20), stats.summary.handled)
        assertEquals(QuantityTotals(1_655, 3), stats.summary.avoided)
        assertEquals(QuantityTotals(1_655, 20), stats.daily.last().handled)
        assertEquals(BigDecimal("50.0"), stats.summary.recyclingRatePercent)
        assertEquals(2L, stats.methods.getValue(DisposalMethod.REDUCE))
        assertEquals(QuantityTotals(1_655, 20), stats.categories.getValue(record.category))
    }

    @Test fun emptyAndReduceOnlyDoNotInventAHandledRate() = runTest {
        val empty = statistics(emptyList(), 30)
        assertEquals(0L, empty.summary.activityCount)
        assertNull(empty.summary.recyclingRatePercent)
        assertTrue(empty.daily.all { it.handled == QuantityTotals(0, 0) })
        val reduce = statistics(store.load().value.filter { it.method == DisposalMethod.REDUCE }, 7)
        assertEquals(1L, reduce.summary.activityCount)
        assertNull(reduce.summary.recyclingRatePercent)
        assertEquals(0L, reduce.daily.sumOf { it.handled.itemCount })
        assertEquals(0L, reduce.daily.sumOf { it.handled.weightGrams })
    }
}
