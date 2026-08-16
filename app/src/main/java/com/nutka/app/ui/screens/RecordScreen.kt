package com.nutka.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nutka.app.ui.components.PulseRing
import com.nutka.app.ui.components.Tag
import com.nutka.app.ui.components.TagStyle
import com.nutka.app.ui.components.WaveformBars
import com.nutka.app.ui.theme.NutkaColors

@Composable
fun RecordScreen(
    isRecording: Boolean,
    isPaused: Boolean,
    elapsedLabel: String,
    bookmarkCount: Int,
    backgroundRecordingEnabled: Boolean,
    onToggleRecord: () -> Unit,
    onTogglePause: () -> Unit,
    onAddBookmark: () -> Unit,
    onImport: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text("Transkrypcja", style = MaterialTheme.typography.headlineMedium)
            Text("Dziś", fontSize = 12.sp, color = NutkaColors.text.copy(alpha = 0.5f))
        }

        if (!isRecording && backgroundRecordingEnabled) {
            Tag(
                text = "Nagrywa w tle i na baterii",
                style = TagStyle.OUTLINE,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(26.dp)) {
                Box(Modifier.height(36.dp), contentAlignment = Alignment.Center) {
                    if (isRecording) {
                        WaveformBars()
                    } else {
                        Text("Gotowa do nagrania", fontSize = 13.sp, color = NutkaColors.text.copy(alpha = 0.5f))
                    }
                }

                Box(contentAlignment = Alignment.Center) {
                    if (isRecording) PulseRing()
                    Box(
                        Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(NutkaColors.accent)
                    ) {
                        IconButton(onClick = onToggleRecord, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = if (isRecording) "Zatrzymaj nagrywanie" else "Rozpocznij nagrywanie",
                                tint = NutkaColors.bg,
                                modifier = Modifier.size(if (isRecording) 26.dp else 34.dp)
                            )
                        }
                    }
                }

                Text(elapsedLabel, style = MaterialTheme.typography.headlineLarge)

                if (isRecording) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = onTogglePause,
                            shape = MaterialTheme.shapes.extraLarge,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NutkaColors.text),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NutkaColors.divider)
                        ) {
                            Icon(
                                imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                if (isPaused) "Wznów" else "Pauza",
                                modifier = Modifier.padding(start = 6.dp),
                                fontSize = 13.sp
                            )
                        }
                        OutlinedButton(
                            onClick = onAddBookmark,
                            shape = MaterialTheme.shapes.extraLarge,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NutkaColors.text),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NutkaColors.divider)
                        ) {
                            Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(14.dp))
                            if (bookmarkCount > 0) {
                                Text("$bookmarkCount", modifier = Modifier.padding(start = 6.dp), fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        if (!isRecording) {
            Button(
                onClick = onImport,
                modifier = Modifier.fillMaxWidth().padding(bottom = 22.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = ButtonDefaults.buttonColors(containerColor = NutkaColors.surface, contentColor = NutkaColors.text)
            ) {
                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(17.dp))
                Text("Importuj plik audio", modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp))
            }
        }
    }
}
