package com.example.wastenot.model

data class ScanPhoto(val path: String, val width: Int, val height: Int, val createdAt: Long)

data class ScanBounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    init {
        require(listOf(left, top, right, bottom).all { it.isFinite() && it in 0f..1f })
        require(left < right && top < bottom)
    }
}

enum class Cleanliness { UNKNOWN, CLEAN, DIRTY }
enum class Integrity { UNKNOWN, INTACT, DAMAGED }
typealias Reusability = AiAnswer
data class ScanContext(val cleanliness: Cleanliness = Cleanliness.UNKNOWN,
    val integrity: Integrity = Integrity.UNKNOWN, val reusable: Reusability = Reusability.UNKNOWN,
    val reuseIntention: Boolean? = null, val cleanable: AiAnswer = AiAnswer.UNKNOWN,
    val soiling: AiSoiling = AiSoiling.UNKNOWN, val formerContents: AiFormerContents = AiFormerContents.UNKNOWN,
    val recyclingAccess: AiAnswer = AiAnswer.UNKNOWN, val riskFlag: Boolean = false,
    val riskType: AiRiskType = AiRiskType.UNKNOWN) {
    fun condition() = AiCondition(cleanliness, integrity, reusable, cleanable, soiling, formerContents)
}

data class ScanPrefill(val category: WasteCategory?, val method: DisposalMethod?, val aiProvenance: AiLogProvenance? = null) {
    init {
        require(method == null || category != null); require(method != DisposalMethod.REDUCE)
        require(aiProvenance == null || (method != null && category == aiProvenance.category))
    }
}

interface ScanPhotoStore {
    fun exists(photo: ScanPhoto): Boolean
    fun delete(photo: ScanPhoto)
    fun read(photo: ScanPhoto): ByteArray
}
