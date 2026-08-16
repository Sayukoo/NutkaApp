package com.nutka.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nutka.app.ui.theme.NutkaColors

/** Animated audio-level bars shown while actively recording. */
@Composable
fun WaveformBars(color: Color = NutkaColors.accent, modifier: Modifier = Modifier) {
    val delays = listOf(0, 100, 200, 300, 150, 250, 50)
    Row(modifier.height(36.dp), verticalAlignment = Alignment.CenterVertically) {
        delays.forEachIndexed { i, delayMs ->
            val transition = rememberInfiniteTransition(label = "wave$i")
            val scale by transition.animateFloat(
                initialValue = 0.35f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(900, delayMillis = delayMs, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "waveScale$i"
            )
            Box(
                Modifier
                    .width(4.dp)
                    .height(36.dp)
                    .scale(scaleX = 1f, scaleY = scale)
                    .background(color, RoundedCornerShape(2.dp))
            )
            if (i != delays.lastIndex) Box(Modifier.width(5.dp))
        }
    }
}

/** Pulsing ring drawn behind the record button while a session is live. */
@Composable
fun PulseRing(color: Color = NutkaColors.accent, size: androidx.compose.ui.unit.Dp = 120.dp, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = 0.8f, targetValue = 1.7f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "pulseScale"
    )
    val alpha by transition.animateFloat(
        initialValue = 0.55f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "pulseAlpha"
    )
    Box(
        modifier
            .width(size).height(size)
            .scale(scale)
            .alpha(alpha)
            .border(2.dp, color, CircleShape)
    )
}
