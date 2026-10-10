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

    private val _stepGoal = MutableStateFlow(prefs.getInt(KEY_STEP_GOAL, DEFAULT_STEP_GOAL))
    /** Daily steps that complete the Move quest. The user's main goal right now is fitness. */
    val stepGoal: StateFlow<Int> = _stepGoal.asStateFlow()

    fun setStepGoal(steps: Int) {
        prefs.edit().putInt(KEY_STEP_GOAL, steps).apply()
        _stepGoal.value = steps
    }

    private val _onboardingDone = MutableStateFlow(prefs.getBoolean(KEY_ONBOARDING_DONE, false))
    val onboardingDone: StateFlow<Boolean> = _onboardingDone.asStateFlow()

    fun finishOnboarding() {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()
        _onboardingDone.value = true
    }

    private val _widgetPromptDismissed = MutableStateFlow(prefs.getBoolean(KEY_WIDGET_PROMPT, false))
    val widgetPromptDismissed: StateFlow<Boolean> = _widgetPromptDismissed.asStateFlow()

    fun dismissWidgetPrompt() {
        prefs.edit().putBoolean(KEY_WIDGET_PROMPT, true).apply()
        _widgetPromptDismissed.value = true
    }

    fun recordSyncSuccess(at: Long) {
        prefs.edit().putLong(KEY_LAST_SYNCED_AT, at).remove(KEY_LAST_SYNC_ERROR).apply()
        _lastSyncedAt.value = at
        _lastSyncError.value = null
    }

    fun recordSyncError(message: String) {
        prefs.edit().putString(KEY_LAST_SYNC_ERROR, message).apply()
        _lastSyncError.value = message
    }

    companion object {
        const val DEFAULT_STEP_GOAL = 7_000
        val STEP_GOALS = listOf(5_000, 7_000, 10_000)
        private const val KEY_STEP_GOAL = "step_goal"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
        private const val KEY_WIDGET_PROMPT = "widget_prompt_dismissed"
        private const val KEY_SPACE = "default_space"
        private const val KEY_SYNCED_USER = "synced_user_id"
        private const val KEY_PULL_CURSOR = "pull_cursor"
        private const val KEY_LAST_SYNCED_AT = "last_synced_at"
        private const val KEY_LAST_SYNC_ERROR = "last_sync_error"
    }
}
