package dev.holdthisthought.ui.settings

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

@Composable
fun SettingsScreen(
    snackbar: SnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(),
) {
    val (account, email) = viewModel.account.collectAsStateWithLifecycle().value
    val sync by viewModel.sync.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

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
        exportLauncher.launch("hold-this-thought-${LocalDate.now()}.${format.extension}")
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(top = 16.dp, bottom = 20.dp),
        )

        SectionLabel("Cloud backup")
        Card {
            when (account) {
                AccountState.NOT_CONFIGURED -> InfoRow(
                    Icons.Outlined.CloudOff,
                    "Not set up in this build",
                    "Add SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY to local.properties.",
                )
                AccountState.LOADING -> InfoRow(Icons.Outlined.CloudSync, "Checking account…", null)
                AccountState.UNREACHABLE -> {
                    InfoRow(
                        Icons.Outlined.CloudOff,
                        "Can't reach your backup",
                        "You're still signed in. Thoughts are safe on this phone and will back up " +
                            "once the server is reachable. (A paused Supabase project causes this.)",
                    )
                    SyncStatus(sync, busy, viewModel::syncNow)
                }
                AccountState.SIGNED_OUT -> {
                    InfoRow(
                        Icons.Outlined.CloudOff,
                        "Only on this phone",
                        "Sign in to back up your thoughts and keep them across devices.",
                    )
                    Spacer(Modifier.height(16.dp))
                    CredentialsForm(
                        primaryLabel = "Sign in",
                        secondaryLabel = "Create account",
                        busy = busy,
                        onPrimary = viewModel::signIn,
                        onSecondary = viewModel::createAccount,
                    )
                }
                AccountState.ANONYMOUS -> {
                    InfoRow(
                        Icons.Outlined.Lock,
                        "Private session",
                        "Backed up, but tied to this install. Add an email and password so a " +
                            "reinstall or new phone — or a script on your laptop — can get them back.",
                    )
                    Spacer(Modifier.height(16.dp))
                    CredentialsForm(
                        primaryLabel = "Secure my account",
                        secondaryLabel = null,
                        busy = busy,
                        onPrimary = viewModel::secureAccount,
                        onSecondary = null,
                    )
                    SyncStatus(sync, busy, viewModel::syncNow)
                }
                AccountState.SIGNED_IN -> {
                    InfoRow(Icons.Outlined.CloudDone, "Backed up", email)
                    SyncStatus(sync, busy, viewModel::syncNow)
                    TextButton(onClick = viewModel::signOut, enabled = !busy) { Text("Sign out") }
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        SectionLabel("Export")
        Card {
            Text(
                "Save everything as a file. Markdown reads well and is ideal for handing to " +
                    "Claude or another assistant to summarise; JSON is for scripts.",
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

        Spacer(Modifier.height(28.dp))
        SectionLabel("About")
        Card {
            Text(
                "Every thought is saved on this phone first, instantly and offline. Backup and AI " +
                    "tagging happen quietly in the background. Your words are never rewritten.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Hold This Thought ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(32.dp))
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
    Spacer(Modifier.height(16.dp))
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
