package dev.holdthisthought.game

import dev.holdthisthought.data.local.Entry
import dev.holdthisthought.data.local.EntryType
import dev.holdthisthought.data.local.HealthDay
import dev.holdthisthought.data.local.MealSlot
import dev.holdthisthought.data.local.Thought
import dev.holdthisthought.data.local.ThoughtKind
import dev.holdthisthought.data.local.Wakeup
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Today's five quests. */
enum class Quest(val title: String, val hint: String) {
    RISE("Rise", "Log your wake-up"),
    REFLECT("Reflect", "Hold one thought"),
    FUEL("Fuel", "Log 2 meals"),
    MOVE("Move", "Go for a walk"),
    VERDICT("Verdict", "Rate your day"),
}

data class QuestState(val quest: Quest, val progress: Int, val target: Int) {
    val done: Boolean get() = progress >= target
    val fraction: Float get() = (progress.toFloat() / target).coerceIn(0f, 1f)
}

data class DayProgress(
    val date: LocalDate,
    val quests: List<QuestState>,
    val xp: Int,
    val meals: Int = 0,
    val moods: Int = 0,
    val captures: Int = 0,
    val learnings: Int = 0,
    val earlyWake: Boolean = false,
    /** From Health Connect; 0 when not connected. */
    val steps: Int = 0,
) {
    val doneCount: Int get() = quests.count { it.done }
    val perfect: Boolean get() = quests.all { it.done }
    /** A day that keeps the streak alive. */
    val counts: Boolean get() = doneCount >= STREAK_QUESTS

    fun quest(quest: Quest) = quests.first { it.quest == quest }

    companion object {
        const val STREAK_QUESTS = 3
    }
}

enum class Badge(val title: String, val description: String, val target: Int) {
    FIRST_LIGHT("First Light", "Log your first wake-up", 1),
    EARLY_BIRD("Early Bird", "Wake before your target 7 times", 7),
    CURIOUS_MIND("Curious Mind", "Capture 10 learnings", 10),
    SCHOLAR("Scholar", "Capture 50 learnings", 50),
    SAGE("Sage", "Capture 100 learnings", 100),
    WISHMAKER("Wishmaker", "Capture 10 desires", 10),
    WELL_FED("Well Fed", "Log 2+ meals 7 days in a row", 7),
    WANDERER("Wanderer", "Walk on 10 days", 10),
    WEEK_WARRIOR("Week Warrior", "Reach a 7-day streak", 7),
    MARATHON("Monthly Marathon", "Reach a 30-day streak", 30),
    PERFECT_DAY("Perfect Day", "Complete all five quests in a day", 1),
    HONEST_MIRROR("Honest Mirror", "Give 7 day verdicts", 7),
}

data class BadgeState(val badge: Badge, val progress: Int) {
    val earned: Boolean get() = progress >= badge.target
    val fraction: Float get() = (progress.toFloat() / badge.target).coerceIn(0f, 1f)
}

/** Weekly challenges (Mon–Sun). Three are active each week, rotating through the pool. */
enum class Challenge(val title: String, val emoji: String, val target: Int) {
    WALK_DAYS("Walk on 4 days", "🥾", 4),
    MEALS("Log 10 meals", "🍱", 10),
    LEARNINGS("Capture 5 learnings", "🧠", 5),
    EARLY_WAKES("Wake before target 3 times", "🐦", 3),
    VERDICTS("Rate 5 days", "⚖️", 5),
    MOODS("Check in your mood 7 times", "💭", 7),
    CAPTURES("Hold 10 thoughts", "💡", 10),
    PERFECT("Have a perfect day", "💎", 1),
    STEP_GOAL_DAYS("Hit your step goal on 4 days", "👟", 4),
    ;

    val isMovement: Boolean get() = this == WALK_DAYS || this == STEP_GOAL_DAYS
}

data class ChallengeState(val challenge: Challenge, val progress: Int) {
    val done: Boolean get() = progress >= challenge.target
    val fraction: Float get() = (progress.toFloat() / challenge.target).coerceIn(0f, 1f)
}

data class Level(val number: Int, val title: String, val xpIntoLevel: Int, val xpForLevel: Int) {
    val fraction: Float get() = if (xpForLevel == 0) 0f else (xpIntoLevel.toFloat() / xpForLevel).coerceIn(0f, 1f)
}

data class GameState(
    val totalXp: Int,
    val level: Level,
    val streak: Int,
    val bestStreak: Int,
    val today: DayProgress,
    val days: Map<LocalDate, DayProgress>,
    val badges: List<BadgeState>,
    val weekly: List<ChallengeState> = emptyList(),
    val stepGoal: Int = Xp.STEPS_FOR_MOVE,
) {
    /** Quests still open today. */
    val questsLeft: List<Quest> get() = today.quests.filter { !it.done }.map { it.quest }

    /** Progress towards today's XP goal, 0..1. */
    val dailyGoalFraction: Float get() = (today.xp.toFloat() / Xp.DAILY_GOAL).coerceIn(0f, 1f)

    val earnedBadges: List<BadgeState> get() = badges.filter { it.earned }
}

/** XP rules, in one place so they are easy to tune. */
object Xp {
    const val WAKE = 20
    const val WAKE_EARLY_BONUS = 10
    const val THOUGHT = 10
    const val LEARNING = 15
    const val DESIRE = 10
    const val TODO_CAPTURED = 5
    const val TODO_DONE = 10
    const val MOOD = 5
    const val MOOD_MAX_PER_DAY = 3
    const val MEAL = 5
    const val MEAL_MAX_PER_DAY = 4
    const val WALK = 15
    const val WALK_MAX_PER_DAY = 2
    const val VERDICT = 20
    const val GOOD_NIGHT = 10
    const val PERFECT_DAY = 50
    const val CHALLENGE = 100
    const val DAILY_GOAL = 50

    /** Health Connect thresholds that complete the Move quest without logging. */
    const val STEPS_FOR_MOVE = 7_000
    const val WALK_MINUTES_FOR_MOVE = 10

    fun forCapture(kind: ThoughtKind) = when (kind) {
        ThoughtKind.THOUGHT -> THOUGHT
        ThoughtKind.LEARNING -> LEARNING
        ThoughtKind.DESIRE -> DESIRE
        ThoughtKind.TODO -> TODO_CAPTURED
    }
}

/**
 * Derives the whole game (quests, XP, level, streak, badges) from the user's data.
 * Nothing here is stored, so it can't drift and the rules can change freely. Pure: unit-tested.
 */
object GameEngine {
    private val levelTitles = listOf(
        "Spark", "Noticer", "Tracer", "Pathfinder", "Cartographer",
        "Navigator", "Chronicler", "Sage", "Luminary", "Oracle",
    )

    fun compute(
        thoughts: List<Thought>,
        entries: List<Entry>,
        wakeups: List<Wakeup>,
        wakeTargetMinutes: Int,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
        health: List<HealthDay> = emptyList(),
        stepGoal: Int = Xp.STEPS_FOR_MOVE,
    ): GameState {
        fun dateOf(millis: Long) = DayClock.logicalDate(millis, zone)

        val capturesByDay = thoughts.groupBy { dateOf(it.createdAt) }
        val todosDoneByDay = thoughts.mapNotNull { it.completedAt }.groupBy { dateOf(it) }
        val entriesByDay = entries.groupBy { dateOf(it.occurredAt) }
        val wakeByDay = wakeups.associateBy { LocalDate.parse(it.day) }
        val healthByDay = health.associateBy { LocalDate.parse(it.day) }

        fun isEarly(w: Wakeup): Boolean {
            val t = Instant.ofEpochMilli(w.time).atZone(zone)
            return t.hour * 60 + t.minute < wakeTargetMinutes
        }

        val firstDay = (capturesByDay.keys + entriesByDay.keys + wakeByDay.keys + todosDoneByDay.keys + healthByDay.keys)
            .minOrNull()?.coerceAtMost(today) ?: today

        val days = linkedMapOf<LocalDate, DayProgress>()
        var day = firstDay
        while (!day.isAfter(today)) {
            val captures = capturesByDay[day].orEmpty()
            val dayEntries = entriesByDay[day].orEmpty()
            val moods = dayEntries.count { it.type == EntryType.MOOD }
            val manualMeals = dayEntries.filter { it.type == EntryType.MEAL }
            val hd = healthByDay[day]
            // A meal slot counts once, whether tapped in Daytrace or logged in a nutrition app.
            val autoMeals = hd?.meals.orEmpty().filter { slot -> manualMeals.none { it.value == slot } }
            val mealSlots = (manualMeals.map { it.value } + autoMeals).distinct()
            val mealCount = manualMeals.size + autoMeals.size
            val manualWalks = dayEntries.count { it.type == EntryType.WALK }
            val autoWalk = hd != null && (hd.walkMinutes >= Xp.WALK_MINUTES_FOR_MOVE || hd.steps >= stepGoal)
            val walks = if (manualWalks == 0 && autoWalk) 1 else manualWalks
            val verdicts = dayEntries.count { it.type == EntryType.VERDICT }
            val wake = wakeByDay[day]

            val quests = listOf(
                QuestState(Quest.RISE, if (wake != null) 1 else 0, 1),
                QuestState(Quest.REFLECT, minOf(captures.size, 1), 1),
                // Two different meals, so logging "lunch" twice doesn't count as two.
                QuestState(Quest.FUEL, minOf(mealSlots.size, 2), 2),
                QuestState(Quest.MOVE, minOf(walks, 1), 1),
                QuestState(Quest.VERDICT, minOf(verdicts, 1), 1),
            )
            var xp = captures.sumOf { Xp.forCapture(it.kind) } +
                todosDoneByDay[day].orEmpty().size * Xp.TODO_DONE +
                minOf(moods, Xp.MOOD_MAX_PER_DAY) * Xp.MOOD +
                minOf(mealCount, Xp.MEAL_MAX_PER_DAY) * Xp.MEAL +
                minOf(walks, Xp.WALK_MAX_PER_DAY) * Xp.WALK +
                minOf(verdicts, 1) * Xp.VERDICT +
                (if (dayEntries.any { it.type == EntryType.BEDTIME }) Xp.GOOD_NIGHT else 0)
            if (wake != null) xp += Xp.WAKE + if (isEarly(wake)) Xp.WAKE_EARLY_BONUS else 0
            if (quests.all { it.done }) xp += Xp.PERFECT_DAY
            days[day] = DayProgress(
                date = day,
                quests = quests,
                xp = xp,
                meals = mealCount,
                moods = moods,
                captures = captures.size,
                learnings = captures.count { it.kind == ThoughtKind.LEARNING },
                earlyWake = wake != null && isEarly(wake),
                steps = hd?.steps ?: 0,
            )
            day = day.plusDays(1)
        }

        val (streak, bestStreak) = streaks(days, today)

        // Weekly challenges: every week so far, each completed one is worth bonus XP.
        val weeks = days.values.groupBy { it.date.with(DayOfWeek.MONDAY) }
        val weekly = weeks.mapValues { (start, weekDays) -> challengesFor(start).map { ChallengeState(it, progress(it, weekDays, stepGoal)) } }
        val challengeXp = weekly.values.sumOf { list -> list.count { it.done } * Xp.CHALLENGE }
        val totalXp = days.values.sumOf { it.xp } + challengeXp
        val thisWeek = weekly[today.with(DayOfWeek.MONDAY)]
            ?: challengesFor(today.with(DayOfWeek.MONDAY)).map { ChallengeState(it, 0) }

        val learnings = thoughts.count { it.kind == ThoughtKind.LEARNING }
        val badges = Badge.entries.map { badge ->
            val progress = when (badge) {
                Badge.FIRST_LIGHT -> wakeups.size
                Badge.EARLY_BIRD -> wakeups.count(::isEarly)
                Badge.CURIOUS_MIND, Badge.SCHOLAR, Badge.SAGE -> learnings
                Badge.WISHMAKER -> thoughts.count { it.kind == ThoughtKind.DESIRE }
                Badge.WELL_FED -> longestRun(days.values) { it.quest(Quest.FUEL).done }
                Badge.WANDERER -> days.values.count { it.quest(Quest.MOVE).done }
                Badge.WEEK_WARRIOR, Badge.MARATHON -> bestStreak
                Badge.PERFECT_DAY -> days.values.count { it.perfect }
                Badge.HONEST_MIRROR -> days.values.count { it.quest(Quest.VERDICT).done }
            }
            BadgeState(badge, minOf(progress, badge.target))
        }

        return GameState(
            totalXp = totalXp,
            level = levelFor(totalXp),
            streak = streak,
            bestStreak = bestStreak,
            today = days.getValue(today),
            days = days,
            badges = badges,
            weekly = thisWeek,
            stepGoal = stepGoal,
        )
    }

    /**
     * Three challenges for the week starting [monday]: always one movement challenge (fitness is
     * the current focus), plus two others rotating through the pool.
     */
    fun challengesFor(monday: LocalDate): List<Challenge> {
        val week = Math.floorDiv(monday.toEpochDay(), 7L).toInt()
        val movement = Challenge.entries.filter { it.isMovement }
        val others = Challenge.entries.filter { !it.isMovement }
        return listOf(movement[Math.floorMod(week, movement.size)]) +
            (0 until 2).map { others[Math.floorMod(week * 2 + it, others.size)] }
    }

    private fun progress(challenge: Challenge, week: List<DayProgress>, stepGoal: Int): Int = when (challenge) {
        Challenge.WALK_DAYS -> week.count { it.quest(Quest.MOVE).done }
        Challenge.MEALS -> week.sumOf { it.meals }
        Challenge.LEARNINGS -> week.sumOf { it.learnings }
        Challenge.EARLY_WAKES -> week.count { it.earlyWake }
        Challenge.VERDICTS -> week.count { it.quest(Quest.VERDICT).done }
        Challenge.MOODS -> week.sumOf { it.moods }
        Challenge.CAPTURES -> week.sumOf { it.captures }
        Challenge.PERFECT -> week.count { it.perfect }
        Challenge.STEP_GOAL_DAYS -> week.count { it.steps >= stepGoal }
    }.coerceAtMost(challenge.target)

    /** Total XP needed to reach [level]: 0, 100, 300, 600, 1000, … */
    fun xpToReach(level: Int): Int = 50 * (level - 1) * level

    fun levelFor(totalXp: Int): Level {
        var level = 1
        while (totalXp >= xpToReach(level + 1)) level++
        val base = xpToReach(level)
        val title = levelTitles.getOrNull(level - 1) ?: "${levelTitles.last()} ${level - levelTitles.size + 1}"
        return Level(level, title, totalXp - base, xpToReach(level + 1) - base)
    }

    /**
     * Current and best streak of counting days. One missed day per 7 is forgiven (a "shield"),
     * and today never breaks the streak while it's still in progress.
     */
    internal fun streaks(days: Map<LocalDate, DayProgress>, today: LocalDate): Pair<Int, Int> {
        var run = 0
        var best = 0
        var lastShield: LocalDate? = null
        for ((date, progress) in days) {
            if (progress.counts) {
                run++
            } else if (date == today) {
                // Still in progress.
            } else if (run > 0 && (lastShield == null || ChronoUnit.DAYS.between(lastShield, date) >= 7)) {
                lastShield = date
            } else {
                run = 0
                lastShield = null
            }
            best = maxOf(best, run)
        }
        return run to best
    }

    private fun longestRun(days: Collection<DayProgress>, predicate: (DayProgress) -> Boolean): Int {
        var run = 0
        var best = 0
        for (d in days) {
            run = if (predicate(d)) run + 1 else 0
            best = maxOf(best, run)
        }
        return best
    }
}

/** The single most useful thing to log right now, or null when all caught up. */
sealed interface Nudge {
    data object WakeUp : Nudge
    data class Meal(val slot: MealSlot) : Nudge
    data object Mood : Nudge
    data object Capture : Nudge
    data object Walk : Nudge
    data object Verdict : Nudge
    data object GoodNight : Nudge
}

object NudgeEngine {
    /**
     * The one thing to suggest right now, following the user's [schedule]. Times are compared in
     * "logical day" minutes (the day starts at 04:00), so a 2 AM bedtime is the end of the day.
     * [autoMeals]: meal slots already known from Health Connect, so we don't ask about them.
     */
    fun next(
        today: DayProgress,
        todayEntries: List<Entry>,
        clockMinute: Int,
        now: Long,
        autoMeals: Collection<String> = emptyList(),
        schedule: DaySchedule = DaySchedule(),
    ): Nudge? {
        val m = DayClock.minuteOfDay(clockMinute)
        fun at(t: Int) = DayClock.minuteOfDay(t)
        fun from(t: Int) = m >= at(t)
        fun between(a: Int, b: Int) = m >= at(a) && m < at(b)
        val meals = todayEntries.filter { it.type == EntryType.MEAL }.map { it.value }.toSet() + autoMeals
        val lastMood = todayEntries.filter { it.type == EntryType.MOOD }.maxOfOrNull { it.occurredAt }
        fun missingMeal(slot: MealSlot) = slot.name !in meals
        if (todayEntries.any { it.type == EntryType.BEDTIME }) return null
        return when {
            !today.quest(Quest.RISE).done && between(schedule.wake - 120, schedule.wake + 180) -> Nudge.WakeUp
            missingMeal(MealSlot.BREAKFAST) && between(schedule.breakfast, schedule.breakfast + 150) -> Nudge.Meal(MealSlot.BREAKFAST)
            missingMeal(MealSlot.LUNCH) && between(schedule.lunch, schedule.lunch + 180) -> Nudge.Meal(MealSlot.LUNCH)
            // Fitness first: from mid-afternoon until after dinner, a walk comes before anything else.
            !today.quest(Quest.MOVE).done && between(schedule.walk - 180, schedule.dinner + 60) -> Nudge.Walk
            missingMeal(MealSlot.DINNER) && between(schedule.dinner, schedule.dinner + 150) -> Nudge.Meal(MealSlot.DINNER)
            from(schedule.bedtime - 60) -> Nudge.GoodNight
            !today.quest(Quest.VERDICT).done && from(schedule.bedtime - 180) -> Nudge.Verdict
            between(schedule.wake + 60, schedule.bedtime - 120) && (lastMood == null || now - lastMood > 4 * 3_600_000L) -> Nudge.Mood
            !today.quest(Quest.REFLECT).done && from(schedule.wake + 60) -> Nudge.Capture
            else -> null
        }
    }
}
