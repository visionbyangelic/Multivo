package com.example.multivo.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.multivo.ui.theme.Motion

/**
 * Remembers which list items already played their entrance, so scrolling back
 * up doesn't replay it.
 */
class RevealTracker {
    internal val seen = HashSet<Any>()
}

@Composable
fun rememberRevealTracker(): RevealTracker = remember { RevealTracker() }

/** Where an item slides in from. */
enum class RevealFrom { Below, Start, End }

/**
 * Slowly fades and slides its content in the first time it appears.
 * [index] staggers items that enter together.
 */
@Composable
fun Reveal(
    modifier: Modifier = Modifier,
    index: Int = 0,
    key: Any? = null,
    tracker: RevealTracker? = null,
    from: RevealFrom = RevealFrom.Below,
    content: @Composable () -> Unit
) {
    val alreadySeen = key != null && tracker != null && key in tracker.seen
    val state = remember { MutableTransitionState(alreadySeen).apply { targetState = true } }
    if (key != null && tracker != null) SideEffect { tracker.seen.add(key) }

    val delay = index.coerceIn(0, Motion.MAX_STAGGERED) * Motion.STAGGER
    val slide = when (from) {
        RevealFrom.Below -> slideInVertically(Motion.slow(delay)) { it / 3 }
        RevealFrom.Start -> slideInHorizontally(Motion.slow(delay)) { -it / 4 }
        RevealFrom.End -> slideInHorizontally(Motion.slow(delay)) { it / 4 }
    }
    AnimatedVisibility(
        visibleState = state,
        modifier = modifier,
        enter = fadeIn(Motion.slow(delay)) + slide + scaleIn(Motion.slow(delay), initialScale = 0.96f)
    ) {
        content()
    }
}

/** Three softly breathing dots, shown while someone is typing. */
@Composable
fun TypingDots(color: Color, modifier: Modifier = Modifier, dot: Dp = 5.dp) {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(3) { i ->
            val a by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(900, delayMillis = i * 220, easing = Motion.Ease), RepeatMode.Reverse),
                label = "dot$i"
            )
            Box(Modifier.size(dot).graphicsLayer { alpha = a }.clip(CircleShape).background(color))
        }
    }
}

/** A dot that slowly pulses, used for "live" status. */
@Composable
fun PulsingDot(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 2.4f,
        animationSpec = infiniteRepeatable(tween(1800, easing = Motion.Ease), RepeatMode.Restart),
        label = "pulseScale"
    )
    val haloAlpha by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1800, easing = Motion.Ease), RepeatMode.Restart),
        label = "pulseAlpha"
    )
    Box(modifier.size(size)) {
        Box(
            Modifier.size(size).graphicsLayer { scaleX = scale; scaleY = scale; alpha = haloAlpha }
                .clip(CircleShape).background(color)
        )
        Box(Modifier.size(size).clip(CircleShape).background(color))
    }
}
