package com.nutka.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.nutka.app.R

// Caprasimo — chunky display face used for headings/titles, matches --font-heading
val NutkaHeadingFont = FontFamily(
    Font(R.font.caprasimo_regular, FontWeight.Normal)
)

// Figtree — body face, matches --font-body
val NutkaBodyFont = FontFamily(
    Font(R.font.figtree_regular, FontWeight.Normal),
    Font(R.font.figtree_semibold, FontWeight.SemiBold),
    Font(R.font.figtree_bold, FontWeight.Bold)
)

val NutkaTypography = Typography(
    headlineLarge = TextStyle(fontFamily = NutkaHeadingFont, fontSize = 32.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = NutkaHeadingFont, fontSize = 26.sp, lineHeight = 30.sp),
    headlineSmall = TextStyle(fontFamily = NutkaHeadingFont, fontSize = 23.sp, lineHeight = 27.sp),
    titleLarge = TextStyle(fontFamily = NutkaHeadingFont, fontSize = 20.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = NutkaHeadingFont, fontSize = 17.sp, lineHeight = 21.sp),
    titleSmall = TextStyle(fontFamily = NutkaHeadingFont, fontSize = 14.5.sp, lineHeight = 18.sp),
    bodyLarge = TextStyle(fontFamily = NutkaBodyFont, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = NutkaBodyFont, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = NutkaBodyFont, fontSize = 12.5.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = NutkaBodyFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = NutkaBodyFont, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = NutkaBodyFont, fontSize = 10.5.sp)
)
