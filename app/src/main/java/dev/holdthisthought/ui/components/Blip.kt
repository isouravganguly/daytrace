package dev.holdthisthought.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** How Blip feels. Derived from the day, never stored. */
enum class BlipMood { SLEEPY, CHEERFUL, CURIOUS, EXCITED, PROUD }

/** What Blip does when you play with it. */
enum class BlipReaction(val durationMs: Int) {
    WAVE(1400), HOP(950), SPIN(1050), GIGGLE(950), PET(1800), DANCE(2200);

    val lines: List<String>
        get() = when (this) {
            WAVE -> listOf("Hi you! 👋", "Hey hey!", "Oh hello there!")
            HOP -> listOf("Wheee!", "Boing!", "Up we go!")
            SPIN -> listOf("Spinny!", "Did you see that?", "Ta-da! ✨")
            GIGGLE -> listOf("Hehe, that tickles!", "Boop!", "Hehehe")
            PET -> listOf("Aww, thank you 💚", "Best human ever", "More pats please")
            DANCE -> listOf("Dance break! 🕺", "Shake it off!", "We've got moves")
        }

    companion object {
        val taps = listOf(WAVE, HOP, SPIN, GIGGLE)
    }
}

/** Levels at which Blip visibly changes. */
object BlipEvolution {
    const val ANTENNA = 2
    const val SCARF = 4
    const val CROWN = 6
    const val SPARKLES = 8

    fun describe(level: Int): String? = when (level) {
        ANTENNA -> "Blip's sprout grew a little sun bud!"
        SCARF -> "Blip got a cosy scarf!"
        CROWN -> "Blip earned a crown!"
        SPARKLES -> "Blip is sparkling now!"
        else -> null
    }
}

/** Soft, matte colours: flat fills with one darker shade for volume, no gloss. */
private object BlipColors {
    val body = Color(0xFFA9C8E2)
    val shade = Color(0xFF8BAFCF)
    val belly = Color(0xFFEDF3F7)
    val limb = Color(0xFF8BAFCF)
    val ink = Color(0xFF26302B)
    val cheek = Color(0xFFEBB4BC)
    val mouth = Color(0xFFC98692)
    val tongue = Color(0xFFE9A3AE)
    val gold = Color(0xFFD9BC62)
    val scarf = Color(0xFF55836B)
    val leaf = Color(0xFF86BA93)
    val leafLight = Color(0xFFAED6B4)
    val stem = Color(0xFF6FA07E)
    val shadow = Color(0x1426302B)
    val heart = Color(0xFFDB9AA6)
}

/**
 * Blip: a cute, matte 2D character with a leaf sprout, arms and legs. It breathes, blinks, glances
 * around, waves now and then, and reacts when played with: tap for a random reaction, double-tap
 * to dance, long-press to pet. [onReact] gets a short line to show in a speech bubble.
 * [bounceKey] changing makes it hop (used for celebrations).
 *
 * Motion follows the 12 principles (see Motion.kt): every reaction is keyed poses with anticipation,
 * squash & stretch that keeps volume, eased in-betweens, arcs, and a sprout that follows through.
 */
@Composable
fun Blip(
    mood: BlipMood,
    level: Int,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    bounceKey: Any? = null,
    onReact: ((String) -> Unit)? = null,
) {
    val transition = rememberInfiniteTransition(label = "blip")
    // Breathing loop (slow in/out comes from the sine).
    val bob by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "bob")
    // Secondary action: a blink, sometimes a double blink.
    val blink by transition.animateFloat(
        1f, 1f,
        infiniteRepeatable(
            keyframes {
                durationMillis = 5200
                1f at 2400
                0.08f at 2480
                1f at 2580
                1f at 4800
                0.08f at 4880
                1f at 4960
                0.08f at 5040
                1f at 5120
            },
            RepeatMode.Restart,
        ),
        label = "blink",
    )
    // Idle cycle: glance left, glance right, then a little wave, every ~9 s.
    val idle by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "idle")

    var reaction by remember { mutableStateOf<BlipReaction?>(null) }
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val react by rememberUpdatedState(onReact)

    fun play(r: BlipReaction, say: Boolean = true) {
        reaction = r
        if (say) react?.invoke(r.lines.random())
        scope.launch {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(r.durationMs, easing = LinearEasing))
            reaction = null
        }
    }

    LaunchedEffect(bounceKey) { if (bounceKey != null) play(BlipReaction.HOP, say = false) }

    val gestures = if (onReact != null) {
        Modifier.pointerInput(Unit) {
            detectTapGestures(
                onTap = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    play(BlipReaction.taps.random())
                },
                onDoubleTap = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    play(BlipReaction.DANCE)
                },
                onLongPress = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    play(BlipReaction.PET)
                },
            )
        }
    } else Modifier

    Canvas(modifier.size(size).then(gestures)) {
        val u = this.size.minDimension / 100f
        val r = reaction
        val pose = pose(r, progress.value, bob, idle)
        drawBlip(u, mood, level, pose, blink, r, idle)
    }
}

// ---------- motion ----------

/** One frame of Blip's body motion, computed from keyed poses. */
private class Pose(
    val hop: Float = 0f, // body units above the ground
    val sx: Float = 1f, // squash & stretch, pivoting at the feet
    val sy: Float = 1f,
    val lean: Float = 0f, // degrees, around the feet
    val flip: Float = 1f, // horizontal turn for the spin
    val shake: Float = 0f, // body units, sideways
    val sprout: Float = 0f, // degrees: the sprout's follow-through
    val armL: Float = REST_ARM,
    val armR: Float = REST_ARM,
    val kick: Float = 0f,
    val t: Float = 0f,
)

private const val REST_ARM = 46f

private fun seg(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
private fun smooth(x: Float) = x * x * (3 - 2 * x)
private fun lerp(a: Float, b: Float, x: Float) = a + (b - a) * x
private fun sinf(x: Double) = sin(x).toFloat()

/** Squash with volume kept: when it gets shorter, it gets wider. */
private fun keepVolume(sy: Float) = 1f + (1f - sy) * 0.85f

/** A damped wobble after an impact: follow-through and settle. */
private fun wobble(x: Float, amp: Float, cycles: Float = 2.5f) =
    amp * sinf(x * cycles * 2 * PI) * (1f - x) * (1f - x)

private fun pose(r: BlipReaction?, t: Float, bob: Float, idle: Float): Pose {
    val breathe = sinf(bob * 2 * PI.toDouble())
    // Overlapping action: the sprout sways a quarter-cycle behind the breath.
    val idleSprout = 5f * sinf(bob * 2 * PI - PI / 2)
    return when (r) {
        null -> {
            val sy = 1f + 0.022f * breathe
            val waving = idle in 0.80f..0.96f
            val w = seg(idle, 0.80f, 0.96f)
            val env = smooth(seg(w, 0f, 0.15f)) * (1f - smooth(seg(w, 0.85f, 1f)))
            Pose(
                sx = 1f - 0.018f * breathe, sy = sy, sprout = idleSprout,
                armR = if (waving) lerp(REST_ARM, 138f + 18f * sinf(w * 5 * 2 * PI), env) else REST_ARM,
                lean = if (waving) -3f * env else 0f,
            )
        }
        BlipReaction.HOP -> {
            // Anticipation: crouch. Stretch on take-off, arc through the air, squash on landing,
            // overshoot, settle.
            val air = seg(t, 0.22f, 0.76f)
            val sy = when {
                t < 0.20f -> lerp(1f, 0.82f, smooth(seg(t, 0f, 0.20f)))
                t < 0.30f -> lerp(0.82f, 1.14f, smooth(seg(t, 0.20f, 0.30f)))
                t < 0.76f -> lerp(1.14f, 1f, smooth(seg(t, 0.30f, 0.55f)))
                t < 0.82f -> lerp(1f, 0.8f, smooth(seg(t, 0.76f, 0.82f)))
                t < 0.91f -> lerp(0.8f, 1.06f, smooth(seg(t, 0.82f, 0.91f)))
                else -> lerp(1.06f, 1f, smooth(seg(t, 0.91f, 1f)))
            }
            val inAir = t in 0.22f..0.76f
            val arms = when {
                t < 0.2f -> lerp(REST_ARM, 25f, smooth(seg(t, 0f, 0.2f))) // swing back
                t < 0.76f -> lerp(25f, 140f, smooth(seg(t, 0.2f, 0.4f))) // overlap: arms trail up
                else -> lerp(140f, REST_ARM, smooth(seg(t, 0.76f, 0.95f)))
            }
            Pose(
                hop = if (inAir) sinf(air * PI.toDouble()) * 20f else 0f,
                sx = keepVolume(sy), sy = sy,
                sprout = if (inAir) -16f * sinf(air * PI.toDouble()) else wobble(seg(t, 0.76f, 1f), 18f),
                armL = arms, armR = arms, t = t,
            )
        }
        BlipReaction.SPIN -> {
            val turn = smooth(seg(t, 0.18f, 0.85f))
            val sy = when {
                t < 0.18f -> lerp(1f, 0.88f, smooth(seg(t, 0f, 0.18f)))
                t < 0.85f -> lerp(0.88f, 1.04f, smooth(seg(t, 0.18f, 0.4f)))
                else -> 1f + wobble(seg(t, 0.85f, 1f), 0.06f, 1.5f)
            }
            Pose(
                flip = cos(turn * 2 * PI).toFloat(),
                sx = keepVolume(sy), sy = sy,
                lean = if (t < 0.18f) -6f * smooth(seg(t, 0f, 0.18f)) else -6f * (1f - smooth(seg(t, 0.18f, 0.4f))),
                hop = sinf(turn * PI.toDouble()) * 6f,
                sprout = if (t < 0.85f) 22f * sinf(turn * PI.toDouble()) else wobble(seg(t, 0.85f, 1f), 14f),
                armL = 125f, armR = 125f, t = t,
            )
        }
        BlipReaction.GIGGLE -> {
            val decay = 1f - smooth(t)
            val beat = sinf(t * 8 * PI)
            val sy = 1f + 0.05f * beat * decay
            Pose(
                shake = beat * 2.4f * decay, sx = keepVolume(sy), sy = sy,
                sprout = beat * 10f * decay, armL = 58f, armR = 58f, t = t,
            )
        }
        BlipReaction.WAVE -> {
            val env = smooth(seg(t, 0f, 0.15f)) * (1f - smooth(seg(t, 0.85f, 1f)))
            Pose(
                sx = 1f - 0.018f * breathe, sy = 1f + 0.022f * breathe,
                armR = lerp(REST_ARM, 138f + 20f * sinf(t * 6 * PI), env),
                lean = -4f * env, sprout = idleSprout + 6f * env * sinf(t * 6 * PI - 0.6), t = t,
            )
        }
        BlipReaction.PET -> {
            // Melts happily under the pat, then springs back.
            val squish = sinf(seg(t, 0f, 0.8f) * PI.toDouble())
            val sy = 1f - 0.08f * squish + wobble(seg(t, 0.8f, 1f), 0.04f)
            Pose(sx = keepVolume(sy), sy = sy, sprout = -10f * squish, armL = 72f, armR = 72f, t = t)
        }
        BlipReaction.DANCE -> {
            val env = smooth(seg(t, 0f, 0.1f)) * (1f - smooth(seg(t, 0.9f, 1f)))
            val beat = sinf(t * 4 * PI)
            val bounce = kotlin.math.abs(sinf(t * 8 * PI))
            val sy = 1f - 0.07f * (1f - bounce) * env
            Pose(
                lean = beat * 10f * env, hop = bounce * 4f * env,
                sx = keepVolume(sy), sy = sy,
                sprout = -beat * 16f * env, // lags opposite the lean
                armL = 95f + beat * 45f * env, armR = 95f - beat * 45f * env,
                kick = beat * env, t = t,
            )
        }
    }
}

// ---------- drawing ----------

private const val BODY_CY = 58f
private const val BODY_W = 60f
private const val BODY_H = 54f
private const val GROUND = 90f

private fun DrawScope.drawBlip(
    u: Float,
    mood: BlipMood,
    level: Int,
    pose: Pose,
    blink: Float,
    reaction: BlipReaction?,
    idle: Float,
) {
    val cx = size.width / 2
    val hop = pose.hop * u
    val feet = Offset(cx, GROUND * u)

    // Ground shadow: shrinks and fades as Blip rises, widens on a squash.
    val shadowW = (44f - pose.hop * 0.9f) * u * pose.sx
    drawOval(
        BlipColors.shadow.copy(alpha = BlipColors.shadow.alpha * (1f - pose.hop / 40f)),
        Offset(cx - shadowW / 2, (GROUND + 1f) * u), Size(shadowW, 6 * u),
    )

    translate(left = pose.shake * u, top = -hop) {
        rotate(pose.lean, pivot = feet) {
            scale(scaleX = pose.sx * pose.flip, scaleY = pose.sy, pivot = feet) {
                drawLegs(cx, u, pose.kick)
                // Arms first, so the body hides the shoulder joints.
                drawArms(cx, u, pose.armL, pose.armR)
                drawSprout(cx, u, pose.sprout, level)
                drawBody(cx, u)
                if (level >= BlipEvolution.SCARF) drawScarf(cx, u)
                if (pose.flip > -0.2f) drawFace(cx, u, mood, blink, reaction, idle, pose.flip)
                if (level >= BlipEvolution.CROWN) drawCrown(cx, u)
            }
        }
    }
    if (level >= BlipEvolution.SPARKLES) drawSparkles(cx, u, idle * 3f)
    if (reaction == BlipReaction.PET) drawHearts(cx, u, pose.t)
}

private fun bodyPath(cx: Float, u: Float) = Path().apply {
    addOval(Rect(cx - BODY_W / 2 * u, (BODY_CY - BODY_H / 2) * u, cx + BODY_W / 2 * u, (BODY_CY + BODY_H / 2) * u))
}

private fun DrawScope.drawBody(cx: Float, u: Float) {
    val path = bodyPath(cx, u)
    clipPath(path) {
        // Cel shade: the darker colour shows as a crescent on the lower right.
        drawPath(path, BlipColors.shade)
        drawOval(
            BlipColors.body,
            topLeft = Offset(cx - BODY_W / 2 * u - 4 * u, (BODY_CY - BODY_H / 2) * u - 4 * u),
            size = Size((BODY_W + 1) * u, (BODY_H + 1) * u),
        )
        // Belly patch.
        drawOval(BlipColors.belly, Offset(cx - 14 * u, 68 * u), Size(28 * u, 18 * u))
    }
}

private fun DrawScope.drawFace(
    cx: Float, u: Float, mood: BlipMood, blink: Float, reaction: BlipReaction?, idle: Float, flip: Float,
) {
    val ink = BlipColors.ink
    // Baby-schema proportions: eyes low and wide on a big head.
    val eyeY = 57 * u
    val spacing = 11f * u
    // Secondary action: glance left, then right, in the idle cycle.
    val glance = when {
        reaction != null -> 0f
        idle in 0.30f..0.42f -> -1.6f * smooth(seg(idle, 0.30f, 0.32f)) * (1 - smooth(seg(idle, 0.40f, 0.42f)))
        idle in 0.52f..0.64f -> 1.6f * smooth(seg(idle, 0.52f, 0.54f)) * (1 - smooth(seg(idle, 0.62f, 0.64f)))
        else -> 0f
    } * u
    val happyEyes = mood == BlipMood.PROUD || reaction == BlipReaction.PET || reaction == BlipReaction.GIGGLE
    for (side in listOf(-1f, 1f)) {
        val ex = cx + side * spacing + glance
        when {
            mood == BlipMood.SLEEPY && reaction == null -> {
                val p = Path().apply {
                    moveTo(ex - 3.4f * u, eyeY)
                    quadraticTo(ex, eyeY + 2.6f * u, ex + 3.4f * u, eyeY)
                }
                drawPath(p, ink, style = Stroke(1.9f * u, cap = StrokeCap.Round))
            }
            happyEyes -> {
                val p = Path().apply {
                    moveTo(ex - 3.6f * u, eyeY + 1.2f * u)
                    quadraticTo(ex, eyeY - 3.4f * u, ex + 3.6f * u, eyeY + 1.2f * u)
                }
                drawPath(p, ink, style = Stroke(2f * u, cap = StrokeCap.Round))
            }
            else -> {
                val big = mood == BlipMood.EXCITED || reaction != null
                val w = (if (big) 7.4f else 6.6f) * u
                val h = (if (big) 8.8f else 8f) * u * blink
                drawOval(ink, Offset(ex - w / 2, eyeY - h / 2), Size(w, h))
                if (blink > 0.5f) {
                    // Two catch-lights make the eyes read as glossy and alive.
                    drawCircle(Color.White, radius = 1.6f * u, center = Offset(ex - 1.3f * u, eyeY - 1.9f * u))
                    drawCircle(Color.White.copy(alpha = 0.8f), radius = 0.75f * u, center = Offset(ex + 1.4f * u, eyeY + 1.6f * u))
                }
            }
        }
        drawOval(
            BlipColors.cheek.copy(alpha = if (reaction == BlipReaction.PET || reaction == BlipReaction.GIGGLE) 0.95f else 0.7f),
            Offset(cx + side * 19.5f * u - 4.5f * u + glance * 0.5f, 61.5f * u),
            Size(9 * u, 5 * u),
        )
    }
    val mx = cx + glance * 0.6f
    val my = 63.5f * u
    val open = mood == BlipMood.EXCITED || reaction == BlipReaction.HOP || reaction == BlipReaction.DANCE ||
        reaction == BlipReaction.SPIN || reaction == BlipReaction.WAVE
    when {
        open -> {
            val p = Path().apply {
                moveTo(mx - 3.4f * u, my - 0.4f * u)
                lineTo(mx + 3.4f * u, my - 0.4f * u)
                quadraticTo(mx + 3.2f * u, my + 4.4f * u, mx, my + 4.4f * u)
                quadraticTo(mx - 3.2f * u, my + 4.4f * u, mx - 3.4f * u, my - 0.4f * u)
                close()
            }
            drawPath(p, BlipColors.mouth)
            clipPath(p) { drawCircle(BlipColors.tongue, radius = 2.4f * u, center = Offset(mx, my + 4.6f * u)) }
        }
        mood == BlipMood.SLEEPY && reaction == null ->
            drawOval(ink, Offset(mx - 1.3f * u, my), Size(2.6f * u, 2.4f * u))
        mood == BlipMood.CURIOUS && reaction == null ->
            drawCircle(ink, radius = 1.4f * u, center = Offset(mx, my + 1 * u))
        else -> {
            // A small "w" smile: the cutest default.
            val p = Path().apply {
                moveTo(mx - 3.6f * u, my)
                quadraticTo(mx - 1.8f * u, my + 2.8f * u, mx, my + 0.5f * u)
                quadraticTo(mx + 1.8f * u, my + 2.8f * u, mx + 3.6f * u, my)
            }
            drawPath(p, ink, style = Stroke(1.6f * u, cap = StrokeCap.Round))
        }
    }
}

/** Little capsule arms from behind the body. Angles are degrees from straight down, outward. */
private fun DrawScope.drawArms(cx: Float, u: Float, left: Float, right: Float) {
    for ((side, angle) in listOf(-1f to left, 1f to right)) {
        val shoulder = Offset(cx + side * 22.5f * u, 66 * u)
        val rad = Math.toRadians(angle.toDouble())
        val len = 13.5f * u
        val hand = Offset(
            shoulder.x + side * (sin(rad) * len).toFloat(),
            shoulder.y + (cos(rad) * len).toFloat(),
        )
        drawLine(BlipColors.limb, shoulder, hand, strokeWidth = 8.5f * u, cap = StrokeCap.Round)
    }
}

private fun DrawScope.drawLegs(cx: Float, u: Float, kick: Float) {
    for (side in listOf(-1f, 1f)) {
        val x = cx + side * 9.5f * u
        val lift = (if (side < 0) kick.coerceAtLeast(0f) else (-kick).coerceAtLeast(0f)) * 3 * u
        drawLine(BlipColors.limb, Offset(x, 80 * u), Offset(x, 87 * u - lift), strokeWidth = 8f * u, cap = StrokeCap.Round)
        drawOval(BlipColors.limb, Offset(x - 5.5f * u + side * 1.5f * u, 85.5f * u - lift), Size(11 * u, 6 * u))
    }
}

/** A two-leaf sprout on Blip's head. It lags behind the body (follow-through). */
private fun DrawScope.drawSprout(cx: Float, u: Float, tilt: Float, level: Int) {
    val base = Offset(cx + 1 * u, (BODY_CY - BODY_H / 2 + 2) * u)
    rotate(tilt, pivot = base) {
        val tip = Offset(base.x + 1.5f * u, base.y - 9 * u)
        val stem = Path().apply {
            moveTo(base.x, base.y)
            quadraticTo(base.x - 1.5f * u, base.y - 5 * u, tip.x, tip.y)
        }
        drawPath(stem, BlipColors.stem, style = Stroke(2.2f * u, cap = StrokeCap.Round))
        rotate(-38f, pivot = tip) {
            drawOval(BlipColors.leaf, Offset(tip.x - 10.5f * u, tip.y - 3 * u), Size(11 * u, 6 * u))
        }
        rotate(32f, pivot = tip) {
            drawOval(BlipColors.leafLight, Offset(tip.x - 0.5f * u, tip.y - 3.2f * u), Size(11.5f * u, 6.4f * u))
        }
        if (level >= BlipEvolution.ANTENNA) {
            drawCircle(BlipColors.gold, radius = 3.4f * u, center = Offset(tip.x, tip.y - 2.5f * u))
        }
    }
}

private fun DrawScope.drawScarf(cx: Float, u: Float) {
    val y = 74 * u
    clipPath(bodyPath(cx, u)) {
        drawRect(BlipColors.scarf, Offset(cx - 32 * u, y - 3.5f * u), Size(64 * u, 7 * u))
    }
    drawRoundRect(
        BlipColors.scarf,
        topLeft = Offset(cx + 13 * u, y),
        size = Size(6 * u, 11 * u),
        cornerRadius = CornerRadius(2 * u),
    )
}

private fun DrawScope.drawCrown(cx: Float, u: Float) {
    val y = 35 * u
    val x = cx - 13 * u
    val p = Path().apply {
        moveTo(x - 7 * u, y)
        lineTo(x - 7 * u, y - 6 * u)
        lineTo(x - 3.5f * u, y - 2.5f * u)
        lineTo(x, y - 8 * u)
        lineTo(x + 3.5f * u, y - 2.5f * u)
        lineTo(x + 7 * u, y - 6 * u)
        lineTo(x + 7 * u, y)
        close()
    }
    rotate(-14f, pivot = Offset(x, y)) { drawPath(p, BlipColors.gold) }
}

private fun DrawScope.drawSparkles(cx: Float, u: Float, phase: Float) {
    val spots = listOf(Offset(-32f, 32f), Offset(33f, 42f), Offset(-28f, 74f))
    spots.forEachIndexed { i, o ->
        val twinkle = ((sin((phase + i / 3f) * 2 * PI) + 1) / 2).toFloat()
        val c = Offset(cx + o.x * u, o.y * u)
        val r = (1.5f + 2.5f * twinkle) * u
        val star = Path().apply {
            moveTo(c.x, c.y - r * 1.6f); lineTo(c.x + r * 0.4f, c.y - r * 0.4f)
            lineTo(c.x + r * 1.6f, c.y); lineTo(c.x + r * 0.4f, c.y + r * 0.4f)
            lineTo(c.x, c.y + r * 1.6f); lineTo(c.x - r * 0.4f, c.y + r * 0.4f)
            lineTo(c.x - r * 1.6f, c.y); lineTo(c.x - r * 0.4f, c.y - r * 0.4f)
            close()
        }
        drawPath(star, BlipColors.gold.copy(alpha = 0.5f + 0.5f * twinkle))
    }
}

/** Hearts drift up on gentle arcs, easing out as they fade. */
private fun DrawScope.drawHearts(cx: Float, u: Float, t: Float) {
    listOf(-15f to 0f, 4f to 0.2f, 17f to 0.4f).forEach { (dx, delay) ->
        val raw = ((t - delay) / (1f - delay)).coerceIn(0f, 1f)
        if (raw <= 0f) return@forEach
        val p = 1f - (1f - raw) * (1f - raw) // ease out
        val c = Offset(cx + dx * u + sin(raw * 2 * PI).toFloat() * 3 * u, (32 - 24 * p) * u)
        val r = 3.4f * u * (0.6f + 0.4f * smooth(seg(raw, 0f, 0.25f)))
        val heart = Path().apply {
            moveTo(c.x, c.y + r)
            cubicTo(c.x - r * 2f, c.y - r * 0.2f, c.x - r * 0.8f, c.y - r * 1.8f, c.x, c.y - r * 0.6f)
            cubicTo(c.x + r * 0.8f, c.y - r * 1.8f, c.x + r * 2f, c.y - r * 0.2f, c.x, c.y + r)
            close()
        }
        drawPath(heart, BlipColors.heart.copy(alpha = 1f - raw * 0.8f))
    }
}
