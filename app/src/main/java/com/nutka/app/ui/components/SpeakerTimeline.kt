package com.nutka.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.nutka.app.data.Segment
import com.nutka.app.ui.theme.NutkaColors

/** Each segment's end time = the next segment's start (last one ends at the recording's end). */
fun segmentEndSeconds(segments: List<Segment>, totalSec: Int): List<Int> =
    segments.indices.map { idx -> segments.getOrNull(idx + 1)?.timeSec ?: totalSec }

/**
 * Per-speaker timeline: a single bar spanning the recording, with one
 * colored block per segment placed at its actual timestamp, plus an
 * optional playhead. Tap anywhere to seek. Pass [filterSpeaker] to draw
 * only that speaker's blocks (used for the individual per-participant
 * tracks), or leave it null for the combined "everyone" track.
 */
@Composable
fun SpeakerTimeline(
    segments: List<Segment>,
    durationSec: Int,
    positionFraction: Float,
    onSeek: (Float) -> Unit,
    filterSpeaker: String? = null,
    showPlayhead: Boolean = true,
    height: androidx.compose.ui.unit.Dp = 28.dp,
    modifier: Modifier = Modifier
) {
    val totalSec = durationSec.coerceAtLeast(1)
    val ends = segmentEndSeconds(segments, totalSec)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(Unit) {
                detectTapGestures { offset -> onSeek((offset.x / size.width).coerceIn(0f, 1f)) }
            }
    ) {
        val w = size.width
        val h = size.height
        val corner = CornerRadius(h / 2)

        drawRoundRect(color = NutkaColors.neutral300, size = size, cornerRadius = corner)

        segments.forEachIndexed { idx, seg ->
            if (filterSpeaker != null && seg.speaker != filterSpeaker) return@forEachIndexed
            val startFrac = (seg.timeSec.toFloat() / totalSec).coerceIn(0f, 1f)
            val endFrac = (ends[idx].toFloat() / totalSec).coerceIn(startFrac, 1f)
            if (endFrac <= startFrac) return@forEachIndexed
            val color = if (seg.speaker == "a") NutkaColors.accent else NutkaColors.accent2
            drawRoundRect(
                color = color,
                topLeft = Offset(startFrac * w, 0f),
                size = Size((endFrac - startFrac) * w, h),
                cornerRadius = CornerRadius(4.dp.toPx().coerceAtMost(h / 2))
            )
        }

        if (showPlayhead) {
            val x = positionFraction.coerceIn(0f, 1f) * w
            drawRoundRect(
                color = NutkaColors.neutral900,
                topLeft = Offset((x - 1.5.dp.toPx()).coerceIn(0f, w - 3.dp.toPx()), 0f),
                size = Size(3.dp.toPx(), h),
                cornerRadius = CornerRadius(1.5.dp.toPx())
            )
        }
    }
}
