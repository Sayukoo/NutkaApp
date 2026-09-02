package com.nutka.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val NutkaColorScheme = lightColorScheme(
    primary = NutkaColors.accent,
    onPrimary = NutkaColors.bg,
    primaryContainer = NutkaColors.accent200,
    onPrimaryContainer = NutkaColors.accent800,
    secondary = NutkaColors.accent2,
    onSecondary = NutkaColors.bg,
    secondaryContainer = NutkaColors.accent2_100,
    onSecondaryContainer = NutkaColors.accent2_800,
    background = NutkaColors.bg,
    onBackground = NutkaColors.text,
    surface = NutkaColors.surface,
    onSurface = NutkaColors.text,
    surfaceVariant = NutkaColors.neutral200,
    onSurfaceVariant = NutkaColors.text,
    surfaceTint = Color.Transparent,
    outline = NutkaColors.divider,
    outlineVariant = NutkaColors.divider,
    error = NutkaColors.accent700
)

private val NutkaShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun NutkaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NutkaColorScheme,
        typography = NutkaTypography,
        shapes = NutkaShapes,
        content = content
    )
}
