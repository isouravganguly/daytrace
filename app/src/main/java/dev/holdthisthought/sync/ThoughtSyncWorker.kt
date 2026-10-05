package dev.holdthisthought.sync

import android.content.Context
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
import java.util.concurrent.TimeUnit

class ThoughtSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val engine = (applicationContext as ThoughtApplication).container.syncEngine
        return when (engine.sync()) {
            SyncEngine.Outcome.Synced, SyncEngine.Outcome.Skipped -> Result.success()
            is SyncEngine.Outcome.Failed ->
                if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 8
    }
}

/** Schedules background sync: soon after every change, and every few hours as a safety net. */
class SyncScheduler(private val context: Context) {
    private val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun requestSync() {
        val request = OneTimeWorkRequestBuilder<ThoughtSyncWorker>()
            .setConstraints(constraints)
            .setInitialDelay(2, TimeUnit.SECONDS) // batches rapid edits into one sync
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ONE_TIME_WORK, ExistingWorkPolicy.REPLACE, request,
        )
    }

    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<ThoughtSyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request,
        )
    }

    private companion object {
        const val ONE_TIME_WORK = "thought-sync"
        const val PERIODIC_WORK = "thought-sync-periodic"
    }
}
