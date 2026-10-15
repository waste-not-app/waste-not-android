package com.example.wastenot.model

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class WasteFormatTest {
    @Test
    fun formatsDetailAndSummaryWeightsWithSeparateUnitAndNoPrecisionLossInTheModel() {
        assertEquals(FormattedQuantity("250", QuantityUnit.GRAM), WasteFormat.quantity(Quantity.Weight.of(250)))
        val quantity = Quantity.Weight.of(1234)
        assertEquals(FormattedQuantity("1,234", QuantityUnit.KILOGRAM), WasteFormat.quantity(quantity))
        assertEquals(FormattedQuantity("1,23", QuantityUnit.KILOGRAM), WasteFormat.quantity(quantity, summary = true))
        assertEquals(1234, quantity.grams)
        assertEquals(FormattedQuantity("0", QuantityUnit.GRAM), WasteFormat.weight(0))
        assertEquals(FormattedQuantity("100", QuantityUnit.KILOGRAM), WasteFormat.weight(100_000))
        assertEquals(FormattedQuantity("10.000", QuantityUnit.ITEM), WasteFormat.quantity(Quantity.Count.of(10_000)))
    }

    @Test
    fun percentageUsesOneOptionalDecimalAndPreservesNoDenominator() {
        assertEquals("50", WasteFormat.percentage(BigDecimal("50.0")))
        assertEquals("33,3", WasteFormat.percentage(BigDecimal("33.333333")))
        assertEquals("33,4", WasteFormat.percentage(BigDecimal("33.35")))
        assertEquals("0", WasteFormat.percentage(BigDecimal.ZERO))
        assertNull(WasteFormat.percentage(null))
        assertThrows(IllegalArgumentException::class.java) { WasteFormat.percentage(BigDecimal("100.1")) }
    }

    @Test
    fun formatsActivityDateWithoutReinterpretingItAsUtc() {
        assertEquals("21 Sep 2026", WasteFormat.activityDate(LocalDate.parse("2026-09-21")))
        val instant = Instant.parse("2026-09-20T17:30:00Z")
        assertEquals("21 Sep 2026, 00.30", WasteFormat.timestamp(instant, ZoneId.of("Asia/Jakarta")))
        assertEquals("20 Sep 2026, 10.30", WasteFormat.timestamp(instant, ZoneId.of("America/Los_Angeles")))
    }

    @Test
    fun prd94ServerSummaryKeepsHandledAndAvoidedQuantitiesSeparate() {
        // PRD 9.4 / API 6 fixture: five activities, two recycled among four handled.
        val summary = WasteSummary(
            handled = QuantityTotals(1400, 0), avoided = QuantityTotals(0, 2),
            activityCount = 5, handledActivityCount = 4, recycledActivityCount = 2,
            recyclingRatePercent = BigDecimal("50.0"),
        )
        assertEquals(FormattedQuantity("1,4", QuantityUnit.KILOGRAM), WasteFormat.weight(summary.handled.weightGrams, summary = true))
        assertEquals("0", WasteFormat.integer(summary.handled.itemCount))
        assertEquals("2", WasteFormat.integer(summary.avoided.itemCount))
        assertEquals("50", WasteFormat.percentage(summary.recyclingRatePercent))
        assertEquals(4L, summary.handledActivityCount)
        assertEquals(5L, summary.activityCount)
        assertEquals(QuantityTotals(0, 2), summary.avoided)
    }

    @Test
    fun emptyAndReduceOnlySummariesHaveNoRecyclingRate() {
        listOf(
            WasteSummary(QuantityTotals(0, 0), QuantityTotals(0, 0), 0, 0, 0, null),
            WasteSummary(QuantityTotals(0, 0), QuantityTotals(500, 2), 2, 0, 0, null),
        ).forEach { assertNull(WasteFormat.percentage(it.recyclingRatePercent)) }
        assertThrows(IllegalArgumentException::class.java) {
            WasteSummary(QuantityTotals(0, 0), QuantityTotals(0, 2), 1, 0, 0, BigDecimal.ZERO)
        }
    }
}
