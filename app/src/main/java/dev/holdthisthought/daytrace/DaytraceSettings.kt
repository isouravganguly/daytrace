package dev.holdthisthought.daytrace

import android.content.Context
import android.os.Build
import dev.holdthisthought.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Daytrace device settings and collector state. Stored in their own prefs file, which is
 * excluded from cloud backup and device transfer (see res/xml/data_extraction_rules.xml)
 * because it holds the device token.
 */
class DaytraceSettings(context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(readConfig())
    val config: StateFlow<Config> = _config.asStateFlow()

    private val _status = MutableStateFlow(readStatus())
    val status: StateFlow<Status> = _status.asStateFlow()

    data class Config(val endpoint: String, val token: String, val deviceName: String) {
        val isComplete: Boolean get() = endpoint.isNotBlank() && token.isNotBlank()
    }

    data class Status(
        val lastUploadAt: Long?,
        val lastError: String?,
        /** True after a 401: uploads stop until the token is changed. */
        val tokenRejected: Boolean,
    )

    fun save(endpoint: String, token: String, deviceName: String) {
        val tokenChanged = token.trim() != _config.value.token
        prefs.edit()
            .putString(KEY_ENDPOINT, endpoint.trim().trimEnd('/'))
            .putString(KEY_TOKEN, token.trim())
            .putString(KEY_DEVICE_NAME, deviceName.trim())
            .apply {
                if (tokenChanged) remove(KEY_TOKEN_REJECTED).remove(KEY_LAST_ERROR)
            }
            .apply()
        _config.value = readConfig()
        _status.value = readStatus()
    }

    /** Stores a token the app obtained itself (see [DeviceProvisioner]). */
    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).remove(KEY_TOKEN_REJECTED).remove(KEY_LAST_ERROR).apply()
        _config.value = readConfig()
        _status.value = readStatus()
    }

    /** A random id for this install, so re-registering rotates the same device instead of adding one. */
    val installId: String
        get() = prefs.getString(KEY_INSTALL_ID, null) ?: (
            java.util.UUID.randomUUID().toString() + java.util.UUID.randomUUID().toString()
            ).replace("-", "").also { prefs.edit().putString(KEY_INSTALL_ID, it).apply() }

    fun recordUpload(at: Long) {
        prefs.edit().putLong(KEY_LAST_UPLOAD, at).remove(KEY_LAST_ERROR).remove(KEY_TOKEN_REJECTED).apply()
        _status.value = readStatus()
    }

    fun recordError(message: String, tokenRejected: Boolean = false) {
        prefs.edit().putString(KEY_LAST_ERROR, message).putBoolean(KEY_TOKEN_REJECTED, tokenRejected).apply()
        _status.value = readStatus()
    }

    /** Timestamp of the newest usage event already processed. */
    var eventCursor: Long
        get() = prefs.getLong(KEY_CURSOR, 0L)
        set(value) = prefs.edit().putLong(KEY_CURSOR, value).apply()

    /** The app still in the foreground at the end of the last collection run. */
    var openInterval: OpenInterval?
        get() {
            val pkg = prefs.getString(KEY_OPEN_PKG, null) ?: return null
            return OpenInterval(
                packageName = pkg,
                className = prefs.getString(KEY_OPEN_CLASS, null),
                start = prefs.getLong(KEY_OPEN_START, 0L),
                pausedAt = prefs.getLong(KEY_OPEN_PAUSED, -1L).takeIf { it >= 0 },
            )
        }
        set(value) {
            prefs.edit().apply {
                if (value == null) {
                    remove(KEY_OPEN_PKG).remove(KEY_OPEN_CLASS).remove(KEY_OPEN_START).remove(KEY_OPEN_PAUSED)
                } else {
                    putString(KEY_OPEN_PKG, value.packageName)
                    putString(KEY_OPEN_CLASS, value.className)
                    putLong(KEY_OPEN_START, value.start)
                    putLong(KEY_OPEN_PAUSED, value.pausedAt ?: -1L)
                }
            }.apply()
        }

    /** Wake-ups before this many minutes after midnight earn the early-bird bonus. */
    var wakeTargetMinutes: Int
        get() = prefs.getInt(KEY_WAKE_TARGET, DEFAULT_WAKE_TARGET)
        set(value) = prefs.edit().putInt(KEY_WAKE_TARGET, value).apply()

    private fun readConfig() = Config(
        endpoint = prefs.getString(KEY_ENDPOINT, null) ?: DEFAULT_ENDPOINT,
        token = prefs.getString(KEY_TOKEN, null).orEmpty(),
        deviceName = prefs.getString(KEY_DEVICE_NAME, null) ?: defaultDeviceName(),
    )

    private fun readStatus() = Status(
        lastUploadAt = prefs.getLong(KEY_LAST_UPLOAD, 0L).takeIf { it > 0 },
        lastError = prefs.getString(KEY_LAST_ERROR, null),
        tokenRejected = prefs.getBoolean(KEY_TOKEN_REJECTED, false),
    )

    companion object {
        const val FILE = "daytrace"
        const val DEFAULT_WAKE_TARGET = 7 * 60 + 30

        /** From `SUPABASE_URL` in local.properties; blank (tracing stays local) when it isn't set. */
        val DEFAULT_ENDPOINT: String =
            BuildConfig.SUPABASE_URL.takeIf { it.isNotBlank() }
                ?.let { it.trimEnd('/') + "/functions/v1/daytrace" }
                .orEmpty()

        private fun defaultDeviceName(): String =
            listOf(Build.MANUFACTURER.replaceFirstChar { it.uppercase() }, Build.MODEL)
                .distinct().joinToString(" ")

        private const val KEY_ENDPOINT = "endpoint"
        private const val KEY_TOKEN = "device_token"
        private const val KEY_DEVICE_NAME = "device_name"
        private const val KEY_LAST_UPLOAD = "last_upload_at"
        private const val KEY_LAST_ERROR = "last_error"
        private const val KEY_TOKEN_REJECTED = "token_rejected"
        private const val KEY_CURSOR = "event_cursor"
        private const val KEY_OPEN_PKG = "open_pkg"
        private const val KEY_OPEN_CLASS = "open_class"
        private const val KEY_OPEN_START = "open_start"
        private const val KEY_OPEN_PAUSED = "open_paused"
        private const val KEY_WAKE_TARGET = "wake_target_minutes"
        private const val KEY_INSTALL_ID = "install_id"
    }
}
