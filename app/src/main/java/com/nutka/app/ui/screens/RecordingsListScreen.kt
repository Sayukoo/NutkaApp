package com.nutka.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nutka.app.data.Recording
import com.nutka.app.data.RecordingStatus
import com.nutka.app.data.Segment
import com.nutka.app.ui.components.Tag
import com.nutka.app.ui.components.TagStyle
import com.nutka.app.ui.theme.NutkaColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatDuration(sec: Int): String {
    val m = sec / 60
    val s = sec % 60
    return "$m:${s.toString().padStart(2, '0')}"
}

fun formatDateLabel(millis: Long): String {
    // Calendar-day based ("wczoraj" really means yesterday's date), not a raw
    // 24h offset — the old version labelled e.g. 23:59 → 00:01 next day as "dziś".
    val now = java.time.LocalDate.now()
    val date = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    val time = SimpleDateFormat("HH:mm", Locale("pl")).format(Date(millis))
    return when (java.time.temporal.ChronoUnit.DAYS.between(date, now)) {
        0L -> "dziś, $time"
        1L -> "wczoraj, $time"
        else -> SimpleDateFormat("d MMM, HH:mm", Locale("pl")).format(Date(millis))
    }
}

private fun extractSnippetAroundMatch(text: String, query: String, maxSurroundingChars: Int = 36): String {
    val lower = text.lowercase()
    val qLower = query.lowercase()
    val idx = lower.indexOf(qLower)
    if (idx < 0) return text.take(72) + if (text.length > 72) "…" else ""

    val start = (idx - maxSurroundingChars).coerceAtLeast(0)
    val end = (idx + query.length + maxSurroundingChars).coerceAtMost(text.length)

    val prefix = if (start > 0) "…" else ""
    val suffix = if (end < text.length) "…" else ""

    return prefix + text.substring(start, end) + suffix
}

private fun highlightMatchedText(
    text: String,
    query: String,
    accentColor: Color
): AnnotatedString = buildAnnotatedString {
    val q = query.trim()
    if (q.isEmpty()) {
        append(text)
        return@buildAnnotatedString
    }
    val lower = text.lowercase()
    val qLower = q.lowercase()
    var cursor = 0
    while (cursor < text.length) {
        val idx = lower.indexOf(qLower, cursor)
        if (idx < 0) {
            append(text.substring(cursor))
            break
        }
        append(text.substring(cursor, idx))
        withStyle(
            SpanStyle(
                background = accentColor.copy(alpha = 0.28f),
                color = NutkaColors.neutral900,
                fontWeight = FontWeight.Bold
            )
        ) {
            append(text.substring(idx, idx + q.length))
        }
        cursor = idx + q.length
    }
}

data class MatchedSegmentItem(
    val index: Int,
    val segment: Segment,
    val snippet: String
)

@Composable
fun RecordingsListScreen(
    recordings: List<Recording>,
    onOpen: (id: String, initialSearch: String?, targetSegmentIndex: Int?) -> Unit,
    onDelete: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val trimmedQuery = searchQuery.trim()

    val filteredList = remember(recordings, trimmedQuery) {
        if (trimmedQuery.isEmpty()) {
            recordings.map { it to emptyList<MatchedSegmentItem>() }
        } else {
            recordings.mapNotNull { rec ->
                val titleMatch = (rec.title ?: "").contains(trimmedQuery, ignoreCase = true)
                val matchingSegments = rec.segments.mapIndexedNotNull { index, seg ->
                    val segTextMatch = seg.text.contains(trimmedQuery, ignoreCase = true)
                    val speakerName = rec.speakerNames[seg.speaker] ?: ""
                    val speakerMatch = speakerName.contains(trimmedQuery, ignoreCase = true)
                    if (segTextMatch || speakerMatch) {
                        MatchedSegmentItem(
                            index = index,
                            segment = seg,
                            snippet = extractSnippetAroundMatch(seg.text, trimmedQuery)
                        )
                    } else null
                }
                if (titleMatch || matchingSegments.isNotEmpty()) {
                    rec to matchingSegments
                } else null
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Nagrania",
                style = MaterialTheme.typography.headlineSmall
            )
            if (recordings.isNotEmpty()) {
                Text(
                    if (trimmedQuery.isEmpty()) "${recordings.size}" else "${filteredList.size} z ${recordings.size}",
                    fontSize = 13.sp,
                    color = NutkaColors.text.copy(alpha = 0.5f)
                )
            }
        }

        if (recordings.isNotEmpty()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                placeholder = {
                    Text(
                        "Szukaj w nagraniach i transkrypcjach…",
                        fontSize = 13.5.sp,
                        color = NutkaColors.text.copy(alpha = 0.45f)
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Szukaj",
                        modifier = Modifier.size(18.dp),
                        tint = if (trimmedQuery.isNotEmpty()) NutkaColors.accent else NutkaColors.text.copy(alpha = 0.5f)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Wyczyść",
                                modifier = Modifier.size(16.dp),
                                tint = NutkaColors.text.copy(alpha = 0.6f)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = NutkaColors.neutral100.copy(alpha = 0.85f),
                    unfocusedContainerColor = NutkaColors.surface.copy(alpha = 0.45f),
                    focusedBorderColor = NutkaColors.accent400,
                    unfocusedBorderColor = NutkaColors.divider,
                    cursorColor = NutkaColors.accent
                ),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, color = NutkaColors.text)
            )
        }

        if (recordings.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Brak nagrań — zacznij od karty Nagrywaj", fontSize = 13.sp, color = NutkaColors.text.copy(alpha = 0.5f))
            }
        } else if (filteredList.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(top = 40.dp), contentAlignment = Alignment.TopCenter) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Brak wyników dla „$searchQuery”",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = NutkaColors.text.copy(alpha = 0.7f)
                    )
                    TextButton(onClick = { searchQuery = "" }) {
                        Text("Wyczyść wyszukiwanie", color = NutkaColors.accent700)
                    }
                }
            }
        } else {
            if (trimmedQuery.isEmpty()) {
                Text(
                    "Przytrzymaj nagranie, aby je usunąć",
                    fontSize = 11.sp, color = NutkaColors.text.copy(alpha = 0.4f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            } else {
                Text(
                    "Kliknij fragment, aby przejść do danego momentu",
                    fontSize = 11.sp, color = NutkaColors.accent700.copy(alpha = 0.75f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(filteredList, key = { it.first.id }) { (rec, matchedSegments) ->
                    RecordingRow(
                        rec = rec,
                        searchQuery = trimmedQuery,
                        matchedSegments = matchedSegments,
                        onClick = { onOpen(rec.id, trimmedQuery.takeIf { it.isNotBlank() }, null) },
                        onOpenSegment = { segIndex -> onOpen(rec.id, trimmedQuery.takeIf { it.isNotBlank() }, segIndex) },
                        onDelete = { onDelete(rec.id) }
                    )
                }
                item { Box(Modifier.padding(bottom = 16.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordingRow(
    rec: Recording,
    searchQuery: String,
    matchedSegments: List<MatchedSegmentItem>,
    onClick: () -> Unit,
    onOpenSegment: (Int) -> Unit,
    onDelete: () -> Unit
) {
    var showConfirm by remember { mutableStateOf(false) }
    var isExpanded by remember { mutableStateOf(false) }

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
            .clip(RoundedCornerShape(24.dp))
            .background(NutkaColors.surface)
            .border(1.dp, NutkaColors.divider, RoundedCornerShape(24.dp))
            .combinedClickable(onClick = onClick, onLongClick = { showConfirm = true })
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val titleText = rec.title?.takeIf { it.isNotBlank() } ?: "Nowe nagranie"
            if (searchQuery.isNotEmpty() && titleText.contains(searchQuery, ignoreCase = true)) {
                Text(
                    text = highlightMatchedText(titleText, searchQuery, NutkaColors.accent),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f, fill = false)
                )
            } else {
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            when (rec.status) {
                RecordingStatus.PROCESSING -> Tag("Przetwarzanie", TagStyle.NEUTRAL)
                RecordingStatus.SENDING -> Tag("Wysyłanie", TagStyle.NEUTRAL)
                RecordingStatus.TRANSCRIBED -> Tag("Do wysłania", TagStyle.OUTLINE)
                RecordingStatus.SENT -> Tag("W Notion", TagStyle.ACCENT2)
                RecordingStatus.ERROR -> Tag("Błąd", TagStyle.OUTLINE)
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = NutkaColors.text.copy(alpha = 0.6f)
                )
                Text(
                    "  ${formatDuration(rec.durationSec)}  ·  ${formatDateLabel(rec.createdAtMillis)}",
                    fontSize = 12.sp,
                    color = NutkaColors.text.copy(alpha = 0.6f)
                )
            }

            if (matchedSegments.isNotEmpty()) {
                val matchCount = matchedSegments.size
                val label = if (matchCount == 1) "1 trafienie w tekście" else "$matchCount trafień w tekście"
                Tag(label, TagStyle.ACCENT)
            }
        }

        // Matching segment snippets preview
        if (matchedSegments.isNotEmpty() && searchQuery.isNotEmpty()) {
            val visibleSnippets = if (isExpanded || matchedSegments.size <= 2) matchedSegments else matchedSegments.take(2)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                visibleSnippets.forEach { item ->
                    val speakerName = rec.speakerNames[item.segment.speaker] ?: item.segment.speakerLabel ?: "Mówca"
                    val roleColor = if (item.segment.speaker == "a") NutkaColors.accent else NutkaColors.accent2
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(NutkaColors.bg.copy(alpha = 0.7f))
                            .clickable { onOpenSegment(item.index) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            formatDuration(item.segment.timeSec),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = roleColor
                        )
                        Text(
                            "$speakerName:",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = NutkaColors.text.copy(alpha = 0.75f)
                        )
                        Text(
                            text = highlightMatchedText(item.snippet, searchQuery, NutkaColors.accent),
                            fontSize = 12.sp,
                            color = NutkaColors.text,
                            maxLines = 2,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = "Przejdź",
                            modifier = Modifier.size(14.dp),
                            tint = NutkaColors.text.copy(alpha = 0.35f)
                        )
                    }
                }

                if (matchedSegments.size > 2) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { isExpanded = !isExpanded }
                            .padding(vertical = 2.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            if (isExpanded) "Zwiń pozostałe" else "Pokaż jeszcze ${matchedSegments.size - 2}…",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = NutkaColors.accent700
                        )
                        Icon(
                            if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = NutkaColors.accent700
                        )
                    }
                }
            }
        }
    }
}
