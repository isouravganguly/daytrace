package dev.holdthisthought.ui.library

import android.content.ClipData
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.holdthisthought.data.local.CaptureInput
import dev.holdthisthought.data.local.Thought
import dev.holdthisthought.data.local.ThoughtKind
import dev.holdthisthought.data.local.ThoughtSpace
import dev.holdthisthought.ui.components.KindPill
import dev.holdthisthought.ui.components.SpaceToggle
import dev.holdthisthought.ui.components.TagChip
import dev.holdthisthought.ui.components.dayLabel
import dev.holdthisthought.ui.components.formatTime
import dev.holdthisthought.ui.components.localDate
import dev.holdthisthought.ui.theme.CanvasTextStyle
import kotlinx.coroutines.launch

/** View and edit one thought. Edits are saved when the sheet closes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThoughtSheet(
    thought: Thought,
    onDismiss: () -> Unit,
    onSave: (id: String, body: String, kind: ThoughtKind, space: ThoughtSpace) -> Unit,
    onToggleDone: () -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var body by rememberSaveable(thought.id) { mutableStateOf(thought.body) }
    var kind by remember(thought.id) { mutableStateOf(thought.kind) }
    var space by remember(thought.id) { mutableStateOf(thought.space) }

    fun saveAndClose() {
        if (body.isNotBlank()) onSave(thought.id, body, kind, space)
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = ::saveAndClose,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    buildString {
                        append(dayLabel(localDate(thought.createdAt)))
                        append(" · ")
                        append(formatTime(thought.createdAt))
                        if (thought.input == CaptureInput.VOICE) append(" · spoken")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                SpaceToggle(space = space, onChange = { space = it })
            }
            Spacer(Modifier.height(16.dp))

            BasicTextField(
                value = body,
                onValueChange = { body = it },
                textStyle = CanvasTextStyle.copy(
                    fontSize = 21.sp,
                    lineHeight = 30.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
            )

            if (thought.title != null || thought.tags.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.AutoAwesome, null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        thought.title ?: "Topics",
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                if (thought.tags.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        thought.tags.forEach { TagChip(it) }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                ThoughtKind.entries.forEach { option ->
                    KindPill(kind = option, selected = kind == option, onClick = { kind = option })
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                if (kind == ThoughtKind.TODO) {
                    SheetAction(
                        if (thought.isDone) "Reopen" else "Done",
                        if (thought.isDone) Icons.Rounded.Undo else Icons.Outlined.TaskAlt,
                        onClick = onToggleDone,
                    )
                }
                SheetAction("Copy", Icons.Outlined.ContentCopy) {
                    scope.launch {
                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Thought", body)))
                    }
                }
                Spacer(Modifier.weight(1f))
                SheetAction(
                    "Delete", Icons.Outlined.DeleteOutline,
                    tint = MaterialTheme.colorScheme.error,
                    onClick = onDelete,
                )
            }
        }
    }
}

@Composable
private fun SheetAction(
    label: String,
    icon: ImageVector,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = tint)
    }
}
