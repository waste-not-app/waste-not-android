package com.example.wastenot.model

import java.time.ZoneId

data class User(
    val id: String,
    val name: String,
    val email: String,
    val timezone: ZoneId,
    val progress: UserProgress?,
)

data class UserProgress(
    val activeLogCount: Int,
    val xp: Int,
    val level: Int,
    val xpIntoLevel: Int,
    val xpToNextLevel: Int,
    val currentStreak: Int?,
    val bestStreak: Int?,
    val hasLoggedToday: Boolean,
)
