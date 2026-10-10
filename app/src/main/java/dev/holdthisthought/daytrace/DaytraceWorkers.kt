package dev.holdthisthought.daytrace

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.holdthisthought.ThoughtApplication
import dev.holdthisthought.widget.WakeupWidget
import java.util.concurrent.TimeUnit

private const val TAG = "Daytrace"

private val Context.container get() = (applicationContext as ThoughtApplication).container

/** Collects new app usage, then uploads the queue. Runs every ~15 minutes and on demand. */
class UsageCollectWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = applicationContext.container
        val stored = runCatching { container.usageCollector.collect() }
            .onFailure { Log.w(TAG, "collect failed", it) }
            .getOrDefault(0)
        runCatching { container.deviceProvisioner.ensureRegistered() }
        runCatching { container.health.sync() }.onFailure { Log.w(TAG, "health sync failed", it) }
        runCatching { container.nudges.check() }.onFailure { Log.w(TAG, "nudge check failed", it) }
        val outcome = container.daytraceUploader.upload()
        Log.i(TAG, "collected $stored, upload: $outcome")
        // Keeps the widget's day current (it resets after midnight).
        WakeupWidget.refresh(applicationContext)
        return when (outcome) {
            is DaytraceUploader.Outcome.Failed ->
                if (runAttemptCount < 5) Result.retry() else Result.success()
            else -> Result.success()
        }
    }
}

/** Tells the server about a "Good morning" tap; waits for a network and retries. */
class WakeupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = applicationContext.container
        return try {
            container.daytraceRepository.sendWakeup()
            Result.success()
        } catch (e: DaytraceApi.UnauthorizedException) {
            container.daytraceSettings.recordError("Token rejected. Check the device token.", tokenRejected = true)
            Result.failure()
        } catch (e: Exception) {
            Log.w(TAG, "wakeup failed", e)
            if (runAttemptCount < 20) Result.retry() else Result.failure()
        } finally {
            WakeupWidget.refresh(applicationContext)
        }
    }
}

class DaytraceScheduler(private val context: Context) {
    private val workManager get() = WorkManager.getInstance(context)

    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<UsageCollectWorker>(15, TimeUnit.MINUTES)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun requestCollect() {
        val request = OneTimeWorkRequestBuilder<UsageCollectWorker>()
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
    }

    fun requestWakeup() {
        val request = OneTimeWorkRequestBuilder<WakeupWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(WAKEUP, ExistingWorkPolicy.REPLACE, request)
    }

    private companion object {
        const val PERIODIC = "daytrace-collect"
        const val NOW = "daytrace-collect-now"
        const val WAKEUP = "daytrace-wakeup"
    }
}

/** Restarts collection after a reboot (catches up on events from before the shutdown). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val scheduler = context.container.daytraceScheduler
        scheduler.schedulePeriodic()
        scheduler.requestCollect()
        context.container.plantAlarm.schedule()
    }
}
