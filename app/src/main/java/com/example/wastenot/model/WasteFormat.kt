package com.example.wastenot.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class FormattedQuantity(val amount: String, val unit: QuantityUnit)

object WasteFormat {
    private val locale: Locale get() = Locale.getDefault()
    private val date: DateTimeFormatter get() = DateTimeFormatter.ofPattern("d MMM uuuu", locale)
    private val timestamp: DateTimeFormatter get() = DateTimeFormatter.ofPattern("d MMM uuuu, HH:mm", locale)

    fun quantity(quantity: Quantity, summary: Boolean = false): FormattedQuantity = when (quantity) {
        is Quantity.Weight -> weight(quantity.grams.toLong(), summary)
        is Quantity.Count -> FormattedQuantity(integer(quantity.items.toLong()), QuantityUnit.ITEM)
    }

    fun weight(grams: Long, summary: Boolean = false): FormattedQuantity {
        require(grams >= 0)
        if (grams < 1_000) return FormattedQuantity(integer(grams), QuantityUnit.GRAM)
        val kilograms = BigDecimal.valueOf(grams).movePointLeft(3)
        return FormattedQuantity(decimal(kilograms, if (summary) 2 else 3), QuantityUnit.KILOGRAM)
    }

    fun integer(value: Long): String = decimal(BigDecimal.valueOf(value), 0)

    // Null stays null so the caller can use the localized no-handled-activity message.
    fun percentage(value: BigDecimal?): String? = value?.let {
        require(it in BigDecimal.ZERO..BigDecimal(100))
        decimal(it, 1)
    }

    fun activityDate(value: LocalDate): String = date.format(value)

    fun timestamp(value: Instant, accountZone: ZoneId): String =
        timestamp.format(value.atZone(accountZone))

    private fun decimal(value: BigDecimal, places: Int): String =
        DecimalFormat("#,##0" + if (places > 0) "." + "#".repeat(places) else "",
            DecimalFormatSymbols.getInstance(locale)).apply {
            roundingMode = RoundingMode.HALF_UP
        }.format(value)
}
