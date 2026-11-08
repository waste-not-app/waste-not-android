package com.example.wastenot.data.network.dto

import com.example.wastenot.data.repository.ActivityInput
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal fun ActivityInput.toRequestFields(): JsonObject = buildJsonObject {
    put("category", category.name)
    put("method", method.name)
    quantity.toRequestFields().forEach { (name, value) -> put(name, value) }
    put("activity_date", date.toString())
    put("notes", notes)
}
