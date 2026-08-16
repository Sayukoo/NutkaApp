package com.nutka.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nutka.app.Screen
import com.nutka.app.ui.theme.NutkaColors

@Composable
fun BottomNav(current: Screen, onSelect: (Screen) -> Unit) {
    Column(Modifier.fillMaxWidth().background(NutkaColors.bg)) {
        HorizontalDivider(color = NutkaColors.divider, thickness = 1.dp)
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp, start = 12.dp, end = 12.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            NavItem("Nagrywaj", Icons.Default.Mic, current == Screen.RECORD) { onSelect(Screen.RECORD) }
            NavItem("Nagrania", Icons.Default.List, current == Screen.LIST) { onSelect(Screen.LIST) }
            NavItem("Ustawienia", Icons.Default.Settings, current == Screen.SETTINGS) { onSelect(Screen.SETTINGS) }
        }
    }
}

@Composable
private fun NavItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) NutkaColors.accent else NutkaColors.text.copy(alpha = 0.45f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 6.dp, horizontal = 16.dp)
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.padding(bottom = 2.dp))
        Text(label, fontSize = 10.5.sp, color = color)
    }
}
