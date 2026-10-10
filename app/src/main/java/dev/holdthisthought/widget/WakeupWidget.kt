package dev.holdthisthought.widget

import dev.holdthisthought.ui.theme.Forest
import dev.holdthisthought.ui.theme.Stone
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.holdthisthought.MainActivity
import dev.holdthisthought.R
import dev.holdthisthought.ThoughtApplication
import dev.holdthisthought.data.local.MealSlot
import dev.holdthisthought.data.local.Wakeup
import dev.holdthisthought.data.local.WakeupState
import dev.holdthisthought.game.DayClock
import dev.holdthisthought.game.GameEngine
import dev.holdthisthought.game.Quest
import dev.holdthisthought.game.QuestState
import androidx.glance.appwidget.LinearProgressIndicator
import dev.holdthisthought.game.Nudge
import dev.holdthisthought.game.NudgeEngine
import dev.holdthisthought.ui.today.emoji
import dev.holdthisthought.ui.today.label
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Blip's poses on the widget. */
enum class WidgetPose(val drawable: Int) {
    SLEEPY(R.drawable.blip_sleepy),
    WAVE(R.drawable.blip_wave),
    HAPPY(R.drawable.blip_happy),
    CHEER(R.drawable.blip_cheer),
}

/**
 * Home-screen widget: Blip plus the one thing to log right now, loggable in one tap without
 * opening the app (Good morning, a meal, a mood). Tap Blip to play with it.
 */
class WakeupWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single

    data class State(
        val wakeup: Wakeup?,
        val streak: Int,
        val quests: List<QuestState>,
        val date: LocalDate,
        val nudge: Nudge?,
        val pose: WidgetPose,
        /** A short reaction after a tap, shown instead of the title for a minute. */
        val reaction: String?,
        val steps: Int?,
        val stepGoal: Int,
    ) {
        val done: Int get() = quests.count { it.done }
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as ThoughtApplication).container
        val today = DayClock.today()
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val hour = LocalTime.now().hour
        val wakeups = container.traceDao.allWakeups()
        val entries = container.checkIns.allEntries()
        val health = container.traceDao.allHealth()
        val game = GameEngine.compute(
            thoughts = container.repository.allVisible(),
            entries = entries,
            wakeups = wakeups,
            wakeTargetMinutes = container.nudgePrefs.settings.value.schedule.wake,
            today = today,
            health = health,
            stepGoal = container.preferences.stepGoal.value,
        )
        val wakeup = wakeups.firstOrNull { it.day == today.toString() }
        val todayEntries = entries.filter { DayClock.logicalDate(it.occurredAt, zone) == today }
        val nudge = NudgeEngine.next(
            game.today, todayEntries, LocalTime.now().let { it.hour * 60 + it.minute }, now,
            autoMeals = health.firstOrNull { it.day == today.toString() }?.meals.orEmpty(),
            schedule = container.nudgePrefs.settings.value.schedule,
        )
        val reaction = WidgetReactions.recent(context, now)

        val pose = reaction?.first ?: when {
            wakeup == null && nudge == Nudge.WakeUp -> WidgetPose.SLEEPY
            nudge == Nudge.GoodNight -> WidgetPose.SLEEPY
            game.today.perfect -> WidgetPose.CHEER
            nudge != null -> WidgetPose.WAVE
            else -> WidgetPose.HAPPY
        }
        val todayHealth = health.firstOrNull { it.day == today.toString() }
        val state = State(
            wakeup = wakeup,
            streak = game.streak,
            quests = game.today.quests,
            date = today,
            nudge = nudge,
            pose = pose,
            reaction = reaction?.second,
            steps = todayHealth?.steps,
            stepGoal = game.stepGoal,
        )
        provideContent { Content(state) }
    }

    @Composable
    private fun Content(state: State) {
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ImageProvider(R.drawable.widget_background))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                ImageProvider(state.pose.drawable),
                contentDescription = "Blip. Tap to play.",
                modifier = GlanceModifier.size(92.dp).clickable(actionRunCallback<PokeBlipAction>()),
            )
            Spacer(GlanceModifier.width(8.dp))
            Column(modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity<MainActivity>())) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
                    Text(
                        state.reaction ?: when (state.done) {
                            5 -> "All done today 💎"
                            else -> "${state.done} of 5 done"
                        },
                        style = TextStyle(color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        modifier = GlanceModifier.defaultWeight(),
                    )
                    if (state.streak > 0) {
                        Text("🔥${state.streak}", style = TextStyle(color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold))
                    }
                }
                Spacer(GlanceModifier.height(6.dp))
                QuestChips(state)
                state.steps?.let { steps ->
                    Spacer(GlanceModifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = (steps.toFloat() / state.stepGoal).coerceIn(0f, 1f),
                        color = ColorProvider(Forest.F600),
                        backgroundColor = ColorProvider(Stone.S150),
                        modifier = GlanceModifier.fillMaxWidth().height(8.dp),
                    )
                    Spacer(GlanceModifier.height(3.dp))
                    Text(
                        if (steps >= state.stepGoal) "👟 %,d steps · goal hit!".format(steps)
                        else "👟 %,d / %,d steps".format(steps, state.stepGoal),
                        style = TextStyle(color = Muted, fontSize = 12.sp),
                    )
                }
                Spacer(GlanceModifier.height(8.dp))
                Actions(state)
            }
        }
    }

    /** One small chip per quest: filled when done, outlined when still to do. */
    @Composable
    private fun QuestChips(state: State) {
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            state.quests.forEachIndexed { i, quest ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = GlanceModifier
                        .size(width = 34.dp, height = 26.dp)
                        .background(ImageProvider(if (quest.done) R.drawable.widget_dot_done else R.drawable.widget_dot_left)),
                ) {
                    Text(quest.quest.emoji, style = TextStyle(fontSize = 13.sp))
                }
                if (i < state.quests.lastIndex) Spacer(GlanceModifier.width(4.dp))
            }
        }
    }

    @Composable
    private fun Actions(state: State) {
        val wakeup = state.wakeup
        val nudge = state.nudge
        when {
            nudge == Nudge.GoodNight -> Pill("🌙  Good night", actionRunCallback<GoodNightAction>())
            wakeup == null && nudge == Nudge.WakeUp -> Pill("☀  Good morning", actionRunCallback<GoodMorningAction>())
            nudge is Nudge.Meal -> Pill(
                "${nudge.slot.emoji}  Ate ${nudge.slot.label.lowercase()}",
                actionRunCallback<LogMealAction>(actionParametersOf(LogMealAction.Slot to nudge.slot.name)),
            )
            nudge == Nudge.Mood -> Row {
                listOf(2 to "🙁", 3 to "😐", 4 to "🙂", 5 to "😄").forEach { (score, face) ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = GlanceModifier
                            .size(34.dp)
                            .background(ImageProvider(R.drawable.widget_chip))
                            .clickable(actionRunCallback<LogMoodAction>(actionParametersOf(LogMoodAction.Score to score))),
                    ) { Text(face, style = TextStyle(fontSize = 18.sp)) }
                    Spacer(GlanceModifier.width(6.dp))
                }
            }
            else -> {
                // Fitness first: if Move is still open, the next thing is always steps.
                val open = state.quests.filter { !it.done }.map { it.quest }
                val stepsLeft = state.steps?.let { (state.stepGoal - it).coerceAtLeast(0) }
                Text(
                    when {
                        open.isEmpty() -> "Everything's done. Enjoy your evening ✨"
                        Quest.MOVE in open && stepsLeft != null -> "Next: walk %,d more steps".format(stepsLeft)
                        Quest.MOVE in open -> "Next: go for a walk"
                        else -> "Next: ${open.first().hint.lowercase()}"
                    },
                    style = TextStyle(color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
            }
        }
    }

    @Composable
    private fun Pill(text: String, action: androidx.glance.action.Action) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(42.dp)
                .background(ImageProvider(R.drawable.widget_button))
                .clickable(action),
        ) {
            Text(text, style = TextStyle(color = ColorProvider(Color.White), fontSize = 14.sp, fontWeight = FontWeight.Bold))
        }
    }

    companion object {
        private val Ink = ColorProvider(Stone.S800)
        private val Muted = ColorProvider(Stone.S600)

        private fun formatTime(millis: Long): String =
            Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

        suspend fun refresh(context: Context) {
            runCatching { WakeupWidget().updateAll(context) }
        }
    }
}

/** Short-lived widget reactions (pose and line) after a tap; they fade after a minute. */
object WidgetReactions {
    private const val FILE = "blip_widget"
    private const val TTL = 60_000L

    private val pokeLines = listOf(
        "Hehe, that tickles!", "Hi you! 👋", "Boop!", "Wheee!", "You're doing great ✨",
        "I believe in you 💙", "Snack break? 🍎", "Let's make today count!",
    )

    fun set(context: Context, pose: WidgetPose, line: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("pose", pose.name).putString("line", line).putLong("at", System.currentTimeMillis()).apply()
    }

    fun recent(context: Context, now: Long): Pair<WidgetPose, String>? {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        if (now - prefs.getLong("at", 0L) > TTL) return null
        val pose = runCatching { WidgetPose.valueOf(prefs.getString("pose", null)!!) }.getOrNull() ?: return null
        return pose to (prefs.getString("line", null) ?: return null)
    }

    fun poke(context: Context) {
        val current = recent(context, System.currentTimeMillis())?.first
        val pose = listOf(WidgetPose.WAVE, WidgetPose.HAPPY, WidgetPose.CHEER).filter { it != current }.random()
        set(context, pose, pokeLines.random())
    }
}

private val Context.container get() = (applicationContext as ThoughtApplication).container

class PokeBlipAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetReactions.poke(context)
        WakeupWidget.refresh(context)
    }
}

class GoodMorningAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        context.container.daytraceRepository.tapGoodMorning()
        WidgetReactions.set(context, WidgetPose.CHEER, "Good morning! ☀️ +20 XP")
        WakeupWidget.refresh(context)
    }
}

class GoodNightAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        context.container.checkIns.logBedtime()
        WidgetReactions.set(context, WidgetPose.SLEEPY, "Good night 🌙 +10 XP")
        WakeupWidget.refresh(context)
    }
}

class LogMealAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val slot = runCatching { MealSlot.valueOf(parameters[Slot]!!) }.getOrNull() ?: return
        context.container.checkIns.logMeal(slot)
        WidgetReactions.set(context, WidgetPose.HAPPY, "Yum! ${slot.emoji} +5 XP")
        WakeupWidget.refresh(context)
    }

    companion object {
        val Slot = ActionParameters.Key<String>("slot")
    }
}

class LogMoodAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val score = parameters[Score] ?: return
        context.container.checkIns.logMood(score)
        WidgetReactions.set(
            context,
            if (score >= 4) WidgetPose.CHEER else WidgetPose.HAPPY,
            if (score >= 4) "Love that for you! +5 XP" else "Thanks for telling me 💙 +5 XP",
        )
        WakeupWidget.refresh(context)
    }

    companion object {
        val Score = ActionParameters.Key<Int>("score")
    }
}

class WakeupWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WakeupWidget()
}

private val Quest.emoji: String
    get() = when (this) {
        Quest.RISE -> "☀️"
        Quest.REFLECT -> "💡"
        Quest.FUEL -> "🍽️"
        Quest.MOVE -> "👟"
        Quest.VERDICT -> "⚖️"
    }
