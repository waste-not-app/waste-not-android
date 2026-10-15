package com.example.wastenot.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class QuantityTest {
    @Test
    fun canonicalEnumsMatchTheApiAndCarryNoTranslatedLabels() {
        assertEquals(setOf("ORGANIC", "PLASTIC", "PAPER", "GLASS", "METAL", "ELECTRONIC", "OTHER"),
            WasteCategory.entries.map { it.name }.toSet())
        assertEquals(setOf("REDUCE", "REUSE", "RECYCLE", "TRASH"),
            DisposalMethod.entries.map { it.name }.toSet())
    }

    @Test
    fun acceptsExactIntegerGramsAndBothKilogramDecimalSeparators() {
        listOf("250" to QuantityUnit.GRAM, "0,25" to QuantityUnit.KILOGRAM,
            "0.250" to QuantityUnit.KILOGRAM, " 0,25 " to QuantityUnit.KILOGRAM).forEach { (raw, unit) ->
            assertEquals(raw, QuantityParseResult.Valid(Quantity.Weight.of(250)), parseQuantity(raw, unit))
        }
        listOf("0.001" to 1, "1.001" to 1001, "99,999" to 99_999, "100.000" to 100_000)
            .forEach { (raw, grams) ->
                assertEquals(raw, QuantityParseResult.Valid(Quantity.Weight.of(grams)),
                    parseQuantity(raw, QuantityUnit.KILOGRAM))
            }
        assertEquals(QuantityParseResult.Valid(Quantity.Weight.of(100_000)),
            parseQuantity("100000", QuantityUnit.GRAM))
        assertEquals(QuantityParseResult.Valid(Quantity.Count.of(10_000)),
            parseQuantity("10000", QuantityUnit.ITEM))
    }

    @Test
    fun rejectsFractionalCountsAndGramsAndNeverRoundsExtraPrecision() {
        listOf(QuantityUnit.ITEM, QuantityUnit.GRAM).forEach { unit ->
            listOf("2,5", "2.0", "1,000", "1.000").forEach { raw ->
                assertEquals(raw, QuantityParseResult.Invalid.FORMAT, parseQuantity(raw, unit))
            }
        }
        listOf("0,0001", "1.0000", "0.2500").forEach { raw ->
            assertEquals(raw, QuantityParseResult.Invalid.PRECISION,
                parseQuantity(raw, QuantityUnit.KILOGRAM))
        }
    }

    @Test
    fun rejectsAmbiguousGroupingNonDecimalNotationAndUnsupportedCharacters() {
        listOf("1,000.00", "1.000,00", "1,000,000", "1 000", "1_000", "1\u00a0000",
            "-1", "+1", "NaN", "Infinity", "1e2", "0x10", ".25", "1,", "٢٥٠", "250 g")
            .forEach { raw ->
                QuantityUnit.entries.forEach { unit ->
                    assertEquals("$raw $unit", QuantityParseResult.Invalid.FORMAT, parseQuantity(raw, unit))
                }
            }
        assertEquals(QuantityParseResult.Invalid.EMPTY, parseQuantity(" \n", QuantityUnit.GRAM))
    }

    @Test
    fun enforcesBothBoundsWithoutIntegerOverflow() {
        listOf("0" to QuantityUnit.GRAM, "100001" to QuantityUnit.GRAM,
            "0" to QuantityUnit.ITEM, "10001" to QuantityUnit.ITEM,
            "0.000" to QuantityUnit.KILOGRAM, "100.001" to QuantityUnit.KILOGRAM,
            "999999999999999999999999999999999999" to QuantityUnit.GRAM).forEach { (raw, unit) ->
            assertEquals(raw, QuantityParseResult.Invalid.OUT_OF_RANGE, parseQuantity(raw, unit))
        }
        assertThrows(IllegalArgumentException::class.java) { Quantity.Weight.of(0) }
        assertThrows(IllegalArgumentException::class.java) { Quantity.Count.of(10_001) }
    }

    @Test
    fun canonicalFieldsRequireExactlyOneValueOfTheSelectedKind() {
        assertEquals(Quantity.Weight.of(250), Quantity.fromFields(QuantityKind.WEIGHT, 250, null))
        assertEquals(Quantity.Count.of(2), Quantity.fromFields(QuantityKind.COUNT, null, 2))
        QuantityKind.entries.forEach { kind ->
            assertNull(Quantity.fromFields(kind, 250, 2))
            assertNull(Quantity.fromFields(kind, null, null))
            assertNull(Quantity.fromFields(kind, 0, null))
            assertNull(Quantity.fromFields(kind, null, 10_001))
        }
        assertNull(Quantity.fromFields(QuantityKind.COUNT, 250, null))
        assertNull(Quantity.fromFields(QuantityKind.WEIGHT, null, 2))
    }

    @Test
    fun switchesWeightDisplayExactlyAndClearsAmountsWhenChangingKind() {
        val grams = QuantityInput("250")
        val kilograms = grams.changeUnit(QuantityUnit.KILOGRAM)
        assertEquals(QuantityInput("0.25", QuantityUnit.KILOGRAM), kilograms)
        assertEquals(grams, kilograms.changeUnit(QuantityUnit.GRAM))
        val count = kilograms.changeUnit(QuantityUnit.ITEM)
        assertEquals(QuantityInput("", QuantityUnit.ITEM), count)
        assertEquals(QuantityInput("", QuantityUnit.GRAM),
            QuantityInput("2", QuantityUnit.ITEM).changeUnit(QuantityUnit.GRAM))
        assertEquals(QuantityInput("", QuantityUnit.KILOGRAM),
            QuantityInput("invalid").changeUnit(QuantityUnit.KILOGRAM))
        assertEquals(QuantityInput("invalid"), QuantityInput("invalid").changeUnit(QuantityUnit.GRAM))
    }

    @Test
    fun allCanonicalWeightsRoundTripWithoutPrecisionLoss() {
        listOf(1, 9, 10, 99, 100, 250, 999, 1000, 1001, 1400, 12345, 99999, 100000).forEach { grams ->
            val input = QuantityInput(grams.toString()).changeUnit(QuantityUnit.KILOGRAM)
            assertEquals(QuantityParseResult.Valid(Quantity.Weight.of(grams)), parseQuantity(input.rawText, input.unit))
            assertEquals(grams.toString(), input.changeUnit(QuantityUnit.GRAM).rawText)
        }
    }
}
