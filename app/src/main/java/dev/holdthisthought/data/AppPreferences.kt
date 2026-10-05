package dev.holdthisthought.data

import android.content.Context
import dev.holdthisthought.data.local.ThoughtSpace
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Small, non-critical app state. Thoughts themselves live in Room. */
class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("hold-this-thought", Context.MODE_PRIVATE)

    private val _lastSyncedAt = MutableStateFlow(prefs.getLong(KEY_LAST_SYNCED_AT, 0L).takeIf { it > 0 })
    val lastSyncedAt: StateFlow<Long?> = _lastSyncedAt.asStateFlow()

    private val _lastSyncError = MutableStateFlow(prefs.getString(KEY_LAST_SYNC_ERROR, null))
    val lastSyncError: StateFlow<String?> = _lastSyncError.asStateFlow()

    var defaultSpace: ThoughtSpace
        get() = runCatching { ThoughtSpace.valueOf(prefs.getString(KEY_SPACE, null)!!) }
            .getOrDefault(ThoughtSpace.WORK)
        set(value) = prefs.edit().putString(KEY_SPACE, value.name).apply()

    /** Cloud account whose data this device holds. Used to detect an account switch. */
    var syncedUserId: String?
        get() = prefs.getString(KEY_SYNCED_USER, null)
        set(value) = prefs.edit().putString(KEY_SYNCED_USER, value).apply()

    /** Server timestamp of the newest change already pulled. */
    var pullCursor: String?
        get() = prefs.getString(KEY_PULL_CURSOR, null)
        set(value) = prefs.edit().putString(KEY_PULL_CURSOR, value).apply()

    fun recordSyncSuccess(at: Long) {
        prefs.edit().putLong(KEY_LAST_SYNCED_AT, at).remove(KEY_LAST_SYNC_ERROR).apply()
        _lastSyncedAt.value = at
        _lastSyncError.value = null
    }

    fun recordSyncError(message: String) {
        prefs.edit().putString(KEY_LAST_SYNC_ERROR, message).apply()
        _lastSyncError.value = message
    }

    private companion object {
        const val KEY_SPACE = "default_space"
        const val KEY_SYNCED_USER = "synced_user_id"
        const val KEY_PULL_CURSOR = "pull_cursor"
        const val KEY_LAST_SYNCED_AT = "last_synced_at"
        const val KEY_LAST_SYNC_ERROR = "last_sync_error"
    }
}
