package com.nutka.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nutka.app.data.Recording
import com.nutka.app.data.RecordingStatus
import com.nutka.app.ui.components.Tag
import com.nutka.app.ui.components.TagStyle
import com.nutka.app.ui.theme.NutkaColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

fun formatDuration(sec: Int): String {
    val m = sec / 60
    val s = sec % 60
    return "$m:${s.toString().padStart(2, '0')}"
}

fun formatDateLabel(millis: Long): String {
    val now = System.currentTimeMillis()
    val diffDays = TimeUnit.MILLISECONDS.toDays(now - millis)
    val time = SimpleDateFormat("HH:mm", Locale("pl")).format(Date(millis))
    return when (diffDays) {
        0L -> "dziś, $time"
        1L -> "wczoraj, $time"
        else -> SimpleDateFormat("d MMM, HH:mm", Locale("pl")).format(Date(millis))
    }
}

@Composable
fun RecordingsListScreen(recordings: List<Recording>, onOpen: (String) -> Unit, onDelete: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text(
            "Nagrania",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 22.dp, bottom = 14.dp)
        )
        if (recordings.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Brak nagrań — zacznij od karty Nagrywaj", fontSize = 13.sp, color = NutkaColors.text.copy(alpha = 0.5f))
            }
        } else {
            Text(
                "Przytrzymaj nagranie, aby je usunąć",
                fontSize = 11.sp, color = NutkaColors.text.copy(alpha = 0.4f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(recordings, key = { it.id }) { rec ->
                    RecordingRow(rec, onClick = { onOpen(rec.id) }, onDelete = { onDelete(rec.id) })
                }
                item { Box(Modifier.padding(bottom = 12.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordingRow(rec: Recording, onClick: () -> Unit, onDelete: () -> Unit) {
    var showConfirm by remember { mutableStateOf(false) }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Usunąć nagranie?") },
            text = { Text("„${rec.title?.takeIf { it.isNotBlank() } ?: "Nowe nagranie"}” zostanie trwale usunięte razem z audio.") },
            confirmButton = {
                TextButton(onClick = { showConfirm = false; onDelete() }) {
                    Text("Usuń", color = NutkaColors.accent700)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("Anuluj") }
            }
        )
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(NutkaColors.surface)
            .combinedClickable(onClick = onClick, onLongClick = { showConfirm = true })
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                rec.title?.takeIf { it.isNotBlank() } ?: "Nowe nagranie",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f, fill = false)
            )
            when (rec.status) {
                RecordingStatus.PROCESSING -> Tag("Przetwarzanie", TagStyle.NEUTRAL)
                RecordingStatus.SENDING -> Tag("Wysyłanie", TagStyle.NEUTRAL)
                RecordingStatus.TRANSCRIBED -> Tag("Do wysłania", TagStyle.OUTLINE)
                RecordingStatus.SENT -> Tag("W Notion", TagStyle.ACCENT2)
                RecordingStatus.ERROR -> Tag("Błąd", TagStyle.OUTLINE)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(12.dp), tint = NutkaColors.text.copy(alpha = 0.6f))
            Text(
                "  ${formatDuration(rec.durationSec)}  ·  ${formatDateLabel(rec.createdAtMillis)}",
                fontSize = 12.sp,
                color = NutkaColors.text.copy(alpha = 0.6f)
            )
        }
    }
}
