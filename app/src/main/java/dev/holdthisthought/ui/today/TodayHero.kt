package dev.holdthisthought.ui.today

import dev.holdthisthought.ui.theme.Earth
import dev.holdthisthought.ui.theme.Forest
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.holdthisthought.data.local.Wakeup
import dev.holdthisthought.game.GameState
import dev.holdthisthought.ui.components.Blip
import dev.holdthisthought.ui.components.BlipMood
import dev.holdthisthought.ui.components.SpeechBubble
import dev.holdthisthought.ui.components.formatTime
import dev.holdthisthought.ui.components.icon
import dev.holdthisthought.ui.components.of
import dev.holdthisthought.ui.theme.LocalGameColors
import kotlinx.coroutines.delay

/**
 * The welcoming top of Today: Blip says the one next thing, the steps ring shows today's main
 * goal, and five dots show which quests are left. Everything else lives further down.
 */
@Composable
fun TodayHero(
    state: GameState,
    mood: BlipMood,
    message: String,
    bounceKey: Any?,
    steps: Int?,
    healthConnected: Boolean,
    onConnectHealth: (() -> Unit)?,
    wakeup: Wakeup?,
    sleepMinutes: Int,
    actions: (@Composable () -> Unit)?,
) {
    val game = LocalGameColors.current
    var reply by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(reply) {
        if (reply != null) {
            delay(2_800)
            reply = null
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(listOf(game.heroStart, game.heroEnd)))
            .drawBehind {
                // Two soft light circles for depth; calm, not flashy.
                drawCircle(Color.White.copy(alpha = 0.06f), radius = size.width * 0.45f, center = Offset(size.width, 0f))
                drawCircle(Color.White.copy(alpha = 0.04f), radius = size.width * 0.3f, center = Offset(0f, size.height))
            }
            .padding(18.dp),
    ) {
        Column {
            SpeechBubble(reply ?: message, bubble = HeroCream, ink = HeroInk)
            if (actions != null && reply == null) {
                Spacer(Modifier.height(10.dp))
                actions()
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Blip(mood = mood, level = state.level.number, size = 140.dp, bounceKey = bounceKey, onReact = { reply = it })
                Spacer(Modifier.weight(1f))
                StepsRing(steps = steps, goal = state.stepGoal, connected = healthConnected, onConnect = onConnectHealth)
                Spacer(Modifier.width(8.dp))
            }
            QuestDots(state)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Lv ${state.level.number} · ${state.level.title}")
                if (wakeup != null) Chip("☀ Up at ${formatTime(wakeup.time)}")
                if (sleepMinutes > 0) Chip("😴 Slept ${formatDuration(sleepMinutes * 60_000L)}")
            }
        }
    }
}

/** Cream on deep forest: bold but calm. */
private val HeroCream = Earth.Cream
private val HeroInk = Forest.F900
private val HeroMint = Earth.Mint

@Composable
private fun Chip(text: String) {
    Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.12f)) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = HeroCream.copy(alpha = 0.9f),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

/** Today's main goal: steps toward the user's target. */
@Composable
private fun StepsRing(steps: Int?, goal: Int, connected: Boolean, onConnect: (() -> Unit)?) {
    val game = LocalGameColors.current
    val fraction by animateFloatAsState(
        ((steps ?: 0).toFloat() / goal).coerceIn(0f, 1f),
        tween(900, easing = FastOutSlowInEasing),
        label = "steps",
    )
    val clickable = if (!connected && onConnect != null) Modifier.clickable(onClick = onConnect) else Modifier
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(132.dp).clip(CircleShape).then(clickable)) {
        Canvas(Modifier.fillMaxSize().padding(6.dp)) {
            val stroke = 11.dp.toPx()
            val inset = stroke / 2
            val arc = Size(size.width - stroke, size.height - stroke)
            drawArc(Color.White.copy(alpha = 0.16f), 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
            drawArc(HeroMint, -90f, 360f * fraction, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when {
                connected -> {
                    Text("👟", fontSize = 16.sp)
                    Text("%,d".format(steps ?: 0), style = MaterialTheme.typography.titleLarge, color = HeroCream)
                    Text(
                        if ((steps ?: 0) >= goal) "goal hit!" else "of %,d steps".format(goal),
                        style = MaterialTheme.typography.labelSmall,
                        color = HeroCream.copy(alpha = 0.75f),
                    )
                }
                onConnect != null -> {
                    Text("👟", fontSize = 22.sp)
                    Text("Count my\nsteps", style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, color = HeroCream)
                }
                else -> Text("👟", fontSize = 26.sp)
            }
        }
    }
}

/** "2 of 5 done" with one dot per quest, filled in its colour when done. */
@Composable
private fun QuestDots(state: GameState) {
    val game = LocalGameColors.current
    val left = state.questsLeft.size
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(
            when (left) {
                0 -> "All 5 done today 💎"
                else -> "${5 - left} of 5 done"
            },
            style = MaterialTheme.typography.titleSmall,
            color = HeroCream,
            modifier = Modifier.weight(1f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            state.today.quests.forEach { quest ->
                val color = game.of(quest.quest)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(if (quest.done) HeroCream else Color.White.copy(alpha = 0.14f)),
                ) {
                    Icon(
                        quest.quest.icon,
                        contentDescription = quest.quest.title + if (quest.done) " done" else "",
                        tint = if (quest.done) color else HeroCream.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}
