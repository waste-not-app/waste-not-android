package com.example.wastenot.data.network.dto

import com.example.wastenot.model.DisposalMethod
import com.example.wastenot.model.GuideReview
import com.example.wastenot.model.WasteCategory
import com.example.wastenot.model.WasteGuide
import java.net.URI
import java.time.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GuideDto(
    val id: String,
    val name: String,
    val category: String,
    @SerialName("recommended_method") val recommendedMethod: String,
    val keywords: List<String> = emptyList(),
    val summary: String,
    val steps: List<String>,
    val cautions: List<String>,
    @SerialName("source_url") val sourceUrl: String,
    @SerialName("reviewed_at") val reviewedAt: String,
    val reviewer: String? = null,
    @SerialName("publication_status") val publicationStatus: String? = null,
) {
    fun toModel(): WasteGuide {
        require(guideId.matches(id) && id != "00000000-0000-0000-0000-000000000000")
        require(name.isNotBlank() && summary.isNotBlank())
        require(steps.isNotEmpty() && steps.all { it.isNotBlank() })
        require(cautions.all { it.isNotBlank() } && keywords.all { it.isNotBlank() })
        require(publicationStatus == null || publicationStatus == "PUBLISHED")
        return WasteGuide(id, name, WasteCategory.valueOf(category), DisposalMethod.valueOf(recommendedMethod),
            keywords, summary, steps, cautions,
            GuideReview(URI.create(sourceUrl), LocalDate.parse(reviewedAt), reviewer))
    }
}

private val guideId = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
