package dev.holdthisthought.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.holdthisthought.ThoughtApplication

/** Fired by AlarmManager at alarm time: start ringing, and line up tomorrow's alarm. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmService.start(context)
        (context.applicationContext as ThoughtApplication).container.plantAlarm.schedule()
    }
}
