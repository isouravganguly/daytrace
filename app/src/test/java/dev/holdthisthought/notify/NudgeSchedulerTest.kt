package dev.holdthisthought.notify

import dev.holdthisthought.data.local.Entry
import dev.holdthisthought.data.local.EntryType
import dev.holdthisthought.game.GameEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

class NudgeSchedulerTest {
    private val zone = ZoneOffset.UTC
    private val day = LocalDate.of(2026, 10, 12)
    private val game = GameEngine.compute(emptyList(), emptyList(), emptyList(), 450, day, zone)

    private fun at(h: Int, m: Int = 0) = LocalDateTime.of(day, LocalTime.of(h, m))
    private fun millis(h: Int, m: Int = 0) = at(h, m).toInstant(zone).toEpochMilli()

    private fun due(
        h: Int,
        m: Int = 0,
        entries: List<Entry> = emptyList(),
        wokeUp: Boolean = false,
        log: SentLog = SentLog(emptySet(), null),
        settings: NudgeSettings = NudgeSettings(),
    ) = NudgeScheduler.due(at(h, m), millis(h, m), game, entries, wokeUp, log, settings)

    private fun meal(slot: String) = Entry(slot, EntryType.MEAL, slot, null, millis(13), millis(13), millis(13))

    @Test
    fun morningAsksForWakeUpAtTheUsersWakeTimeOnlyIfNotLogged() {
        assertNull(due(8))
        assertEquals(Push.WAKE, due(10))
        assertEquals(null, due(10, wokeUp = true))
    }

    @Test
    fun skipsWhatIsAlreadyLogged() {
        assertEquals(Push.LUNCH, due(14, wokeUp = true))
        assertNull(due(14, wokeUp = true, entries = listOf(meal("LUNCH"))))
    }

    @Test
    fun quietFromAnHourAfterBedtimeUntilWakeUp() {
        assertNull(due(4))
        assertNull(due(9, 30))
        assertNull(due(10, settings = NudgeSettings(enabled = false)))
    }

    @Test
    fun goodNightAtBedtimeAfterMidnight() {
        val log = SentLog(setOf(Push.CLOSE_DAY, Push.STREAK), null)
        assertEquals(Push.GOOD_NIGHT, due(1, 50, wokeUp = true, log = log))
    }

    @Test
    fun neverTwiceAndRespectsGapAndCap() {
        assertNull(due(10, log = SentLog(setOf(Push.WAKE), null)))
        assertNull(due(14, wokeUp = true, log = SentLog(emptySet(), millis(13, 50))))
        assertEquals(Push.LUNCH, due(14, wokeUp = true, log = SentLog(emptySet(), millis(13, 0))))
        val full = SentLog(setOf(Push.WAKE, Push.BREAKFAST, Push.MOOD, Push.WALK), null)
        assertNull(due(14, wokeUp = true, log = full, settings = NudgeSettings(intensity = Intensity.GENTLE)))
    }

    @Test
    fun gentleOnlySendsCoreNudges() {
        assertNull(due(11, wokeUp = true, settings = NudgeSettings(intensity = Intensity.GENTLE)))
        assertEquals(Push.BREAKFAST, due(11, wokeUp = true))
    }

    @Test
    fun moodTimeVariesByDayWithinTheAfternoon() {
        val minutes = (0L until 30L).map { NudgeScheduler.moodMinute(day.toEpochDay() + it) }
        assertTrue(minutes.all { it in 15 * 60 + 30 until 18 * 60 })
        assertTrue(minutes.toSet().size > 5)
    }
}
