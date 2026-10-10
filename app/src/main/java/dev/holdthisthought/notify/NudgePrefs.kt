package dev.holdthisthought.notify

import android.content.Context
import dev.holdthisthought.game.DaySchedule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/** Nudge settings and today's sent log. */
class NudgePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("nudges", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<NudgeSettings> = _settings.asStateFlow()

    fun update(transform: (NudgeSettings) -> NudgeSettings) {
        val next = transform(_settings.value)
        prefs.edit()
            .putBoolean("enabled", next.enabled)
            .putString("intensity", next.intensity.name)
            .putInt("wake", next.schedule.wake)
            .putInt("breakfast", next.schedule.breakfast)
            .putInt("lunch", next.schedule.lunch)
            .putInt("walk", next.schedule.walk)
            .putInt("dinner", next.schedule.dinner)
            .putInt("bedtime", next.schedule.bedtime)
            .apply()
        _settings.value = next
    }

    fun log(today: LocalDate): SentLog {
        if (prefs.getString("sent_day", null) != today.toString()) return SentLog(emptySet(), prefs.lastSent())
        val sent = prefs.getStringSet("sent", emptySet()).orEmpty()
            .mapNotNull { runCatching { Push.valueOf(it) }.getOrNull() }.toSet()
        return SentLog(sent, prefs.lastSent())
    }

    fun recordSent(push: Push, today: LocalDate, at: Long) {
        val sent = log(today).sent + push
        prefs.edit()
            .putString("sent_day", today.toString())
            .putStringSet("sent", sent.map { it.name }.toSet())
            .putLong("last_sent_at", at)
            .apply()
    }

    private fun android.content.SharedPreferences.lastSent() = getLong("last_sent_at", 0L).takeIf { it > 0 }

    private fun read() = NudgeSettings(
        enabled = prefs.getBoolean("enabled", true),
        intensity = runCatching { Intensity.valueOf(prefs.getString("intensity", null)!!) }.getOrDefault(Intensity.BALANCED),
        schedule = DaySchedule().let { d ->
            DaySchedule(
                wake = prefs.getInt("wake", d.wake),
                breakfast = prefs.getInt("breakfast", d.breakfast),
                lunch = prefs.getInt("lunch", d.lunch),
                walk = prefs.getInt("walk", d.walk),
                dinner = prefs.getInt("dinner", d.dinner),
                bedtime = prefs.getInt("bedtime", d.bedtime),
            )
        },
    )
}
