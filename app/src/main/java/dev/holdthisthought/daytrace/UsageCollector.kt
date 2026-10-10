package dev.holdthisthought.daytrace

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import dev.holdthisthought.data.local.TraceDao
import dev.holdthisthought.data.local.UsageInterval
import java.util.concurrent.TimeUnit

/**
 * Reads foreground-app events since the last run and stores them as [UsageInterval]s.
 * The cursor and the still-open interval are persisted, so runs can be any length apart.
 */
class UsageCollector(
    private val context: Context,
    private val dao: TraceDao,
    private val settings: DaytraceSettings,
) {
    private val labels = mutableMapOf<String, String>()

    fun hasUsageAccess(): Boolean = hasUsageAccess(context)

    /** Returns the number of new intervals stored. */
    suspend fun collect(now: Long = System.currentTimeMillis()): Int {
        if (!hasUsageAccess()) return 0
        val manager = context.getSystemService(UsageStatsManager::class.java) ?: return 0

        val from = settings.eventCursor.takeIf { it > 0 }?.plus(1) ?: (now - BACKFILL)
        val events = manager.queryEvents(from, now)
        val signals = mutableListOf<UsageSignal>()
        var newest = settings.eventCursor
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            newest = maxOf(newest, event.timeStamp)
            val kind = when (event.eventType) {
                ACTIVITY_RESUMED -> UsageSignal.Kind.RESUMED
                ACTIVITY_PAUSED -> UsageSignal.Kind.PAUSED
                SCREEN_NON_INTERACTIVE, KEYGUARD_SHOWN -> UsageSignal.Kind.SCREEN_OFF
                else -> null
            } ?: continue
            signals += UsageSignal(kind, event.timeStamp, event.packageName, event.className)
        }

        val result = IntervalBuilder(ignored = ignoredPackages()).build(settings.openInterval, signals, now)
        val intervals = result.spans.map { span ->
            UsageInterval(
                packageName = span.packageName,
                app = label(span.packageName),
                startedAt = span.start,
                endedAt = span.end,
            )
        }
        if (intervals.isNotEmpty()) dao.insertIntervals(intervals)
        settings.openInterval = result.open
        settings.eventCursor = newest
        dao.pruneUploaded(before = now - KEEP_LOCAL)
        return intervals.size
    }

    /** The app in the foreground right now, for a live "Today" view. */
    fun currentApp(): Pair<String, Long>? =
        settings.openInterval?.takeIf { it.pausedAt == null }?.let { label(it.packageName) to it.start }

    private fun ignoredPackages(): Set<String> {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val launchers = context.packageManager
            .queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.packageName }
        return (launchers + "com.android.systemui" + "android").toSet()
    }

    private fun label(packageName: String): String = labels.getOrPut(packageName) {
        runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
    }

    companion object {
        // UsageEvents.Event constants, inlined: the named ones need API 29+.
        private const val ACTIVITY_RESUMED = 1
        private const val ACTIVITY_PAUSED = 2
        private const val SCREEN_NON_INTERACTIVE = 16
        private const val KEYGUARD_SHOWN = 17

        private val BACKFILL = TimeUnit.DAYS.toMillis(7)
        private val KEEP_LOCAL = TimeUnit.DAYS.toMillis(35)

        fun hasUsageAccess(context: Context): Boolean {
            val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
            @Suppress("DEPRECATION")
            val mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName,
            )
            return mode == AppOpsManager.MODE_ALLOWED
        }
    }
}
