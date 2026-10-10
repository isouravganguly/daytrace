package dev.holdthisthought.ui.you

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.holdthisthought.game.BadgeState
import dev.holdthisthought.game.GameState
import dev.holdthisthought.ui.components.GameCard
import dev.holdthisthought.ui.components.BlipMood
import dev.holdthisthought.ui.components.HeroCard
import dev.holdthisthought.ui.components.emoji
import dev.holdthisthought.ui.theme.LocalGameColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

@Composable
fun ProfileSection(state: GameState) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        HeroCard(
            state = state,
            mood = if (state.streak >= 7 || state.today.perfect) BlipMood.PROUD else BlipMood.CHEERFUL,
            message = "${state.earnedBadges.size} badge${if (state.earnedBadges.size == 1) "" else "s"} and ${state.totalXp} XP so far. Nice tracing! ✨",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("🔥", "${state.streak}", "day streak", Modifier.weight(1f))
            Stat("🏆", "${state.bestStreak}", "best streak", Modifier.weight(1f))
            Stat("🎖️", "${state.earnedBadges.size}/${state.badges.size}", "badges", Modifier.weight(1f))
        }
        GameCard {
            Column {
                Text("Last 12 weeks", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                StreakCalendar(state)
            }
        }
        GameCard {
            Column {
                Text("Badges", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                BadgeGrid(state.badges)
            }
        }
    }
}

@Composable
private fun Stat(emoji: String, value: String, label: String, modifier: Modifier) {
    GameCard(modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(emoji, fontSize = 22.sp)
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** GitHub-style grid: one column per week, one cell per day, filled by quests done. */
@Composable
private fun StreakCalendar(state: GameState) {
    val game = LocalGameColors.current
    val today = state.today.date
    val firstMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(11)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        for (week in 0 until 12) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                for (dow in 0 until 7) {
                    val date = firstMonday.plusWeeks(week.toLong()).plusDays(dow.toLong())
                    val done = state.days[date]?.doneCount ?: 0
                    val future = date.isAfter(today)
                    val color = when {
                        future -> MaterialTheme.colorScheme.surfaceContainer
                        done == 0 -> MaterialTheme.colorScheme.surfaceContainerHighest
                        done == 5 -> game.xp
                        else -> game.fuel.copy(alpha = 0.25f + 0.18f * done)
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(color),
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(
        "Darker = more quests done · gold = perfect day",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun BadgeGrid(badges: List<BadgeState>) {
    BoxWithConstraints {
        val columns = if (maxWidth > 560.dp) 6 else 3
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            badges.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { BadgeTile(it, Modifier.weight(1f)) }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun BadgeTile(state: BadgeState, modifier: Modifier) {
    val game = LocalGameColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(if (state.earned) game.xp.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Text(state.badge.emoji, fontSize = 26.sp, modifier = Modifier.alpha(if (state.earned) 1f else 0.35f))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            state.badge.title,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            color = if (state.earned) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!state.earned) {
            Spacer(Modifier.height(4.dp))
            Box(Modifier.width(48.dp).height(5.dp).clip(CircleShape).background(game.track)) {
                Box(
                    Modifier
                        .fillMaxWidth(state.fraction.coerceAtLeast(0.04f))
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(game.reflect),
                )
            }
            Text(
                "${state.progress}/${state.badge.target}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                state.badge.description,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
    }
}
