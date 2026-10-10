package dev.holdthisthought.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import dev.holdthisthought.MainActivity
import dev.holdthisthought.R
import dev.holdthisthought.data.local.MealSlot
import dev.holdthisthought.game.GameState

/** Builds and posts nudge notifications, each with one-tap actions that log directly. */
class Notifier(private val context: Context) {

    fun canPost(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun ensureChannel() {
        val channel = NotificationChannel(CHANNEL, "Nudges from Blip", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Friendly one-tap reminders to log your day"
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    fun post(push: Push, game: GameState, epochDay: Long) {
        if (!canPost()) return
        ensureChannel()
        val text = NudgeCopy.of(push, game, epochDay)
        val builder = base(push.id)
            .setContentTitle(text.title)
            .setContentText(text.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.body))
            .setContentIntent(openApp(push.id, if (push == Push.CLOSE_DAY) OPEN_VERDICT else null))
        actionsFor(push).forEach { (label, intent) -> builder.addAction(0, label, intent) }
        notify(push.id, builder)
    }

    /** Replaces a nudge with an instant reward, which disappears on its own. */
    fun reward(notificationId: Int, xp: Int) {
        if (!canPost()) return
        val builder = base(notificationId)
            .setContentTitle(NudgeCopy.reward(xp))
            .setContentText("Logged. See your quests in Daytrace.")
            .setContentIntent(openApp(notificationId, null))
            .setTimeoutAfter(6_000)
            .setOnlyAlertOnce(true)
            .setSilent(true)
        notify(notificationId, builder)
    }

    private fun base(id: Int) = NotificationCompat.Builder(context, CHANNEL)
        .setSmallIcon(R.drawable.ic_stat_sun)
        .setLargeIcon(ContextCompat.getDrawable(context, R.drawable.blip_wave)?.toBitmap(192, 192))
        .setColor(0xFF5F88AE.toInt())
        .setAutoCancel(true)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)

    private fun notify(id: Int, builder: NotificationCompat.Builder) {
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post; nothing to do.
        }
    }

    private fun actionsFor(push: Push): List<Pair<String, PendingIntent>> = when (push) {
        Push.WAKE -> listOf("☀️ I'm up" to action(push, NudgeActionReceiver.GOOD_MORNING))
        Push.BREAKFAST -> mealActions(push, MealSlot.BREAKFAST)
        Push.LUNCH -> mealActions(push, MealSlot.LUNCH)
        Push.DINNER -> mealActions(push, MealSlot.DINNER)
        Push.MOOD -> listOf(2 to "🙁", 3 to "😐", 4 to "🙂").map { (score, face) ->
            face to action(push, NudgeActionReceiver.MOOD, score = score)
        }
        Push.WALK -> listOf("🥾 Walked" to action(push, NudgeActionReceiver.WALK))
        Push.CLOSE_DAY -> listOf("⚖️ Rate my day" to openApp(push.id, OPEN_VERDICT))
        Push.STREAK, Push.ALMOST -> listOf("Let's go" to openApp(push.id, null))
        Push.GOOD_NIGHT -> listOf("🌙 Good night" to action(push, NudgeActionReceiver.GOOD_NIGHT))
    }

    private fun mealActions(push: Push, slot: MealSlot) = listOf(
        "🍽️ Ate it" to action(push, NudgeActionReceiver.MEAL, slot = slot.name),
        "Skipped" to action(push, NudgeActionReceiver.DISMISS),
    )

    private fun action(push: Push, what: String, slot: String? = null, score: Int = 0): PendingIntent {
        val intent = Intent(context, NudgeActionReceiver::class.java)
            .setAction(what)
            .putExtra(NudgeActionReceiver.EXTRA_ID, push.id)
            .putExtra(NudgeActionReceiver.EXTRA_SLOT, slot)
            .putExtra(NudgeActionReceiver.EXTRA_SCORE, score)
        // Unique request code per action button so extras don't collide.
        val request = push.id * 100 + what.hashCode().mod(50) + score
        return PendingIntent.getBroadcast(context, request, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun openApp(id: Int, open: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_OPEN, open)
        return PendingIntent.getActivity(context, id + (if (open != null) 50 else 0), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        const val CHANNEL = "nudges"
        const val EXTRA_OPEN = "daytrace.open"
        const val OPEN_VERDICT = "verdict"
        const val OPEN_CAPTURE = "capture"
    }
}
