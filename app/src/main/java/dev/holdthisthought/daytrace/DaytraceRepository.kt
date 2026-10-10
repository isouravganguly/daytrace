package dev.holdthisthought.daytrace

import dev.holdthisthought.data.local.TraceDao
import dev.holdthisthought.data.local.Wakeup
import dev.holdthisthought.data.local.WakeupState
import dev.holdthisthought.game.DayClock
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Wake-ups and screen time: the UI's and the widget's way into daytrace data. */
class DaytraceRepository(
    private val dao: TraceDao,
    private val api: DaytraceApi,
    private val settings: DaytraceSettings,
    private val scheduler: DaytraceScheduler,
) {
    val wakeups: Flow<List<Wakeup>> = dao.observeWakeups()
    val uploadQueueSize: Flow<Int> = dao.observeUploadQueueSize()

    /** App usage for a logical day (04:00 to 04:00). */
    fun intervals(day: LocalDate, zone: ZoneId = ZoneId.systemDefault()) = dao.observeIntervals(
        from = DayClock.startOf(day, zone),
        to = DayClock.startOf(day.plusDays(1), zone),
    )

    suspend fun today(): Wakeup? = dao.wakeup(DayClock.today().toString())

    /**
     * "Good morning" was tapped: remember it locally right away (so the UI and widget react
     * offline), then let a worker tell the server.
     */
    suspend fun tapGoodMorning(now: Long = System.currentTimeMillis()): Wakeup {
        val day = dayOf(now)
        val existing = dao.wakeup(day)
        val wakeup = existing ?: Wakeup(day = day, tappedAt = now)
        if (existing == null) dao.upsertWakeup(wakeup)
        scheduler.requestWakeup()
        return wakeup
    }

    /** Called by [WakeupWorker]. Throws on network errors so the worker retries. */
    suspend fun sendWakeup() {
        val pending = dao.allWakeups().lastOrNull { it.state == WakeupState.PENDING }
        val result = api.wakeup(pending?.tappedAt)
        val wokeAt = result.wokeAtMillis
        val day = dayOf(wokeAt)
        val existing = dao.wakeup(day)
        dao.upsertWakeup(
            Wakeup(
                day = day,
                tappedAt = existing?.tappedAt ?: wokeAt,
                wokeAt = wokeAt,
                greeting = result.greeting,
                state = WakeupState.LOGGED,
            ),
        )
        settings.recordUpload(System.currentTimeMillis())
    }

    private fun dayOf(millis: Long): String =
        DayClock.logicalDate(millis).toString()
}
