package dev.holdthisthought.ui.capture

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.holdthisthought.data.local.ThoughtKind
import dev.holdthisthought.ui.components.KindPill
import dev.holdthisthought.ui.components.SpaceToggle
import dev.holdthisthought.ui.components.label
import dev.holdthisthought.ui.theme.CanvasTextStyle
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun CaptureScreen(
    snackbar: SnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: CaptureViewModel = viewModel(),
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val kind by viewModel.kind.collectAsStateWithLifecycle()
    val space by viewModel.space.collectAsStateWithLifecycle()
    val speech by viewModel.speechState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.saved.collect { thought ->
            val result = snackbar.showSnackbar(
                message = "Kept as ${thought.kind.label.lowercase()}",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undo(thought)
        }
    }
    LaunchedEffect(speech.error) {
        speech.error?.let { snackbar.showSnackbar(it) }
    }

    val hasText = draft.isNotBlank() || speech.partial.isNotBlank()

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 20.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
        ) {
            Text(
                LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            SpaceToggle(space = space, onChange = viewModel::onSpaceChange)
        }

        CaptureCanvas(
            draft = draft,
            partial = speech.partial,
            isListening = speech.isListening,
            onDraftChange = viewModel::onDraftChange,
            modifier = Modifier.weight(1f),
        )

        KindRow(
            selected = kind,
            autoGuess = if (hasText) viewModel.guessedKind(draft + " " + speech.partial) else null,
            onSelect = viewModel::onKindChange,
        )
        Spacer(Modifier.height(14.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        ) {
            VoiceButton(
                isListening = speech.isListening,
                level = speech.level,
                onStart = viewModel::startListening,
                onStop = viewModel::stopListening,
                modifier = Modifier.weight(1f),
            )
            AnimatedVisibility(
                visible = hasText,
                enter = fadeIn() + expandHorizontally(expandFrom = Alignment.Start),
                exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.Start),
            ) {
                Row {
                    Spacer(Modifier.width(10.dp))
                    KeepButton(onClick = viewModel::save)
                }
            }
        }
    }
}

@Composable
private fun CaptureCanvas(
    draft: String,
    partial: String,
    isListening: Boolean,
    onDraftChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val textStyle = CanvasTextStyle.copy(color = MaterialTheme.colorScheme.onSurface)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 12.dp),
    ) {
        if (isListening || partial.isNotBlank()) {
            // While dictating, show committed words plus the live phrase in a softer colour.
            val text = buildAnnotatedString {
                append(draft)
                if (partial.isNotBlank()) {
                    if (draft.isNotBlank()) append(" ")
                    withStyle(SpanStyle(color = muted)) { append(partial) }
                }
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                if (text.isEmpty()) {
                    Text("Listening…", style = textStyle.copy(color = muted))
                } else {
                    Text(text, style = textStyle)
                }
            }
        } else {
            BasicTextField(
                value = draft,
                onValueChange = onDraftChange,
                textStyle = textStyle,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(focusRequester)
                    .semantics { contentDescription = "Thought text" },
                decorationBox = { inner ->
                    Box(Modifier.fillMaxSize()) {
                        if (draft.isEmpty()) {
                            Column {
                                Text("What's on your mind?", style = textStyle.copy(color = muted))
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "Type, or hold the mic and talk. Start with \"TIL\" for a " +
                                        "learning or \"todo\" for a task — or just say it and the " +
                                        "app will sort it.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = muted.copy(alpha = 0.8f),
                                )
                            }
                        }
                        inner()
                    }
                },
            )
        }
    }
}

@Composable
private fun KindRow(
    selected: ThoughtKind?,
    autoGuess: ThoughtKind?,
    onSelect: (ThoughtKind?) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    ) {
        KindPill(
            kind = null,
            text = if (selected == null && autoGuess != null) "Auto · ${autoGuess.label}" else "Auto",
            selected = selected == null,
            onClick = { onSelect(null) },
        )
        ThoughtKind.entries.forEach { kind ->
            KindPill(kind = kind, selected = selected == kind, onClick = { onSelect(kind) })
        }
    }
}

/**
 * Press and hold to talk, release to stop. A quick tap starts hands-free dictation
 * instead, which keeps going through pauses until tapped again.
 */
@Composable
private fun VoiceButton(
    isListening: Boolean,
    level: Float,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var handsFree by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(isListening) { if (!isListening) handsFree = false }
    val listeningNow by rememberUpdatedState(isListening)
    val start by rememberUpdatedState(onStart)
    val stop by rememberUpdatedState(onStop)

    val container by animateColorAsState(
        if (isListening) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "voice-bg",
    )
    val content = if (isListening) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurface

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(60.dp)
            .clip(CircleShape)
            .background(container)
            .semantics { contentDescription = if (isListening) "Stop listening" else "Talk" }
            .pointerInput(hasPermission) {
                awaitEachGesture {
                    awaitFirstDown()
                    if (!hasPermission) {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        waitForUpOrCancellation()
                        return@awaitEachGesture
                    }
                    val wasListening = listeningNow
                    if (!wasListening) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        start()
                    }
                    val pressedAt = System.currentTimeMillis()
                    waitForUpOrCancellation()
                    val heldMillis = System.currentTimeMillis() - pressedAt
                    when {
                        wasListening -> {
                            handsFree = false
                            stop()
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        heldMillis > HOLD_THRESHOLD_MS -> {
                            handsFree = false
                            stop()
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        else -> handsFree = true
                    }
                }
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 20.dp),
        ) {
            if (isListening) {
                Waveform(level = level, color = content)
                Spacer(Modifier.width(12.dp))
                Text(
                    if (handsFree) "Listening · tap to stop" else "Listening · release to stop",
                    style = MaterialTheme.typography.labelLarge,
                    color = content,
                )
                if (handsFree) {
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Rounded.Stop, null, tint = content, modifier = Modifier.size(18.dp))
                }
            } else {
                Icon(Icons.Rounded.Mic, null, tint = content, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Hold to talk", style = MaterialTheme.typography.labelLarge, color = content)
                    Text(
                        "or tap for hands-free",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun Waveform(level: Float, color: androidx.compose.ui.graphics.Color) {
    val smoothed by animateFloatAsState(level, tween(120), label = "level")
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "phase",
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(24.dp),
    ) {
        val weights = listOf(0.55f, 0.85f, 1f, 0.7f, 0.45f)
        weights.forEachIndexed { index, weight ->
            val wobble = if (index % 2 == 0) phase else 1f - phase
            val fraction = (0.2f + (smoothed * 0.6f + wobble * 0.2f) * weight).coerceIn(0.15f, 1f)
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight(fraction)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color),
            )
        }
    }
}

@Composable
private fun KeepButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.onSurface,
        contentColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.height(60.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 22.dp),
        ) {
            Icon(Icons.Rounded.Check, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Keep", style = MaterialTheme.typography.labelLarge)
        }
    }
}

private const val HOLD_THRESHOLD_MS = 350L
