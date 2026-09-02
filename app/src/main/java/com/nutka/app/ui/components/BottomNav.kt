package com.nutka.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nutka.app.Screen
import com.nutka.app.ui.theme.NutkaColors

/** Flat 2D nav bar: cream bar on a hairline border, sliding terracotta tab. */
@Composable
fun BottomNav(current: Screen, onSelect: (Screen) -> Unit) {
    val items = listOf(
        Triple(Screen.RECORD, "Nagrywaj", Icons.Default.Mic),
        Triple(Screen.LIST, "Nagrania", Icons.AutoMirrored.Filled.List),
        Triple(Screen.SETTINGS, "Ustawienia", Icons.Default.Settings)
    )
    val tooltips = mapOf(
        Screen.RECORD to "Nagraj nową transkrypcję na żywo",
        Screen.LIST to "Przeglądaj zapisane nagrania i szukaj w transkrypcjach",
        Screen.SETTINGS to "Notion, język, mówcy, klucz ElevenLabs i dziennik zdarzeń"
    )
    val selectedIndex = items.indexOfFirst { it.first == current }.coerceAtLeast(0)
    val pillShape = RoundedCornerShape(24.dp)

    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp)
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(62.dp)
                .clip(pillShape)
                .background(NutkaColors.neutral100)
                .border(1.dp, NutkaColors.divider, pillShape)
        ) {
            val itemWidth = maxWidth / items.size

            // Sliding selected-tab marker, drawn behind the labels
            val indicatorX by animateDpAsState(
                targetValue = itemWidth * selectedIndex,
                animationSpec = spring(
                    dampingRatio = 0.75f,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "navIndicator"
            )
            Box(
                Modifier
                    .offset(x = indicatorX)
                    .width(itemWidth)
                    .fillMaxSize()
                    .padding(5.dp)
                    .clip(RoundedCornerShape(19.dp))
                    .background(NutkaColors.accent100)
                    .border(1.dp, NutkaColors.accent300, RoundedCornerShape(19.dp))
            )

            Row(Modifier.fillMaxSize()) {
                items.forEachIndexed { index, (screen, label, icon) ->
                    NavItem(
                        modifier = Modifier.weight(1f),
                        label = label,
                        icon = icon,
                        tooltip = tooltips[screen] ?: "",
                        selected = index == selectedIndex,
                        onClick = { onSelect(screen) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NavItem(
    modifier: Modifier,
    label: String,
    icon: ImageVector,
    tooltip: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }

    val tint by animateColorAsState(
        targetValue = if (selected) NutkaColors.accent700 else NutkaColors.text.copy(alpha = 0.55f),
        animationSpec = tween(180),
        label = "navTint"
    )

    HintTooltip(tooltip, modifier = modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .clickable(interactionSource = interaction, indication = null, onClick = onClick)
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(if (selected) 23.dp else 21.dp)
            )
            Text(
                label,
                fontSize = 10.5.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = tint,
                lineHeight = 13.sp,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}
