package dev.holdthisthought.notify

import dev.holdthisthought.data.local.Entry
import dev.holdthisthought.data.local.EntryType
import dev.holdthisthought.data.local.MealSlot
import dev.holdthisthought.game.DayClock
import dev.holdthisthought.game.DaySchedule
import dev.holdthisthought.game.GameState
import dev.holdthisthought.game.Quest
import java.time.LocalDateTime

/** Every kind of nudge notification. [id] doubles as the Android notification id. */
enum class Push(val id: Int) {
    WAKE(101), BREAKFAST(102), LUNCH(103), DINNER(104), MOOD(105), WALK(106),
    CLOSE_DAY(107), STREAK(108), ALMOST(109), GOOD_NIGHT(110),
}

/** How chatty nudges are. ADHD-friendly default: enough to remember, not enough to tune out. */
enum class Intensity(val label: String, val maxPerDay: Int, val allowed: Set<Push>) {
    GENTLE("Gentle", 4, setOf(Push.WAKE, Push.LUNCH, Push.WALK, Push.GOOD_NIGHT)),
    BALANCED("Balanced", 6, Push.entries.toSet() - Push.ALMOST),
    HYPE("Hype", 9, Push.entries.toSet()),
}

data class NudgeSettings(
    val enabled: Boolean = true,
    val intensity: Intensity = Intensity.BALANCED,
    /** The user's rhythm; nudges fire at these times and stay quiet from bedtime to wake-up. */
    val schedule: DaySchedule = DaySchedule(),
)

/** What was already sent today. */
data class SentLog(val sent: Set<Push>, val lastSentAt: Long?)

/**
 * Decides which nudge, if any, is due right now. Pure, so it's unit-tested.
 *
 * Each nudge has a window that opens at the user's own time for it (wake-up, meals, walk,
 * bedtime). Rules, in order of importance:
 * - only nudge for things not done yet today;
 * - never between an hour after bedtime and wake-up, never twice for the same thing in a day;
 * - at most [Intensity.maxPerDay] a day and never within [MIN_GAP_MS] of the last one.
 */
object NudgeScheduler {
    const val MIN_GAP_MS = 45 * 60_000L

    fun due(
        now: LocalDateTime,
        nowMillis: Long,
        game: GameState,
        todayEntries: List<Entry>,
        wokeUp: Boolean,
        log: SentLog,
        settings: NudgeSettings,
        autoMeals: Collection<String> = emptyList(),
    ): Push? {
        if (!settings.enabled) return null
        val schedule = settings.schedule
        val clock = now.hour * 60 + now.minute
        if (inQuietHours(clock, schedule)) return null
        if (log.sent.size >= settings.intensity.maxPerDay) return null
        if (log.lastSentAt != null && nowMillis - log.lastSentAt < MIN_GAP_MS) return null
        if (todayEntries.any { it.type == EntryType.BEDTIME }) return null

        val today = game.today
        val meals = todayEntries.filter { it.type == EntryType.MEAL }.map { it.value }.toSet() + autoMeals
        val lastMood = todayEntries.filter { it.type == EntryType.MOOD }.maxOfOrNull { it.occurredAt }
        val m = DayClock.minuteOfDay(clock)
        fun window(start: Int, length: Int): Boolean {
            val from = DayClock.minuteOfDay(start)
            return m >= from && m < from + length
        }
        val moodStart = moodMinute(now.toLocalDate().toEpochDay(), schedule)
        val afternoon = DayClock.minuteOfDay(schedule.walk) - DayClock.minuteOfDay(schedule.lunch) + 120

        val candidates = listOf(
            Push.WAKE to (!wokeUp && window(schedule.wake, 120)),
            Push.BREAKFAST to (wokeUp && MealSlot.BREAKFAST.name !in meals && window(schedule.breakfast, 90)),
            Push.LUNCH to (MealSlot.LUNCH.name !in meals && window(schedule.lunch, 120)),
            Push.MOOD to ((lastMood == null || nowMillis - lastMood > 4 * 3_600_000L) && window(moodStart, 90)),
            Push.WALK to (!today.quest(Quest.MOVE).done && window(schedule.walk, 150)),
            Push.DINNER to (MealSlot.DINNER.name !in meals && window(schedule.dinner, 90)),
            Push.STREAK to (game.streak >= 2 && !today.counts && window(schedule.bedtime - 210, 90)),
            Push.CLOSE_DAY to (!today.quest(Quest.VERDICT).done && window(schedule.bedtime - 120, 90)),
            Push.GOOD_NIGHT to window(schedule.bedtime - 15, 60),
            Push.ALMOST to (game.weekly.any { !it.done && it.challenge.target - it.progress == 1 } && window(schedule.lunch, afternoon)),
        )
        return candidates.firstOrNull { (push, isDue) ->
            isDue && push !in log.sent && push in settings.intensity.allowed
        }?.first
    }

    /** Quiet from an hour after bedtime until wake-up. */
    fun inQuietHours(clockMinute: Int, schedule: DaySchedule): Boolean {
        val m = DayClock.minuteOfDay(clockMinute)
        return m >= DayClock.minuteOfDay(schedule.bedtime + 60) || m < DayClock.minuteOfDay(schedule.wake)
    }

    /** A slightly different time each afternoon (from 90 minutes after lunch), so mood pings stay fresh. */
    fun moodMinute(epochDay: Long, schedule: DaySchedule = DaySchedule()): Int =
        schedule.lunch + 90 + Math.floorMod(epochDay * 37, 150L).toInt()
}
