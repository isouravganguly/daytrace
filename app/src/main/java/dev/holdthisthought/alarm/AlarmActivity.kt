package dev.holdthisthought.alarm

import dev.holdthisthought.ui.theme.Earth
import dev.holdthisthought.ui.theme.Forest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.holdthisthought.ThoughtApplication
import dev.holdthisthought.ui.components.Blip
import dev.holdthisthought.ui.components.BlipMood
import dev.holdthisthought.ui.theme.HoldThisThoughtTheme
import dev.holdthisthought.widget.WakeupWidget
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Full-screen alarm over the lock screen. Only a plant photo stops it. */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        // No backing out of the alarm.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })
        setContent { HoldThisThoughtTheme { AlarmScreen(onDone = ::finish) } }
    }
}

private val Cream = Earth.Cream
private val ForestInk = Forest.F900

@Composable
private fun AlarmScreen(onDone: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf("Show me a plant and I'll stop 🌿") }
    var checking by remember { mutableStateOf(false) }
    var success by remember { mutableStateOf(false) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap == null) return@rememberLauncherForActivityResult
        checking = true
        scope.launch {
            val result = runCatching { PlantDetector.check(context, bitmap) }.getOrNull()
            checking = false
            when {
                result == null -> message = "I couldn't look at that one. Try again 🌿"
                result.isPlant -> {
                    success = true
                    message = "That's a plant! Good morning ☀️ +20 XP"
                    AlarmService.stop(context)
                    val container = (context.applicationContext as ThoughtApplication).container
                    container.daytraceRepository.tapGoodMorning()
                    WakeupWidget.refresh(context)
                }
                else -> {
                    val seen = result.labels.take(2).joinToString { it.first.lowercase() }
                    message = if (seen.isBlank()) "Hmm, no plant there. Find a green friend 🌿"
                    else "That looks like $seen, not a plant. Try again 🌿"
                }
            }
        }
    }

    LaunchedEffect(success) {
        if (success) {
            delay(2_500)
            onDone()
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Forest.F700, Forest.F600)))
            .safeDrawingPadding()
            .padding(28.dp),
    ) {
        Text(
            LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm")),
            color = Cream,
            fontSize = 72.sp,
            style = MaterialTheme.typography.displayLarge,
        )
        Spacer(Modifier.height(8.dp))
        Blip(mood = if (success) BlipMood.PROUD else BlipMood.EXCITED, level = 1, size = 200.dp, bounceKey = success, onReact = {})
        Spacer(Modifier.height(16.dp))
        Text(message, color = Cream, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        if (!success) {
            Button(
                onClick = { camera.launch(null) },
                enabled = !checking,
                colors = ButtonDefaults.buttonColors(containerColor = Cream, contentColor = ForestInk),
                modifier = Modifier.fillMaxWidth().height(60.dp),
            ) { Text(if (checking) "Looking…" else "🌿  Take a photo of a plant", style = MaterialTheme.typography.titleMedium) }
            Spacer(Modifier.height(12.dp))
            Text(
                "The photo stays on your phone. It's only checked for a plant, then forgotten.",
                color = Cream.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}
