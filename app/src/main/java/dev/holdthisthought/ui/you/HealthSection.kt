package dev.holdthisthought.ui.you

import android.app.Application
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.holdthisthought.ThoughtApplication
import dev.holdthisthought.health.HealthConnectSource
import dev.holdthisthought.health.HealthRepository
import dev.holdthisthought.ui.components.relativeTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HealthViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as ThoughtApplication).container
    val source = container.health.source
    val status: StateFlow<HealthRepository.Status> = container.health.status

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    fun refresh() = viewModelScope.launch(Dispatchers.IO) { container.health.refreshStatus() }

    fun syncNow() = viewModelScope.launch(Dispatchers.IO) {
        _syncing.value = true
        try {
            container.health.sync()
            container.daytraceUploader.upload()
        } finally {
            _syncing.value = false
        }
    }
}

/** You → Health: Health Connect status, what's shared, sync and permissions. */
@Composable
fun HealthSection(viewModel: HealthViewModel = viewModel()) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val syncing by viewModel.syncing.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val ask = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        viewModel.syncNow()
    }
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Column {
        when (status.availability) {
            HealthConnectSource.Availability.UNAVAILABLE -> Text(
                "Health Connect isn't available on this device.",
                style = MaterialTheme.typography.bodyMedium,
            )
            HealthConnectSource.Availability.NEEDS_UPDATE -> Text(
                "Health Connect needs an update from the Play Store.",
                style = MaterialTheme.typography.bodyMedium,
            )
            HealthConnectSource.Availability.AVAILABLE -> {
                val source = viewModel.source
                val parts = listOfNotNull(
                    "steps".takeIf { source.stepsPermission in status.granted },
                    "walks".takeIf { source.exercisePermission in status.granted },
                    "sleep".takeIf { source.sleepPermission in status.granted },
                    "meals".takeIf { source.nutritionPermission in status.granted },
                )
                Text(
                    if (status.connected) "Reading ${parts.joinToString()}" else "Not connected",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    buildString {
                        append(
                            if (status.connected) "From Google Fit, Mi Health, MyFitnessPal and anything else that writes to Health Connect. "
                            else "Connect to fill steps, walks, sleep and meals in automatically. ",
                        )
                        status.lastSyncAt?.let { append("Updated ${relativeTime(it)}, automatically. ") }
                        if (status.connected && !status.background) append("Updates when you open the app (background reading is off).")
                        status.lastError?.let { append("\nLast read failed: $it") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!status.connected || status.granted.size < source.requested.size || !status.background) {
                        Button(onClick = { ask.launch(source.requested + source.backgroundPermission) }) {
                            Text(if (status.connected) "Allow more" else "Connect")
                        }
                    }
                }
                if (status.connected) {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        },
                    ) { Text("Manage in Health Connect") }
                }
            }
        }
    }
}
