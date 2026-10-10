package dev.holdthisthought.alarm

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class PlantAlarmTest {
    private val nineThirty = 9 * 60 + 30

    @Test
    fun ringsLaterTodayOrTomorrow() {
        assertEquals(
            LocalDateTime.of(2026, 10, 11, 9, 30),
            PlantAlarm.nextRing(LocalDateTime.of(2026, 10, 11, 7, 0), nineThirty, null),
        )
        assertEquals(
            LocalDateTime.of(2026, 10, 12, 9, 30),
            PlantAlarm.nextRing(LocalDateTime.of(2026, 10, 11, 9, 30), nineThirty, null),
        )
    }

    @Test
    fun skipsEveryMorningThroughThePause() {
        assertEquals(
            LocalDateTime.of(2026, 10, 19, 9, 30),
            PlantAlarm.nextRing(LocalDateTime.of(2026, 10, 10, 23, 0), nineThirty, LocalDate.of(2026, 10, 18)),
        )
    }

    @Test
    fun aPastPauseChangesNothing() {
        assertEquals(
            LocalDateTime.of(2026, 10, 20, 9, 30),
            PlantAlarm.nextRing(LocalDateTime.of(2026, 10, 20, 8, 0), nineThirty, LocalDate.of(2026, 10, 18)),
        )
    }
}
