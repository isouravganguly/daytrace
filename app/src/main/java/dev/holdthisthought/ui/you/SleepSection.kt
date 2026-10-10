package dev.holdthisthought.ui.you

import java.time.format.DateTimeFormatter
import java.time.LocalDate
import android.app.DatePickerDialog
import android.app.Application
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.holdthisthought.ThoughtApplication
import dev.holdthisthought.game.DaySchedule

private val DayFormat = DateTimeFormatter.ofPattern("EEE, d MMM")

class SleepViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as ThoughtApplication).container
    val alarm = container.plantAlarm
    val lock = container.bedtimeLock
    val schedule get() = container.nudgePrefs.settings.value.schedule

    fun canFullScreen(): Boolean =
        Build.VERSION.SDK_INT < 34 ||
            getApplication<Application>().getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
}

/** You → Sleep: the plant alarm and the bedtime lock. */
@Composable
fun SleepSection(viewModel: SleepViewModel = viewModel()) {
    val context = LocalContext.current
    val alarm by viewModel.alarm.settings.collectAsStateWithLifecycle()
    val lockOn by viewModel.lock.enabled.collectAsStateWithLifecycle()
    var serviceOn by remember { mutableStateOf(viewModel.lock.serviceEnabled()) }
    var fullScreen by remember { mutableStateOf(viewModel.canFullScreen()) }
    var exact by remember { mutableStateOf(viewModel.alarm.canScheduleExact()) }
    LifecycleResumeEffect(Unit) {
        serviceOn = viewModel.lock.serviceEnabled()
        fullScreen = viewModel.canFullScreen()
        exact = viewModel.alarm.canScheduleExact()
        if (exact) viewModel.alarm.schedule()
        onPauseOrDispose { }
    }

    Column {
        // ---------- plant alarm ----------
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("🌿 Plant alarm", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Rings loud, even on silent, and only stops when you show it a plant. It also logs your Good morning.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = alarm.enabled, onCheckedChange = { viewModel.alarm.update(enabled = it) })
        }
        if (alarm.enabled) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        TimePickerDialog(context, { _, h, m -> viewModel.alarm.update(minute = h * 60 + m) }, alarm.minute / 60, alarm.minute % 60, false).show()
                    }
                    .padding(vertical = 10.dp),
            ) {
                Text("Rings at", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(DaySchedule.format(alarm.minute), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
            // Travelling, or nowhere near a plant: skip mornings up to a chosen day, then it's back.
            val today = LocalDate.now()
            if (alarm.pausedOn(today)) {
                val last = alarm.pausedThrough!!
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("Paused through ${last.format(DayFormat)}", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Back on ${last.plusDays(1).format(DayFormat)} at ${DaySchedule.format(alarm.minute)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { viewModel.alarm.pauseThrough(null) }) { Text("Resume") }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            DatePickerDialog(
                                context,
                                { _, y, m, d -> viewModel.alarm.pauseThrough(LocalDate.of(y, m + 1, d)) },
                                today.year, today.monthValue - 1, today.dayOfMonth,
                            ).apply { datePicker.minDate = System.currentTimeMillis() }.show()
                        }
                        .padding(vertical = 10.dp),
                ) {
                    Text("Pause for a trip", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text("Pick last day", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (!exact) {
                TextButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                }) { Text("Allow alarms") }
            }
            if (!fullScreen && Build.VERSION.SDK_INT >= 34) {
                TextButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}")))
                }) { Text("Allow full-screen alarm") }
            }
            TextButton(onClick = { viewModel.alarm.ringNow() }) { Text("Try it now") }
        }

        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

        // ---------- bedtime lock ----------
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("🌙 Bedtime lock", style = MaterialTheme.typography.titleSmall)
                Text(
                    "From ${DaySchedule.format(viewModel.schedule.bedtime)} until ${DaySchedule.format(viewModel.schedule.wake)}, " +
                        "only phone, messages, clock and Daytrace open. Other apps show a wind-down screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = lockOn, onCheckedChange = { viewModel.lock.setEnabled(it) })
        }
        if (lockOn && !serviceOn) {
            Spacer(Modifier.height(8.dp))
            Text(
                "One more step: turn on \"Daytrace bedtime lock\" in Accessibility. It only sees which app opens, never what's on screen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }) { Text("Open Accessibility settings") }
        }
    }
}
