package com.example.wastenot.model

data class ValidationLimits(
    val weightGrams: IntRange,
    val itemCount: IntRange,
    val nameLength: IntRange,
    val emailMaxLength: Int,
    val passwordLength: IntRange,
    val notesMaxLength: Int,
    val activityDatePastDays: Int,
    val kilogramMaxDecimalPlaces: Int,
)

data class Metadata(
    val categories: List<WasteCategory>,
    val methods: List<DisposalMethod>,
    val limits: ValidationLimits,
    val capabilities: Map<String, Boolean>,
    val aiObjectCatalogVersion: String?,
) {
    fun isEnabled(capability: String): Boolean = capabilities[capability] == true
}
