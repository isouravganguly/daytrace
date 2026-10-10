package dev.holdthisthought.notify

import android.util.Log
import dev.holdthisthought.data.CheckInRepository
import dev.holdthisthought.data.ThoughtRepository
import dev.holdthisthought.data.local.TraceDao
import dev.holdthisthought.daytrace.DaytraceSettings
import dev.holdthisthought.game.DayClock
import dev.holdthisthought.game.GameEngine
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Called from the 15-minute background worker: posts at most one due nudge. */
class Nudges(
    private val thoughts: ThoughtRepository,
    private val checkIns: CheckInRepository,
    private val traceDao: TraceDao,
    private val daytraceSettings: DaytraceSettings,
    private val prefs: NudgePrefs,
    private val notifier: Notifier,
    private val stepGoal: () -> Int,
) {
    suspend fun check(now: Long = System.currentTimeMillis()) {
        val settings = prefs.settings.value
        if (!settings.enabled || !notifier.canPost()) return
        val zone = ZoneId.systemDefault()
        val today = DayClock.today(zone)
        val entries = checkIns.allEntries()
        val wakeups = traceDao.allWakeups()
        val health = traceDao.allHealth()
        val game = GameEngine.compute(thoughts.allVisible(), entries, wakeups, settings.schedule.wake, today, zone, health, stepGoal())
        val todayEntries = entries.filter { DayClock.logicalDate(it.occurredAt, zone) == today }
        val push = NudgeScheduler.due(
            now = LocalDateTime.now(zone),
            nowMillis = now,
            game = game,
            todayEntries = todayEntries,
            wokeUp = wakeups.any { it.day == today.toString() },
            log = prefs.log(today),
            settings = settings,
            autoMeals = health.firstOrNull { it.day == today.toString() }?.meals.orEmpty(),
        ) ?: return
        Log.i("Daytrace", "nudge: $push")
        notifier.post(push, game, today.toEpochDay())
        prefs.recordSent(push, today, now)
    }
}
