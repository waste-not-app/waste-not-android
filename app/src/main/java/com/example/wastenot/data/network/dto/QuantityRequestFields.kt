package com.example.wastenot.data.network.dto

import com.example.wastenot.model.Quantity
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// PATCH must clear the old field when the quantity kind changes.
fun Quantity.toRequestFields(): JsonObject = buildJsonObject {
    put("quantity_kind", kind.name)
    when (val quantity = this@toRequestFields) {
        is Quantity.Weight -> {
            put("weight_g", quantity.grams)
            put("item_count", JsonNull)
        }
        is Quantity.Count -> {
            put("weight_g", JsonNull)
            put("item_count", quantity.items)
        }
    }
}
