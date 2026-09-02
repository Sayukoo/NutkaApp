package com.nutka.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nutka.app.ui.theme.NutkaColors

/**
 * Audio-level bars reflecting real-time voice input from the microphone.
 * Pure 2D: solid rounded sticks in one accent colour, dimmed while paused.
 */
@Composable
fun WaveformBars(
    audioLevel: Float = 0f,
    isPaused: Boolean = false,
    color: Color = NutkaColors.accent,
    modifier: Modifier = Modifier
) {
    // Natural vocal frequency envelope (taller toward the centre)
    val barWeights = listOf(0.38f, 0.58f, 0.78f, 0.94f, 1.00f, 0.92f, 0.84f, 0.64f, 0.44f)

    val activeColor by animateColorAsState(
        targetValue = if (isPaused) NutkaColors.neutral400 else color,
        animationSpec = tween(220),
        label = "waveformColor"
    )

    Row(
        modifier = modifier.height(46.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        barWeights.forEachIndexed { i, weight ->
            val targetScale = when {
                isPaused -> 0.12f
                audioLevel <= 0.02f -> 0.16f + (i % 2) * 0.05f // resting breath
                else -> (0.18f + (audioLevel * weight * 0.82f)).coerceIn(0.15f, 1f)
            }

            val animatedScale by animateFloatAsState(
                targetValue = targetScale,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "barScale$i"
            )

            Box(
                Modifier
                    .width(5.dp)
                    .height(42.dp)
                    .scale(scaleX = 1f, scaleY = animatedScale)
                    .clip(RoundedCornerShape(50))
                    .background(activeColor)
            )
        }
    }
}

/**
 * Reactive pulse rings behind the record button while a session is live.
 * Flat 2D: thin outline circles drifting outward and fading, front one
 * swelling with the actual microphone level.
 */
@Composable
fun PulseRing(
    color: Color = NutkaColors.accent,
    audioLevel: Float = 0f,
    isPaused: Boolean = false,
    size: Dp = 120.dp,
    modifier: Modifier = Modifier
) {
    if (isPaused) return

    val transition = rememberInfiniteTransition(label = "pulse")

    @Composable
    fun ring(delayFraction: Float): Pair<Float, Float> {
        val scale by transition.animateFloat(
            initialValue = 0.85f,
            targetValue = 1.55f,
            animationSpec = infiniteRepeatable(
                tween(1700, delayMillis = (delayFraction * 1700).toInt(), easing = LinearEasing)
            ),
            label = "pulseScale$delayFraction"
        )
        val alpha by transition.animateFloat(
            initialValue = 0.40f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                tween(1700, delayMillis = (delayFraction * 1700).toInt(), easing = LinearEasing)
            ),
            label = "pulseAlpha$delayFraction"
        )
        return scale to alpha
    }

    val (scaleA, alphaA) = ring(0f)
    val (scaleB, alphaB) = ring(0.5f)

    val reactiveBoost = audioLevel * 0.22f

    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier
                .size(size)
                .scale(scaleB + reactiveBoost)
                .alpha(alphaB * 0.8f)
                .clip(CircleShape)
                .background(Color.Transparent)
                .border(1.dp, color, CircleShape)
        )
        Box(
            modifier
                .size(size)
                .scale(scaleA + reactiveBoost)
                .alpha(alphaA)
                .clip(CircleShape)
                .background(Color.Transparent)
                .border(2.dp, color, CircleShape)
        )
    }
}
