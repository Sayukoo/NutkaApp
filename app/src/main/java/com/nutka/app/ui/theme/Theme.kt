package com.nutka.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val NutkaColorScheme = lightColorScheme(
    primary = NutkaColors.accent,
    onPrimary = NutkaColors.bg,
    secondary = NutkaColors.accent2,
    onSecondary = NutkaColors.bg,
    background = NutkaColors.bg,
    onBackground = NutkaColors.text,
    surface = NutkaColors.surface,
    onSurface = NutkaColors.text,
    surfaceVariant = NutkaColors.neutral200,
    onSurfaceVariant = NutkaColors.text,
    outline = NutkaColors.divider,
    error = NutkaColors.accent700
)

private val NutkaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
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
