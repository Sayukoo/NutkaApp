package com.nutka.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.nutka.app.R

// Calistoga — chunky warm display face with full Polish character support (ą, ć, ę, ł, ń, ó, ś, ź, ż)
val NutkaHeadingFont = FontFamily(
    Font(R.font.calistoga_regular, FontWeight.Normal)
)

// Figtree — body face, matches --font-body
val NutkaBodyFont = FontFamily(
    Font(R.font.figtree_regular, FontWeight.Normal),
    Font(R.font.figtree_semibold, FontWeight.SemiBold),
    Font(R.font.figtree_bold, FontWeight.Bold)
)

/** Tabular digits so the recording timer never jitters sideways. */
val NutkaTimerStyle = TextStyle(
    fontFamily = NutkaBodyFont,
    fontWeight = FontWeight.Bold,
    fontSize = 34.sp,
    letterSpacing = 0.5.sp,
    fontFeatureSettings = "tnum"
)

private val centerTrim = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)

val NutkaTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = NutkaHeadingFont, fontSize = 32.sp, lineHeight = 38.sp,
        lineHeightStyle = centerTrim
    ),
    headlineMedium = TextStyle(
        fontFamily = NutkaHeadingFont, fontSize = 26.sp, lineHeight = 32.sp,
        lineHeightStyle = centerTrim
    ),
    headlineSmall = TextStyle(
        fontFamily = NutkaHeadingFont, fontSize = 23.sp, lineHeight = 29.sp,
        lineHeightStyle = centerTrim
    ),
    titleLarge = TextStyle(fontFamily = NutkaHeadingFont, fontSize = 20.sp, lineHeight = 25.sp),
    titleMedium = TextStyle(
        fontFamily = NutkaHeadingFont, fontSize = 17.sp, lineHeight = 22.sp,
        letterSpacing = 0.1.sp
    ),
    titleSmall = TextStyle(fontFamily = NutkaHeadingFont, fontSize = 14.5.sp, lineHeight = 19.sp),
    bodyLarge = TextStyle(fontFamily = NutkaBodyFont, fontSize = 15.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = NutkaBodyFont, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = NutkaBodyFont, fontSize = 12.5.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(
        fontFamily = NutkaBodyFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
        letterSpacing = 0.2.sp
    ),
    labelMedium = TextStyle(fontFamily = NutkaBodyFont, fontSize = 12.sp, letterSpacing = 0.15.sp),
    labelSmall = TextStyle(fontFamily = NutkaBodyFont, fontSize = 10.5.sp, letterSpacing = 0.2.sp)
)
