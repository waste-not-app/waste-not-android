package com.example.wastenot.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.wastenot.R

val PlusJakartaSans = FontFamily(
    Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
    Font(R.font.plus_jakarta_sans_extrabold, FontWeight.ExtraBold),
)

private fun role(size: Int, height: Int, weight: FontWeight, tracking: Float = 0f) = TextStyle(
    fontFamily = PlusJakartaSans,
    fontSynthesis = FontSynthesis.None,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = height.sp,
    letterSpacing = tracking.sp,
    fontFeatureSettings = "tnum",
)

val WasteNotWordmark = role(29, 36, FontWeight.ExtraBold, -1.3f)
val WasteNotAuthHeading = role(30, 36, FontWeight.Bold, -0.5f)
val WasteNotDialogHeading = role(24, 29, FontWeight.ExtraBold, -1.3f)

val Typography = Typography(
    displayLarge = role(48, 56, FontWeight.ExtraBold, -1f),
    displayMedium = role(36, 44, FontWeight.ExtraBold, -0.5f),
    displaySmall = role(32, 40, FontWeight.Bold, -0.5f),
    headlineLarge = role(28, 36, FontWeight.Bold, -0.5f),
    headlineMedium = role(24, 32, FontWeight.Bold, -0.5f),
    headlineSmall = role(20, 28, FontWeight.Bold),
    titleLarge = role(18, 26, FontWeight.Bold),
    titleMedium = role(16, 24, FontWeight.SemiBold),
    titleSmall = role(14, 20, FontWeight.SemiBold),
    bodyLarge = role(16, 24, FontWeight.Normal),
    bodyMedium = role(14, 22, FontWeight.Normal),
    bodySmall = role(12, 18, FontWeight.Normal),
    labelLarge = role(14, 20, FontWeight.Bold),
    labelMedium = role(12, 18, FontWeight.SemiBold),
    labelSmall = role(11, 16, FontWeight.SemiBold),
)
