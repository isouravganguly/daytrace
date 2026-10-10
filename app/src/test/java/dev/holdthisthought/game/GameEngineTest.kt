package dev.holdthisthought.game

import dev.holdthisthought.data.local.Entry
import dev.holdthisthought.data.local.EntryType
import dev.holdthisthought.data.local.MealSlot
import dev.holdthisthought.data.local.Thought
import dev.holdthisthought.data.local.ThoughtKind
import dev.holdthisthought.data.local.ThoughtSpace
import dev.holdthisthought.data.local.Wakeup
import dev.holdthisthought.data.local.WakeupState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

class GameEngineTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 10)

    private fun at(day: LocalDate, hour: Int, minute: Int = 0) =
        day.atTime(LocalTime.of(hour, minute)).toInstant(zone).toEpochMilli()

    private fun thought(day: LocalDate, kind: ThoughtKind = ThoughtKind.THOUGHT) = Thought(
        id = "$day-$kind-${Math.random()}", body = "x", kind = kind, space = ThoughtSpace.WORK,
        createdAt = at(day, 10), updatedAt = at(day, 10),
    )

    private fun entry(day: LocalDate, type: EntryType, value: String, hour: Int = 12) = Entry(
        id = "$day-$type-$value-$hour", type = type, value = value,
        occurredAt = at(day, hour), createdAt = at(day, hour), updatedAt = at(day, hour),
    )

    private fun wake(day: LocalDate, hour: Int, minute: Int = 0) =
        Wakeup(day.toString(), at(day, hour, minute), at(day, hour, minute), "Good morning", WakeupState.LOGGED)

    /** Three quests (rise, reflect, fuel) on [day]. */
    private fun countingDay(day: LocalDate) = Triple(
        listOf(thought(day)),
        listOf(entry(day, EntryType.MEAL, MealSlot.BREAKFAST.name, 8), entry(day, EntryType.MEAL, MealSlot.LUNCH.name, 13)),
        listOf(wake(day, 8)),
    )

    private fun compute(days: List<LocalDate>, extraEntries: List<Entry> = emptyList()): GameState {
        val parts = days.map(::countingDay)
        return GameEngine.compute(
            thoughts = parts.flatMap { it.first },
            entries = parts.flatMap { it.second } + extraEntries,
            wakeups = parts.flatMap { it.third },
            wakeTargetMinutes = 7 * 60 + 30,
            today = today,
            zone = zone,
        )
    }

    @Test
    fun xpAndQuestsForADay() {
        val state = GameEngine.compute(
            thoughts = listOf(thought(today, ThoughtKind.LEARNING), thought(today, ThoughtKind.DESIRE)),
            entries = listOf(
                entry(today, EntryType.MEAL, "LUNCH"),
                entry(today, EntryType.MEAL, "LUNCH", 13), // same meal twice: still one for the quest
                entry(today, EntryType.MOOD, "4"),
            ),
            wakeups = listOf(wake(today, 6, 50)),
            wakeTargetMinutes = 7 * 60 + 30,
            today = today,
            zone = zone,
        )
        // wake 20 + early 10 + learning 15 + desire 10 + 2 meals 10 + mood 5
        assertEquals(70, state.today.xp)
        assertEquals(1, state.today.quest(Quest.FUEL).progress)
        assertTrue(state.today.quest(Quest.RISE).done)
        assertFalse(state.today.quest(Quest.MOVE).done)
        assertTrue(state.badges.first { it.badge == Badge.FIRST_LIGHT }.earned)
    }

    @Test
    fun perfectDayBonus() {
        val (t, e, w) = countingDay(today)
        val state = GameEngine.compute(
            thoughts = t,
            entries = e + entry(today, EntryType.WALK, "20") + entry(today, EntryType.VERDICT, "4", 21),
            wakeups = w,
            wakeTargetMinutes = 0,
            today = today,
            zone = zone,
        )
        assertTrue(state.today.perfect)
        // wake 20 + thought 10 + meals 10 + walk 15 + verdict 20 + perfect 50
        assertEquals(125, state.today.xp)
    }

    @Test
    fun streakCountsConsecutiveDaysAndTodayInProgressDoesNotBreakIt() {
        val days = (1L..4L).map { today.minusDays(it) }
        assertEquals(4, compute(days).streak)
    }

    @Test
    fun oneMissedDayIsShieldedButTwoBreakTheStreak() {
        val shielded = listOf(5L, 4L, 2L, 1L).map { today.minusDays(it) }
        assertEquals(4, compute(shielded).streak)
        val broken = listOf(6L, 5L, 2L, 1L).map { today.minusDays(it) }
        assertEquals(2, compute(broken).streak)
        assertEquals(2, compute(broken).bestStreak)
    }

    @Test
    fun weeklyChallengesRotateAndPayOut() {
        val monday = today.with(java.time.DayOfWeek.MONDAY)
        val picked = GameEngine.challengesFor(monday)
        assertEquals(3, picked.toSet().size)
        assertTrue(picked != GameEngine.challengesFor(monday.plusWeeks(1)))

        // Every day this week counts (3 quests, 2 meals each): enough for several challenges.
        val days = (0L..5L).map { monday.plusDays(it) }.filter { !it.isAfter(today) }
        val state = compute(days)
        val done = state.weekly.count { it.done }
        assertEquals(state.days.values.sumOf { it.xp } + done * Xp.CHALLENGE, state.totalXp)
    }

    @Test
    fun healthConnectCompletesMoveAndFuel() {
        val health = dev.holdthisthought.data.local.HealthDay(
            day = today.toString(), steps = 8_200, meals = listOf("BREAKFAST", "LUNCH"),
        )
        val state = GameEngine.compute(
            thoughts = emptyList(),
            entries = listOf(entry(today, EntryType.MEAL, "LUNCH")), // also tapped: counts once
            wakeups = emptyList(),
            wakeTargetMinutes = 450,
            today = today,
            zone = zone,
            health = listOf(health),
        )
        assertTrue(state.today.quest(Quest.MOVE).done)
        assertTrue(state.today.quest(Quest.FUEL).done)
        // walk 15 + manual lunch 5 + auto breakfast 5
        assertEquals(25, state.today.xp)
        assertEquals(8_200, state.today.steps)
    }

    @Test
    fun fewStepsAndShortWalksDoNotCompleteMove() {
        val health = dev.holdthisthought.data.local.HealthDay(day = today.toString(), steps = 3_000, walkMinutes = 6)
        val state = GameEngine.compute(emptyList(), emptyList(), emptyList(), 450, today, zone, listOf(health))
        assertFalse(state.today.quest(Quest.MOVE).done)
    }

    @Test
    fun levelCurve() {
        assertEquals(1, GameEngine.levelFor(0).number)
        assertEquals(1, GameEngine.levelFor(99).number)
        assertEquals(2, GameEngine.levelFor(100).number)
        assertEquals(3, GameEngine.levelFor(300).number)
        val l = GameEngine.levelFor(400)
        assertEquals(3, l.number)
        assertEquals(100, l.xpIntoLevel)
        assertEquals(300, l.xpForLevel)
    }

    @Test
    fun nudgesFollowTheUsersSchedule() {
        // Default schedule: wake 10:00, breakfast 11:00, lunch 14:00, walk 18:00, dinner 21:00, bed 02:00.
        val empty = GameEngine.compute(emptyList(), emptyList(), emptyList(), 600, today, zone).today
        fun next(h: Int, m: Int = 0, entries: List<Entry> = emptyList()) =
            NudgeEngine.next(empty, entries, h * 60 + m, at(today, h, m))
        assertEquals(Nudge.WakeUp, next(10))
        assertEquals(Nudge.Meal(MealSlot.LUNCH), next(14, 30))
        // Fitness first: a walk comes before the verdict in the evening.
        assertEquals(Nudge.Walk, next(18))
        assertEquals(Nudge.Verdict, next(23, 45))
        // After midnight is still "today" for a night owl, and bedtime brings Good night.
        assertEquals(Nudge.GoodNight, next(1, 30))
        val lunchAndMood = listOf(entry(today, EntryType.MEAL, "LUNCH"), entry(today, EntryType.MOOD, "3", 14))
        assertEquals(Nudge.Capture, NudgeEngine.next(empty, lunchAndMood, 14 * 60 + 30, at(today, 14, 30)))
        // Once Good night is tapped, nothing else is suggested.
        assertNull(next(1, 30, listOf(entry(today, EntryType.BEDTIME, "", 1))))
    }
}
