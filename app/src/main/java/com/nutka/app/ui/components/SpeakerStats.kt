package com.nutka.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nutka.app.data.Segment
import com.nutka.app.ui.theme.NutkaColors

data class SpeakerStat(val role: String, val name: String, val seconds: Int, val percent: Int)

/** How much of the total spoken time (not recording time — silence excluded) each speaker took. */
fun computeSpeakerStats(segments: List<Segment>, speakerNames: Map<String, String>, durationSec: Int): List<SpeakerStat> {
    if (segments.isEmpty()) return emptyList()
    val totalSec = durationSec.coerceAtLeast(1)
    val ends = segmentEndSeconds(segments, totalSec)
    val secondsByRole = linkedMapOf<String, Int>()
    segments.forEachIndexed { idx, seg ->
        val dur = (ends[idx] - seg.timeSec).coerceAtLeast(0)
        secondsByRole[seg.speaker] = (secondsByRole[seg.speaker] ?: 0) + dur
    }
    val totalSpoken = secondsByRole.values.sum().coerceAtLeast(1)
    return secondsByRole.entries.map { (role, sec) ->
        SpeakerStat(role, speakerNames[role] ?: role, sec, sec * 100 / totalSpoken)
    }
}

@Composable
fun SpeakerStatsBar(stats: List<SpeakerStat>, modifier: Modifier = Modifier) {
    if (stats.isEmpty() || stats.size < 2) return
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
        ) {
            stats.forEach { stat ->
                val color = if (stat.role == "a") NutkaColors.accent else NutkaColors.accent2
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .weight(stat.percent.coerceAtLeast(1).toFloat())
                        .fillMaxHeight()
                        .background(color)
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            stats.forEach { stat ->
                val color = if (stat.role == "a") NutkaColors.accent else NutkaColors.accent2
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.layout.Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                    Text("  ${stat.name} ${stat.percent}%", fontSize = 11.5.sp, color = NutkaColors.text.copy(alpha = 0.7f))
                }
            }
        }
    }
}
