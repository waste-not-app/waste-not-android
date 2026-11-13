package com.example.wastenot.model

import java.time.Instant

enum class AchievementRequirement {
    ACTIVE_LOG_COUNT,
    RECYCLE_LOG_COUNT,
    BEST_STREAK,
}

data class Achievement(
    val id: String,
    val code: String,
    val title: String,
    val description: String,
    val requirementType: AchievementRequirement,
    val currentValue: Long,
    val targetValue: Long,
    val isUnlocked: Boolean,
    val unlockedAt: Instant?,
)
