package com.nutka.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
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
import com.nutka.app.ui.components.HintTooltip
import com.nutka.app.ui.components.SpeakerStatsBar
import com.nutka.app.ui.components.SpeakerTimeline
import com.nutka.app.ui.components.Tag
import com.nutka.app.ui.components.TagStyle
import com.nutka.app.ui.components.computeSpeakerStats
import com.nutka.app.ui.components.rememberAudioPlayerState
import com.nutka.app.ui.theme.NutkaColors
import kotlinx.coroutines.launch

enum class ExportKind { AUDIO, SUBTITLES }

private fun processingLabel(uploadPercent: Int?): String = when {
    uploadPercent == null -> "Transkrybuję…"
    uploadPercent < 100 -> "Wysyłanie… $uploadPercent%"
    else -> "Przetwarzanie po stronie ElevenLabs…"
}

data class SearchMatch(
    val segmentIndex: Int,
    val charOffset: Int,
    val length: Int,
    val timeSec: Int
)

@Composable
fun TranscriptScreen(
    recording: Recording,
    editingSpeaker: String?,
    nameDraft: String,
    autoNotion: Boolean,
    keyterms: List<String>,
    uploadPercent: Int?,
    initialSearchQuery: String? = null,
    initialSegmentIndex: Int? = null,
    onConsumeInitialSearch: () -> Unit = {},
    onBack: () -> Unit,
    onTitleChange: (String) -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onExport: (ExportKind) -> Unit,
    onSendToNotion: () -> Unit,
    onRetry: () -> Unit,
    onCancelTranscription: () -> Unit,
    onDelete: () -> Unit,
    onStartEditSpeaker: (String) -> Unit,
    onNameDraftChange: (String) -> Unit,
    onCommitName: () -> Unit
) {
    val player = rememberAudioPlayerState(recording.filePath, recording.durationSec)
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    var showExportMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Which bubble opened the rename field. The ViewModel only tracks the
    // *role* being renamed ("a"/"b"), and matching on that alone turned every
    // single bubble of that speaker into a text field at once — a dozen inputs
    // all bound to the same draft. Only the tapped one should become editable.
    var editingSegmentIndex by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(editingSpeaker) {
        if (editingSpeaker == null) editingSegmentIndex = null
    }

    var isSearchOpen by remember { mutableStateOf(!initialSearchQuery.isNullOrBlank()) }
    var searchQuery by remember { mutableStateOf(initialSearchQuery ?: "") }
    var currentMatchIndex by remember { mutableStateOf(0) }

    val trimmedSearch = searchQuery.trim()

    // Find all occurrences of the search query across all segments
    val matches = remember(recording.segments, trimmedSearch) {
        if (trimmedSearch.isEmpty()) {
            emptyList()
        } else {
            val list = mutableListOf<SearchMatch>()
            val queryLower = trimmedSearch.lowercase()
            recording.segments.forEachIndexed { segIdx, seg ->
                var startIndex = 0
                val textLower = seg.text.lowercase()
                while (startIndex < textLower.length) {
                    val foundIdx = textLower.indexOf(queryLower, startIndex)
                    if (foundIdx < 0) break
                    list.add(SearchMatch(segIdx, foundIdx, trimmedSearch.length, seg.timeSec))
                    startIndex = foundIdx + trimmedSearch.length.coerceAtLeast(1)
                }
            }
            list
        }
    }

    // Handle initial search & jump parameters passed on screen entry
    LaunchedEffect(initialSearchQuery, initialSegmentIndex) {
        if (!initialSearchQuery.isNullOrBlank()) {
            isSearchOpen = true
            searchQuery = initialSearchQuery
        }
        if (initialSegmentIndex != null && initialSegmentIndex in recording.segments.indices) {
            val targetMatchIdx = matches.indexOfFirst { it.segmentIndex == initialSegmentIndex }
            if (targetMatchIdx >= 0) {
                currentMatchIndex = targetMatchIdx
            }
            listState.scrollToItem(initialSegmentIndex)
            onConsumeInitialSearch()
        }
    }

    // Auto scroll when current match index changes
    LaunchedEffect(currentMatchIndex, matches) {
        if (matches.isNotEmpty() && currentMatchIndex in matches.indices) {
            val match = matches[currentMatchIndex]
            listState.animateScrollToItem(match.segmentIndex)
        }
    }

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
        // Top Action Bar
        Row(
            Modifier.fillMaxWidth().padding(top = 14.dp, start = 16.dp, end = 16.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            HintTooltip("Wróć do listy nagrań") {
                RoundIconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz", modifier = Modifier.size(20.dp))
                }
            }
            TextField(
                value = recording.title ?: "",
                onValueChange = onTitleChange,
                placeholder = { Text("Nazwa nagrania") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.titleLarge,
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent
                )
            )

            // Search toggle button
            HintTooltip(if (isSearchOpen) "Zamknij wyszukiwanie" else "Szukaj frazy w transkrypcji — trafienia podświetlą się w tekście") {
                IconButton(
                    onClick = {
                        isSearchOpen = !isSearchOpen
                        if (!isSearchOpen) {
                            searchQuery = ""
                        }
                    }
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Szukaj w nagraniu",
                        modifier = Modifier.size(19.dp),
                        tint = if (isSearchOpen) NutkaColors.accent else NutkaColors.text.copy(alpha = 0.6f)
                    )
                }
            }

            HintTooltip("Skopiuj cały tekst transkrypcji do schowka") {
                IconButton(onClick = onCopy) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Kopiuj", modifier = Modifier.size(17.dp), tint = NutkaColors.text.copy(alpha = 0.6f))
                }
            }
            if (recording.status != RecordingStatus.PROCESSING && recording.status != RecordingStatus.SENDING && recording.status != RecordingStatus.SENT) {
                HintTooltip("Transkrybuj ponownie — przyda się po dodaniu klucza ElevenLabs lub zmianie ustawień (język, mówcy, słowa kluczowe)") {
                    IconButton(onClick = onRetry) {
                        Icon(Icons.Default.Refresh, contentDescription = "Transkrybuj ponownie", modifier = Modifier.size(17.dp), tint = NutkaColors.text.copy(alpha = 0.6f))
                    }
                }
            }
            Box {
                HintTooltip("Eksport: pobierz oryginalny plik audio albo napisy SRT") {
                    IconButton(onClick = { showExportMenu = true }) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Eksportuj", modifier = Modifier.size(17.dp), tint = NutkaColors.text.copy(alpha = 0.6f))
                    }
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
            HintTooltip("Wyślij tekst transkrypcji przez dowolną aplikację na telefonie") {
                IconButton(onClick = onShare) {
                    Icon(Icons.Default.Share, contentDescription = "Udostępnij tekst", modifier = Modifier.size(17.dp), tint = NutkaColors.text.copy(alpha = 0.6f))
                }
            }
            HintTooltip("Usuń nagranie — razem z plikiem audio i transkrypcją. Nie da się tego cofnąć") {
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Usuń", modifier = Modifier.size(17.dp), tint = NutkaColors.text.copy(alpha = 0.6f))
                }
            }
        }

        // In-recording Search Bar
        AnimatedVisibility(
            visible = isSearchOpen,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(NutkaColors.surface)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.padding(start = 6.dp).size(18.dp),
                    tint = NutkaColors.accent
                )
                TextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        currentMatchIndex = 0
                    },
                    placeholder = {
                        Text(
                            "Szukaj w wypowiedziach…",
                            fontSize = 13.sp,
                            color = NutkaColors.text.copy(alpha = 0.5f)
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        cursorColor = NutkaColors.accent
                    )
                )

                if (trimmedSearch.isNotEmpty()) {
                    if (matches.isNotEmpty()) {
                        Text(
                            "${currentMatchIndex + 1}/${matches.size}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NutkaColors.accent700,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    } else {
                        Text(
                            "0 wyników",
                            fontSize = 11.5.sp,
                            color = NutkaColors.text.copy(alpha = 0.45f),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            if (matches.isNotEmpty()) {
                                val nextIdx = if (currentMatchIndex - 1 < 0) matches.size - 1 else currentMatchIndex - 1
                                currentMatchIndex = nextIdx
                                val match = matches[nextIdx]
                                player.seekToSec(match.timeSec, autoPlay = false)
                            }
                        },
                        enabled = matches.size > 1,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowUp,
                            contentDescription = "Poprzedni wynik",
                            modifier = Modifier.size(20.dp),
                            tint = if (matches.size > 1) NutkaColors.text else NutkaColors.text.copy(alpha = 0.25f)
                        )
                    }

                    IconButton(
                        onClick = {
                            if (matches.isNotEmpty()) {
                                val nextIdx = (currentMatchIndex + 1) % matches.size
                                currentMatchIndex = nextIdx
                                val match = matches[nextIdx]
                                player.seekToSec(match.timeSec, autoPlay = false)
                            }
                        },
                        enabled = matches.size > 1,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = "Następny wynik",
                            modifier = Modifier.size(20.dp),
                            tint = if (matches.size > 1) NutkaColors.text else NutkaColors.text.copy(alpha = 0.25f)
                        )
                    }
                }

                IconButton(
                    onClick = {
                        if (searchQuery.isNotEmpty()) {
                            searchQuery = ""
                        } else {
                            isSearchOpen = false
                        }
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Zamknij wyszukiwanie",
                        modifier = Modifier.size(17.dp),
                        tint = NutkaColors.text.copy(alpha = 0.6f)
                    )
                }
            }
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
                        onClick = {
                            // Renaming from the chip: point the field at this
                            // speaker's first bubble so exactly one opens.
                            editingSegmentIndex =
                                recording.segments.indexOfFirst { it.speaker == role }.takeIf { it >= 0 }
                            onStartEditSpeaker(role)
                        }
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
            val haptics = LocalHapticFeedback.current
            var isFastForwarding by remember { mutableStateOf(false) }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .pointerInput(player) {
                            detectTapGestures(
                                onPress = {
                                    if (player.isPlaying) {
                                        isFastForwarding = true
                                        player.setSpeed(2f)
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                    tryAwaitRelease()
                                    if (isFastForwarding) {
                                        isFastForwarding = false
                                        player.setSpeed(1f)
                                    }
                                },
                                onTap = { player.toggle() }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (player.isPlaying) "Pauza" else "Odtwórz (przytrzymaj, by przyspieszyć 2x)",
                        tint = NutkaColors.accent
                    )
                }
                if (isFastForwarding) {
                    Text("2×", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NutkaColors.accent)
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
                RecordingStatus.TRANSCRIBED -> if (!autoNotion || recording.errorMessage != null) {
                    // Show the manual send button both when auto-export is off AND
                    // when a previous auto-send failed — otherwise the user was left
                    // staring at the "Do wysłania" tag with no way to retry.
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
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (uploadPercent != null && uploadPercent < 100) {
                        CircularProgressIndicator(
                            progress = { uploadPercent / 100f },
                            modifier = Modifier.size(38.dp), strokeWidth = 3.dp, color = NutkaColors.accent
                        )
                    } else {
                        CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 3.dp, color = NutkaColors.accent)
                    }
                    Text(processingLabel(uploadPercent), fontSize = 13.sp, color = NutkaColors.text.copy(alpha = 0.7f))
                    if (uploadPercent != null) {
                        Text(
                            "Nie zamykaj aplikacji z paska ostatnich — w tle jest OK",
                            fontSize = 11.sp, color = NutkaColors.text.copy(alpha = 0.4f)
                        )
                    }
                    OutlinedButton(
                        onClick = onCancelTranscription,
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NutkaColors.text.copy(alpha = 0.75f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NutkaColors.divider),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text("Anuluj transkrypcję", fontSize = 12.5.sp)
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
            val currentPosSec = player.positionMs / 1000
            val activeSegmentIndex = if (player.isPlaying || player.positionMs > 0) {
                recording.segments.indexOfLast { it.timeSec <= currentPosSec }
            } else -1

            val currentMatch = matches.getOrNull(currentMatchIndex)

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
            ) {
                itemsIndexed(recording.segments) { index, seg ->
                    val activeCharOffset = if (currentMatch != null && currentMatch.segmentIndex == index) {
                        currentMatch.charOffset
                    } else null

                    SegmentBubble(
                        seg = seg,
                        speakerName = recording.speakerNames[seg.speaker] ?: seg.speakerLabel ?: "Mówca",
                        keyterms = keyterms,
                        searchQuery = trimmedSearch,
                        activeCharOffset = activeCharOffset,
                        isActive = (index == activeSegmentIndex && player.isPlaying),
                        isEditing = editingSpeaker == seg.speaker && editingSegmentIndex == index,
                        nameDraft = nameDraft,
                        onStartEdit = {
                            editingSegmentIndex = index
                            onStartEditSpeaker(seg.speaker)
                        },
                        onNameDraftChange = onNameDraftChange,
                        onCommitName = {
                            editingSegmentIndex = null
                            onCommitName()
                        },
                        onSeekToTime = {
                            player.seekToSec(seg.timeSec, autoPlay = true)
                            // If search is active, focus first match in this segment if present
                            if (matches.isNotEmpty()) {
                                val matchInSeg = matches.indexOfFirst { it.segmentIndex == index }
                                if (matchInSeg >= 0) {
                                    currentMatchIndex = matchInSeg
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeakerChip(name: String, color: Color, onClick: () -> Unit) {
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
    searchQuery: String,
    activeCharOffset: Int?,
    isActive: Boolean,
    isEditing: Boolean,
    nameDraft: String,
    onStartEdit: () -> Unit,
    onNameDraftChange: (String) -> Unit,
    onCommitName: () -> Unit,
    onSeekToTime: () -> Unit
) {
    val roleColor = if (seg.speaker == "a") NutkaColors.accent else NutkaColors.accent2
    val bubbleColor = if (seg.speaker == "a") NutkaColors.accent100 else NutkaColors.accent2_100

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                            unfocusedContainerColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onSeekToTime)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (isActive) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Odtwarzanie",
                        modifier = Modifier.size(12.dp),
                        tint = roleColor
                    )
                }
                Text(
                    formatDuration(seg.timeSec),
                    fontSize = 11.sp,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isActive) roleColor else NutkaColors.text.copy(alpha = 0.4f)
                )
            }
        }
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(bubbleColor)
                .then(
                    if (isActive) {
                        Modifier.border(1.5.dp, roleColor.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                    } else Modifier
                )
                .clickable(onClick = onSeekToTime)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text(
                highlightTranscriptText(
                    text = seg.text,
                    searchQuery = searchQuery,
                    activeCharOffset = activeCharOffset,
                    keyterms = keyterms,
                    speakerAccentColor = roleColor
                ),
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = NutkaColors.text
            )
        }
    }
}

private fun highlightTranscriptText(
    text: String,
    searchQuery: String,
    activeCharOffset: Int?,
    keyterms: List<String>,
    speakerAccentColor: Color
): AnnotatedString = buildAnnotatedString {
    val q = searchQuery.trim()
    val hasSearch = q.isNotEmpty()
    val hasKeyterms = keyterms.isNotEmpty()

    if (!hasSearch && !hasKeyterms) {
        append(text)
        return@buildAnnotatedString
    }

    val lower = text.lowercase()
    val qLower = q.lowercase()

    // Find all search match ranges:
    val searchRanges = mutableListOf<Pair<Int, Int>>() // Pair(start, length)
    if (hasSearch) {
        var cursor = 0
        while (cursor < lower.length) {
            val idx = lower.indexOf(qLower, cursor)
            if (idx < 0) break
            searchRanges.add(idx to q.length)
            cursor = idx + q.length.coerceAtLeast(1)
        }
    }

    // Find keyterm match ranges (only those that don't overlap with search ranges)
    val keytermRanges = mutableListOf<Pair<Int, Int>>()
    if (hasKeyterms) {
        keyterms.filter { it.isNotBlank() }.forEach { term ->
            val termLower = term.lowercase()
            var cursor = 0
            while (cursor < lower.length) {
                val idx = lower.indexOf(termLower, cursor)
                if (idx < 0) break
                val len = term.length
                val overlapsSearch = searchRanges.any { (sStart, sLen) ->
                    idx < sStart + sLen && idx + len > sStart
                }
                if (!overlapsSearch) {
                    keytermRanges.add(idx to len)
                }
                cursor = idx + len.coerceAtLeast(1)
            }
        }
    }

    // Build ordered list of highlight intervals
    data class HighlightInterval(
        val start: Int,
        val end: Int,
        val isSearch: Boolean,
        val isActiveSearch: Boolean
    )

    val intervals = mutableListOf<HighlightInterval>()
    for ((start, len) in searchRanges) {
        val isActive = (activeCharOffset != null && start == activeCharOffset)
        intervals.add(HighlightInterval(start, start + len, isSearch = true, isActiveSearch = isActive))
    }
    for ((start, len) in keytermRanges) {
        intervals.add(HighlightInterval(start, start + len, isSearch = false, isActiveSearch = false))
    }
    intervals.sortBy { it.start }

    var cursor = 0
    for (interval in intervals) {
        if (interval.start < cursor) continue
        if (interval.start > cursor) {
            append(text.substring(cursor, interval.start))
        }
        val matchText = text.substring(interval.start, interval.end.coerceAtMost(text.length))
        if (interval.isSearch) {
            if (interval.isActiveSearch) {
                // Active/Focused search match: prominent accent background and bold white/light text
                withStyle(
                    SpanStyle(
                        background = NutkaColors.accent,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                ) {
                    append(matchText)
                }
            } else {
                // General search matches: highlighted pill background
                withStyle(
                    SpanStyle(
                        background = NutkaColors.accent300.copy(alpha = 0.85f),
                        color = NutkaColors.neutral900,
                        fontWeight = FontWeight.SemiBold
                    )
                ) {
                    append(matchText)
                }
            }
        } else {
            // Keyterms: bold speaker accent color
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = speakerAccentColor)) {
                append(matchText)
            }
        }
        cursor = interval.end
    }

    if (cursor < text.length) {
        append(text.substring(cursor))
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
