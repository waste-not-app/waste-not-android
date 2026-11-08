package com.example.wastenot.data.demo

import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.repository.ActivityInput
import com.example.wastenot.model.*
import java.math.BigDecimal
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class DemoActivityStoreTest {
    private fun store() = DemoActivityStore(ZoneId.of("Asia/Jakarta"))

    @Test fun canonicalDatasetKeepsWeightCountAndDenominatorSeparate() = runTest {
        val source = store()
        val records = source.load().value
        val today = demoSummary(records.filter { it.activityDate == source.referenceDate })
        assertEquals(650L, today.handled.weightGrams)
        assertEquals(0L, today.handled.itemCount)
        assertEquals(2L, today.avoided.itemCount)
        assertEquals(2L, today.handledActivityCount)
        assertEquals(BigDecimal("50.0"), today.recyclingRatePercent)
        val week = demoSummary(records)
        assertEquals(1_400L, week.handled.weightGrams)
        assertEquals(5L, week.activityCount)
        assertEquals(4L, week.handledActivityCount)
        assertEquals(BigDecimal("50.0"), week.recyclingRatePercent)
    }

    @Test fun createReplaysOneIdAndRejectsChangedPayload() = runTest {
        val source = store()
        val input = ActivityInput(WasteCategory.OTHER, DisposalMethod.TRASH, Quantity.Count.of(3), source.referenceDate, "contoh")
        val request = UUID.randomUUID().toString()
        val first = source.create(input, request) as ApiResult.Success
        val replay = (source.create(input, request) as ApiResult.Success).value
        assertTrue(first.value.created)
        assertFalse(replay.created)
        assertEquals(first.value.log.id, replay.log.id)
        assertEquals(ApiFailureKind.IDEMPOTENCY_CONFLICT, (source.create(input.copy(notes = "berbeda"), request) as ApiResult.Failure).error.kind)
        val records = source.load().value
        assertEquals(6, records.size)
        assertEquals(first.value.log.id, records.first().id)
        assertEquals(1_400L, demoSummary(records).handled.weightGrams)
        assertEquals(3L, demoSummary(records).handled.itemCount)
    }

    @Test fun editingWeightToCountDoesNotRetainGramsOrAwardANewActivity() = runTest {
        val source = store()
        val original = source.load().value.first { it.quantity == Quantity.Weight.of(250) }
        val input = ActivityInput(original.category, original.method, Quantity.Count.of(2), original.activityDate, "diubah")
        val updated = (source.update(original.id, original.version, input) as ApiResult.Success).value
        assertEquals(Quantity.Count.of(2), updated.quantity)
        assertEquals(2, updated.version)
        assertEquals(ApiFailureKind.VERSION_CONFLICT, (source.update(original.id, original.version, input) as ApiResult.Failure).error.kind)
        val summary = demoSummary(source.load().value)
        assertEquals(5L, summary.activityCount)
        assertEquals(1_150L, summary.handled.weightGrams)
        assertEquals(2L, summary.handled.itemCount)
        assertEquals(2L, summary.avoided.itemCount)
    }

    @Test fun clearingDropsRecordsAndReplayCache() = runTest {
        val source = store()
        val request = UUID.randomUUID().toString()
        val input = ActivityInput(WasteCategory.PLASTIC, DisposalMethod.REDUCE, Quantity.Count.of(1), source.referenceDate, "")
        val first = (source.create(input, request) as ApiResult.Success).value.log.id
        source.clear()
        assertTrue(source.load().value.isEmpty())
        val next = (source.create(input, request) as ApiResult.Success).value.log.id
        assertNotEquals(first, next)
    }

    @Test fun editsKeepCreationTimeAndAdvanceTheDemoUpdateTime() = runTest {
        val source = store()
        val input = ActivityInput(WasteCategory.PLASTIC, DisposalMethod.RECYCLE, Quantity.Weight.of(255), source.referenceDate, "contoh")
        val created = (source.create(input, UUID.randomUUID().toString()) as ApiResult.Success).value.log
        val updated = (source.update(created.id, created.version, input.copy(notes = "koreksi")) as ApiResult.Success).value
        assertEquals(created.createdAt, updated.createdAt)
        assertTrue(updated.updatedAt > created.updatedAt)
        val next = (source.update(updated.id, updated.version, input.copy(notes = "koreksi kedua")) as ApiResult.Success).value
        assertEquals(created.createdAt, next.createdAt)
        assertTrue(next.updatedAt > updated.updatedAt)
        assertEquals(3, next.version)
        assertEquals(6, source.load().value.size)
    }

    @Test fun reduceOnlyHasNoHandledRateAndNoInventedGramConversion() = runTest {
        val source = store()
        val reduce = source.load().value.filter { it.method == DisposalMethod.REDUCE }
        val summary = demoSummary(reduce)
        assertEquals(0L, summary.handled.weightGrams)
        assertEquals(2L, summary.avoided.itemCount)
        assertNull(summary.recyclingRatePercent)
    }
}
