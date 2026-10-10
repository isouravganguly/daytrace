package dev.holdthisthought.bedtime

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager
import dev.holdthisthought.game.DayClock
import dev.holdthisthought.game.DaySchedule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalTime

/**
 * Bedtime lock: from bedtime until wake-up, only essential apps open; everything else shows the
 * Wind-down screen. An escape hatch (hold 10 s) unlocks for 10 minutes, at most twice a night.
 */
class BedtimeLock(private val context: Context, private val schedule: () -> DaySchedule) {
    private val prefs = context.getSharedPreferences("bedtime_lock", Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun setEnabled(on: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, on).apply()
        _enabled.value = on
    }

    /** True between bedtime and wake-up (in logical-day minutes). */
    fun isBedtime(now: LocalTime = LocalTime.now()): Boolean {
        val s = schedule()
        val m = DayClock.minuteOfDay(now.hour * 60 + now.minute)
        val start = DayClock.minuteOfDay(s.bedtime)
        val end = DayClock.minuteOfDay(s.wake)
        // Bedtime (e.g. 02:00) is near the end of the logical day; wake-up (10:00) near the start.
        return m >= start || m < end
    }

    fun unlockedUntil(): Long = prefs.getLong(KEY_UNLOCKED_UNTIL, 0L)

    /** Unlocks used tonight (the counter resets each logical day). */
    fun unlocksUsed(): Int =
        if (prefs.getString(KEY_UNLOCK_DAY, null) == nightKey()) prefs.getInt(KEY_UNLOCKS, 0) else 0

    fun unlocksLeft(): Int = MAX_UNLOCKS - unlocksUsed()

    fun unlockFor10Minutes(): Boolean {
        if (unlocksLeft() <= 0) return false
        prefs.edit()
            .putString(KEY_UNLOCK_DAY, nightKey())
            .putInt(KEY_UNLOCKS, unlocksUsed() + 1)
            .putLong(KEY_UNLOCKED_UNTIL, System.currentTimeMillis() + UNLOCK_MS)
            .apply()
        return true
    }

    fun isActiveNow(): Boolean =
        _enabled.value && isBedtime() && System.currentTimeMillis() > unlockedUntil()

    /** Apps that always open: phone, messages, clock, settings, keyboard, launcher, Daytrace. */
    fun isAllowed(packageName: String): Boolean {
        if (packageName == context.packageName) return true
        if (packageName in ALWAYS_ALLOWED) return true
        if (packageName.contains("dialer") || packageName.contains("incallui") || packageName.contains("telecom")) return true
        if (packageName.contains("deskclock") || packageName.contains("clock")) return true
        if (packageName.contains("emergency")) return true
        return packageName in dynamicAllowed()
    }

    private fun dynamicAllowed(): Set<String> = buildSet {
        runCatching { context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage }.getOrNull()?.let(::add)
        runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull()?.let(::add)
        // Current keyboard, and the home screen(s).
        runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
                ?.let { ComponentName.unflattenFromString(it)?.packageName }
        }.getOrNull()?.let(::add)
        runCatching { context.getSystemService(InputMethodManager::class.java)?.enabledInputMethodList?.map { it.packageName } }
            .getOrNull()?.let(::addAll)
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        context.packageManager.queryIntentActivities(home, 0).forEach { add(it.activityInfo.packageName) }
    }

    /** Is the accessibility service switched on in Android settings? */
    fun serviceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val me = ComponentName(context, BedtimeGuardService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    private fun nightKey(): String = DayClock.today().toString()

    companion object {
        const val MAX_UNLOCKS = 2
        const val UNLOCK_MS = 10 * 60_000L
        private const val KEY_ENABLED = "enabled"
        private const val KEY_UNLOCKED_UNTIL = "unlocked_until"
        private const val KEY_UNLOCKS = "unlocks"
        private const val KEY_UNLOCK_DAY = "unlock_day"

        private val ALWAYS_ALLOWED = setOf(
            "com.android.systemui", "android", "com.android.settings", "com.miui.securitycenter",
            "com.android.phone", "com.android.server.telecom", "com.google.android.dialer",
            "com.google.android.apps.messaging", "com.android.mms", "com.android.permissioncontroller",
            "com.google.android.permissioncontroller", "com.google.android.healthconnect.controller",
        )
    }
}
