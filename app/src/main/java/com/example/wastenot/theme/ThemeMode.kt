package com.example.wastenot.theme

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
