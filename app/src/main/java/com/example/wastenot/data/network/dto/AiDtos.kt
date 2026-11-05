package com.example.wastenot.data.network.dto

import com.example.wastenot.model.*
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class AiThresholdsDto(val lower: Double, val offered: Double) {
    fun toModel() = AiThresholds(lower, offered)
}

@Serializable
data class AiCatalogItemDto(@SerialName("object_key") val key: String, val label: String,
    val category: String, @SerialName("special_handling") val specialHandling: Boolean,
    @SerialName("guide_status") val guideStatus: String) {
    fun toModel(): AiCatalogItem {
        require(key.isNotBlank() && key.length <= 60 && label.isNotBlank())
        return AiCatalogItem(key, label, WasteCategory.valueOf(category), specialHandling, AiGuideStatus.valueOf(guideStatus))
    }
}

@Serializable
data class AiCatalogDto(@SerialName("catalog_version") val version: String,
    val items: List<AiCatalogItemDto>, @SerialName("model_version") val modelVersion: String? = null,
    @SerialName("mapping_version") val mappingVersion: String? = null,
    @SerialName("review_profile") val reviewProfile: String = "catalog-default-01",
    @SerialName("class_thresholds") val classThresholds: Map<String, AiThresholdsDto> = emptyMap(),
    @SerialName("review_only_classes") val reviewOnlyClasses: List<String> = emptyList(),
    @SerialName("supported_object_labels") val supportedObjectLabels: Map<String, String> = emptyMap()) {
    fun toModel(): AiCatalog {
        require(version.isNotBlank() && reviewProfile.isNotBlank())
        require(modelVersion == null || modelVersion.isNotBlank())
        require(mappingVersion == null || mappingVersion.isNotBlank())
        val rows = items.map { it.toModel() }
        require(rows.map { it.key }.distinct().size == rows.size)
        require(reviewOnlyClasses.all { it.isNotBlank() } && reviewOnlyClasses.distinct().size == reviewOnlyClasses.size)
        require(supportedObjectLabels.all { it.key.isNotBlank() && it.value.isNotBlank() })
        require(classThresholds.keys.all { it.isNotBlank() })
        return AiCatalog(version, rows, modelVersion, mappingVersion, reviewProfile,
            classThresholds.mapValues { it.value.toModel() }, reviewOnlyClasses.toSet(), supportedObjectLabels)
    }
}

@Serializable
data class AiImageDto(val width: Int, val height: Int)

@Serializable
data class AiDetectionObjectDto(@SerialName("object_id") val id: String,
    @SerialName("class_id") val classId: Int, @SerialName("raw_class") val rawClass: String,
    val confidence: Double, val bbox: List<Double>, @SerialName("confidence_band") val band: String,
    @SerialName("requires_material_confirmation") val requiresMaterialConfirmation: Boolean,
    @SerialName("supported_for_review") val supportedForReview: Boolean,
    @SerialName("suggested_object_keys") val suggestedObjectKeys: List<String>,
    val category: String? = null, @SerialName("object_key") val objectKey: String? = null,
    @SerialName("review_reasons") val reviewReasons: List<String> = emptyList(),
    @SerialName("ambiguous_with") val ambiguousWith: List<String> = emptyList(),
    @SerialName("review_thresholds") val reviewThresholds: AiThresholdsDto? = null) {
    fun toModel(): AiDetectionObject {
        require(id.isNotBlank() && id.length <= 100 && classId >= 0 && rawClass.isNotBlank() && rawClass.length <= 100)
        require(confidence.isFinite() && confidence in 0.0..1.0 && bbox.size == 4)
        require(bbox.all { it.isFinite() && it in 0.0..1.0 })
        require(category == null && objectKey == null)
        require(suggestedObjectKeys.size <= 100 && suggestedObjectKeys.all { it.isNotBlank() })
        require(suggestedObjectKeys.distinct().size == suggestedObjectKeys.size)
        require(reviewReasons.size <= 2 && reviewReasons.all { it in setOf("CLASS_REVIEW_ONLY", "OVERLAPPING_CLASSES") })
        require(reviewReasons.distinct().size == reviewReasons.size && ambiguousWith.size <= 49)
        require(ambiguousWith.all { it.isNotBlank() && it != id } && ambiguousWith.distinct().size == ambiguousWith.size)
        return AiDetectionObject(id, classId, rawClass, confidence, ScanBounds(bbox[0].toFloat(), bbox[1].toFloat(), bbox[2].toFloat(), bbox[3].toFloat()),
            AiConfidenceBand.valueOf(band), requiresMaterialConfirmation, supportedForReview,
            suggestedObjectKeys, reviewReasons, ambiguousWith, reviewThresholds?.toModel())
    }
}

@Serializable
data class AiDetectionDto(@SerialName("detection_id") val id: String,
    @SerialName("expires_at") val expiresAt: String, val status: String,
    @SerialName("model_version") val modelVersion: String,
    @SerialName("mapping_version") val mappingVersion: String,
    @SerialName("review_profile") val reviewProfile: String = "catalog-default-01",
    val thresholds: AiThresholdsDto, val image: AiImageDto, val objects: List<AiDetectionObjectDto>) {
    fun toModel(): AiDetection {
        requireAiUuid(id)
        require(listOf(modelVersion, mappingVersion, reviewProfile).all { it.isNotBlank() && it.length <= 100 })
        require(image.width in 1..4096 && image.height in 1..4096 && objects.size <= 50)
        val rows = objects.map { it.toModel() }
        val ids = rows.map { it.id }.toSet()
        require(ids.size == rows.size && rows.all { row -> row.ambiguousWith.all { it in ids } })
        val limits = thresholds.toModel()
        val byId = rows.associateBy { it.id }
        rows.forEach { row ->
            val rule = row.reviewThresholds ?: limits
            val expected = if (row.supportedForReview) {
                require(row.confidence >= rule.lower && row.suggestedObjectKeys.isNotEmpty())
                if (row.confidence >= rule.offered && row.reviewReasons.isEmpty()) AiConfidenceBand.OFFERED else AiConfidenceBand.UNCERTAIN
            } else AiConfidenceBand.MANUAL_REQUIRED
            require(row.band == expected && row.requiresMaterialConfirmation == row.supportedForReview)
            require(row.supportedForReview || (row.suggestedObjectKeys.isEmpty() && row.reviewReasons.isEmpty()))
            require(row.ambiguousWith.isNotEmpty() == ("OVERLAPPING_CLASSES" in row.reviewReasons))
            row.ambiguousWith.forEach { other -> require(byId.getValue(other).let {
                row.id in it.ambiguousWith && row.classId != it.classId
            }) }
        }
        val state = AiDetectionStatus.valueOf(status)
        require((state == AiDetectionStatus.REVIEW_REQUIRED) == rows.any { it.supportedForReview })
        return AiDetection(id, Instant.parse(expiresAt), state, modelVersion,
            mappingVersion, reviewProfile, limits, image.width, image.height, rows)
    }
}

@Serializable
data class AiGuideReferenceDto(@SerialName("guide_id") val id: String, val version: Int) {
    fun toModel(): AiGuideReference { require(id.isNotBlank() && version >= 1); return AiGuideReference(id, version) }
}

@Serializable
data class AiSourceReferenceDto(val url: String, val title: String, val publisher: String,
    val region: String, @SerialName("checked_at") val checkedAt: String?, @SerialName("scope_note") val scopeNote: String) {
    fun toModel() = AiSourceReference(aiSourceUri(url), title, publisher, region, checkedAt, scopeNote)
}

@Serializable
data class AiRecommendationDto(val status: String, val reason: String, val provider: String,
    @SerialName("prototype_only") val prototypeOnly: Boolean,
    @SerialName("recommendation_id") val id: String, @SerialName("detection_id") val detectionId: String?,
    @SerialName("object_id") val objectId: String?, @SerialName("context_revision") val contextRevision: Int,
    @SerialName("confirmed_object_key") val objectKey: String, val category: String,
    @SerialName("expires_at") val expiresAt: String,
    @SerialName("recommended_method") val method: String? = null,
    @SerialName("recommendation_scope") val scope: String? = null,
    @SerialName("recommendation_basis") val basis: String? = null,
    @SerialName("conditions_to_verify") val conditionsToVerify: List<String> = emptyList(),
    @SerialName("missing_context") val missingContext: List<String> = emptyList(),
    @SerialName("reason_code") val reasonCode: String? = null,
    @SerialName("special_handling") val specialHandling: Boolean = false, val caution: String = "",
    @SerialName("recommendation_version") val version: String = "pilot-policy-06",
    @SerialName("evidence_ids") val evidenceIds: List<String> = emptyList(),
    @SerialName("source_urls") val sourceUrls: List<String> = emptyList(),
    @SerialName("source_details") val sourceDetails: List<AiSourceReferenceDto> = emptyList(),
    @SerialName("guide_refs") val guideRefs: List<AiGuideReferenceDto> = emptyList(), val steps: List<String> = emptyList()) {
    fun toModel(request: AiRecommendationRequest): AiRecommendation {
        requireAiUuid(id)
        detectionId?.let(::requireAiUuid)
        require((detectionId?.equals(request.detectionId, ignoreCase = true) ?: (request.detectionId == null)) &&
            objectId == request.objectId && contextRevision == request.contextRevision)
        require(objectKey == request.objectKey && WasteCategory.valueOf(category) == request.category)
        require(provider.isNotBlank() && version.isNotBlank())
        val state = AiRecommendationStatus.valueOf(status)
        val chosen = method?.let(DisposalMethod::valueOf)
        val range = scope?.let(AiRecommendationScope::valueOf)
        val source = basis?.let(AiRecommendationBasis::valueOf)
        val conditions = conditionsToVerify.map(::contextField)
        val missing = missingContext.map(::contextField)
        require(conditions.distinct().size == conditions.size && missing.distinct().size == missing.size)
        val urls = sourceUrls.map(::aiSourceUri)
        val details = sourceDetails.map { it.toModel() }
        require(details.all { it.url in urls })
        if (state == AiRecommendationStatus.READY) {
            require(chosen != null && range != null && source != null && evidenceIds.isNotEmpty() && urls.isNotEmpty())
            require(evidenceIds.all { it.isNotBlank() } && missing.isEmpty())
            require((chosen == DisposalMethod.REDUCE) == (range == AiRecommendationScope.PREVENTION))
            require((source == AiRecommendationBasis.CONDITIONAL) == conditions.isNotEmpty())
        } else require(chosen == null && range == null && source == null && conditions.isEmpty())
        require((state == AiRecommendationStatus.NEEDS_CONTEXT) == missing.isNotEmpty())
        return AiRecommendation(id, Instant.parse(expiresAt), state, detectionId, objectId, contextRevision,
            objectKey, request.category, chosen, range, source, conditions, missing, reason, reasonCode,
            specialHandling, caution, provider, prototypeOnly, version, evidenceIds, urls, details,
            guideRefs.map { it.toModel() }, steps)
    }
}

private fun contextField(value: String): AiContextField =
    requireNotNull(AiContextField.entries.find { it.wireName == value })

internal fun AiRecommendationRequest.toRequestFields(): JsonObject = buildJsonObject {
    put("client_request_id", requestId); put("confirmed_object", confirmedObject)
    put("confirmed_object_key", objectKey); put("confirmed_category", category.name)
    put("input_source", inputSource.name); put("detection_id", detectionId?.let(::JsonPrimitive) ?: JsonNull)
    put("object_id", objectId?.let(::JsonPrimitive) ?: JsonNull); put("context_revision", contextRevision)
    put("recommendation_goal", "HANDLING"); put("allow_conditional", allowConditional)
    put("reuse_intention", reuseIntention?.let(::JsonPrimitive) ?: JsonNull)
    put("recycling_access", recyclingAccess.name); put("risk_flag", riskFlag); put("risk_type", riskType.name)
    put("condition", buildJsonObject {
        put("cleanliness", condition.cleanliness.name); put("integrity", condition.integrity.name)
        put("reusable", condition.reusable.name); put("cleanable", condition.cleanable.name)
        put("soiling", condition.soiling.name); put("former_contents", condition.formerContents.name)
    })
}
