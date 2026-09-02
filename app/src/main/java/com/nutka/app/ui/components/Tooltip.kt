package com.nutka.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.nutka.app.ui.theme.NutkaColors
import kotlinx.coroutines.delay

/**
 * Hover-first tooltip, hand-rolled so it can NEVER steal a plain tap from the
 * content it wraps:
 *  - mouse / stylus: shows while the pointer hovers the content;
 *  - touchscreen: long-press shows it INSTEAD of the click — the press is
 *    consumed in the pointer Initial pass, so the wrapped clickable never
 *    receives the release and won't fire.
 *
 * Don't wrap components that use long-press themselves (e.g. delete-on-
 * long-press rows, hold-to-2x play) — both would fight for the gesture.
 */
@Composable
fun HintTooltip(
    tooltip: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    var showByLongPress by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }

    // Keep the hint readable for a moment after the finger lifts.
    LaunchedEffect(pressed) {
        if (!pressed && showByLongPress) {
            delay(1600)
            showByLongPress = false
        }
    }

    Box(
        modifier
            .hoverable(hoverSource)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial
                    )
                    pressed = true
                    val downTime = down.uptimeMillis
                    val downPos = down.position
                    val slop = viewConfiguration.touchSlop
                    var longFired = false

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null) break
                        val allUp = event.changes.none { it.pressed }

                        if (longFired) {
                            change.consume()
                            if (allUp) break
                        } else {
                            // Moved, scrolled, or someone else claimed the gesture?
                            // Not a long-press — back off completely.
                            val moved = (change.position - downPos).getDistance() > slop
                            val stolen = event.changes.any { it.isConsumed }
                            if (moved || stolen) break

                            if (change.uptimeMillis - downTime >= viewConfiguration.longPressTimeoutMillis) {
                                longFired = true
                                showByLongPress = true
                                change.consume()
                            } else if (allUp) break
                        }
                    }
                    pressed = false
                    if (!longFired) showByLongPress = false
                }
            }
    ) {
        content()

        AnimatedVisibility(
            visible = hovered || showByLongPress,
            enter = fadeIn(tween(140)) + scaleIn(
                initialScale = 0.92f,
                animationSpec = tween(140),
                transformOrigin = TransformOrigin(0.5f, 1.0f)
            ),
            exit = fadeOut(tween(110)),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Popup(alignment = Alignment.TopCenter) {
                NutkaTooltipCard(tooltip)
            }
        }
    }
}

@Composable
fun NutkaTooltipCard(text: String) {
    Box(
        Modifier
            .padding(horizontal = 14.dp)
            .padding(bottom = 10.dp)
            .widthIn(max = 264.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(NutkaColors.neutral900)
            .border(1.dp, NutkaColors.neutral700, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        androidx.compose.material3.Text(
            text,
            color = NutkaColors.bg,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Normal
        )
    }
}
