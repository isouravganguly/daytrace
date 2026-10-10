package dev.holdthisthought.game

import java.time.LocalDate
import java.time.ZoneId

data class SleepEstimate(val start: Long, val end: Long) {
    val minutes: Int get() = ((end - start) / 60_000).toInt()
}

/**
 * Estimates last night's sleep from phone use: the longest stretch with no app in the foreground
 * between 20:00 the evening before and 15:00 on [day]. Calls with the screen off (falling asleep
 * on a call) count as not using the phone. Quick glances under [glanceMs] are ignored, so
 * checking the time at 4 AM doesn't split the night. Pure, so it's unit-tested.
 */
object SleepEstimator {
    private const val MIN_SLEEP_MS = 3 * 3_600_000L
    private const val GLANCE_MS = 3 * 60_000L

    fun estimate(
        usage: List<Pair<Long, Long>>,
        day: LocalDate,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        glanceMs: Long = GLANCE_MS,
    ): SleepEstimate? {
        val windowStart = day.minusDays(1).atTime(20, 0).atZone(zone).toInstant().toEpochMilli()
        val windowEnd = minOf(day.atTime(15, 0).atZone(zone).toInstant().toEpochMilli(), now)
        if (windowEnd <= windowStart) return null

        val used = usage
            .filter { (s, e) -> e > windowStart && s < windowEnd && e - s >= glanceMs }
            .map { (s, e) -> maxOf(s, windowStart) to minOf(e, windowEnd) }
            .sortedBy { it.first }
        // Without any phone use there's nothing to anchor the night to.
        if (used.isEmpty()) return null

        var best: SleepEstimate? = null
        fun consider(start: Long, end: Long) {
            if (end - start >= MIN_SLEEP_MS && (best == null || end - start > best!!.end - best!!.start)) {
                best = SleepEstimate(start, end)
            }
        }
        var cursor = windowStart
        for ((s, e) in used) {
            // Only gaps that end with phone use count: that's when you woke up.
            if (s > cursor && cursor != windowStart) consider(cursor, s)
            cursor = maxOf(cursor, e)
        }
        return best
    }
}
