package com.example.wastenot.data.dummy

import com.example.wastenot.data.network.ApiFailure
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.network.PageInfo
import com.example.wastenot.model.DisposalMethod
import com.example.wastenot.model.LogDateRange
import com.example.wastenot.model.WasteCategory
import com.example.wastenot.model.WasteLog

internal fun fixtureLogPage(records: List<WasteLog>, category: WasteCategory?, method: DisposalMethod?,
    dates: LogDateRange?, cursor: String?): ApiResult<List<WasteLog>> {
    val scope = "$category:$method:${dates?.from}:${dates?.to}:"
    val offset = if (cursor == null) 0 else cursor.takeIf { it.startsWith(scope) }?.removePrefix(scope)?.toIntOrNull()
    val filtered = records.filter { (category == null || it.category == category) &&
        (method == null || it.method == method) && (dates == null || it.activityDate in dates.from..dates.to) }
        .sortedWith(compareByDescending<WasteLog> { it.activityDate }.thenByDescending { it.id })
    if (offset == null || offset < 0 || (offset > 0 && offset >= filtered.size)) {
        return ApiResult.Failure(ApiFailure(ApiFailureKind.INVALID_REQUEST))
    }
    val next = (offset + 20).takeIf { it < filtered.size }?.let { scope + it }
    return ApiResult.Success(filtered.drop(offset).take(20), PageInfo(next, next != null))
}
