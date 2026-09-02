package com.nutka.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nutka.app.ui.theme.NutkaColors

enum class TagStyle { ACCENT, ACCENT2, NEUTRAL, OUTLINE }

@Composable
fun Tag(text: String, style: TagStyle, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    val base = modifier.clip(shape)
    when (style) {
        TagStyle.ACCENT -> Text(
            text, fontSize = 11.sp, color = NutkaColors.accent700,
            modifier = base
                .border(1.dp, NutkaColors.accent300.copy(alpha = 0.8f), shape)
                .background(NutkaColors.accent100)
                .padding(horizontal = 10.dp, vertical = 3.dp)
        )
        TagStyle.ACCENT2 -> Text(
            text, fontSize = 11.sp, color = NutkaColors.accent2_800,
            modifier = base
                .border(1.dp, NutkaColors.accent2_300.copy(alpha = 0.9f), shape)
                .background(NutkaColors.accent2_100)
                .padding(horizontal = 10.dp, vertical = 3.dp)
        )
        TagStyle.NEUTRAL -> Text(
            text, fontSize = 11.sp, color = NutkaColors.neutral800,
            modifier = base
                .border(1.dp, NutkaColors.divider, shape)
                .background(NutkaColors.neutral100)
                .padding(horizontal = 10.dp, vertical = 3.dp)
        )
        TagStyle.OUTLINE -> Text(
            text, fontSize = 11.sp, color = NutkaColors.accent,
            modifier = base.border(1.dp, NutkaColors.accent400, shape).padding(horizontal = 10.dp, vertical = 3.dp)
        )
    }
}
