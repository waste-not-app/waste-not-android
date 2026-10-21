package com.example.wastenot.model

import java.net.URI
import java.time.LocalDate

data class GuideReview(val source: URI, val date: LocalDate) {
    init {
        require(source.scheme.equals("https", ignoreCase = true) && !source.host.isNullOrBlank() && source.userInfo == null)
    }
}

data class WasteGuide(
    val id: String,
    val name: String,
    val category: WasteCategory,
    val method: DisposalMethod,
    val keywords: List<String>,
    val summary: String,
    val steps: List<String>,
    val cautions: List<String>,
    val review: GuideReview? = null,
)
