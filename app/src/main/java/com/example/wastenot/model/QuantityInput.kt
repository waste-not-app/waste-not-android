package com.example.wastenot.model

import java.math.BigDecimal

enum class QuantityUnit(val kind: QuantityKind) {
    GRAM(QuantityKind.WEIGHT), KILOGRAM(QuantityKind.WEIGHT), ITEM(QuantityKind.COUNT),
}

sealed interface QuantityParseResult {
    @JvmInline
    value class Valid(val quantity: Quantity) : QuantityParseResult

    enum class Invalid : QuantityParseResult { EMPTY, FORMAT, PRECISION, OUT_OF_RANGE }
}

private val decimalInput = Regex("[0-9]+(?:[.,][0-9]+)?")

fun parseQuantity(rawText: String, unit: QuantityUnit): QuantityParseResult {
    val text = rawText.trim()
    if (text.isEmpty()) return QuantityParseResult.Invalid.EMPTY
    if (!decimalInput.matches(text)) return QuantityParseResult.Invalid.FORMAT
    val decimalPlaces = text.substringAfter('.', text.substringAfter(',', "")).length
    if (unit != QuantityUnit.KILOGRAM && decimalPlaces > 0) {
        return QuantityParseResult.Invalid.FORMAT
    }
    if (decimalPlaces > 3) return QuantityParseResult.Invalid.PRECISION
    val decimal = BigDecimal(text.replace(',', '.'))
    val canonical = if (unit == QuantityUnit.KILOGRAM) decimal.movePointRight(3) else decimal
    val limit = if (unit == QuantityUnit.ITEM) 10_000 else 100_000
    if (canonical < BigDecimal.ONE || canonical > BigDecimal(limit)) {
        return QuantityParseResult.Invalid.OUT_OF_RANGE
    }
    val value = canonical.intValueExact()
    return QuantityParseResult.Valid(
        if (unit == QuantityUnit.ITEM) Quantity.Count.of(value) else Quantity.Weight.of(value),
    )
}

data class QuantityInput(val rawText: String = "", val unit: QuantityUnit = QuantityUnit.GRAM) {
    fun changeUnit(newUnit: QuantityUnit): QuantityInput {
        if (newUnit == unit) return this
        if (newUnit.kind != unit.kind) return QuantityInput(unit = newUnit)
        val weight = (parseQuantity(rawText, unit) as? QuantityParseResult.Valid)?.quantity
            as? Quantity.Weight
        val converted = weight?.let {
            if (newUnit == QuantityUnit.GRAM) it.grams.toString()
            else BigDecimal(it.grams).movePointLeft(3).stripTrailingZeros().toPlainString()
        }.orEmpty()
        return QuantityInput(converted, newUnit)
    }
}
