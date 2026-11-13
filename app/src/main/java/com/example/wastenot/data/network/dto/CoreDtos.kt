package com.example.wastenot.data.network.dto

import com.example.wastenot.data.network.PageInfo
import com.example.wastenot.model.DisposalMethod
import com.example.wastenot.model.Metadata
import com.example.wastenot.model.Quantity
import com.example.wastenot.model.QuantityKind
import com.example.wastenot.model.ValidationLimits
import com.example.wastenot.model.WasteCategory
import com.example.wastenot.model.WasteLog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DataEnvelope<T>(val data: T, val page: PageDto? = null)

@Serializable
data class PageDto(@SerialName("next_cursor") val nextCursor: String?, @SerialName("has_more") val hasMore: Boolean) {
    fun toModel(): PageInfo {
        require(hasMore == !nextCursor.isNullOrBlank())
        return PageInfo(nextCursor, hasMore)
    }
}

@Serializable
data class ErrorEnvelope(val error: ErrorDto, @SerialName("request_id") val requestId: String? = null)

@Serializable
data class ErrorDto(val code: String, val message: String? = null, val fields: Map<String, String> = emptyMap())

@Serializable
data class MetadataDto(
    val categories: List<String>,
    val methods: List<String>,
    val limits: ValidationLimitsDto,
    val capabilities: Map<String, Boolean>,
    @SerialName("ai_object_catalog_version") val aiObjectCatalogVersion: String? = null,
) {
    fun toModel(): Metadata {
        val categories = categories.map(WasteCategory::valueOf)
        val methods = methods.map(DisposalMethod::valueOf)
        require(categories.isNotEmpty() && categories.distinct().size == categories.size)
        require(methods.isNotEmpty() && methods.distinct().size == methods.size)
        return Metadata(categories, methods, limits.toModel(), capabilities.toMap(), aiObjectCatalogVersion)
    }
}

@Serializable
data class BoundsDto(val min: Int, val max: Int) {
    fun within(baseline: IntRange): IntRange {
        require(min in baseline && max in baseline && min <= max)
        return min..max
    }
}

@Serializable
data class ValidationLimitsDto(
    @SerialName("weight_g") val weightG: BoundsDto,
    @SerialName("item_count") val itemCount: BoundsDto,
    @SerialName("name_length") val nameLength: BoundsDto,
    @SerialName("email_max_length") val emailMaxLength: Int,
    @SerialName("password_length") val passwordLength: BoundsDto,
    @SerialName("notes_max_length") val notesMaxLength: Int,
    @SerialName("activity_date_past_days") val activityDatePastDays: Int,
    @SerialName("kg_max_decimal_places") val kilogramMaxDecimalPlaces: Int,
) {
    fun toModel(): ValidationLimits {
        require(emailMaxLength in 1..254 && notesMaxLength in 0..500)
        require(activityDatePastDays in 0..30 && kilogramMaxDecimalPlaces in 0..3)
        return ValidationLimits(weightG.within(1..100_000), itemCount.within(1..10_000),
            nameLength.within(2..60), emailMaxLength, passwordLength.within(12..128),
            notesMaxLength, activityDatePastDays, kilogramMaxDecimalPlaces)
    }
}

@Serializable
data class WasteLogDto(
    val id: String,
    val category: String,
    val method: String,
    @SerialName("quantity_kind") val quantityKind: String,
    @SerialName("weight_g") val weightG: Int?,
    @SerialName("item_count") val itemCount: Int?,
    @SerialName("activity_date") val activityDate: String,
    @SerialName("activity_timezone") val activityTimezone: String,
    val notes: String,
    val version: Int,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("photo_id") val photoId: String? = null,
) {
    fun toModel(): WasteLog {
        require(uuid.matches(id) && version >= 1 && notes.length <= 500)
        require(photoId == null || uuid.matches(photoId))
        require(activityTimezone in ZoneId.getAvailableZoneIds())
        val quantity = requireNotNull(Quantity.fromFields(QuantityKind.valueOf(quantityKind), weightG, itemCount))
        return WasteLog(id, WasteCategory.valueOf(category), DisposalMethod.valueOf(method), quantity,
            LocalDate.parse(activityDate), ZoneId.of(activityTimezone), notes, version,
            Instant.parse(createdAt), Instant.parse(updatedAt), photoId = photoId)
    }
}

private val uuid = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
