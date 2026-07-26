package dev.holdthisthought.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.holdthisthought.ThoughtApplication

class ThoughtSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as ThoughtApplication
        if (!app.remoteDataSource.isConfigured) return Result.success()

        return runCatching {
            app.repository.repairLocalClassifications()
            val pending = app.repository.pendingSync()
            val result = app.remoteDataSource.sync(pending)
            if (result.completed) {
                app.repository.updateKinds(result.kinds)
                app.repository.markSynced(pending.map { it.id })
            }
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
        )
    }

    companion object {
        const val UNIQUE_WORK_NAME = "thought-cloud-sync"
    }
}
