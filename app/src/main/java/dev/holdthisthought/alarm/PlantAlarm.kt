package dev.holdthisthought.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dev.holdthisthought.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The morning alarm that only stops for a photo of a plant. By default it rings 30 minutes
 * before the user's wake-up time (9:30 for a 10:00 wake-up).
 */
class PlantAlarm(private val context: Context) {
    private val prefs = context.getSharedPreferences("plant_alarm", Context.MODE_PRIVATE)

    /** [pausedThrough]: no rings up to and including this day (travel, holidays); null when not paused. */
    data class Settings(val enabled: Boolean, val minute: Int, val pausedThrough: LocalDate? = null) {
        fun pausedOn(today: LocalDate) = pausedThrough != null && !today.isAfter(pausedThrough)
    }

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    fun update(enabled: Boolean = _settings.value.enabled, minute: Int = _settings.value.minute) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).putInt(KEY_MINUTE, minute).apply()
        _settings.value = read()
        schedule()
    }

    /** Skips every ring up to and including [lastDay]; null resumes right away. */
    fun pauseThrough(lastDay: LocalDate?) {
        prefs.edit().apply {
            if (lastDay == null) remove(KEY_PAUSED_THROUGH) else putLong(KEY_PAUSED_THROUGH, lastDay.toEpochDay())
        }.apply()
        _settings.value = read()
        schedule()
    }

    private fun read() = Settings(
        enabled = prefs.getBoolean(KEY_ENABLED, true),
        minute = prefs.getInt(KEY_MINUTE, DEFAULT_MINUTE),
        pausedThrough = prefs.getLong(KEY_PAUSED_THROUGH, Long.MIN_VALUE)
            .takeIf { it != Long.MIN_VALUE }?.let(LocalDate::ofEpochDay),
    )

    /** When the alarm rings next, or null when it's off. */
    fun nextRing(now: LocalDateTime = LocalDateTime.now()): LocalDateTime? {
        val s = _settings.value
        return if (s.enabled) nextRing(now, s.minute, s.pausedThrough) else null
    }

    fun canScheduleExact(): Boolean = alarmManager.canScheduleExactAlarms()

    /** Schedules the next ring (or cancels it). Safe to call any time; called on boot and app start. */
    fun schedule(now: LocalDateTime = LocalDateTime.now()) {
        val pending = ringIntent()
        alarmManager.cancel(pending)
        val s = _settings.value
        if (!s.enabled || !canScheduleExact()) return
        val next = nextRing(now, s.minute, s.pausedThrough)
        val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val show = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pending)
    }

    /** Rings right away (from settings, to try it). */
    fun ringNow() = AlarmService.start(context)

    private val alarmManager get() = context.getSystemService(AlarmManager::class.java)

    private fun ringIntent() = PendingIntent.getBroadcast(
        context, 1, Intent(context, AlarmReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val DEFAULT_MINUTE = 9 * 60 + 30
        private const val KEY_PAUSED_THROUGH = "paused_through"

        /** The first ring time after [now], skipping days up to and including [pausedThrough]. */
        fun nextRing(now: LocalDateTime, minute: Int, pausedThrough: LocalDate?): LocalDateTime {
            var next = now.toLocalDate().atTime(minute / 60, minute % 60)
            if (!next.isAfter(now)) next = next.plusDays(1)
            if (pausedThrough != null && !next.toLocalDate().isAfter(pausedThrough)) {
                next = pausedThrough.plusDays(1).atTime(minute / 60, minute % 60)
            }
            return next
        }
        private const val KEY_ENABLED = "enabled"
        private const val KEY_MINUTE = "minute"
    }
}
