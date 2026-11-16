package com.example.wastenot.data.repository

import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.model.Achievement

interface AchievementStore {
    suspend fun achievements(): ApiResult<List<Achievement>>
}
