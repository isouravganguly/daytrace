package dev.holdthisthought

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dev.holdthisthought.data.SupabaseRemoteDataSource
import dev.holdthisthought.data.ThoughtDatabase
import dev.holdthisthought.data.ThoughtRepository
import dev.holdthisthought.sync.ThoughtSyncWorker

class ThoughtApplication : Application() {
    private val database by lazy { ThoughtDatabase.create(this) }
    val remoteDataSource by lazy { SupabaseRemoteDataSource.create() }

    val repository by lazy {
        ThoughtRepository(database.thoughtDao(), ::scheduleSync)
    }

    override fun onCreate() {
        super.onCreate()
        scheduleSync()
    }

    fun scheduleSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<ThoughtSyncWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(this).enqueueUniqueWork(
            ThoughtSyncWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
