package dev.holdthisthought.bedtime

import dev.holdthisthought.ui.theme.Earth
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import dev.holdthisthought.MainActivity
import dev.holdthisthought.ThoughtApplication
import dev.holdthisthought.notify.Notifier
import dev.holdthisthought.ui.components.Blip
import dev.holdthisthought.ui.components.BlipMood
import dev.holdthisthought.ui.theme.HoldThisThoughtTheme
import dev.holdthisthought.widget.WakeupWidget
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Shown instead of non-essential apps at bedtime: gentle ideas to wind down, and Good night. */
class WindDownActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Back goes home, not back into the blocked app.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })
        val container = (application as ThoughtApplication).container
        setContent {
            HoldThisThoughtTheme {
                WindDownScreen(
                    unlocksLeft = container.bedtimeLock.unlocksLeft(),
                    onGoodNight = {
                        lifecycleScope.launch {
                            container.checkIns.logBedtime()
                            WakeupWidget.refresh(this@WindDownActivity)
                            goHome()
                        }
                    },
                    onWriteThought = {
                        startActivity(
                            Intent(this, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                .putExtra(Notifier.EXTRA_OPEN, Notifier.OPEN_CAPTURE),
                        )
                        finish()
                    },
                    onUnlock = {
                        if (container.bedtimeLock.unlockFor10Minutes()) finish()
                    },
                )
            }
        }
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    companion object {
        const val EXTRA_BLOCKED = "blocked"
    }
}

private val Night = Earth.Night
private val NightEnd = Earth.NightEnd
private val Cream = Earth.Cream

@Composable
private fun WindDownScreen(unlocksLeft: Int, onGoodNight: () -> Unit, onWriteThought: () -> Unit, onUnlock: () -> Unit) {
    var breathing by remember { mutableStateOf(false) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Night, NightEnd)))
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Blip(mood = BlipMood.SLEEPY, level = 1, size = 170.dp, onReact = {})
        Text("It's past bedtime 🌙", color = Cream, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            "This app can wait until morning. How about one of these instead?",
            color = Cream.copy(alpha = 0.8f),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Idea("📖", "Read a few pages of a physical book", "Paper, not pixels. Your brain will thank you.")
        Idea("🌬️", "Breathe 4-7-8", if (breathing) "In for 4 · hold for 7 · out for 8. Four rounds." else "Tap to see how", onClick = { breathing = !breathing })
        Idea("✍️", "Write down what's on your mind", "Hold the thought here; deal with it tomorrow.", onClick = onWriteThought)
        Idea("🎧", "Put on something calm", "A sleep story or soft music, screen face down.")
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onGoodNight,
            colors = ButtonDefaults.buttonColors(containerColor = Cream, contentColor = Night),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text("🌙  Good night", style = MaterialTheme.typography.titleMedium) }
        Spacer(Modifier.height(24.dp))
        if (unlocksLeft > 0) HoldToUnlock(unlocksLeft, onUnlock)
        else Text("No more unlocks tonight. Phone and messages still work.", color = Cream.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Idea(emoji: String, title: String, body: String, onClick: (() -> Unit)? = null) {
    Surface(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        shape = MaterialTheme.shapes.medium,
        color = Color.White.copy(alpha = 0.08f),
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(14.dp)) {
            Text(emoji, fontSize = 26.sp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, color = Cream, style = MaterialTheme.typography.titleSmall)
                Text(body, color = Cream.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** The escape hatch: hold for 10 seconds to unlock for 10 minutes. */
@Composable
private fun HoldToUnlock(unlocksLeft: Int, onUnlock: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.06f))
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    job = scope.launch {
                        progress.animateTo(1f, tween(10_000, easing = LinearEasing))
                        onUnlock()
                    }
                    waitForUpOrCancellation()
                    job?.cancel()
                    scope.launch { progress.animateTo(0f, tween(300)) }
                }
            },
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(progress.value)
                .background(Color.White.copy(alpha = 0.14f)),
        )
        Text(
            "Hold 10 s to unlock for 10 minutes · $unlocksLeft left tonight",
            color = Cream.copy(alpha = 0.6f),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
        )
    }
}
