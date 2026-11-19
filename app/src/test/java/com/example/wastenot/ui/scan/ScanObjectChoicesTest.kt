package com.example.wastenot.ui.scan

import com.example.wastenot.model.*
import org.junit.Assert.*
import org.junit.Test

class ScanObjectChoicesTest {
    private val other = AiCatalogItem("plastic_other", "Other plastic", WasteCategory.PLASTIC, false, AiGuideStatus.UNAVAILABLE)
    private val pet = AiCatalogItem("pet_bottle", "PET bottle", WasteCategory.PLASTIC, false, AiGuideStatus.PUBLISHED)
    private val hdpe = AiCatalogItem("hdpe_bottle", "HDPE bottle", WasteCategory.PLASTIC, false, AiGuideStatus.PUBLISHED)
    private val glass = AiCatalogItem("glass_bottle", "Glass bottle", WasteCategory.GLASS, false, AiGuideStatus.UNAVAILABLE)
    private fun candidate(id: String, hints: List<String>) = AiDetectionObject(id, 39, "bottle", .91,
        ScanBounds(.1f, .1f, .5f, .5f), AiConfidenceBand.OFFERED, true, true, hints, emptyList(), emptyList(), null)
    private fun catalog(items: List<AiCatalogItem>) = AiCatalog("fixture", items, null, null, "fixture", emptyMap(), emptySet(), emptyMap())
    private fun state(hints: List<String>) = ScanState(phase = ScanPhase.OBJECT, category = WasteCategory.PLASTIC,
        catalog = catalog(listOf(other, pet, hdpe, glass)), objects = listOf(candidate("one", hints)), selectedId = "one")

    @Test fun matchingKeysFollowDetectionOrderAndOtherChoicesKeepCatalogOrder() {
        val value = state(listOf(hdpe.key, pet.key))
        assertEquals(listOf(hdpe, pet, other), value.objectChoices)
        assertEquals(listOf(other, pet, hdpe, glass), value.catalog!!.items)
    }

    @Test fun unrelatedUnknownAndDuplicateHintsCannotAddOrRepeatChoices() {
        val value = state(listOf("unknown_key", glass.key, hdpe.key, hdpe.key, pet.key))
        assertEquals(listOf(hdpe, pet, other), value.objectChoices)
        assertEquals(listOf(glass), value.copy(category = WasteCategory.GLASS).objectChoices)
    }

    @Test fun missingOrUnmatchedSuggestionsPreserveTheAvailableCatalogOrder() {
        for (value in listOf(state(emptyList()), state(listOf("unknown_key")), state(listOf(hdpe.key)).copy(selectedId = null))) {
            assertEquals(listOf(other, pet, hdpe), value.objectChoices)
        }
    }

    @Test fun materialOrCatalogMustExistBeforeAnyChoiceIsOffered() {
        val value = state(listOf(hdpe.key))
        assertTrue(value.copy(category = null).objectChoices.isEmpty())
        assertTrue(value.copy(catalog = null).objectChoices.isEmpty())
        assertTrue(value.copy(category = WasteCategory.ELECTRONIC).objectChoices.isEmpty())
    }

    @Test fun selectingAnotherCandidateReordersHintsWithoutChangingAManualChoice() {
        val value = state(listOf(hdpe.key, pet.key)).copy(objectKey = other.key)
        assertEquals(hdpe, value.objectChoices.first())
        assertEquals(other.key, value.objectKey)
        assertFalse(value.objectConfirmed)
        val next = value.copy(objects = value.objects + candidate("two", listOf(other.key, pet.key)), selectedId = "two")
        assertEquals(listOf(other, pet, hdpe), next.objectChoices)
        assertEquals(other.key, next.objectKey)
        assertFalse(next.objectConfirmed)
    }

    @Test fun dynamicCatalogChoicesDoNotDependOnPilotNamesOrPublishedGuides() {
        val choices = (0 until 85).map { AiCatalogItem("dynamic_$it", "Option $it", WasteCategory.PLASTIC, false, AiGuideStatus.UNAVAILABLE) }
        val value = state(listOf("dynamic_84")).copy(catalog = catalog(choices))
        assertEquals(listOf(choices.last()) + choices.dropLast(1), value.objectChoices)
    }
}
