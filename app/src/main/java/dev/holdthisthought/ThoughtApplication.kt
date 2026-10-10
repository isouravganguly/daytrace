package dev.holdthisthought

import android.app.Application
import android.content.Context
import dev.holdthisthought.data.AppPreferences
import dev.holdthisthought.data.CheckInRepository
import dev.holdthisthought.data.ThoughtRepository
import dev.holdthisthought.data.local.ThoughtDatabase
import dev.holdthisthought.data.remote.SupabaseRemoteDataSource
import dev.holdthisthought.daytrace.DaytraceApi
import dev.holdthisthought.daytrace.DaytraceRepository
import dev.holdthisthought.daytrace.DaytraceScheduler
import dev.holdthisthought.daytrace.DaytraceSettings
import dev.holdthisthought.daytrace.DaytraceUploader
import dev.holdthisthought.alarm.AlarmService
import dev.holdthisthought.alarm.PlantAlarm
import dev.holdthisthought.bedtime.BedtimeLock
import dev.holdthisthought.daytrace.DeviceProvisioner
import dev.holdthisthought.daytrace.UsageCollector
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import dev.holdthisthought.health.HealthConnectSource
import dev.holdthisthought.health.HealthRepository
import dev.holdthisthought.notify.Notifier
import dev.holdthisthought.notify.NudgePrefs
import dev.holdthisthought.notify.Nudges
import dev.holdthisthought.sync.SyncEngine
import dev.holdthisthought.sync.SyncScheduler

/** Manual dependency wiring. Small enough that a DI framework would be overhead. */
class AppContainer(context: Context) {
    private val database = ThoughtDatabase.create(context)
    val traceDao = database.traceDao()
    val preferences = AppPreferences(context)
    val remote = SupabaseRemoteDataSource.create()
    val syncScheduler = SyncScheduler(context)
    val repository = ThoughtRepository(database.thoughtDao(), syncScheduler::requestSync)
    val syncEngine = SyncEngine(database.thoughtDao(), remote, preferences)
    val checkIns = CheckInRepository(traceDao)

    // Daytrace: wake-ups and app usage, sent to the `daytrace` function with a device token.
    val daytraceSettings = DaytraceSettings(context)
    val daytraceScheduler = DaytraceScheduler(context)
    private val daytraceApi = DaytraceApi(daytraceSettings)
    val deviceProvisioner by lazy { DeviceProvisioner(context, remote, daytraceApi, daytraceSettings, daytraceScheduler) }
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val usageCollector = UsageCollector(context, traceDao, daytraceSettings)
    val daytraceUploader = DaytraceUploader(traceDao, daytraceApi, daytraceSettings)

    // Health Connect: steps, walks, sleep and meals, read-only.
    val health = HealthRepository(context, HealthConnectSource(context), traceDao)
    val daytraceRepository = DaytraceRepository(traceDao, daytraceApi, daytraceSettings, daytraceScheduler)

    // Nudges: friendly one-tap notifications, checked by the 15-minute background worker.
    val nudgePrefs = NudgePrefs(context)
    val notifier = Notifier(context)
    // Sleep: the morning plant alarm and the bedtime lock.
    val plantAlarm = PlantAlarm(context)
    val bedtimeLock = BedtimeLock(context) { nudgePrefs.settings.value.schedule }

    val nudges = Nudges(repository, checkIns, traceDao, daytraceSettings, nudgePrefs, notifier) { preferences.stepGoal.value }
}

class ThoughtApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.syncScheduler.schedulePeriodic()
        container.syncScheduler.requestSync()
        container.daytraceScheduler.schedulePeriodic()
        container.daytraceScheduler.requestCollect()
        container.notifier.ensureChannel()
        AlarmService.ensureChannel(this)
        container.plantAlarm.schedule()
        // As soon as the owner is signed in, this phone gets its own daytrace token.
        container.appScope.launch {
            container.remote.sessionStatus.collect { status ->
                if (status is SessionStatus.Authenticated) container.deviceProvisioner.ensureRegistered()
            }
        }
    }
}
