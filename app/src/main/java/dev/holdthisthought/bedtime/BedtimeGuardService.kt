package dev.holdthisthought.bedtime

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import dev.holdthisthought.ThoughtApplication

/**
 * Notices which app comes to the front (package name only; never screen content) and, during
 * bedtime, covers non-essential apps with the Wind-down screen. Nothing leaves the phone.
 */
class BedtimeGuardService : AccessibilityService() {
    private var lastShown = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val lock = (application as ThoughtApplication).container.bedtimeLock
        if (!lock.isActiveNow() || lock.isAllowed(pkg)) return
        // Avoid a loop of re-launches while the app is still settling.
        val now = System.currentTimeMillis()
        if (now - lastShown < 800) return
        lastShown = now
        startActivity(
            Intent(this, WindDownActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(WindDownActivity.EXTRA_BLOCKED, pkg),
        )
    }

    override fun onInterrupt() = Unit
}
