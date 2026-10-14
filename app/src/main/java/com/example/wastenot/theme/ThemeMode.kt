package com.example.wastenot.theme

import android.content.SharedPreferences
import androidx.annotation.StringRes
import com.example.wastenot.R

enum class ThemeMode(@param:StringRes val label: Int) {
    SYSTEM(R.string.theme_system), LIGHT(R.string.theme_light), DARK(R.string.theme_dark);

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }
}

class ThemePreferences(private val preferences: SharedPreferences) {
    fun read(): ThemeMode = ThemeMode.entries.firstOrNull {
        it.name == preferences.getString("theme", null)
    } ?: ThemeMode.SYSTEM

    fun write(mode: ThemeMode) {
        preferences.edit().putString("theme", mode.name).apply()
    }
}
