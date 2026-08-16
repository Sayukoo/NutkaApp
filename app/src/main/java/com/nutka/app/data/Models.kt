package com.nutka.app.data

enum class RecordingStatus { PROCESSING, TRANSCRIBED, SENDING, SENT, ERROR }

data class Segment(
    val speaker: String, // "a" or "b" — the two visual roles the transcript UI supports
    val timeSec: Int,
    val text: String,
    val speakerLabel: String? = null // raw id/name from a diarizing backend, if any (e.g. "SPEAKER_01")
)

data class Recording(
    val id: String,
    val title: String?,
    val filePath: String?,
    val durationSec: Int,
    val createdAtMillis: Long,
    val status: RecordingStatus,
    val bookmarks: List<Int> = emptyList(),
    val segments: List<Segment> = emptyList(),
    val speakerNames: Map<String, String> = mapOf("a" to "Osoba 1", "b" to "Osoba 2"),
    val errorMessage: String? = null
)
