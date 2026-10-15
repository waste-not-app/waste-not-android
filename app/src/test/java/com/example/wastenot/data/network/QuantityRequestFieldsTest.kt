package com.example.wastenot.data.network

import com.example.wastenot.data.network.dto.toRequestFields
import com.example.wastenot.model.Quantity
import com.example.wastenot.model.QuantityParseResult
import com.example.wastenot.model.QuantityUnit
import com.example.wastenot.model.parseQuantity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class QuantityRequestFieldsTest {
    private val keys = setOf("quantity_kind", "weight_g", "item_count")

    @Test
    fun weightIsAnIntegerInGramsAndExplicitlyClearsCount() {
        listOf(1, 250, 100_000).forEach { grams ->
            val fields = Quantity.Weight.of(grams).toRequestFields()
            assertEquals(keys, fields.keys)
            assertEquals("WEIGHT", fields.getValue("quantity_kind").jsonPrimitive.content)
            assertEquals(grams, fields.getValue("weight_g").jsonPrimitive.int)
            assertFalse(fields.getValue("weight_g").jsonPrimitive.isString)
            assertEquals(JsonNull, fields.getValue("item_count"))
        }
    }

    @Test
    fun countIsAnIntegerAndExplicitlyClearsGrams() {
        listOf(1, 2, 10_000).forEach { items ->
            val fields = Quantity.Count.of(items).toRequestFields()
            assertEquals(keys, fields.keys)
            assertEquals("COUNT", fields.getValue("quantity_kind").jsonPrimitive.content)
            assertEquals(items, fields.getValue("item_count").jsonPrimitive.int)
            assertFalse(fields.getValue("item_count").jsonPrimitive.isString)
            assertEquals(JsonNull, fields.getValue("weight_g"))
        }
    }

    @Test
    fun gramAndBothKilogramSeparatorsProduceTheSameCanonicalFields() {
        val expected = Quantity.Weight.of(250).toRequestFields()
        listOf("250" to QuantityUnit.GRAM, "0,25" to QuantityUnit.KILOGRAM,
            "0.250" to QuantityUnit.KILOGRAM).forEach { (raw, unit) ->
            val quantity = (parseQuantity(raw, unit) as QuantityParseResult.Valid).quantity
            assertEquals(expected, quantity.toRequestFields())
            assertEquals("250", quantity.toRequestFields().getValue("weight_g").toString())
        }
    }

    @Test
    fun switchingKindInAPatchCannotRetainThePreviousQuantity() {
        val before = Quantity.Weight.of(250).toRequestFields()
        val counted = JsonObject(before + Quantity.Count.of(2).toRequestFields())
        assertEquals(Quantity.Count.of(2).toRequestFields(), counted)
        val weighed = JsonObject(counted + Quantity.Weight.of(500).toRequestFields())
        assertEquals(Quantity.Weight.of(500).toRequestFields(), weighed)
    }

    @Test
    fun nullClearsRemainPresentEvenWhenDefaultNullPropertiesAreOmitted() {
        val json = Json { explicitNulls = false }
        listOf(Quantity.Weight.of(250), Quantity.Count.of(2)).forEach { quantity ->
            val fields = quantity.toRequestFields()
            val encoded = json.encodeToString(JsonObject.serializer(), fields)
            assertEquals(fields, Json.parseToJsonElement(encoded).jsonObject)
            assertEquals(keys, Json.parseToJsonElement(encoded).jsonObject.keys)
        }
    }
}
