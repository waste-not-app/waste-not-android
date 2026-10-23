package com.example.wastenot.model

data class ScanPhoto(val path: String, val width: Int, val height: Int, val createdAt: Long)

data class ScanBounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    init {
        require(listOf(left, top, right, bottom).all { it.isFinite() && it in 0f..1f })
        require(left < right && top < bottom)
    }
}

data class ScanObject(val id: String, val name: String, val score: Float, val bounds: ScanBounds,
    val category: WasteCategory? = null, val specialHandling: Boolean = false) {
    init { require(id.isNotBlank() && name.isNotBlank() && score.isFinite() && score in 0f..1f) }
}

enum class Cleanliness { UNKNOWN, CLEAN, DIRTY }
enum class Integrity { UNKNOWN, INTACT, DAMAGED }
enum class Reusability { UNKNOWN, YES, NO }
data class ScanContext(val cleanliness: Cleanliness = Cleanliness.UNKNOWN,
    val integrity: Integrity = Integrity.UNKNOWN, val reusable: Reusability = Reusability.UNKNOWN,
    val reuseIntention: Boolean? = null)

data class ScanRecommendation(val method: DisposalMethod?, val reason: String,
    val guideTitle: String, val guideVersion: String, val steps: List<String>, val cautions: List<String>) {
    init { require(method != DisposalMethod.REDUCE) }
}

data class ScanPrefill(val category: WasteCategory?, val method: DisposalMethod?) {
    init { require(method == null || category != null); require(method != DisposalMethod.REDUCE) }
}

interface ScanPhotoStore {
    fun exists(photo: ScanPhoto): Boolean
    fun delete(photo: ScanPhoto)
}
