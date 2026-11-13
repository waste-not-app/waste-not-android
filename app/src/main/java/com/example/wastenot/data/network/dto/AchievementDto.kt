package com.example.wastenot.data.network.dto

import com.example.wastenot.model.Achievement
import com.example.wastenot.model.AchievementRequirement
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AchievementDto(
    val id: String,
    val code: String,
    val title: String,
    val description: String,
    @SerialName("requirement_type") val requirementType: String,
    @SerialName("current_value") val currentValue: Long,
    @SerialName("target_value") val targetValue: Long,
    @SerialName("is_unlocked") val isUnlocked: Boolean,
    @SerialName("unlocked_at") val unlockedAt: String? = null,
) {
    fun toModel(): Achievement {
        require(uuidRegex.matches(id) && code.isNotBlank() && title.isNotBlank())
        require(targetValue > 0 && currentValue in 0..targetValue)
        val parsedUnlockedAt = unlockedAt?.let(Instant::parse)
        require(isUnlocked == (parsedUnlockedAt != null))
        return Achievement(
            id = id,
            code = code,
            title = title,
            description = description,
            requirementType = AchievementRequirement.valueOf(requirementType),
            currentValue = currentValue,
            targetValue = targetValue,
            isUnlocked = isUnlocked,
            unlockedAt = parsedUnlockedAt,
        )
    }
}

private val uuidRegex = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
