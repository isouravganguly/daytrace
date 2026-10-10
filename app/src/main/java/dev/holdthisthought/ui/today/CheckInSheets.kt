package dev.holdthisthought.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.holdthisthought.data.local.MealSlot
import dev.holdthisthought.ui.components.EmojiChoice
import dev.holdthisthought.ui.theme.LocalGameColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CheckInSheet(onDismiss: () -> Unit, emoji: String, title: String, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            Text(emoji, fontSize = 40.sp)
            Spacer(Modifier.height(4.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(18.dp))
            content()
        }
    }
}

@Composable
fun VerdictSheet(onDismiss: () -> Unit, onSave: (score: Int, note: String?) -> Unit) {
    var score by rememberSaveable { mutableIntStateOf(0) }
    var note by rememberSaveable { mutableStateOf("") }
    val accent = LocalGameColors.current.verdict
    CheckInSheet(onDismiss, "⚖️", "How was today?") {
        Row(Modifier.fillMaxWidth()) {
            Verdicts.forEachIndexed { i, (emoji, label) ->
                EmojiChoice(emoji, label, score == i + 1, { score = i + 1 }, Modifier.weight(1f), accent)
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = note,
            onValueChange = { note = it.take(280) },
            label = { Text("One line about the day (optional)") },
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onSave(score, note) },
            enabled = score > 0,
            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Save verdict · +20 XP") }
    }
}

@Composable
fun WalkSheet(onDismiss: () -> Unit, onSave: (minutes: Int) -> Unit) {
    val accent = LocalGameColors.current.move
    CheckInSheet(onDismiss, "🥾", "Nice. How long was the walk?") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(10, 20, 30, 45, 60).forEach { minutes ->
                ChoiceChip(if (minutes == 60) "60+ min" else "$minutes min", accent) { onSave(minutes) }
            }
            ChoiceChip("Not sure", accent) { onSave(0) }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Soon this fills in by itself from Health Connect.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun MealSheet(logged: Set<String>, onDismiss: () -> Unit, onPick: (MealSlot) -> Unit) {
    val accent = LocalGameColors.current.fuel
    CheckInSheet(onDismiss, "🍽️", "Which meal?") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MealSlot.entries.forEach { slot ->
                val done = slot.name in logged
                ChoiceChip("${slot.emoji}  ${slot.label}${if (done) " ✓" else ""}", accent) { onPick(slot) }
            }
        }
    }
}

@Composable
private fun ChoiceChip(text: String, accent: Color, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = accent.copy(alpha = 0.14f)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
            Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(2.dp))
        }
    }
}
