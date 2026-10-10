package dev.holdthisthought.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.IBinder
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import dev.holdthisthought.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Plays the alarm in bursts (1 minute ringing, 1 minute quiet) at full alarm volume, even on
 * silent, until a plant photo stops it. Gives up after 30 minutes as a safety cap.
 */
class AlarmService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var loop: Job? = null
    private var player: MediaPlayer? = null
    private var previousVolume: Int? = null
    private var beeper: ToneGenerator? = null
    private var beepJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopEverything()
            return START_NOT_STICKY
        }
        ensureChannel(this)
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification(ringing = true),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
        if (loop == null) {
            _ringing.value = true
            loop = scope.launch {
                val started = System.currentTimeMillis()
                while (System.currentTimeMillis() - started < GIVE_UP_MS) {
                    ring(true)
                    delay(RING_MS)
                    ring(false)
                    delay(PAUSE_MS)
                }
                gaveUp()
            }
            startActivity(
                Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }
        return START_STICKY
    }

    private fun ring(on: Boolean) {
        val audio = getSystemService(AudioManager::class.java)
        if (on) {
            if (previousVolume == null) previousVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
            audio.setStreamVolume(AudioManager.STREAM_ALARM, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
            // Some phones (Xiaomi) point the default alarm at a file that no longer exists, so try
            // several sounds and fall back to a built-in beep rather than ringing silently.
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val candidates = listOfNotNull(
                RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM),
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE),
                RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_NOTIFICATION),
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
            )
            player = candidates.firstNotNullOfOrNull { uri ->
                val candidate = MediaPlayer()
                runCatching {
                    candidate.apply {
                        setAudioAttributes(attrs)
                        setDataSource(this@AlarmService, uri)
                        isLooping = true
                        prepare()
                        start()
                    }
                }.onFailure { candidate.release() }.getOrNull()
            }
            if (player == null) {
                beeper = runCatching { ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME) }.getOrNull()
                beepJob = scope.launch {
                    while (true) {
                        beeper?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 800)
                        delay(1_200)
                    }
                }
            }
            vibrator().vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0))
        } else {
            player?.run { runCatching { stop() }; release() }
            player = null
            beepJob?.cancel()
            beepJob = null
            beeper?.release()
            beeper = null
            vibrator().cancel()
        }
        NotificationManagerCompat.from(this).runCatching { notify(NOTIFICATION_ID, notification(on)) }
    }

    private fun gaveUp() {
        stopEverything()
        val text = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_sun)
            .setContentTitle("Alarm stopped after 30 minutes")
            .setContentText("No plant this time. Tap Good morning when you're up ☀️")
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(this).runCatching { notify(GAVE_UP_ID, text) }
    }

    private fun stopEverything() {
        loop?.cancel()
        loop = null
        ring(false)
        previousVolume?.let { getSystemService(AudioManager::class.java).setStreamVolume(AudioManager.STREAM_ALARM, it, 0) }
        previousVolume = null
        _ringing.value = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        player?.release()
        vibrator().cancel()
        _ringing.value = false
        super.onDestroy()
    }

    private fun vibrator() = getSystemService(VibratorManager::class.java).defaultVibrator

    private fun notification(ringing: Boolean) = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_stat_sun)
        .setContentTitle(if (ringing) "Rise and shine! 🌿" else "Alarm paused for a minute…")
        .setContentText("Show Blip a plant to stop the alarm.")
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setOngoing(true)
        .setSilent(true)
        .setFullScreenIntent(alarmScreen(), true)
        .setContentIntent(alarmScreen())
        .build()

    private fun alarmScreen() = PendingIntent.getActivity(
        this, 2, Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val CHANNEL = "alarm"
        private const val NOTIFICATION_ID = 201
        private const val GAVE_UP_ID = 202
        private const val ACTION_STOP = "dev.holdthisthought.alarm.STOP"
        private const val RING_MS = 60_000L
        private const val PAUSE_MS = 60_000L
        private const val GIVE_UP_MS = 30 * 60_000L

        private val _ringing = MutableStateFlow(false)
        val ringing: StateFlow<Boolean> = _ringing.asStateFlow()

        fun start(context: Context) {
            context.startForegroundService(Intent(context, AlarmService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, AlarmService::class.java).setAction(ACTION_STOP))
        }

        fun ensureChannel(context: Context) {
            val channel = NotificationChannel(CHANNEL, "Plant alarm", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "The morning alarm"
                setSound(null, null)
                enableVibration(false)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
