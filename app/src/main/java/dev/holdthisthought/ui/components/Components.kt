package dev.holdthisthought.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.holdthisthought.data.local.CaptureInput
import dev.holdthisthought.data.local.Thought
import dev.holdthisthought.data.local.ThoughtKind
import dev.holdthisthought.data.local.ThoughtSpace
import dev.holdthisthought.ui.theme.LocalKindPalette
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

val ThoughtKind.label: String
    get() = when (this) {
        ThoughtKind.THOUGHT -> "Thought"
        ThoughtKind.LEARNING -> "Learning"
        ThoughtKind.TODO -> "Todo"
        ThoughtKind.DESIRE -> "Desire"
    }

val ThoughtKind.pluralLabel: String
    get() = when (this) {
        ThoughtKind.THOUGHT -> "Thoughts"
        ThoughtKind.LEARNING -> "Learnings"
        ThoughtKind.TODO -> "Todos"
        ThoughtKind.DESIRE -> "Desires"
    }

val ThoughtKind.icon: ImageVector
    get() = when (this) {
        ThoughtKind.THOUGHT -> Icons.Outlined.Lightbulb
        ThoughtKind.LEARNING -> Icons.Outlined.AutoStories
        ThoughtKind.TODO -> Icons.Outlined.TaskAlt
        ThoughtKind.DESIRE -> Icons.Outlined.FavoriteBorder
    }

val ThoughtSpace.label: String
    get() = when (this) {
        ThoughtSpace.WORK -> "Work"
        ThoughtSpace.PERSONAL -> "Personal"
    }

/** Selectable pill. [accent] tints it when selected. */
@Composable
fun Pill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    selectedContainer: Color = MaterialTheme.colorScheme.primaryContainer,
) {
    val container by animateColorAsState(
        if (selected) selectedContainer else Color.Transparent, label = "pill-bg",
    )
    val content by animateColorAsState(
        if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant, label = "pill-fg",
    )
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = container,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.height(36.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge, color = content)
        }
    }
}

@Composable
fun KindPill(
    kind: ThoughtKind?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = kind?.label ?: "Auto",
) {
    val colors = kind?.let { LocalKindPalette.current.of(it) }
    Pill(
        text = text,
        selected = selected,
        onClick = onClick,
        icon = kind?.icon ?: Icons.Outlined.AutoAwesome,
        accent = colors?.onContainer ?: MaterialTheme.colorScheme.onPrimaryContainer,
        selectedContainer = colors?.container ?: MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier,
    )
}

/** Two-option segmented control for Work / Personal. */
@Composable
fun SpaceToggle(
    space: ThoughtSpace,
    onChange: (ThoughtSpace) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(3.dp),
    ) {
        ThoughtSpace.entries.forEach { option ->
            val selected = option == space
            val bg by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.surfaceContainerLowest else Color.Transparent,
                label = "space-bg",
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(bg)
                    .clickable { onChange(option) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(
                    option.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun KindBadge(kind: ThoughtKind, modifier: Modifier = Modifier) {
    val colors = LocalKindPalette.current.of(kind)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(CircleShape)
            .background(colors.container)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Icon(kind.icon, null, tint = colors.onContainer, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(kind.label, style = MaterialTheme.typography.labelSmall, color = colors.onContainer)
    }
}

@Composable
fun TagChip(tag: String) {
    Text(
        "#$tag",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** A thought in the library. Todos get a tappable check circle. */
@Composable
fun ThoughtCard(
    thought: Thought,
    onClick: () -> Unit,
    onToggleDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalKindPalette.current.of(thought.kind)
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            if (thought.kind == ThoughtKind.TODO) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .padding(end = 12.dp, top = 2.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(if (thought.isDone) accent.accent else Color.Transparent)
                        .border(1.5.dp, accent.accent, CircleShape)
                        .clickable(onClick = onToggleDone),
                ) {
                    if (thought.isDone) {
                        Icon(
                            Icons.Rounded.Check, "Mark not done",
                            tint = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                thought.title?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                }
                Text(
                    thought.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (thought.isDone) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (thought.isDone) TextDecoration.LineThrough else null,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    KindBadge(thought.kind)
                    thought.tags.take(3).forEach { TagChip(it) }
                    MetaText(thought)
                }
            }
        }
    }
}

@Composable
private fun MetaText(thought: Thought) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (thought.input == CaptureInput.VOICE) {
            Icon(
                Icons.Rounded.Mic, "Spoken",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(2.dp))
        }
        Text(
            "${formatTime(thought.createdAt)} · ${thought.space.label}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
private val dateFormatter = DateTimeFormatter.ofPattern("EEE, d MMM")
private val dateYearFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")

fun formatTime(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(timeFormatter)

fun localDate(epochMillis: Long): LocalDate =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()

fun dayLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String = when {
    date == today -> "Today"
    date == today.minusDays(1) -> "Yesterday"
    date.year == today.year -> date.format(dateFormatter)
    else -> date.format(dateYearFormatter)
}

fun relativeTime(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = (now - epochMillis) / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 24 * 60 -> "${minutes / 60} h ago"
        else -> dayLabel(localDate(epochMillis))
    }
}
