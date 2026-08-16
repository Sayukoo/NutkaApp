package com.nutka.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nutka.app.data.Recording
import com.nutka.app.data.RecordingStatus
import com.nutka.app.data.Segment
import com.nutka.app.ui.components.SpeakerStatsBar
import com.nutka.app.ui.components.SpeakerTimeline
import com.nutka.app.ui.components.Tag
import com.nutka.app.ui.components.TagStyle
import com.nutka.app.ui.components.computeSpeakerStats
import com.nutka.app.ui.components.rememberAudioPlayerState
import com.nutka.app.ui.theme.NutkaColors

enum class ExportKind { AUDIO, SUBTITLES }

private fun processingLabel(uploadPercent: Int?): String = when {
    uploadPercent == null -> "Transkrybuję…"
    uploadPercent < 100 -> "Wysyłanie… $uploadPercent%"
    else -> "Przetwarzanie po stronie ElevenLabs…"
}

@Composable
fun TranscriptScreen(
    recording: Recording,
    editingSpeaker: String?,
    nameDraft: String,
    autoNotion: Boolean,
    keyterms: List<String>,
    uploadPercent: Int?,
    onBack: () -> Unit,
    onTitleChange: (String) -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onExport: (ExportKind) -> Unit,
    onSendToNotion: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onStartEditSpeaker: (String) -> Unit,
    onNameDraftChange: (String) -> Unit,
    onCommitName: () -> Unit
) {
    val player = rememberAudioPlayerState(recording.filePath)
    var showExportMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Usunąć nagranie?") },
            text = { Text("Nagranie audio i transkrypcja zostaną trwale usunięte z telefonu.") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text("Usuń", color = NutkaColors.accent700)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Anuluj") }
            }
        )
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(top = 14.dp, start = 16.dp, end = 16.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            RoundIconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Wstecz", modifier = Modifier.size(20.dp)) }
            TextField(
                value = recording.title ?: "",
                onValueChange = onTitleChange,
                placeholder = { Text("Nazwa nagrania") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.titleLarge,
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                )
            )
            IconButton(onClick = onCopy) { Icon(Icons.Default.ContentCopy, contentDescription = "Kopiuj", modifier = Modifier.size(17.dp), tint = NutkaColors.text.copy(alpha = 0.6f)) }
            Box {
                IconButton(onClick = { showExportMenu = true }) {
                    Icon(Icons.Default.FileDownload, contentDescription = "Eksportuj", modifier = Modifier.size(17.dp), tint = NutkaColors.text.copy(alpha = 0.6f))
                }
                DropdownMenu(expanded = showExportMenu, onDismissRequest = { showExportMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Pobierz oryginalne audio") },
                        onClick = { showExportMenu = false; onExport(ExportKind.AUDIO) }
                    )
                    DropdownMenuItem(
                        text = { Text("Eksportuj napisy (SRT)") },
                        onClick = { showExportMenu = false; onExport(ExportKind.SUBTITLES) }
                    )
                }
            }
            IconButton(onClick = onShare) { Icon(Icons.Default.Share, contentDescription = "Udostępnij tekst", modifier = Modifier.size(17.dp), tint = NutkaColors.text.copy(alpha = 0.6f)) }
            IconButton(onClick = { showDeleteConfirm = true }) { Icon(Icons.Default.Delete, contentDescription = "Usuń", modifier = Modifier.size(17.dp), tint = NutkaColors.text.copy(alpha = 0.6f)) }
        }

        val speakerOrder = recording.segments.map { it.speaker }.distinct()
        if (speakerOrder.isNotEmpty()) {
            LazyRow(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(speakerOrder) { role ->
                    SpeakerChip(
                        name = recording.speakerNames[role] ?: role,
                        color = if (role == "a") NutkaColors.accent else NutkaColors.accent2,
                        onClick = { onStartEditSpeaker(role) }
                    )
                }
            }
        }

        if (speakerOrder.size > 1) {
            SpeakerStatsBar(
                stats = computeSpeakerStats(recording.segments, recording.speakerNames, recording.durationSec),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        if (!recording.filePath.isNullOrBlank() && recording.durationSec > 0) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(onClick = { player.toggle() }, modifier = Modifier.size(30.dp)) {
                    Icon(
                        if (player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (player.isPlaying) "Pauza" else "Odtwórz",
                        tint = NutkaColors.accent
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val posFraction = if (player.durationMs > 0) player.positionMs.toFloat() / player.durationMs else 0f
                    SpeakerTimeline(
                        segments = recording.segments,
                        durationSec = recording.durationSec,
                        positionFraction = posFraction,
                        onSeek = { player.seekToFraction(it) }
                    )
                    if (speakerOrder.size > 1) {
                        speakerOrder.forEach { role ->
                            SpeakerTimeline(
                                segments = recording.segments,
                                durationSec = recording.durationSec,
                                positionFraction = posFraction,
                                onSeek = { player.seekToFraction(it) },
                                filterSpeaker = role,
                                showPlayhead = false,
                                height = 12.dp
                            )
                        }
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(formatDuration(recording.durationSec), fontSize = 12.sp, color = NutkaColors.text.copy(alpha = 0.6f))
            if (recording.bookmarks.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(11.dp), tint = NutkaColors.text.copy(alpha = 0.6f))
                    Text(
                        "  " + recording.bookmarks.joinToString(" · ") { formatDuration(it) },
                        fontSize = 12.sp, color = NutkaColors.text.copy(alpha = 0.6f)
                    )
                }
            }
            Box(Modifier.weight(1f))
            when (recording.status) {
                RecordingStatus.PROCESSING -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = NutkaColors.accent)
                    Text("  " + processingLabel(uploadPercent), fontSize = 12.sp, color = NutkaColors.text.copy(alpha = 0.8f))
                }
                RecordingStatus.TRANSCRIBED -> if (!autoNotion) {
                    OutlinedButton(
                        onClick = onSendToNotion,
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NutkaColors.accent700),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NutkaColors.accent),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) { Text("Wyślij do Notion", fontSize = 12.sp) }
                }
                RecordingStatus.SENDING -> Tag("Wysyłanie…", TagStyle.NEUTRAL)
                RecordingStatus.SENT -> Tag("W Notion", TagStyle.ACCENT2)
                RecordingStatus.ERROR -> OutlinedButton(
                    onClick = onRetry,
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NutkaColors.accent700),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NutkaColors.accent),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) { Text("Spróbuj ponownie", fontSize = 12.sp) }
            }
        }

        if (recording.status == RecordingStatus.PROCESSING) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (uploadPercent != null && uploadPercent < 100) {
                        androidx.compose.material3.CircularProgressIndicator(
                            progress = { uploadPercent / 100f },
                            modifier = Modifier.size(36.dp), strokeWidth = 3.dp, color = NutkaColors.accent
                        )
                    } else {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 3.dp, color = NutkaColors.accent)
                    }
                    Text(processingLabel(uploadPercent), fontSize = 13.sp, color = NutkaColors.text.copy(alpha = 0.6f))
                    if (uploadPercent != null) {
                        Text(
                            "Nie zamykaj aplikacji z paska ostatnich — w tle jest OK",
                            fontSize = 11.sp, color = NutkaColors.text.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        } else if (recording.segments.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    recording.errorMessage ?: "Brak rozpoznanego tekstu",
                    fontSize = 13.sp, color = NutkaColors.text.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 32.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
            ) {
                items(recording.segments) { seg ->
                    SegmentBubble(
                        seg = seg,
                        speakerName = recording.speakerNames[seg.speaker] ?: seg.speakerLabel ?: "Mówca",
                        keyterms = keyterms,
                        isEditing = editingSpeaker == seg.speaker,
                        nameDraft = nameDraft,
                        onStartEdit = { onStartEditSpeaker(seg.speaker) },
                        onNameDraftChange = onNameDraftChange,
                        onCommitName = onCommitName
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeakerChip(name: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(NutkaColors.surface)
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(name, fontSize = 12.5.sp, color = NutkaColors.text)
    }
}

@Composable
private fun SegmentBubble(
    seg: Segment,
    speakerName: String,
    keyterms: List<String>,
    isEditing: Boolean,
    nameDraft: String,
    onStartEdit: () -> Unit,
    onNameDraftChange: (String) -> Unit,
    onCommitName: () -> Unit
) {
    val roleColor = if (seg.speaker == "a") NutkaColors.accent else NutkaColors.accent2
    val bubbleColor = if (seg.speaker == "a") NutkaColors.accent100 else NutkaColors.accent2_100

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(26.dp).clip(CircleShape).background(roleColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    speakerName.trim().firstOrNull()?.uppercase() ?: "?",
                    fontSize = 12.sp, color = NutkaColors.bg, fontWeight = FontWeight.Bold
                )
            }
            Box(Modifier.padding(start = 8.dp)) {
                if (isEditing) {
                    TextField(
                        value = nameDraft,
                        onValueChange = onNameDraftChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleSmall,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onCommitName() }),
                        modifier = Modifier.size(width = 130.dp, height = 44.dp),
                        colors = TextFieldDefaults.colors(
                            unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedIndicatorColor = roleColor,
                            focusedIndicatorColor = roleColor
                        )
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(onClick = onStartEdit)) {
                        Text(speakerName, style = MaterialTheme.typography.titleSmall)
                        Icon(
                            Icons.Default.Edit, contentDescription = "Zmień nazwę",
                            modifier = Modifier.padding(start = 4.dp).size(12.dp),
                            tint = NutkaColors.text.copy(alpha = 0.35f)
                        )
                    }
                }
            }
            Box(Modifier.weight(1f))
            Text(formatDuration(seg.timeSec), fontSize = 11.sp, color = NutkaColors.text.copy(alpha = 0.4f))
        }
        Text(
            highlightKeyterms(seg.text, keyterms, roleColor),
            fontSize = 14.sp,
            lineHeight = 21.sp,
            color = NutkaColors.text,
            modifier = Modifier
                .padding(top = 6.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(bubbleColor)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        )
    }
}

private fun highlightKeyterms(text: String, keyterms: List<String>, accentColor: androidx.compose.ui.graphics.Color) =
    buildAnnotatedString {
        if (keyterms.isEmpty()) {
            append(text)
            return@buildAnnotatedString
        }
        var cursor = 0
        val lower = text.lowercase()
        while (cursor < text.length) {
            val nextMatch = keyterms
                .filter { it.isNotBlank() }
                .mapNotNull { term ->
                    val idx = lower.indexOf(term.lowercase(), cursor)
                    if (idx >= 0) idx to term.length else null
                }
                .minByOrNull { it.first }
            if (nextMatch == null) {
                append(text.substring(cursor))
                break
            }
            val (idx, len) = nextMatch
            append(text.substring(cursor, idx))
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = accentColor)) {
                append(text.substring(idx, idx + len))
            }
            cursor = idx + len
        }
    }

@Composable
private fun RoundIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(NutkaColors.surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}
