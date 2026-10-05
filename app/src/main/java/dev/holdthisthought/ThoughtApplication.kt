package dev.holdthisthought

import android.app.Application
import android.content.Context
import dev.holdthisthought.data.AppPreferences
import dev.holdthisthought.data.ThoughtRepository
import dev.holdthisthought.data.local.ThoughtDatabase
import dev.holdthisthought.data.remote.SupabaseRemoteDataSource
import dev.holdthisthought.sync.SyncEngine
import dev.holdthisthought.sync.SyncScheduler

/** Manual dependency wiring. Small enough that a DI framework would be overhead. */
class AppContainer(context: Context) {
    private val database = ThoughtDatabase.create(context)
    val preferences = AppPreferences(context)
    val remote = SupabaseRemoteDataSource.create()
    val syncScheduler = SyncScheduler(context)
    val repository = ThoughtRepository(database.thoughtDao(), syncScheduler::requestSync)
    val syncEngine = SyncEngine(database.thoughtDao(), remote, preferences)
}

class ThoughtApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.syncScheduler.schedulePeriodic()
        container.syncScheduler.requestSync()
    }
}
