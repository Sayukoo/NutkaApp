package com.nutka.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.nutka.app.ui.theme.NutkaColors

private fun Modifier.settingsSurface() =
    this.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(NutkaColors.surface)

/**
 * Compact settings row: leading icon, title, trailing control. The long
 * explanation is intentionally NOT printed under the title — it lives in
 * [tooltip] and appears only while hovered (mouse) or long-pressed (touch).
 */
@Composable
fun SettingsToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tooltip: String? = null
) {
    val row: @Composable () -> Unit = {
        Row(
            modifier
                .settingsSurface()
                .clickable { onCheckedChange(!checked) }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) SettingIcon(icon)
            Column(Modifier.weight(1f).padding(start = if (icon != null) 12.dp else 0.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = NutkaColors.bg,
                    checkedTrackColor = NutkaColors.accent,
                    uncheckedThumbColor = NutkaColors.bg,
                    uncheckedTrackColor = NutkaColors.neutral400
                )
            )
        }
    }
    if (tooltip != null) HintTooltip(tooltip) { row() } else row()
}

/**
 * Static (non-toggle) settings row with optional trailing content — used e.g.
 * for the language picker and the log entry.
 */
@Composable
fun SettingsActionRow(
    title: String,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tooltip: String? = null,
    trailing: @Composable () -> Unit = {}
) {
    val row: @Composable () -> Unit = {
        Row(
            modifier
                .settingsSurface()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) SettingIcon(icon)
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f).padding(start = if (icon != null) 12.dp else 0.dp)
            )
            trailing()
        }
    }
    if (tooltip != null) HintTooltip(tooltip) { row() } else row()
}

/** Small rounded square behind a row icon, matching the app's tile look. */
@Composable
fun SettingIcon(icon: ImageVector, tint: androidx.compose.ui.graphics.Color = NutkaColors.accent) {
    Box(
        Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(NutkaColors.bg),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
    }
}

/** Tiny ⓘ affordance marking that a block hides more details behind hover/long-press. */
@Composable
fun TooltipHintMark(modifier: Modifier = Modifier) {
    Icon(
        Icons.Default.Info,
        contentDescription = null,
        tint = NutkaColors.text.copy(alpha = 0.28f),
        modifier = modifier.size(13.dp)
    )
}

@Composable
fun SettingsSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = NutkaColors.text.copy(alpha = 0.5f),
        modifier = modifier.padding(top = 6.dp, bottom = 2.dp, start = 4.dp)
    )
}
