package dev.holdthisthought.ui.today

import dev.holdthisthought.ui.theme.Earth
import dev.holdthisthought.ui.theme.Forest
import android.Manifest
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.IconButton
import androidx.compose.ui.res.painterResource
import dev.holdthisthought.R
import android.appwidget.AppWidgetManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.holdthisthought.data.local.EntryType
import dev.holdthisthought.data.local.HealthDay
import dev.holdthisthought.health.HealthConnectSource
import dev.holdthisthought.health.HealthRepository
import androidx.health.connect.client.PermissionController
import dev.holdthisthought.data.local.MealSlot
import dev.holdthisthought.data.local.Thought
import dev.holdthisthought.data.local.Wakeup
import dev.holdthisthought.data.local.WakeupState
import dev.holdthisthought.game.GameState
import dev.holdthisthought.game.Nudge
import dev.holdthisthought.game.Quest
import dev.holdthisthought.ui.components.CardTitle
import dev.holdthisthought.ui.components.EmojiChoice
import dev.holdthisthought.ui.components.GameCard
import dev.holdthisthought.ui.components.KindBadge
import dev.holdthisthought.ui.components.HeroCard
import dev.holdthisthought.game.Xp
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import dev.holdthisthought.ui.components.QuestRing
import dev.holdthisthought.ui.components.StreakChip
import dev.holdthisthought.ui.components.formatTime
import dev.holdthisthought.ui.theme.LocalGameColors
import dev.holdthisthought.ui.theme.popIn
import dev.holdthisthought.widget.WakeupWidgetReceiver
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

val Moods = listOf("😫" to "Rough", "😕" to "Low", "😐" to "Okay", "🙂" to "Good", "😄" to "Great")
val Verdicts = listOf("😣" to "Rough", "😕" to "Meh", "🙂" to "Okay", "😊" to "Good", "🤩" to "Great")

val MealSlot.emoji: String
    get() = when (this) {
        MealSlot.BREAKFAST -> "🥐"
        MealSlot.LUNCH -> "🍜"
        MealSlot.DINNER -> "🍝"
        MealSlot.SNACK -> "🍎"
    }

val MealSlot.label: String get() = name.lowercase().replaceFirstChar { it.uppercase() }

@Composable
fun TodayScreen(
    snackbar: SnackbarHostState,
    onOpenCapture: () -> Unit,
    onOpenYou: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TodayViewModel = viewModel(),
) {
    val game by viewModel.game.collectAsStateWithLifecycle()
    val nudge by viewModel.nudge.collectAsStateWithLifecycle()
    val entries by viewModel.todayEntries.collectAsStateWithLifecycle()
    val thoughts by viewModel.todayThoughts.collectAsStateWithLifecycle()
    val wakeup by viewModel.wakeupToday.collectAsStateWithLifecycle()
    val usage by viewModel.usage.collectAsStateWithLifecycle()
    val usageAccess by viewModel.usageAccess.collectAsStateWithLifecycle()
    val tracingConfigured by viewModel.tracingConfigured.collectAsStateWithLifecycle()
    val todayHealth by viewModel.todayHealth.collectAsStateWithLifecycle()
    val healthStatus by viewModel.healthStatus.collectAsStateWithLifecycle()
    val healthPermissions = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { viewModel.onHealthPermissionsResult() }
    val appContext = LocalContext.current.applicationContext
    val connectHealth: () -> Unit = {
        val source = container(appContext).health.source
        healthPermissions.launch(source.requested + source.backgroundPermission)
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    var sheet by rememberSaveable { mutableStateOf<Sheet?>(null) }
    val celebration by viewModel.celebration.collectAsStateWithLifecycle()
    val verdictRequest by viewModel.verdictRequest.collectAsStateWithLifecycle()
    LaunchedEffect(verdictRequest) {
        if (verdictRequest != 0L) {
            sheet = Sheet.VERDICT
            viewModel.consumeVerdictRequest()
        }
    }
    var excited by remember { mutableStateOf(false) }
    LaunchedEffect(celebration?.id) {
        if (celebration == null) return@LaunchedEffect
        excited = true
        delay(4_000)
        excited = false
    }

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    val undoable: (String) -> (String) -> Unit = { message ->
        { id ->
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            scope.launch {
                val result = snackbar.showSnackbar(message, "Undo", duration = SnackbarDuration.Short)
                if (result == SnackbarResult.ActionPerformed) viewModel.undo(id)
            }
        }
    }
    val actions = TodayActions(
        goodMorning = {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            viewModel.goodMorning()
        },
        mood = { score -> viewModel.logMood(score, undoable("Mood logged · ${Moods[score - 1].first}")) },
        meal = { slot -> viewModel.logMeal(slot, undoable("${slot.label} logged ${slot.emoji}")) },
        openWalk = { sheet = Sheet.WALK },
        openVerdict = { sheet = Sheet.VERDICT },
        openMeals = { sheet = Sheet.MEALS },
        capture = onOpenCapture,
        goodNight = { viewModel.goodNight(undoable("Good night 🌙 Sleep well")) },
    )

    val widgetDismissed by viewModel.widgetPromptDismissed.collectAsStateWithLifecycle()
    var moreOpen by rememberSaveable { mutableStateOf(false) }

    val state = game ?: return
    val hour = LocalTime.now().hour
    val healthAvailable = healthStatus.availability == HealthConnectSource.Availability.AVAILABLE
    val widgetPlaced = remember(widgetDismissed) { widgetPlaced(context) }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Box(Modifier.popIn("today", 0)) { Header(state) }
            Box(Modifier.popIn("today", 1)) { TodayHero(
                state = state,
                mood = BlipVoice.mood(state, nudge, wakeup != null, hour, excited),
                message = BlipVoice.line(state, nudge, wakeup != null, hour),
                bounceKey = celebration?.id,
                steps = todayHealth?.steps,
                healthConnected = healthStatus.connected,
                onConnectHealth = connectHealth.takeIf { healthAvailable },
                wakeup = wakeup,
                sleepMinutes = todayHealth?.sleepMinutes ?: 0,
                actions = nudge?.let { { NudgeActions(it, actions) } },
            ) }
            Box(Modifier.popIn("today", 2)) { QuestsCard(state, actions) }
            Box(Modifier.popIn("today", 3)) {
                QuickLogCard(
                    latestMood = entries.lastOrNull { it.type == EntryType.MOOD }?.value?.toIntOrNull(),
                    meals = entries.filter { it.type == EntryType.MEAL }.map { it.value }.toSet() + todayHealth?.meals.orEmpty(),
                    actions = actions,
                )
            }
            if (!widgetPlaced && !widgetDismissed) {
                WidgetPromptCard(onAdd = { requestWidget(context) }, onDismiss = viewModel::dismissWidgetPrompt)
            }
            MoreToggle(open = moreOpen) { moreOpen = !moreOpen }
            AnimatedVisibility(moreOpen) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    ChallengesCard(state)
                    if (healthAvailable) BodyCard(todayHealth, healthStatus.connected, connectHealth)
                    TimeCard(usage, usageAccess, context)
                    CapturesCard(thoughts, onOpenCapture)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    when (sheet) {
        Sheet.VERDICT -> VerdictSheet(
            onDismiss = { sheet = null },
            onSave = { score, note ->
                sheet = null
                viewModel.logVerdict(score, note, undoable("Day verdict saved ${Verdicts[score - 1].first}"))
            },
        )
        Sheet.WALK -> WalkSheet(
            onDismiss = { sheet = null },
            onSave = { minutes ->
                sheet = null
                viewModel.logWalk(minutes, undoable("Walk logged 🥾"))
            },
        )
        Sheet.MEALS -> MealSheet(
            logged = entries.filter { it.type == EntryType.MEAL }.map { it.value }.toSet(),
            onDismiss = { sheet = null },
            onPick = { slot ->
                sheet = null
                actions.meal(slot)
            },
        )
        null -> Unit
    }
}

private enum class Sheet { VERDICT, WALK, MEALS }

private class TodayActions(
    val goodMorning: () -> Unit,
    val mood: (Int) -> Unit,
    val meal: (MealSlot) -> Unit,
    val openWalk: () -> Unit,
    val openVerdict: () -> Unit,
    val openMeals: () -> Unit,
    val capture: () -> Unit,
    val goodNight: () -> Unit,
)

@Composable
private fun Header(state: GameState) {
    val hour = LocalTime.now().hour
    val greeting = when (hour) {
        in 4..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Hello, night owl"
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 20.dp)) {
        Column(Modifier.weight(1f)) {
            Text(
                state.today.date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(greeting, style = MaterialTheme.typography.headlineLarge)
        }
        StreakChip(state.streak)
    }
}

@Composable
private fun QuestsCard(state: GameState, actions: TodayActions) {
    GameCard {
        Column {
            CardTitle("Today's quests") {
                Text(
                    "${state.today.doneCount}/5",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                state.today.quests.forEach { quest ->
                    QuestRing(
                        state = quest,
                        onClick = {
                            when (quest.quest) {
                                Quest.RISE -> actions.goodMorning()
                                Quest.REFLECT -> actions.capture()
                                Quest.FUEL -> actions.openMeals()
                                Quest.MOVE -> actions.openWalk()
                                Quest.VERDICT -> actions.openVerdict()
                            }
                        },
                        size = 54.dp,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    state.today.perfect -> "Perfect day. Every quest done 💎"
                    state.today.counts -> "Streak safe for today. Bonus quests still give XP."
                    else -> "Finish ${3 - state.today.doneCount} more to keep your streak alive."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The nudge's quick actions, under Blip's speech bubble. */
@Composable
private fun NudgeActions(nudge: Nudge, actions: TodayActions) {
    val colors = ButtonDefaults.buttonColors(containerColor = Earth.Cream, contentColor = Forest.F900)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        when (nudge) {
            Nudge.WakeUp -> Button(onClick = actions.goodMorning, colors = colors) { Text("☀  Good morning") }
            is Nudge.Meal -> {
                Button(onClick = { actions.meal(nudge.slot) }, colors = colors) { Text("Yes, logged ${nudge.slot.emoji}") }
                TextButton(onClick = actions.openMeals) { Text("Other meal", color = Earth.Cream) }
            }
            Nudge.Mood -> Moods.forEachIndexed { i, (emoji, _) ->
                Surface(onClick = { actions.mood(i + 1) }, shape = CircleShape, color = Color.White.copy(alpha = 0.16f)) {
                    Text(emoji, fontSize = 24.sp, modifier = Modifier.padding(8.dp))
                }
            }
            Nudge.Capture -> Button(onClick = actions.capture, colors = colors) { Text("💡  Capture") }
            Nudge.Walk -> Button(onClick = actions.openWalk, colors = colors) { Text("🥾  Log a walk") }
            Nudge.Verdict -> Button(onClick = actions.openVerdict, colors = colors) { Text("⚖️  Rate the day") }
            Nudge.GoodNight -> Button(onClick = actions.goodNight, colors = colors) { Text("🌙  Good night") }
        }
    }
}

@Composable
private fun ChallengesCard(state: GameState) {
    val game = LocalGameColors.current
    GameCard {
        Column {
            CardTitle("This week's challenges") {
                Text("+${Xp.CHALLENGE} XP each", style = MaterialTheme.typography.labelMedium, color = game.xp)
            }
            Spacer(Modifier.height(8.dp))
            state.weekly.forEach { challenge ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (challenge.done) game.xp.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceContainer),
                    ) { Text(challenge.challenge.emoji, fontSize = 20.sp) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(challenge.challenge.title, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(5.dp))
                        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(game.track)) {
                            Box(
                                Modifier
                                    .fillMaxWidth(challenge.fraction.coerceAtLeast(0.02f))
                                    .height(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (challenge.done) Brush.horizontalGradient(listOf(game.xp, game.rise))
                                        else Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))),
                                    ),
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (challenge.done) "✓" else "${challenge.progress}/${challenge.challenge.target}",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (challenge.done) game.xp else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Mood and meals in one small card: the two things worth a tap during the day. */
@Composable
private fun QuickLogCard(latestMood: Int?, meals: Set<String>, actions: TodayActions) {
    val game = LocalGameColors.current
    GameCard {
        Column {
            CardTitle("Quick log")
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                Moods.forEachIndexed { i, (emoji, label) ->
                    EmojiChoice(emoji, label, latestMood == i + 1, { actions.mood(i + 1) }, Modifier.weight(1f), game.reflect)
                }
            }
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MealSlot.entries.forEach { slot ->
                    val done = slot.name in meals
                    Surface(
                        onClick = { actions.meal(slot) },
                        shape = CircleShape,
                        color = if (done) game.fuel.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(slot.emoji, fontSize = 16.sp)
                            Spacer(Modifier.width(6.dp))
                            Text(slot.label, style = MaterialTheme.typography.labelLarge)
                            if (done) {
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Rounded.CheckCircle, null, tint = game.fuel, modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One-time, dismissible: put Blip on the home screen. */
@Composable
private fun WidgetPromptCard(onAdd: () -> Unit, onDismiss: () -> Unit) {
    GameCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.blip_wave), contentDescription = null, modifier = Modifier.size(52.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Put Blip on your home screen", style = MaterialTheme.typography.titleSmall)
                Text(
                    "See what's left and log with one tap.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onAdd) { Text("Add") }
            IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Not now", modifier = Modifier.size(18.dp)) }
        }
    }
}

@Composable
private fun MoreToggle(open: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (open) "Show less" else "More about your day",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!open) {
                Text(
                    "Challenges, body, screen time, captures",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }
        }
        Icon(
            if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun widgetPlaced(context: Context): Boolean = runCatching {
    context.getSystemService(AppWidgetManager::class.java)
        ?.getAppWidgetIds(ComponentName(context, WakeupWidgetReceiver::class.java))
        ?.isNotEmpty() == true
}.getOrDefault(false)

private val AppColors = listOf(
    Earth.Sky, Forest.F500, Earth.Moss, Earth.Ochre, Earth.Heather,
)

/** Steps, walks, sleep and calories from Health Connect. Fills in by itself. */
@Composable
private fun BodyCard(health: HealthDay?, connected: Boolean, connect: () -> Unit) {
    val game = LocalGameColors.current
    GameCard {
        Column(Modifier.animateContentSize()) {
            CardTitle("Body") {
                Text("from Health Connect", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            when {
                !connected -> {
                    Text(
                        "Connect Health Connect and your steps, walks, sleep and meals (from Google Fit, Mi Health, " +
                            "MyFitnessPal) fill in by themselves.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = connect) { Text("Connect") }
                }
                health == null -> Text(
                    "Connected. Nothing for today yet; make sure Google Fit's \"Sync with Health Connect\" is on.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    val fraction = (health.steps.toFloat() / Xp.STEPS_FOR_MOVE).coerceIn(0f, 1f)
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(92.dp)) {
                        Canvas(Modifier.fillMaxSize()) {
                            val stroke = 9.dp.toPx()
                            val inset = stroke / 2
                            val arc = Size(size.width - stroke, size.height - stroke)
                            drawArc(game.track, 0f, 360f, false, Offset(inset, inset), arc, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                            drawArc(
                                game.move, -90f, 360f * fraction, false, Offset(inset, inset), arc,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("%,d".format(health.steps), style = MaterialTheme.typography.titleMedium)
                            Text("steps", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.width(18.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        BodyStat("🥾", if (health.walkMinutes > 0) "${health.walkMinutes} min walking" else "No walks yet")
                        BodyStat(
                            "😴",
                            if (health.sleepMinutes > 0) {
                                val times = if (health.sleepStart != null && health.sleepEnd != null)
                                    " (${formatTime(health.sleepStart)}–${formatTime(health.sleepEnd)})" else ""
                                "Slept ${formatDuration(health.sleepMinutes * 60_000L)}$times"
                            } else "Sleep shows after you wake up",
                        )
                        health.calories?.let { BodyStat("🍽️", "%,d kcal logged".format(it)) }
                        if (fraction >= 1f || health.walkMinutes >= Xp.WALK_MINUTES_FOR_MOVE) {
                            Text("Move quest done for you ✓", style = MaterialTheme.typography.labelMedium, color = game.move)
                        } else {
                            Text(
                                "%,d steps to finish Move".format(Xp.STEPS_FOR_MOVE - health.steps),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BodyStat(emoji: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = 16.sp)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun container(context: Context) = (context.applicationContext as dev.holdthisthought.ThoughtApplication).container

@Composable
private fun TimeCard(usage: UsageSummary?, usageAccess: Boolean, context: Context) {
    GameCard {
        Column(Modifier.animateContentSize()) {
            CardTitle("Where your day went")
            Spacer(Modifier.height(6.dp))
            when {
                !usageAccess -> Column {
                    Text(
                        "Optional: allow usage access and Daytrace shows which apps your time went to. Nothing to type.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { openUsageAccessSettings(context) }) { Text("Allow") }
                }
                usage == null || usage.totalMillis == 0L -> Text(
                    "Collecting… your first screen time appears within a few minutes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(formatDuration(usage.totalMillis), style = MaterialTheme.typography.displaySmall)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "on your phone",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    val top = usage.topApps.take(5)
                    DayStrip(usage, top.map { it.app })
                    Spacer(Modifier.height(14.dp))
                    top.forEachIndexed { i, app ->
                        AppRow(app, usage.topApps.first().millis, AppColors[i])
                    }
                    val rest = usage.topApps.drop(5)
                    if (rest.isNotEmpty()) {
                        Text(
                            "+ ${rest.size} more apps · ${formatDuration(rest.sumOf { it.millis })}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/** A 24-hour strip: each app's intervals in its colour. */
@Composable
private fun DayStrip(usage: UsageSummary, topApps: List<String>) {
    val track = LocalGameColors.current.track
    val other = MaterialTheme.colorScheme.outline
    Column {
        Canvas(Modifier.fillMaxWidth().height(22.dp)) {
            val day = 24 * 3_600_000f
            drawRoundRect(track, cornerRadius = CornerRadius(8.dp.toPx()))
            usage.intervals.forEach { interval ->
                val x0 = (interval.startedAt - usage.dayStart) / day * size.width
                val x1 = (interval.endedAt - usage.dayStart) / day * size.width
                val index = topApps.indexOf(interval.app)
                drawRect(
                    color = if (index >= 0) AppColors[index] else other,
                    topLeft = Offset(x0, 0f),
                    size = Size(maxOf(x1 - x0, 1.5f), size.height),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            listOf("4a", "10a", "4p", "10p", "4a").forEachIndexed { i, label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = when (i) {
                        0 -> androidx.compose.ui.text.style.TextAlign.Start
                        4 -> androidx.compose.ui.text.style.TextAlign.End
                        else -> androidx.compose.ui.text.style.TextAlign.Center
                    },
                )
            }
        }
    }
}

@Composable
private fun AppRow(app: AppTime, max: Long, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            app.app,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(110.dp),
        )
        Spacer(Modifier.width(8.dp))
        Row(Modifier.weight(1f).height(10.dp).clip(CircleShape).background(LocalGameColors.current.track)) {
            Spacer(
                Modifier
                    .fillMaxWidth((app.millis.toFloat() / max).coerceIn(0.02f, 1f))
                    .height(10.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            formatDuration(app.millis),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.widthIn(min = 52.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

@Composable
private fun CapturesCard(thoughts: List<Thought>, onOpenCapture: () -> Unit) {
    GameCard {
        Column {
            CardTitle("Held today") {
                FilledTonalButton(onClick = onOpenCapture) { Text("+ Capture") }
            }
            if (thoughts.isEmpty()) {
                Text(
                    "Nothing yet. A thought, a learning or something you want: say it out loud.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Spacer(Modifier.height(6.dp))
                thoughts.take(5).forEach { t ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 5.dp)) {
                        KindBadge(t.kind)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            t.title ?: t.body,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (thoughts.size > 5) {
                    Text(
                        "+ ${thoughts.size - 5} more in Library",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

fun formatDuration(millis: Long): String {
    val minutes = millis / 60_000
    return when {
        minutes < 1 -> "<1m"
        minutes < 60 -> "${minutes}m"
        else -> "${minutes / 60}h ${minutes % 60}m"
    }
}

fun openUsageAccessSettings(context: Context) {
    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

fun requestWidget(context: Context) {
    val manager = context.getSystemService(AppWidgetManager::class.java) ?: return
    if (manager.isRequestPinAppWidgetSupported) {
        manager.requestPinAppWidget(ComponentName(context, WakeupWidgetReceiver::class.java), null, null)
    }
}
