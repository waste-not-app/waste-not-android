package com.example.wastenot.data.repository

import com.example.wastenot.theme.ThemeMode
import kotlinx.coroutines.flow.Flow

interface ThemeStore {
    val modes: Flow<ThemeMode>
    suspend fun write(mode: ThemeMode)
}
