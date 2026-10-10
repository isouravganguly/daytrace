package dev.holdthisthought.daytrace

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import dev.holdthisthought.data.remote.SupabaseRemoteDataSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Gets this phone its own daytrace token, so the user never sees one: once they're signed in
 * with their email account, the app registers itself with the backend and stores the token.
 */
class DeviceProvisioner(
    private val context: Context,
    private val remote: SupabaseRemoteDataSource,
    private val api: DaytraceApi,
    private val settings: DaytraceSettings,
    private val scheduler: DaytraceScheduler,
) {
    private val mutex = Mutex()

    /** True when a usable token is in place (now or already). */
    suspend fun ensureRegistered(): Boolean = mutex.withLock {
        val needsToken = settings.config.value.token.isBlank() || settings.status.value.tokenRejected
        if (!needsToken) return true
        val jwt = remote.ownerAccessToken() ?: return false
        return try {
            val tablet = context.resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK >=
                Configuration.SCREENLAYOUT_SIZE_LARGE
            val token = api.register(jwt, settings.installId, settings.config.value.deviceName, if (tablet) "tablet" else "phone")
            settings.saveToken(token)
            // Catch up: upload what was collected, and send a wake-up that was waiting.
            scheduler.requestCollect()
            scheduler.requestWakeup()
            Log.i(TAG, "device registered")
            true
        } catch (e: Exception) {
            Log.w(TAG, "device registration failed", e)
            settings.recordError("Couldn't connect this phone yet. Will retry.")
            false
        }
    }

    private companion object {
        const val TAG = "Daytrace"
    }
}
