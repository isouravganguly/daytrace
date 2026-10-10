package dev.holdthisthought.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dev.holdthisthought.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The morning alarm that only stops for a photo of a plant. By default it rings 30 minutes
 * before the user's wake-up time (9:30 for a 10:00 wake-up).
 */
class PlantAlarm(private val context: Context) {
    private val prefs = context.getSharedPreferences("plant_alarm", Context.MODE_PRIVATE)

    data class Settings(val enabled: Boolean, val minute: Int)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    fun update(enabled: Boolean = _settings.value.enabled, minute: Int = _settings.value.minute) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).putInt(KEY_MINUTE, minute).apply()
        _settings.value = read()
        schedule()
    }

    private fun read() = Settings(
        enabled = prefs.getBoolean(KEY_ENABLED, true),
        minute = prefs.getInt(KEY_MINUTE, DEFAULT_MINUTE),
    )

    fun canScheduleExact(): Boolean = alarmManager.canScheduleExactAlarms()

    /** Schedules the next ring (or cancels it). Safe to call any time; called on boot and app start. */
    fun schedule(now: LocalDateTime = LocalDateTime.now()) {
        val pending = ringIntent()
        alarmManager.cancel(pending)
        val s = _settings.value
        if (!s.enabled || !canScheduleExact()) return
        var next = now.toLocalDate().atTime(s.minute / 60, s.minute % 60)
        if (!next.isAfter(now)) next = next.plusDays(1)
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
        private const val KEY_ENABLED = "enabled"
        private const val KEY_MINUTE = "minute"
    }
}
