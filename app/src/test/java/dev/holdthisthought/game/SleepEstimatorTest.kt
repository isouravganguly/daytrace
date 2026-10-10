package dev.holdthisthought.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

class SleepEstimatorTest {
    private val zone = ZoneOffset.UTC
    private val day = LocalDate.of(2026, 10, 11)

    private fun at(date: LocalDate, h: Int, m: Int = 0) = date.atTime(LocalTime.of(h, m)).toInstant(zone).toEpochMilli()
    private fun use(fromH: Int, fromM: Int, toH: Int, toM: Int, date: LocalDate = day) = at(date, fromH, fromM) to at(date, toH, toM)
    private val night = day.minusDays(1)
    private val noon = at(day, 12)

    @Test
    fun longestQuietStretchIsTheNight() {
        val usage = listOf(
            use(23, 0, 23, 59, night),
            at(day, 0, 30) to at(day, 2, 10), // scrolling until 2:10
            use(9, 40, 10, 5),                // first use in the morning
        )
        val sleep = SleepEstimator.estimate(usage, day, noon, zone)!!
        assertEquals(at(day, 2, 10), sleep.start)
        assertEquals(at(day, 9, 40), sleep.end)
        assertEquals(450, sleep.minutes)
    }

    @Test
    fun quickGlancesDoNotSplitTheNight() {
        val usage = listOf(
            use(1, 0, 2, 0),
            use(5, 0, 5, 1),   // checked the time for a minute
            use(9, 0, 9, 30),
        )
        val sleep = SleepEstimator.estimate(usage, day, noon, zone)!!
        assertEquals(at(day, 2), sleep.start)
        assertEquals(at(day, 9), sleep.end)
    }

    @Test
    fun noNightWhenStillAsleepOrNoGapLongEnough() {
        // It's 7 AM and the phone hasn't been used since 2: no wake-up yet, so no estimate.
        assertNull(SleepEstimator.estimate(listOf(use(1, 0, 2, 0)), day, at(day, 7), zone))
        // Phone used all night with only short gaps.
        val restless = (0 until 12).map { h -> use(h, 0, h, 40) }
        assertNull(SleepEstimator.estimate(restless, day, noon, zone))
    }
}
