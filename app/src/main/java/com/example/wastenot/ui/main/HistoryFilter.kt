package com.example.wastenot.ui.main

import com.example.wastenot.model.DisposalMethod
import com.example.wastenot.model.WasteCategory
import com.example.wastenot.model.WasteLog
import java.time.LocalDate

data class HistoryFilter(
    val category: WasteCategory? = null,
    val method: DisposalMethod? = null,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
) {
    enum class DateError { INCOMPLETE, REVERSED }

    val dateError: DateError? get() = when {
        (from == null) != (to == null) -> DateError.INCOMPLETE
        from != null && to != null && from > to -> DateError.REVERSED
        else -> null
    }

    val active: Boolean get() = category != null || method != null || from != null || to != null

    fun matches(log: WasteLog): Boolean =
        (category == null || category == log.category) && (method == null || method == log.method) &&
            (from == null || log.activityDate >= from) && (to == null || log.activityDate <= to)

    fun apply(records: List<WasteLog>): List<WasteLog> {
        require(dateError == null)
        return records.filter(::matches).sortedWith(compareByDescending<WasteLog> { it.activityDate }.thenByDescending { it.id })
    }
}
