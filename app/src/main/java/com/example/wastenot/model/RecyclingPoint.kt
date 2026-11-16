package com.example.wastenot.model

import androidx.annotation.StringRes

data class RecyclingPoint(
    val id: String,
    @param:StringRes val name: Int,
    @param:StringRes val address: Int,
    val sources: List<RecyclingPointSource>,
    val sourceCheckedOn: String,
)

data class RecyclingPointSource(
    @param:StringRes val label: Int,
    val url: String,
)
