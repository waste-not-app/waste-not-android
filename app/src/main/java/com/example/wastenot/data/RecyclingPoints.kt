package com.example.wastenot.data

import com.example.wastenot.R
import com.example.wastenot.model.RecyclingPoint
import com.example.wastenot.model.RecyclingPointSource

object RecyclingPoints {
    private val collectionSource = RecyclingPointSource(
        R.string.recycling_source_collection,
        "https://www.jakarta.go.id/e-waste",
    )
    private val serviceSource = RecyclingPointSource(
        R.string.recycling_source_service,
        "https://lingkunganhidup.jakarta.go.id/layanan/ewaste",
    )

    // Source check is a web-document check, not an on-site inspection or a promise of acceptance.
    val all: List<RecyclingPoint> = listOf(
        RecyclingPoint(
            id = "jakarta-balai-kota-ewaste",
            name = R.string.recycling_balai_kota_name,
            address = R.string.recycling_balai_kota_address,
            sources = listOf(
                collectionSource,
                serviceSource,
                RecyclingPointSource(R.string.recycling_source_address, "https://www.jakarta.go.id/wisata-bersejarah"),
            ),
            sourceCheckedOn = "2026-11-16",
        ),
        RecyclingPoint(
            id = "jakarta-barat-walikota-ewaste",
            name = R.string.recycling_jakbar_name,
            address = R.string.recycling_jakbar_address,
            sources = listOf(
                collectionSource,
                serviceSource,
                RecyclingPointSource(R.string.recycling_source_address, "https://barat.jakarta.go.id/"),
            ),
            sourceCheckedOn = "2026-11-16",
        ),
        RecyclingPoint(
            id = "jakarta-timur-walikota-ewaste",
            name = R.string.recycling_jaktim_name,
            address = R.string.recycling_jaktim_address,
            sources = listOf(
                collectionSource,
                serviceSource,
                RecyclingPointSource(R.string.recycling_source_address, "https://timur.jakarta.go.id/"),
            ),
            sourceCheckedOn = "2026-11-16",
        ),
    )

    fun find(id: String): RecyclingPoint? = all.firstOrNull { it.id == id }
}
