package dev.holdthisthought.ui.today

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.holdthisthought.ThoughtApplication
import dev.holdthisthought.data.local.Entry
import dev.holdthisthought.data.local.HealthDay
import dev.holdthisthought.data.local.MealSlot
import dev.holdthisthought.health.HealthRepository
import dev.holdthisthought.data.local.Thought
import dev.holdthisthought.data.local.UsageInterval
import dev.holdthisthought.data.local.Wakeup
import dev.holdthisthought.game.DayClock
import dev.holdthisthought.game.DaySchedule
import dev.holdthisthought.game.GameEngine
import dev.holdthisthought.game.GameState
import dev.holdthisthought.game.Nudge
import dev.holdthisthought.game.NudgeEngine
import dev.holdthisthought.ui.components.Celebration
import dev.holdthisthought.widget.WakeupWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One app's share of today's screen time. */
data class AppTime(val app: String, val millis: Long)

data class UsageSummary(
    val totalMillis: Long,
    val topApps: List<AppTime>,
    /** Intervals clipped to the day, for the 24-hour strip. */
    val intervals: List<UsageInterval>,
    val dayStart: Long,
)

/**
 * The game board: quests, XP, streak, nudges and screen time. Activity-scoped, so the Today and
 * You tabs and the celebration overlay share one instance.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as ThoughtApplication).container
    private val checkIns = container.checkIns
    private val daytrace = container.daytraceRepository

    /** Ticks every minute so the date, nudges and "in progress" day stay current. */
    private val now: StateFlow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(60_000)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, System.currentTimeMillis())

    private val today = now.map { DayClock.today() }.distinctUntilChanged()

    /** The user's rhythm (wake-up, meals, walk, bedtime); nudges and the early-bird target follow it. */
    val schedule: StateFlow<DaySchedule> = container.nudgePrefs.settings
        .map { it.schedule }
        .stateIn(viewModelScope, SharingStarted.Eagerly, container.nudgePrefs.settings.value.schedule)
    private val wakeTarget = schedule.map { it.wake }

    private val health = container.health

    private data class Inputs(
        val thoughts: List<Thought>,
        val entries: List<Entry>,
        val wakeups: List<Wakeup>,
        val health: List<HealthDay>,
    )

    val stepGoal: StateFlow<Int> = container.preferences.stepGoal

    // One Room query for all thoughts, shared by the game and today's captures.
    private val thoughts = container.repository.observeAllVisible()
        .shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)

    val game: StateFlow<GameState?> = combine(
        combine(thoughts, checkIns.entries, daytrace.wakeups, health.days, ::Inputs),
        today,
        wakeTarget,
        stepGoal,
    ) { inputs, day, target, goal ->
        GameEngine.compute(inputs.thoughts, inputs.entries, inputs.wakeups, target, day, health = inputs.health, stepGoal = goal)
    }
        .flowOn(Dispatchers.Default)
        .onEach(::celebrateChanges)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val todayEntries: StateFlow<List<Entry>> = combine(checkIns.entries, today) { entries, day ->
        val zone = ZoneId.systemDefault()
        entries.filter { DayClock.logicalDate(it.occurredAt, zone) == day }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val todayThoughts: StateFlow<List<Thought>> = combine(thoughts, today) { all, day ->
        val zone = ZoneId.systemDefault()
        all.filter { DayClock.logicalDate(it.createdAt, zone) == day }.reversed()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val wakeupToday: StateFlow<Wakeup?> = combine(daytrace.wakeups, today) { wakeups, day ->
        wakeups.firstOrNull { it.day == day.toString() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Today's Health Connect totals, or null when not connected / no data yet. */
    val todayHealth: StateFlow<HealthDay?> = combine(health.days, today) { days, day ->
        days.firstOrNull { it.day == day.toString() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val healthStatus: StateFlow<HealthRepository.Status> = health.status

    val nudge: StateFlow<Nudge?> = combine(game, todayEntries, now, todayHealth, schedule) { game, entries, time, hd, schedule ->
        game ?: return@combine null
        val clock = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).let { it.hour * 60 + it.minute }
        NudgeEngine.next(game.today, entries, clock, time, autoMeals = hd?.meals.orEmpty(), schedule = schedule)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val usage: StateFlow<UsageSummary?> = today
        .flatMapLatest { day -> daytrace.intervals(day).map { summarize(it, day) } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _usageAccess = MutableStateFlow(container.usageCollector.hasUsageAccess())
    val usageAccess: StateFlow<Boolean> = _usageAccess.asStateFlow()

    val tracingConfigured: StateFlow<Boolean> = container.daytraceSettings.config
        .map { it.isComplete }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _verdictRequest = MutableStateFlow(0L)
    /** Bumped when something outside the screen (a notification) asks for the verdict sheet. */
    val verdictRequest: StateFlow<Long> = _verdictRequest.asStateFlow()

    fun requestVerdict() {
        _verdictRequest.value = System.nanoTime()
    }

    fun consumeVerdictRequest() {
        _verdictRequest.value = 0L
    }

    private val _celebration = MutableStateFlow<Celebration?>(null)
    val celebration: StateFlow<Celebration?> = _celebration.asStateFlow()

    /** Re-checks permissions (call on resume) and pulls in the latest usage. */
    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) { health.sync() }
        _usageAccess.value = container.usageCollector.hasUsageAccess()
        if (_usageAccess.value) {
            viewModelScope.launch(Dispatchers.IO) { runCatching { container.usageCollector.collect() } }
            container.daytraceScheduler.requestCollect()
        }
    }

    val widgetPromptDismissed: StateFlow<Boolean> = container.preferences.widgetPromptDismissed

    fun dismissWidgetPrompt() = container.preferences.dismissWidgetPrompt()

    fun setStepGoal(steps: Int) = container.preferences.setStepGoal(steps)

    /** After the Health Connect permission sheet closes. */
    fun onHealthPermissionsResult() = viewModelScope.launch(Dispatchers.IO) { health.sync() }

    fun goodMorning() = viewModelScope.launch {
        daytrace.tapGoodMorning()
        WakeupWidget.refresh(getApplication())
    }

    /** Each log calls [onLogged] with the new entry's id, so the UI can offer undo. */
    fun logMood(score: Int, onLogged: (String) -> Unit = {}) = log(onLogged) { checkIns.logMood(score) }
    fun logMeal(slot: MealSlot, onLogged: (String) -> Unit = {}) = log(onLogged) { checkIns.logMeal(slot) }
    fun logWalk(minutes: Int, onLogged: (String) -> Unit = {}) = log(onLogged) { checkIns.logWalk(minutes) }
    fun goodNight(onLogged: (String) -> Unit = {}) = log(onLogged) { checkIns.logBedtime() }

    fun logVerdict(score: Int, note: String?, onLogged: (String) -> Unit = {}) =
        log(onLogged) { checkIns.logVerdict(score, note) }

    fun undo(entryId: String) = viewModelScope.launch {
        checkIns.delete(entryId)
        WakeupWidget.refresh(getApplication())
    }

    private fun log(onLogged: (String) -> Unit, block: suspend () -> Entry) = viewModelScope.launch {
        val entry = block()
        onLogged(entry.id)
        WakeupWidget.refresh(getApplication())
    }

    // ---------- celebrations ----------

    private var previous: GameState? = null

    private fun celebrateChanges(state: GameState) {
        val before = previous
        previous = state
        // Nothing to celebrate on first load or on a new day.
        if (before == null || before.today.date != state.today.date) return
        val gained = state.totalXp - before.totalXp
        if (gained <= 0) return
        val newlyDone = state.today.quests.filter { it.done && !before.today.quest(it.quest).done }.map { it.quest }
        val badgesBefore = before.earnedBadges.map { it.badge }.toSet()
        _celebration.value = Celebration(
            id = System.nanoTime(),
            xp = gained,
            questsDone = newlyDone,
            levelUp = state.level.takeIf { it.number > before.level.number },
            perfect = state.today.perfect && !before.today.perfect,
            newBadges = state.earnedBadges.map { it.badge }.filter { it !in badgesBefore },
        )
    }

    private fun summarize(intervals: List<UsageInterval>, day: LocalDate): UsageSummary {
        val zone = ZoneId.systemDefault()
        val start = DayClock.startOf(day, zone)
        val end = DayClock.startOf(day.plusDays(1), zone)
        val clipped = intervals.map { it.copy(startedAt = maxOf(it.startedAt, start), endedAt = minOf(it.endedAt, end)) }
        val byApp = clipped.groupBy { it.app }
            .map { (app, list) -> AppTime(app, list.sumOf { it.endedAt - it.startedAt }) }
            .sortedByDescending { it.millis }
        return UsageSummary(
            totalMillis = byApp.sumOf { it.millis },
            topApps = byApp,
            intervals = clipped,
            dayStart = start,
        )
    }
}
