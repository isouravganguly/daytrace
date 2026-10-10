package dev.holdthisthought.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Daytrace motion, layer 3 of the design system, built on Disney's 12 principles of animation.
 * Every animation in the app should pick from these instead of inventing its own timing.
 *
 *  1. Squash & stretch: [pressSquish] and Blip's hops keep volume (x grows when y shrinks).
 *  2. Anticipation: Blip crouches before a hop; [Easing.anticipate] dips back before moving.
 *  3. Staging: one hero moment at a time (celebrations dim the rest, Blip centred in the hero).
 *  4. Straight ahead & pose to pose: Blip's reactions are keyed poses with eased in-betweens.
 *  5. Follow-through & overlapping action: Blip's sprout and arms lag behind the body and settle
 *     with a damped wobble; [Springs.bouncy] overshoots slightly.
 *  6. Slow in & slow out: [Easing.standard] everywhere; nothing moves linearly except loops.
 *  7. Arcs: hops, hearts and confetti travel on curves, never straight lines.
 *  8. Secondary action: blinking, glancing eyes and breathing while idle.
 *  9. Timing: [Duration] keeps everything sharp (120-240 ms); only Blip's playful reactions run longer.
 * 10. Exaggeration: landings squash harder than feels "real", so they read at small sizes.
 * 11. Solid drawing: Blip has a consistent volume with one cel-shade, the same from every pose.
 * 12. Appeal: big head, low wide eyes, blush and a tiny sprout: simple, readable, cute.
 */
object Motion {
    /** Sharp and quick: nothing in everyday UI makes you wait more than ~200 ms. */
    object Duration {
        const val instant = 70
        const val short = 120
        const val medium = 180
        const val long = 240
        const val hero = 400
    }

    object Easing {
        /** Slow in, slow out: the default for anything that starts and stops on screen. */
        val standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
        /** Things arriving: fast start, gentle landing. */
        val decelerate = CubicBezierEasing(0f, 0f, 0f, 1f)
        /** Things leaving: gentle start, fast exit. */
        val accelerate = CubicBezierEasing(0.3f, 0f, 1f, 1f)
        /** Anticipation: a small dip backwards before moving forward. */
        val anticipate = CubicBezierEasing(0.5f, -0.45f, 0.6f, 1f)
        /** Overshoot: goes a touch past the target, then settles (follow-through). */
        val overshoot = CubicBezierEasing(0.34f, 1.45f, 0.64f, 1f)
    }

    object Springs {
        /** Playful settle with a small overshoot: buttons, chips, Blip. */
        fun <T> bouncy() = spring<T>(dampingRatio = 0.6f, stiffness = 900f)
        /** Calm, no overshoot: layout and colour. */
        fun <T> gentle() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
        /** Quick and firm: the squash on press. */
        fun <T> snappy() = spring<T>(dampingRatio = 0.75f, stiffness = 2400f)
    }

    fun <T> standard(duration: Int = Duration.medium) = tween<T>(duration, easing = Easing.standard)
}

/**
 * Squash & stretch on press with follow-through on release: the element squashes a little (wider,
 * shorter) while held, then springs back past its rest size and settles. It only observes touches,
 * so it works on top of any clickable without consuming events.
 */
fun Modifier.pressSquish(amount: Float = 0.06f): Modifier = composed {
    val sx = remember { Animatable(1f) }
    val sy = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    this
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                scope.launch { sx.animateTo(1f + amount * 0.6f, Motion.Springs.snappy()) }
                scope.launch { sy.animateTo(1f - amount, Motion.Springs.snappy()) }
                waitForUpOrCancellation(PointerEventPass.Initial)
                scope.launch { sx.animateTo(1f, Motion.Springs.bouncy()) }
                scope.launch { sy.animateTo(1f, Motion.Springs.bouncy()) }
            }
        }
        .graphicsLayer {
            scaleX = sx.value
            scaleY = sy.value
            transformOrigin = TransformOrigin(0.5f, 1f)
        }
}

/** Screens whose entrance already played this launch; revisiting a tab shows it instantly. */
private val playedIntros = mutableSetOf<String>()

/**
 * Staged entrance: a quick rise and fade, staggered 20 ms per [order] so a screen assembles in
 * reading order. It plays once per app launch for each [screen]; after that it's a no-op, so
 * switching tabs never waits on animation.
 */
fun Modifier.popIn(screen: String, order: Int = 0): Modifier = composed {
    val key = "$screen#$order"
    val firstTime = remember { key !in playedIntros }
    if (!firstTime) return@composed this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        playedIntros += key
        delay(order * 20L)
        progress.animateTo(1f, tween(Motion.Duration.medium, easing = Motion.Easing.decelerate))
    }
    graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = (1f - p) * 8.dp.toPx()
    }
}
