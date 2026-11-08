package com.example.wastenot.ui.main

import com.example.wastenot.model.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class HistoryFilterTest {
    private val today = LocalDate.of(2026, 9, 21)
    private fun log(id: String, date: LocalDate = today, category: WasteCategory = WasteCategory.PLASTIC,
        method: DisposalMethod = DisposalMethod.RECYCLE) = WasteLog(id, category, method, Quantity.Weight.of(250),
        date, ZoneId.of("Asia/Jakarta"), "", 1, Instant.EPOCH, Instant.EPOCH)

    @Test fun categoryMethodAndInclusiveDateBoundsAreCombined() {
        val filter = HistoryFilter(WasteCategory.PLASTIC, DisposalMethod.RECYCLE, today.minusDays(1), today)
        val records = listOf(log("upper"), log("lower", today.minusDays(1)), log("old", today.minusDays(2)),
            log("method", method = DisposalMethod.REDUCE), log("category", category = WasteCategory.GLASS))
        assertEquals(listOf("upper", "lower"), filter.apply(records).map { it.id })
    }

    @Test fun datesRequireACompletePairInOrder() {
        assertEquals(HistoryFilter.DateError.INCOMPLETE, HistoryFilter(from = today).dateError)
        assertEquals(HistoryFilter.DateError.INCOMPLETE, HistoryFilter(to = today).dateError)
        assertEquals(HistoryFilter.DateError.REVERSED, HistoryFilter(from = today, to = today.minusDays(1)).dateError)
        assertNull(HistoryFilter(from = today, to = today).dateError)
        assertNull(HistoryFilter().dateError)
    }

    @Test fun resetIncludesAllMethodsAndUnitsAndUsesApiDateIdOrder() {
        val records = listOf(log("a"), log("c"), log("z", today.minusDays(1)),
            log("b", method = DisposalMethod.REDUCE).copy(quantity = Quantity.Count.of(2)))
        val filter = HistoryFilter()
        assertFalse(filter.active)
        assertEquals(listOf("c", "b", "a", "z"), filter.apply(records).map { it.id })
        assertTrue(HistoryFilter(method = DisposalMethod.REDUCE).active)
    }

    @Test(expected = IllegalArgumentException::class)
    fun incompleteDraftCannotBeUsedAsAnActiveFilter() {
        HistoryFilter(from = today).apply(listOf(log("a")))
    }
}
