package com.example.wastenot.data.network.dto

import com.example.wastenot.model.PhotoAttachment
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PhotoDto(
    val id: String,
    @SerialName("mime_type") val mimeType: String,
    @SerialName("size_bytes") val sizeBytes: Int,
    val status: String,
    @SerialName("preview_url") val previewUrl: String,
    @SerialName("created_at") val createdAt: String,
) {
    fun toModel(): PhotoAttachment {
        require(photoUuidRegex.matches(id) && sizeBytes in 1..5_242_880)
        return PhotoAttachment(
            id = id,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            previewUrl = previewUrl,
            createdAt = Instant.parse(createdAt),
        )
    }
}

private val photoUuidRegex = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
