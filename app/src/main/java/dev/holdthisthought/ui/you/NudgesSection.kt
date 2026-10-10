package dev.holdthisthought.ui.you

import android.Manifest
import android.app.Application
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.holdthisthought.ThoughtApplication
import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.LocalContext
import dev.holdthisthought.game.DaySchedule
import dev.holdthisthought.notify.Intensity
import dev.holdthisthought.notify.NudgeSettings
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class NudgesViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as ThoughtApplication).container
    val settings: StateFlow<NudgeSettings> = container.nudgePrefs.settings

    fun canPost() = container.notifier.canPost()
    fun setEnabled(enabled: Boolean) = container.nudgePrefs.update { it.copy(enabled = enabled) }
    fun setIntensity(intensity: Intensity) = container.nudgePrefs.update { it.copy(intensity = intensity) }
    fun setSchedule(transform: (DaySchedule) -> DaySchedule) =
        container.nudgePrefs.update { it.copy(schedule = transform(it.schedule)) }
}

/** You → Nudges: on/off, how chatty, notification permission and a test button. */
@Composable
fun NudgesSection(viewModel: NudgesViewModel = viewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var permitted by remember { mutableStateOf(viewModel.canPost()) }
    LifecycleResumeEffect(Unit) {
        permitted = viewModel.canPost()
        onPauseOrDispose { }
    }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permitted = it }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Nudges from Blip", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Short, friendly reminders you can answer in one tap, only for things you haven't logged yet. " +
                        "They follow your day below, and stay quiet from an hour after bedtime until you wake.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = settings.enabled, onCheckedChange = viewModel::setEnabled)
        }
        if (settings.enabled) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Intensity.entries.forEach { option ->
                    FilterChip(
                        selected = settings.intensity == option,
                        onClick = { viewModel.setIntensity(option) },
                        label = { Text(option.label) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Text(
                "Up to ${settings.intensity.maxPerDay} nudges a day",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Text("My day", style = MaterialTheme.typography.titleSmall)
            val context = LocalContext.current
            val s = settings.schedule
            listOf(
                Triple("☀️  Wake up", s.wake) { m: Int -> viewModel.setSchedule { it.copy(wake = m) } },
                Triple("🥐  Breakfast", s.breakfast) { m: Int -> viewModel.setSchedule { it.copy(breakfast = m) } },
                Triple("🍜  Lunch", s.lunch) { m: Int -> viewModel.setSchedule { it.copy(lunch = m) } },
                Triple("👟  Walk", s.walk) { m: Int -> viewModel.setSchedule { it.copy(walk = m) } },
                Triple("🍝  Dinner", s.dinner) { m: Int -> viewModel.setSchedule { it.copy(dinner = m) } },
                Triple("🌙  Bedtime", s.bedtime) { m: Int -> viewModel.setSchedule { it.copy(bedtime = m) } },
            ).forEach { (label, minute, onPick) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            TimePickerDialog(context, { _, h, min -> onPick(h * 60 + min) }, minute / 60, minute % 60, false).show()
                        }
                        .padding(vertical = 10.dp),
                ) {
                    Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(DaySchedule.format(minute), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(12.dp))
            if (!permitted && Build.VERSION.SDK_INT >= 33) {
                Button(
                    onClick = { askPermission.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Allow notifications") }
            }
        }
    }
}
