package dev.holdthisthought.ui.settings

import androidx.compose.foundation.layout.widthIn
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilterChip
import dev.holdthisthought.data.AppPreferences
import dev.holdthisthought.ui.today.TodayViewModel
import dev.holdthisthought.ui.you.HealthSection
import dev.holdthisthought.ui.you.NudgesSection
import dev.holdthisthought.ui.you.SleepSection
import dev.holdthisthought.ui.you.ProfileSection
import dev.holdthisthought.ui.you.TracingSection
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.holdthisthought.BuildConfig
import dev.holdthisthought.ui.components.relativeTime
import java.time.LocalDate

/** The You tab: your progress, with settings one tap away behind the gear. */
@Composable
fun YouScreen(
    snackbar: SnackbarHostState,
    modifier: Modifier = Modifier,
    todayViewModel: TodayViewModel = viewModel(),
) {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = showSettings) { showSettings = false }
    AnimatedContent(
        targetState = showSettings,
        transitionSpec = {
            val dir = if (targetState) 1 else -1
            (slideInHorizontally { it * dir / 4 } + fadeIn()) togetherWith (slideOutHorizontally { -it * dir / 4 } + fadeOut())
        },
        label = "you",
        modifier = modifier,
    ) { settings ->
        if (settings) {
            SettingsScreen(snackbar, onBack = { showSettings = false }, todayViewModel = todayViewModel)
        } else {
            val game by todayViewModel.game.collectAsStateWithLifecycle()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(Modifier.widthIn(max = 760.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
                    ) {
                        Text("You", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                        }
                    }
                    game?.let { ProfileSection(it) }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

/** Every setting in one place: fitness, sleep, nudges, this phone, account. */
@Composable
fun SettingsScreen(
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(),
    todayViewModel: TodayViewModel = viewModel(),
) {
    val (account, email) = viewModel.account.collectAsStateWithLifecycle().value
    val sync by viewModel.sync.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val awaitingConfirmation by viewModel.awaitingConfirmation.collectAsStateWithLifecycle()

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    var pendingFormat by rememberSaveable { mutableStateOf(ExportFormat.MARKDOWN) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri -> uri?.let { viewModel.export(it, pendingFormat) } }

    fun export(format: ExportFormat) {
        pendingFormat = format
        exportLauncher.launch("daytrace-thoughts-${LocalDate.now()}.${format.extension}")
    }

    val stepGoal by todayViewModel.stepGoal.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Column(Modifier.widthIn(max = 760.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
            Spacer(Modifier.width(4.dp))
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
        }

        Spacer(Modifier.height(12.dp))
        SectionLabel("Daily step goal")
        Card {
            Text(
                "Reaching it completes your Move quest.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppPreferences.STEP_GOALS.forEach { option ->
                    FilterChip(
                        selected = stepGoal == option,
                        onClick = { todayViewModel.setStepGoal(option) },
                        label = { Text("%,d".format(option)) },
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        SectionLabel("Health")
        Card { HealthSection() }

        Spacer(Modifier.height(28.dp))
        SectionLabel("Sleep & mornings")
        Card { SleepSection() }

        Spacer(Modifier.height(28.dp))
        SectionLabel("Nudges & my day")
        Card { NudgesSection() }

        Spacer(Modifier.height(28.dp))
        SectionLabel("Screen time & widget")
        Card { TracingSection() }

        Spacer(Modifier.height(28.dp))
        SectionLabel("Account & backup")
        Card {
            when (account) {
                AccountState.NOT_CONFIGURED -> InfoRow(Icons.Outlined.CloudOff, "Backup isn't set up in this build", null)
                AccountState.LOADING -> InfoRow(Icons.Outlined.CloudSync, "Checking…", null)
                AccountState.UNREACHABLE -> InfoRow(
                    Icons.Outlined.CloudOff,
                    "Backup is offline for now",
                    "Everything stays safe on this phone and backs up when the server is back.",
                )
                AccountState.SIGNED_OUT -> {
                    InfoRow(
                        if (awaitingConfirmation) Icons.Outlined.MarkEmailUnread else Icons.Outlined.CloudOff,
                        if (awaitingConfirmation) "Confirm your email" else "Not backed up yet",
                        if (awaitingConfirmation) "Open the link we emailed you, then sign in here."
                        else "Sign in once and everything backs up by itself.",
                    )
                    Spacer(Modifier.height(16.dp))
                    CredentialsForm(
                        primaryLabel = "Sign in",
                        secondaryLabel = null,
                        busy = busy,
                        onPrimary = viewModel::signIn,
                        onSecondary = null,
                    )
                }
                AccountState.ANONYMOUS -> {
                    InfoRow(Icons.Outlined.Lock, "Private session", "Add an email so a new phone can get everything back.")
                    Spacer(Modifier.height(16.dp))
                    CredentialsForm(
                        primaryLabel = "Secure my account",
                        secondaryLabel = null,
                        busy = busy,
                        onPrimary = viewModel::secureAccount,
                        onSecondary = null,
                    )
                }
                AccountState.SIGNED_IN -> {
                    InfoRow(Icons.Outlined.CloudDone, "Everything backs up automatically", email)
                    Spacer(Modifier.height(12.dp))
                    SyncStatus(sync, busy, viewModel::syncNow)
                    TextButton(onClick = viewModel::signOut, enabled = !busy) { Text("Sign out") }
                }
            }
        }


        Spacer(Modifier.height(28.dp))
        SectionLabel("Export thoughts")
        Card {
            Text(
                "Save your thoughts as a file. Markdown is ideal for handing to Claude; JSON is for scripts.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { export(ExportFormat.MARKDOWN) }, enabled = !busy) {
                    Icon(Icons.Outlined.Description, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Markdown")
                }
                OutlinedButton(onClick = { export(ExportFormat.JSON) }, enabled = !busy) {
                    Icon(Icons.Outlined.DataObject, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("JSON")
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "Daytrace ${BuildConfig.VERSION_NAME} · everything is saved on this phone first and backed up quietly.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(32.dp))
      }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp),
    )
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), content = content)
    }
}

@Composable
private fun InfoRow(icon: ImageVector, title: String, body: String?) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(
                icon, null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(8.dp).size(20.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (body != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SyncStatus(sync: SyncUi, busy: Boolean, onSyncNow: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                when {
                    sync.pending > 0 -> "${sync.pending} waiting to back up"
                    sync.lastSyncedAt != null -> "Synced ${relativeTime(sync.lastSyncedAt)}"
                    else -> "Not synced yet"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            sync.lastError?.let {
                Text(
                    "Last attempt failed: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                )
            }
        }
        TextButton(onClick = onSyncNow, enabled = !busy) {
            Text(if (busy) "Syncing…" else "Sync now")
        }
    }
}

@Composable
private fun CredentialsForm(
    primaryLabel: String,
    secondaryLabel: String?,
    busy: Boolean,
    onPrimary: (String, String) -> Unit,
    onSecondary: ((String, String) -> Unit)?,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val valid = email.contains('@') && password.length >= 6 && !busy

    OutlinedTextField(
        value = email,
        onValueChange = { email = it },
        label = { Text("Email") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text("Password") },
        supportingText = { Text("At least 6 characters") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = { onPrimary(email, password) }, enabled = valid) {
            Text(if (busy) "Working…" else primaryLabel)
        }
        if (secondaryLabel != null && onSecondary != null) {
            OutlinedButton(onClick = { onSecondary(email, password) }, enabled = valid) {
                Text(secondaryLabel)
            }
        }
    }
}
