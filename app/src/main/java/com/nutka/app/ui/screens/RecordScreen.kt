package com.nutka.app.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nutka.app.ui.components.HintTooltip
import com.nutka.app.ui.components.PulseRing
import com.nutka.app.ui.components.Tag
import com.nutka.app.ui.components.TagStyle
import com.nutka.app.ui.components.WaveformBars
import com.nutka.app.ui.theme.NutkaColors
import com.nutka.app.ui.theme.NutkaTimerStyle

@Composable
fun RecordScreen(
    isRecording: Boolean,
    isPaused: Boolean,
    audioLevel: Float,
    elapsedLabel: String,
    bookmarkCount: Int,
    backgroundRecordingEnabled: Boolean,
    onStartRecord: () -> Unit,
    onStopRecord: () -> Unit,
    onCancelRecord: () -> Unit,
    onTogglePause: () -> Unit,
    onAddBookmark: () -> Unit,
    onImport: () -> Unit
) {
    var showCancelConfirm by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    val startRecording = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onStartRecord()
    }
    val stopRecording = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onStopRecord()
    }

    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = { Text("Anulować nagranie?") },
            text = { Text("Trwające nagranie zostanie usunięte i nie zostanie przetranskrybowane.") },
            confirmButton = {
                TextButton(onClick = {
                    showCancelConfirm = false
                    onCancelRecord()
                }) {
                    Text("Odrzuć nagranie", color = NutkaColors.accent700)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirm = false }) {
                    Text("Wróć")
                }
            }
        )
    }

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
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                Box(Modifier.height(46.dp), contentAlignment = Alignment.Center) {
                    if (isRecording) {
                        WaveformBars(audioLevel = audioLevel, isPaused = isPaused)
                    } else {
                        Text("Gotowa do nagrania", fontSize = 13.sp, color = NutkaColors.text.copy(alpha = 0.5f))
                    }
                }

                if (isRecording) {
                    // Live orb with flat outline pulse rings
                    Box(contentAlignment = Alignment.Center) {
                        PulseRing(audioLevel = audioLevel, isPaused = isPaused)

                        RecordOrb(containerAlpha = if (isPaused) 0.82f else 1f) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                tint = NutkaColors.bg,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(elapsedLabel, style = NutkaTimerStyle, color = NutkaColors.text)
                        Tag(
                            text = if (isPaused) "Wstrzymano" else "Nagrywanie…",
                            style = if (isPaused) TagStyle.NEUTRAL else TagStyle.ACCENT
                        )
                    }

                    // Recording Controls: [Anuluj] [Wstrzymaj/Wznów] [Zatrzymaj]
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HintTooltip("Przerwij i usuń trwające nagranie — nic nie zostanie zapisane") {
                            OutlinedButton(
                                onClick = { showCancelConfirm = true },
                                shape = MaterialTheme.shapes.extraLarge,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = NutkaColors.text.copy(alpha = 0.75f)),
                                border = BorderStroke(1.dp, NutkaColors.divider),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Anuluj", modifier = Modifier.size(15.dp))
                                Text("Anuluj", modifier = Modifier.padding(start = 5.dp), fontSize = 13.sp)
                            }
                        }

                        HintTooltip(if (isPaused) "Wznów nagrywanie od tego samego miejsca" else "Zatrzymaj mikrofon na chwilę — czas nagrania też się zatrzyma") {
                            OutlinedButton(
                                onClick = onTogglePause,
                                shape = MaterialTheme.shapes.extraLarge,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = NutkaColors.text),
                                border = BorderStroke(1.dp, NutkaColors.divider),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    if (isPaused) "Wznów" else "Wstrzymaj",
                                    modifier = Modifier.padding(start = 5.dp),
                                    fontSize = 13.sp
                                )
                            }
                        }

                        HintTooltip("Zakończ nagranie i przejdź do transkrypcji. Plik zostanie zapisany na telefonie") {
                            Button(
                                onClick = stopRecording,
                                shape = MaterialTheme.shapes.extraLarge,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NutkaColors.accent,
                                    contentColor = NutkaColors.bg
                                ),
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = "Zatrzymaj", modifier = Modifier.size(16.dp))
                                Text("Zatrzymaj", modifier = Modifier.padding(start = 5.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    HintTooltip(
                        if (bookmarkCount > 0) "Dodaj znacznik czasu w tym miejscu nagrania (masz już $bookmarkCount)"
                        else "Oznacz ważny moment znacznikiem czasu — wyskoczy później na osi transkrypcji"
                    ) {
                        OutlinedButton(
                            onClick = onAddBookmark,
                            shape = MaterialTheme.shapes.extraLarge,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NutkaColors.text),
                            border = BorderStroke(1.dp, NutkaColors.divider),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(14.dp))
                            Text(
                                if (bookmarkCount > 0) "Zakładka ($bookmarkCount)" else "Dodaj zakładkę",
                                modifier = Modifier.padding(start = 6.dp),
                                fontSize = 12.sp
                            )
                        }
                    }
                } else {
                    IdleMicOrb(onClick = startRecording)
                }

                Text(
                    if (!isRecording) "Dotknij, aby rozpocząć" else "",
                    fontSize = 13.sp,
                    color = NutkaColors.text.copy(alpha = 0.5f)
                )
            }
        }

        if (!isRecording) {
            HintTooltip("Wybierz gotowy plik audio z telefonu (MP3, M4A, WAV…) i przetranskrybuj go tak samo jak własne nagranie") {
                Button(
                    onClick = onImport,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 22.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = ButtonDefaults.buttonColors(containerColor = NutkaColors.surface, contentColor = NutkaColors.text),
                    border = BorderStroke(1.dp, NutkaColors.divider)
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(17.dp))
                    Text("Importuj plik audio", modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp))
                }
            }
        }
    }
}

/** The hero button: a flat terracotta disc with a crisp outline ring. */
@Composable
private fun IdleMicOrb(onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pressScale"
    )

    Box(contentAlignment = Alignment.Center) {
        // Flat outline ring around the disc
        Box(
            Modifier
                .size(132.dp)
                .clip(CircleShape)
                .border(1.5.dp, NutkaColors.accent300, CircleShape)
        )

        RecordOrb(modifier = Modifier.scale(pressScale)) {
            IconButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
                interactionSource = interaction,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Rozpocznij nagrywanie",
                    tint = NutkaColors.bg,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

/** Flat 2D disc: solid accent fill with a darker outline — no gloss, no shadow. */
@Composable
private fun RecordOrb(
    modifier: Modifier = Modifier,
    containerAlpha: Float = 1f,
    content: @Composable () -> Unit
) {
    Box(
        modifier
            .size(104.dp)
            .clip(CircleShape)
            .alpha(containerAlpha)
            .background(NutkaColors.accent)
            .border(2.dp, NutkaColors.accent700, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
