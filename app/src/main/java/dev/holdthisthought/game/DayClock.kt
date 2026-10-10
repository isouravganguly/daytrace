package dev.holdthisthought.game

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * A "day" runs from 04:00 to 04:00, so a night owl's 1 AM verdict or 2 AM good-night still
 * counts for the day they're finishing, not the next one.
 */
object DayClock {
    const val DAY_START_HOUR = 4

    fun logicalDate(millis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).minusHours(DAY_START_HOUR.toLong()).toLocalDate()

    fun today(zone: ZoneId = ZoneId.systemDefault()): LocalDate = logicalDate(System.currentTimeMillis(), zone)

    /** Start of a logical day, in epoch millis. */
    fun startOf(day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
        day.atTime(DAY_START_HOUR, 0).atZone(zone).toInstant().toEpochMilli()

    /** Minutes since the logical day started (0 at 04:00, 1439 at 03:59). */
    fun minuteOfDay(clockMinute: Int): Int = Math.floorMod(clockMinute - DAY_START_HOUR * 60, 24 * 60)
}

/** The user's own rhythm, in clock minutes after midnight. Nudges follow it. */
data class DaySchedule(
    val wake: Int = 10 * 60,
    val breakfast: Int = 11 * 60,
    val lunch: Int = 14 * 60,
    val walk: Int = 18 * 60,
    val dinner: Int = 21 * 60,
    val bedtime: Int = 2 * 60,
) {
    companion object {
        fun format(minute: Int): String {
            val h = minute / 60
            val m = minute % 60
            val suffix = if (h < 12) "AM" else "PM"
            val h12 = if (h % 12 == 0) 12 else h % 12
            return if (m == 0) "$h12 $suffix" else "%d:%02d %s".format(h12, m, suffix)
        }
    }
}
