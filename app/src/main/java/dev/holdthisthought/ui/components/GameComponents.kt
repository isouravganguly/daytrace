package dev.holdthisthought.ui.components

import dev.holdthisthought.ui.theme.Motion
import dev.holdthisthought.ui.theme.Forest
import dev.holdthisthought.ui.theme.Earth
import dev.holdthisthought.ui.theme.pressSquish
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.holdthisthought.game.Badge
import dev.holdthisthought.game.GameState
import dev.holdthisthought.game.Xp
import dev.holdthisthought.game.Level
import dev.holdthisthought.game.Quest
import dev.holdthisthought.game.QuestState
import dev.holdthisthought.ui.theme.GameColors
import dev.holdthisthought.ui.theme.LocalGameColors
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

fun GameColors.of(quest: Quest): Color = when (quest) {
    Quest.RISE -> rise
    Quest.REFLECT -> reflect
    Quest.FUEL -> fuel
    Quest.MOVE -> move
    Quest.VERDICT -> verdict
}

val Quest.icon: ImageVector
    get() = when (this) {
        Quest.RISE -> Icons.Rounded.WbSunny
        Quest.REFLECT -> Icons.Rounded.Lightbulb
        Quest.FUEL -> Icons.Rounded.Restaurant
        Quest.MOVE -> Icons.Rounded.DirectionsWalk
        Quest.VERDICT -> Icons.Rounded.Gavel
    }

val Badge.emoji: String
    get() = when (this) {
        Badge.FIRST_LIGHT -> "🌅"
        Badge.EARLY_BIRD -> "🐦"
        Badge.CURIOUS_MIND -> "🧠"
        Badge.SCHOLAR -> "📚"
        Badge.SAGE -> "🦉"
        Badge.WISHMAKER -> "🌠"
        Badge.WELL_FED -> "🍱"
        Badge.WANDERER -> "🥾"
        Badge.WEEK_WARRIOR -> "🔥"
        Badge.MARATHON -> "🏅"
        Badge.PERFECT_DAY -> "💎"
        Badge.HONEST_MIRROR -> "🪞"
    }

/** A progress ring with an icon; fills with the quest's colour and pops when complete. */
@Composable
fun QuestRing(
    state: QuestState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 60.dp,
) {
    val color = LocalGameColors.current.of(state.quest)
    val track = LocalGameColors.current.track
    val sweep by animateFloatAsState(
        state.fraction,
        animationSpec = Motion.Springs.gentle(),
        label = "quest-sweep",
    )
    val pop by animateFloatAsState(
        if (state.done) 1f else 0.92f,
        animationSpec = Motion.Springs.bouncy(),
        label = "quest-pop",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .pressSquish()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size).scale(pop)) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 6.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                drawArc(
                    color, -90f, 360f * sweep, false, Offset(inset, inset), arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
                if (state.done) drawCircle(color.copy(alpha = 0.16f), radius = this.size.minDimension / 2 - stroke)
            }
            Icon(
                if (state.done) Icons.Rounded.Check else state.quest.icon,
                contentDescription = null,
                tint = if (state.done) color else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.42f),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            state.quest.title,
            style = MaterialTheme.typography.labelMedium,
            color = if (state.done) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The hero: Blip with a speech bubble, the level, XP progress and today's XP goal, on a soft
 * tinted card. Play with Blip (tap, double-tap, long-press) and it answers in the bubble.
 * [actions] renders under the bubble (the nudge's quick actions).
 */
@Composable
fun HeroCard(
    state: GameState,
    mood: BlipMood,
    message: String,
    modifier: Modifier = Modifier,
    bounceKey: Any? = null,
    actions: (@Composable () -> Unit)? = null,
) {
    val game = LocalGameColors.current
    val colors = MaterialTheme.colorScheme
    val level = state.level
    val progress by animateFloatAsState(level.fraction, Motion.standard(Motion.Duration.hero), label = "xp")
    val goal by animateFloatAsState(state.dailyGoalFraction, Motion.standard(Motion.Duration.hero), label = "goal")
    var reply by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(reply) {
        if (reply != null) {
            delay(2_800)
            reply = null
        }
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(listOf(game.heroStart, game.heroEnd)))
            .padding(18.dp),
    ) {
        Column {
            SpeechBubble(reply ?: message, bubble = Earth.Cream, ink = Forest.F900)
            if (actions != null && reply == null) {
                Spacer(Modifier.height(10.dp))
                actions()
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Blip(
                    mood = mood,
                    level = level.number,
                    size = 136.dp,
                    bounceKey = bounceKey,
                    onReact = { reply = it },
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("LEVEL ${level.number}", color = Earth.Mint, style = MaterialTheme.typography.labelMedium)
                    Text(level.title, color = Earth.Cream, style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(Earth.Cream.copy(alpha = 0.18f))) {
                        Box(
                            Modifier
                                .fillMaxWidth(progress.coerceAtLeast(0.03f))
                                .height(10.dp)
                                .clip(CircleShape)
                                .background(game.xp),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${level.xpIntoLevel} / ${level.xpForLevel} XP to level ${level.number + 1}",
                        color = Earth.Cream.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Spacer(Modifier.height(10.dp))
                    Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.12f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(24.dp)) {
                                Canvas(Modifier.fillMaxSize()) {
                                    val stroke = 3.dp.toPx()
                                    drawCircle(Earth.Cream.copy(alpha = 0.2f), style = Stroke(stroke), radius = size.minDimension / 2 - stroke / 2)
                                    drawArc(
                                        game.xp, -90f, 360f * goal, false,
                                        topLeft = Offset(stroke / 2, stroke / 2),
                                        size = Size(size.width - stroke, size.height - stroke),
                                        style = Stroke(stroke, cap = StrokeCap.Round),
                                    )
                                }
                                Text(if (goal >= 1f) "★" else "", color = game.xp, fontSize = 11.sp)
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (goal >= 1f) "Daily goal hit!" else "Today ${state.today.xp}/${Xp.DAILY_GOAL} XP",
                                color = Earth.Cream,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }
            Text(
                "Tap, double-tap or hold Blip",
                style = MaterialTheme.typography.labelSmall,
                color = Earth.Cream.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
fun SpeechBubble(
    message: String,
    bubble: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ink: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column {
        Surface(shape = RoundedCornerShape(20.dp), color = bubble, shadowElevation = 1.dp) {
            Text(
                message,
                style = MaterialTheme.typography.titleMedium,
                color = ink,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        // Tail pointing down at Blip.
        Canvas(Modifier.padding(start = 44.dp).size(18.dp, 10.dp)) {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width * 0.3f, size.height)
                close()
            }
            drawPath(path, bubble)
        }
    }
}

@Composable
fun StreakChip(streak: Int, modifier: Modifier = Modifier, onDark: Boolean = false) {
    val flame = LocalGameColors.current.flame
    Surface(
        shape = CircleShape,
        color = when {
            onDark -> Color.White.copy(alpha = 0.22f)
            streak > 0 -> flame.copy(alpha = 0.14f)
            else -> MaterialTheme.colorScheme.primaryContainer
        },
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(if (streak > 0) "🔥" else "🌱", fontSize = 16.sp)
            Spacer(Modifier.width(4.dp))
            Text(
                "$streak",
                style = MaterialTheme.typography.titleMedium,
                color = when {
                    onDark -> Color.White
                    streak > 0 -> flame
                    else -> MaterialTheme.colorScheme.onPrimaryContainer
                },
            )
        }
    }
}

/** A rounded tile used across Today and You. */
@Composable
fun GameCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    content: @Composable () -> Unit,
) {
    val base = MaterialTheme.colorScheme.surfaceContainerLowest
    Surface(
        shape = MaterialTheme.shapes.large,
        // Translucent tints are flattened onto the card colour; a see-through card shows its shadow.
        color = if (color.alpha < 1f) color.compositeOver(base) else color,
        // Flat with a hairline edge: calmer than shadows, and clean in dark mode.
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(Modifier.padding(18.dp)) { content() }
    }
}

@Composable
fun CardTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** A big tappable emoji button (mood faces, verdicts). */
@Composable
fun EmojiChoice(
    emoji: String,
    label: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    val scale by animateFloatAsState(
        if (selected) 1.12f else 1f,
        Motion.Springs.bouncy(),
        label = "emoji-scale",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .pressSquish(0.1f)
            .clip(MaterialTheme.shapes.medium)
            .background(if (selected) accent.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
    ) {
        Text(emoji, fontSize = 30.sp, modifier = Modifier.scale(scale))
        if (label != null) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** What the overlay celebrates after a log. */
data class Celebration(
    val id: Long,
    val xp: Int,
    val questsDone: List<Quest>,
    val levelUp: Level?,
    val perfect: Boolean,
    val newBadges: List<Badge> = emptyList(),
) {
    val big: Boolean get() = questsDone.isNotEmpty() || levelUp != null || perfect || newBadges.isNotEmpty()
}

/** Floating "+15 XP" toast, plus confetti for quests, level-ups and perfect days. */
@Composable
fun CelebrationOverlay(celebration: Celebration?, modifier: Modifier = Modifier) {
    if (celebration == null) return
    Box(modifier.fillMaxSize()) {
        if (celebration.big) Confetti(key = celebration.id)
        XpToast(celebration, Modifier.align(Alignment.TopCenter).padding(top = 12.dp))
    }
}

@Composable
private fun XpToast(celebration: Celebration, modifier: Modifier) {
    val anim = remember(celebration.id) { Animatable(0f) }
    LaunchedEffect(celebration.id) {
        anim.animateTo(1f, tween(1600, easing = LinearEasing))
    }
    val t = anim.value
    // Rise in, hold, fade out.
    val alpha = when {
        t < 0.1f -> t / 0.1f
        t > 0.8f -> (1f - t) / 0.2f
        else -> 1f
    }
    val offset = (1f - minOf(t / 0.15f, 1f)) * -24f
    if (t >= 1f) return
    val game = LocalGameColors.current
    val headline = when {
        celebration.levelUp != null -> "Level up! ${celebration.levelUp.number} · ${celebration.levelUp.title}"
        celebration.perfect -> "Perfect day! 💎"
        celebration.newBadges.isNotEmpty() -> celebration.newBadges.joinToString(prefix = "Badge unlocked: ") { "${it.title} ${it.emoji}" }
        celebration.questsDone.isNotEmpty() -> "${celebration.questsDone.joinToString { it.title }} quest done!"
        else -> null
    }
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.inverseSurface,
        shadowElevation = 6.dp,
        modifier = modifier
            .offset(y = offset.dp)
            .graphicsLayer {
                this.alpha = alpha
                scaleX = 0.9f + 0.1f * alpha
                scaleY = 0.9f + 0.1f * alpha
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            if (celebration.xp > 0) {
                Text(
                    "+${celebration.xp} XP",
                    color = game.xp,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (headline != null) {
                Text(
                    headline,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

private data class Particle(val angle: Float, val speed: Float, val color: Color, val spin: Float, val size: Float)

@Composable
private fun Confetti(key: Long) {
    val game = LocalGameColors.current
    val colors = listOf(game.rise, game.reflect, game.fuel, game.move, game.verdict, game.xp)
    val particles = remember(key) {
        val random = Random(key)
        List(70) {
            Particle(
                angle = (-160 + random.nextFloat() * 140f) * (Math.PI / 180).toFloat(),
                speed = 0.55f + random.nextFloat() * 0.6f,
                color = colors[random.nextInt(colors.size)],
                spin = random.nextFloat() * 720f - 360f,
                size = 6f + random.nextFloat() * 8f,
            )
        }
    }
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { progress.animateTo(1f, tween(1100, easing = LinearEasing)) }
    val t = progress.value
    if (t >= 1f) return
    Canvas(Modifier.fillMaxSize()) {
        val origin = Offset(size.width / 2, size.height * 0.28f)
        val reach = size.minDimension * 0.9f
        particles.forEach { p ->
            val distance = reach * p.speed * t
            val gravity = size.height * 0.55f * t * t
            val x = origin.x + cos(p.angle) * distance
            val y = origin.y + sin(p.angle) * distance + gravity
            val alpha = (1f - t).coerceIn(0f, 1f)
            rotate(p.spin * t, Offset(x, y)) {
                drawRoundRect(
                    color = p.color.copy(alpha = alpha),
                    topLeft = Offset(x - p.size / 2, y - p.size / 4),
                    size = Size(p.size, p.size / 2),
                    cornerRadius = CornerRadius(2f),
                )
            }
        }
    }
}

/** Full-screen "level up" moment. Evolution levels get their own line. */
@Composable
fun LevelUpDialog(level: Level, onDismiss: () -> Unit) {
    val game = LocalGameColors.current
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraLarge)
                .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceContainerLowest)))
                .padding(24.dp),
        ) {
            Confetti(key = level.number.toLong())
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("LEVEL UP", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                Blip(mood = BlipMood.EXCITED, level = level.number, size = 190.dp, bounceKey = level.number, onReact = {})
                Text(
                    "Level ${level.number} · ${level.title}",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    BlipEvolution.describe(level.number) ?: "Every trace counts. Keep going!",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(18.dp))
                androidx.compose.material3.Button(
                    onClick = onDismiss,
                ) { Text("Let's go!") }
            }
        }
    }
}
