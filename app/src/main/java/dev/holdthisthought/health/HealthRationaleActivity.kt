package dev.holdthisthought.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.holdthisthought.ui.theme.HoldThisThoughtTheme

/**
 * Health Connect requires apps to explain why they read health data. Shown from the permission
 * sheet ("Read privacy policy") and from Android's permission-usage screens.
 */
class HealthRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            HoldThisThoughtTheme {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .safeDrawingPadding()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                    ) {
                        Text("Why Daytrace reads health data", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(16.dp))
                        listOf(
                            "Steps and exercise: to complete your Move quest automatically, so you don't have to log walks.",
                            "Sleep: to show how long you slept and, later, how sleep relates to your days.",
                            "Nutrition: meals you already log in apps like MyFitnessPal count towards your Fuel quest.",
                            "Daytrace only reads. It never writes to Health Connect.",
                            "Only daily totals (steps, minutes, meal times, calories) are backed up, to your own private Supabase project. Nothing is shared or sold, and no ads.",
                            "You can turn any of this off at any time in Health Connect.",
                        ).forEach {
                            Text("• $it", style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(10.dp))
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { finish() }) { Text("Got it") }
                    }
                }
            }
        }
    }
}
