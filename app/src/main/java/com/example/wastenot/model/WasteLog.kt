package com.example.wastenot.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class WasteCategory { ORGANIC, PLASTIC, PAPER, GLASS, METAL, ELECTRONIC, OTHER }

enum class DisposalMethod { REDUCE, REUSE, RECYCLE, TRASH }

enum class QuantityKind { WEIGHT, COUNT }

enum class ActivitySyncState { SYNCED, PENDING, CONFLICT, FAILED }

sealed interface Quantity {
    val kind: QuantityKind

    @JvmInline
    value class Weight private constructor(val grams: Int) : Quantity {
        override val kind: QuantityKind get() = QuantityKind.WEIGHT

        companion object {
            fun of(grams: Int): Weight {
                require(grams in 1..100_000)
                return Weight(grams)
            }
        }
    }

    @JvmInline
    value class Count private constructor(val items: Int) : Quantity {
        override val kind: QuantityKind get() = QuantityKind.COUNT

        companion object {
            fun of(items: Int): Count {
                require(items in 1..10_000)
                return Count(items)
            }
        }
    }

    companion object {
        fun fromFields(kind: QuantityKind, weightG: Int?, itemCount: Int?): Quantity? = when {
            kind == QuantityKind.WEIGHT && weightG != null && weightG in 1..100_000 &&
                itemCount == null -> Weight.of(weightG)
            kind == QuantityKind.COUNT && itemCount != null && itemCount in 1..10_000 &&
                weightG == null -> Count.of(itemCount)
            else -> null
        }
    }
}

data class WasteLog(
    val id: String,
    val category: WasteCategory,
    val method: DisposalMethod,
    val quantity: Quantity,
    val activityDate: LocalDate,
    val activityTimezone: ZoneId,
    val notes: String,
    val version: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    val syncState: ActivitySyncState = ActivitySyncState.SYNCED,
    val photoId: String? = null,
)
