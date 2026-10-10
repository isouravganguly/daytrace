package dev.holdthisthought.health

import android.content.Context
import android.util.Log
import dev.holdthisthought.data.local.HealthDay
import dev.holdthisthought.data.local.TraceDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import dev.holdthisthought.game.SleepEstimator
import java.time.LocalDate
import java.time.ZoneId

/** Health Connect → local `health_days`. The rest of the app only reads the table. */
class HealthRepository(
    context: Context,
    val source: HealthConnectSource,
    private val dao: TraceDao,
) {
    data class Status(
        val availability: HealthConnectSource.Availability,
        val granted: Set<String>,
        val background: Boolean,
        val lastSyncAt: Long?,
        val lastError: String?,
    ) {
        val connected: Boolean get() = granted.isNotEmpty()
    }

    private val prefs = context.getSharedPreferences("health", Context.MODE_PRIVATE)
    private val mutex = Mutex()

    val days: Flow<List<HealthDay>> = dao.observeHealth()

    private val _status = MutableStateFlow(
        Status(source.availability(), emptySet(), false, prefs.getLong(KEY_LAST_SYNC, 0L).takeIf { it > 0 }, null),
    )
    val status: StateFlow<Status> = _status.asStateFlow()

    suspend fun refreshStatus(): Status {
        val availability = source.availability()
        val granted = runCatching { source.granted() }.getOrDefault(emptySet())
        val next = _status.value.copy(
            availability = availability,
            granted = granted.intersect(source.requested),
            background = source.backgroundPermission in granted,
        )
        _status.value = next
        return next
    }

    /** Re-reads the last [days] days. Returns how many changed. Safe to call often. */
    suspend fun sync(days: Long = 7): Int = mutex.withLock {
        val status = refreshStatus()
        val granted = if (status.availability == HealthConnectSource.Availability.AVAILABLE) status.granted else emptySet()
        val existing = dao.allHealth().associateBy { it.day }
        val today = LocalDate.now()
        try {
            val changed = (0 until days).map { today.minusDays(it) }
                .map { day ->
                    val read = if (granted.isEmpty()) HealthDay(day = day.toString(), updatedAt = System.currentTimeMillis())
                    else source.readDay(day, granted)
                    withPhoneSleep(read, day)
                }
                .filter { fresh -> existing[fresh.day]?.sameDataAs(fresh) != true }
            if (changed.isNotEmpty()) dao.upsertHealth(changed)
            val now = System.currentTimeMillis()
            prefs.edit().putLong(KEY_LAST_SYNC, now).apply()
            _status.value = _status.value.copy(lastSyncAt = now, lastError = null)
            changed.size
        } catch (e: SecurityException) {
            // Background read without the background permission, or permission just revoked.
            Log.i(TAG, "health read not allowed now: ${e.message}")
            0
        } catch (e: Exception) {
            Log.w(TAG, "health sync failed", e)
            _status.value = _status.value.copy(lastError = e.message ?: "Health Connect read failed")
            0
        }
    }

    /** No sleep from a band or watch? Estimate it from when the phone wasn't used. */
    private suspend fun withPhoneSleep(day: HealthDay, date: LocalDate): HealthDay {
        if (day.sleepMinutes > 0) return day
        val zone = ZoneId.systemDefault()
        val from = date.minusDays(1).atTime(20, 0).atZone(zone).toInstant().toEpochMilli()
        val to = date.atTime(15, 0).atZone(zone).toInstant().toEpochMilli()
        val usage = dao.intervalsBetween(from, to).map { it.startedAt to it.endedAt }
        val estimate = SleepEstimator.estimate(usage, date, System.currentTimeMillis(), zone) ?: return day
        return day.copy(sleepMinutes = estimate.minutes, sleepStart = estimate.start, sleepEnd = estimate.end)
    }

    private companion object {
        const val TAG = "Daytrace"
        const val KEY_LAST_SYNC = "last_sync_at"
    }
}
