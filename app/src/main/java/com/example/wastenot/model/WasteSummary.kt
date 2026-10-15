package com.example.wastenot.model

import java.math.BigDecimal

data class QuantityTotals(val weightGrams: Long, val itemCount: Long) {
    init {
        require(weightGrams >= 0 && itemCount >= 0)
    }
}

// Values come from the server; handled and avoided totals stay independent.
data class WasteSummary(
    val handled: QuantityTotals,
    val avoided: QuantityTotals,
    val activityCount: Long,
    val handledActivityCount: Long,
    val recycledActivityCount: Long,
    val recyclingRatePercent: BigDecimal?,
) {
    init {
        require(activityCount >= 0 && handledActivityCount in 0..activityCount)
        require(recycledActivityCount in 0..handledActivityCount)
        require((handledActivityCount == 0L) == (recyclingRatePercent == null))
        require(recyclingRatePercent == null ||
            recyclingRatePercent in BigDecimal.ZERO..BigDecimal(100))
    }
}
