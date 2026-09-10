package com.nutka.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nutka.app.ui.theme.NutkaColors

/**
 * The live voice meter: a scrolling column of sticks, one per microphone
 * sample, newest on the right — the same read as a dictaphone's level trace.
 * Speak louder and the sticks that arrive are taller; go quiet and they
 * collapse to dots that keep drifting left, so the last few seconds of your
 * voice are always visible as a shape rather than a single pulsing blob.
 *
 * [levels] is read *inside* the draw lambda on purpose: the service pushes a
 * new sample every 33 ms, and reading the state here confines that to the draw
 * phase — no recomposition of the record screen 30 times a second.
 */
@Composable
fun LiveWaveform(
    levels: State<List<Float>>,
    isPaused: Boolean,
    modifier: Modifier = Modifier,
    color: Color = NutkaColors.accent,
    barCount: Int = 52
) {
    val activeColor by animateColorAsState(
        targetValue = if (isPaused) NutkaColors.neutral400 else color,
        animationSpec = tween(220),
        label = "waveformColor"
    )
    val pausedDim by animateColorAsState(
        targetValue = if (isPaused) NutkaColors.neutral300 else color.copy(alpha = 0.28f),
        animationSpec = tween(220),
        label = "waveformIdle"
    )

    Canvas(modifier.fillMaxWidth().height(52.dp)) {
        val samples = levels.value
        val slot = size.width / barCount
        val barWidth = (slot * 0.46f).coerceIn(2f, 7f)
        val centerY = size.height / 2f
        val maxHeight = size.height

        for (i in 0 until barCount) {
            // Right-align the history so the newest sample is always the
            // right-most stick and older ones scroll away to the left.
            val level = samples.getOrElse(samples.size - barCount + i) { 0f }
            val barHeight = (maxHeight * level).coerceAtLeast(barWidth)
            val x = slot * i + slot / 2f

            // Older samples fade out slightly, which reads as motion even
            // during a steady tone.
            val age = if (barCount > 1) i.toFloat() / (barCount - 1) else 1f
            val tint = if (level <= 0.001f) pausedDim else activeColor
            drawLine(
                color = tint.copy(alpha = tint.alpha * (0.35f + 0.65f * age)),
                start = Offset(x, centerY - barHeight / 2f),
                end = Offset(x, centerY + barHeight / 2f),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Reactive pulse rings behind the record button while a session is live.
 * Flat 2D: thin outline circles drifting outward and fading, both swelling
 * with the actual microphone level.
 *
 * [audioLevel] is read inside [graphicsLayer] so a louder voice only re-runs
 * the layer phase — never a recomposition.
 */
@Composable
fun PulseRing(
    audioLevel: State<Float>,
    modifier: Modifier = Modifier,
    isPaused: Boolean = false,
    color: Color = NutkaColors.accent,
    size: Dp = 120.dp
) {
    if (isPaused) return

    val transition = rememberInfiniteTransition(label = "pulse")

    val scaleA by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.55f,
        animationSpec = infiniteRepeatable(tween(1700, easing = LinearEasing)),
        label = "pulseScaleA"
    )
    val alphaA by transition.animateFloat(
        initialValue = 0.40f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1700, easing = LinearEasing)),
        label = "pulseAlphaA"
    )
    val scaleB by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.55f,
        animationSpec = infiniteRepeatable(tween(1700, delayMillis = 850, easing = LinearEasing)),
        label = "pulseScaleB"
    )
    val alphaB by transition.animateFloat(
        initialValue = 0.40f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1700, delayMillis = 850, easing = LinearEasing)),
        label = "pulseAlphaB"
    )

    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier
                .size(size)
                .graphicsLayer {
                    val boost = audioLevel.value * 0.24f
                    scaleX = scaleB + boost
                    scaleY = scaleB + boost
                    alpha = alphaB * 0.8f
                }
                .border(1.dp, color, CircleShape)
        )
        Box(
            modifier
                .size(size)
                .graphicsLayer {
                    val boost = audioLevel.value * 0.24f
                    scaleX = scaleA + boost
                    scaleY = scaleA + boost
                    alpha = alphaA
                }
                .border(2.dp, color, CircleShape)
        )
    }
}
