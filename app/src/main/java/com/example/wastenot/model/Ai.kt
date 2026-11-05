package com.example.wastenot.model

import java.net.URI
import java.time.Instant
import java.util.UUID

enum class AiGuideStatus { DRAFT, PUBLISHED, UNAVAILABLE }
enum class AiConfidenceBand { OFFERED, UNCERTAIN, MANUAL_REQUIRED }
enum class AiDetectionStatus { REVIEW_REQUIRED, MANUAL_REQUIRED }
enum class AiRecommendationStatus { READY, NEEDS_CONTEXT, UNAVAILABLE }
enum class AiRecommendationBasis { CONFIRMED, CONDITIONAL }
enum class AiRecommendationScope { PREVENTION, HANDLING }
enum class AiInputSource { DETECTION, MANUAL }
enum class AiAnswer { YES, NO, UNKNOWN }
enum class AiSoiling { FOOD_DRINK, DUST, HAZARDOUS, UNKNOWN }
enum class AiFormerContents { FOOD_DRINK, CHEMICAL, OTHER, UNKNOWN }
enum class AiRiskType { UNKNOWN, SHARP, CHEMICAL, BIOLOGICAL, ELECTRICAL, OTHER }
enum class AiContextField(val wireName: String) {
    CLEANLINESS("condition.cleanliness"), INTEGRITY("condition.integrity"),
    REUSABLE("condition.reusable"), CLEANABLE("condition.cleanable"), SOILING("condition.soiling"),
    FORMER_CONTENTS("condition.former_contents"), REUSE_INTENTION("reuse_intention"),
    RECYCLING_ACCESS("recycling_access"), RISK_TYPE("risk_type"),
}

data class AiThresholds(val lower: Double, val offered: Double) {
    init { require(lower.isFinite() && offered.isFinite() && lower in 0.0..1.0 && offered in lower..1.0) }
}

data class AiCatalogItem(val key: String, val label: String, val category: WasteCategory,
    val specialHandling: Boolean, val guideStatus: AiGuideStatus)

data class AiCatalog(val version: String, val items: List<AiCatalogItem>,
    val modelVersion: String?, val mappingVersion: String?, val reviewProfile: String,
    val classThresholds: Map<String, AiThresholds>, val reviewOnlyClasses: Set<String>,
    val supportedObjectLabels: Map<String, String>)

data class AiDetectionObject(val id: String, val classId: Int, val rawClass: String,
    val confidence: Double, val bounds: ScanBounds, val band: AiConfidenceBand,
    val requiresMaterialConfirmation: Boolean, val supportedForReview: Boolean,
    val suggestedObjectKeys: List<String>, val reviewReasons: List<String>,
    val ambiguousWith: List<String>, val reviewThresholds: AiThresholds?)

data class AiDetection(val id: String, val expiresAt: Instant, val status: AiDetectionStatus,
    val modelVersion: String, val mappingVersion: String, val reviewProfile: String,
    val thresholds: AiThresholds, val width: Int, val height: Int, val objects: List<AiDetectionObject>)

data class AiCondition(val cleanliness: Cleanliness = Cleanliness.UNKNOWN,
    val integrity: Integrity = Integrity.UNKNOWN, val reusable: AiAnswer = AiAnswer.UNKNOWN,
    val cleanable: AiAnswer = AiAnswer.UNKNOWN, val soiling: AiSoiling = AiSoiling.UNKNOWN,
    val formerContents: AiFormerContents = AiFormerContents.UNKNOWN)

data class AiRecommendationRequest(val requestId: String, val confirmedObject: Boolean,
    val objectKey: String, val category: WasteCategory, val inputSource: AiInputSource,
    val detectionId: String? = null, val objectId: String? = null, val contextRevision: Int = 1,
    val condition: AiCondition = AiCondition(), val reuseIntention: Boolean? = null,
    val recyclingAccess: AiAnswer = AiAnswer.UNKNOWN, val riskFlag: Boolean = false,
    val riskType: AiRiskType = AiRiskType.UNKNOWN, val allowConditional: Boolean = true) {
    init {
        requireAiUuid(requestId)
        require(confirmedObject && objectKey.isNotBlank() && objectKey.length <= 60 && contextRevision >= 1)
        if (inputSource == AiInputSource.DETECTION) {
            requireAiUuid(requireNotNull(detectionId))
            require(!objectId.isNullOrBlank() && objectId.length <= 100)
        } else require(detectionId == null && objectId == null)
    }
}

data class AiGuideReference(val id: String, val version: Int)
data class AiLogProvenance(val recommendationId: String, val category: WasteCategory) {
    init { requireAiUuid(recommendationId) }
}
data class AiSourceReference(val url: URI, val title: String, val publisher: String,
    val region: String, val checkedAt: String?, val scopeNote: String)

data class AiRecommendation(val id: String, val expiresAt: Instant,
    val status: AiRecommendationStatus, val detectionId: String?, val objectId: String?,
    val contextRevision: Int, val objectKey: String, val category: WasteCategory,
    val method: DisposalMethod?, val scope: AiRecommendationScope?, val basis: AiRecommendationBasis?,
    val conditionsToVerify: List<AiContextField>, val missingContext: List<AiContextField>,
    val reason: String, val reasonCode: String?, val specialHandling: Boolean, val caution: String,
    val provider: String, val prototypeOnly: Boolean, val version: String,
    val evidenceIds: List<String>, val sources: List<URI>, val sourceDetails: List<AiSourceReference>,
    val guideRefs: List<AiGuideReference>, val steps: List<String>) {
    val canUseForTracking: Boolean get() = status == AiRecommendationStatus.READY &&
        scope == AiRecommendationScope.HANDLING && method != null && method != DisposalMethod.REDUCE && !prototypeOnly
}

internal fun requireAiUuid(value: String) {
    val canonical = try { UUID.fromString(value).toString() } catch (_: IllegalArgumentException) { null }
    require(canonical != null && canonical.equals(value, ignoreCase = true))
}

internal fun aiSourceUri(value: String): URI = URI.create(value).also {
    require(it.scheme.equals("https", ignoreCase = true) && !it.host.isNullOrBlank() && it.userInfo == null)
}
