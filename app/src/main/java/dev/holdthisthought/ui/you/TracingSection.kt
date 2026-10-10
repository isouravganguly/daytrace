package dev.holdthisthought.ui.you

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.holdthisthought.ThoughtApplication
import dev.holdthisthought.daytrace.DaytraceSettings
import dev.holdthisthought.ui.components.relativeTime
import dev.holdthisthought.ui.theme.LocalGameColors
import dev.holdthisthought.ui.today.openUsageAccessSettings
import dev.holdthisthought.ui.today.requestWidget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TracingViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as ThoughtApplication).container
    private val settings = container.daytraceSettings

    val config: StateFlow<DaytraceSettings.Config> = settings.config
    val status: StateFlow<DaytraceSettings.Status> = settings.status
    private val _usageAccess = MutableStateFlow(container.usageCollector.hasUsageAccess())
    val usageAccess: StateFlow<Boolean> = _usageAccess.asStateFlow()

    fun refresh() {
        _usageAccess.value = container.usageCollector.hasUsageAccess()
    }
}

/** Screen time: usage access, backup status, widget. No tokens or URLs — those are set up automatically. */
@Composable
fun TracingSection(viewModel: TracingViewModel = viewModel()) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val usageAccess by viewModel.usageAccess.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Column {
        StatusRow(
            ok = usageAccess,
            title = if (usageAccess) "Screen time is on" else "Screen time is off (optional)",
            body = if (usageAccess) "Daytrace notes which app is in front, to show where your time went." else
                "Allow usage access to see where your phone time goes.",
            action = if (usageAccess) null else "Allow" to { openUsageAccessSettings(context) },
        )
        Spacer(Modifier.height(12.dp))
        StatusRow(
            ok = config.isComplete && !status.tokenRejected,
            title = when {
                !config.isComplete -> "Backs up after you sign in"
                status.tokenRejected -> "Reconnecting…"
                else -> "Screen time backs up automatically"
            },
            body = buildString {
                append(status.lastUploadAt?.let { "Last upload ${relativeTime(it)}" } ?: "Not uploaded yet")
                status.lastError?.takeIf { !status.tokenRejected }?.let { append("\nLast attempt failed: $it") }
            },
            action = null,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { requestWidget(context) }) { Text("Add home-screen widget") }
    }
}

@Composable
private fun StatusRow(ok: Boolean, title: String, body: String, action: Pair<String, () -> Unit>?) {
    val game = LocalGameColors.current
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
            null,
            tint = if (ok) game.move else game.fuel,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (action != null) TextButton(onClick = action.second) { Text(action.first) }
    }
}
