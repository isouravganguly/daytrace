package dev.holdthisthought.daytrace

import dev.holdthisthought.daytrace.UsageSignal.Kind.PAUSED
import dev.holdthisthought.daytrace.UsageSignal.Kind.RESUMED
import dev.holdthisthought.daytrace.UsageSignal.Kind.SCREEN_OFF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IntervalBuilderTest {
    private val builder = IntervalBuilder(ignored = setOf("launcher"))

    private fun r(t: Long, p: String, c: String = "Main") = UsageSignal(RESUMED, t, p, c)
    private fun p(t: Long, pkg: String, c: String = "Main") = UsageSignal(PAUSED, t, pkg, c)
    private fun off(t: Long) = UsageSignal(SCREEN_OFF, t)

    @Test
    fun switchingAppsClosesThePreviousOne() {
        val result = builder.build(null, listOf(r(0, "yt"), p(60_000, "yt"), r(60_100, "chrome")), now = 70_000)
        assertEquals(listOf(Span("yt", 0, 60_000)), result.spans)
        assertEquals("chrome", result.open?.packageName)
    }

    @Test
    fun activitySwitchInsideAnAppDoesNotSplitIt() {
        val signals = listOf(r(0, "yt", "A"), p(10_000, "yt", "A"), r(10_200, "yt", "B"), off(30_000))
        assertEquals(listOf(Span("yt", 0, 30_000)), builder.build(null, signals, now = 40_000).spans)
    }

    @Test
    fun pauseOfAnOlderActivityDoesNotEndTheApp() {
        // B resumed before A's pause arrived: the app is still in the foreground.
        val signals = listOf(r(0, "yt", "A"), r(5_000, "yt", "B"), p(5_100, "yt", "A"), off(20_000))
        assertEquals(listOf(Span("yt", 0, 20_000)), builder.build(null, signals, now = 30_000).spans)
    }

    @Test
    fun screenOffClosesAndShortIntervalsAreDropped() {
        val signals = listOf(r(0, "yt"), off(1_500), r(5_000, "chrome"), off(9_000))
        val result = builder.build(null, signals, now = 10_000)
        assertEquals(listOf(Span("chrome", 5_000, 9_000)), result.spans)
        assertNull(result.open)
    }

    @Test
    fun goingHomeEndsTheAppAndLauncherIsNotRecorded() {
        val signals = listOf(r(0, "yt"), p(8_000, "yt"), r(8_050, "launcher"), off(20_000))
        assertEquals(listOf(Span("yt", 0, 8_000)), builder.build(null, signals, now = 30_000).spans)
    }

    @Test
    fun openIntervalCarriesAcrossRuns() {
        val first = builder.build(null, listOf(r(0, "yt")), now = 50_000)
        assertEquals(emptyList<Span>(), first.spans)
        val second = builder.build(first.open, listOf(off(90_000)), now = 100_000)
        assertEquals(listOf(Span("yt", 0, 90_000)), second.spans)
    }

    @Test
    fun stalePauseAtEndOfRunCloses() {
        val result = builder.build(null, listOf(r(0, "yt"), p(10_000, "yt")), now = 60_000)
        assertEquals(listOf(Span("yt", 0, 10_000)), result.spans)
        assertNull(result.open)
    }
}
