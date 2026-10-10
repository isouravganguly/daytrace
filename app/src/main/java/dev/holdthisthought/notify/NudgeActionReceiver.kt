package dev.holdthisthought.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import dev.holdthisthought.ThoughtApplication
import dev.holdthisthought.data.local.MealSlot
import dev.holdthisthought.game.Xp
import dev.holdthisthought.widget.WakeupWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Handles one-tap notification actions: log, then swap the nudge for a quick reward. */
class NudgeActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as ThoughtApplication).container
        val id = intent.getIntExtra(EXTRA_ID, 0)
        val pending = goAsync()
        scope.launch {
            try {
                val xp = when (intent.action) {
                    GOOD_MORNING -> { container.daytraceRepository.tapGoodMorning(); Xp.WAKE }
                    MEAL -> {
                        val slot = runCatching { MealSlot.valueOf(intent.getStringExtra(EXTRA_SLOT)!!) }.getOrNull()
                        if (slot != null) { container.checkIns.logMeal(slot); Xp.MEAL } else 0
                    }
                    MOOD -> { container.checkIns.logMood(intent.getIntExtra(EXTRA_SCORE, 3)); Xp.MOOD }
                    WALK -> { container.checkIns.logWalk(0); Xp.WALK }
                    GOOD_NIGHT -> { container.checkIns.logBedtime(); Xp.GOOD_NIGHT }
                    else -> 0
                }
                if (xp > 0) container.notifier.reward(id, xp)
                else NotificationManagerCompat.from(context).cancel(id)
                WakeupWidget.refresh(context)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val GOOD_MORNING = "dev.holdthisthought.nudge.GOOD_MORNING"
        const val MEAL = "dev.holdthisthought.nudge.MEAL"
        const val MOOD = "dev.holdthisthought.nudge.MOOD"
        const val WALK = "dev.holdthisthought.nudge.WALK"
        const val DISMISS = "dev.holdthisthought.nudge.DISMISS"
        const val GOOD_NIGHT = "dev.holdthisthought.nudge.GOOD_NIGHT"
        const val EXTRA_ID = "id"
        const val EXTRA_SLOT = "slot"
        const val EXTRA_SCORE = "score"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
