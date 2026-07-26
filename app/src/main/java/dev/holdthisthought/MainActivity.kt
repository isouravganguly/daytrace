package dev.holdthisthought

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.holdthisthought.data.Thought
import dev.holdthisthought.data.ThoughtSpace
import dev.holdthisthought.ui.AuthUiState
import dev.holdthisthought.ui.Destination
import dev.holdthisthought.ui.ThoughtTheme
import dev.holdthisthought.ui.ThoughtViewModel
import kotlinx.coroutines.launch

private val AppButtonShape = RoundedCornerShape(12.dp)
private val AppFieldShape = RoundedCornerShape(12.dp)
private val AppCardShape = RoundedCornerShape(8.dp)
private val AppPillShape = RoundedCornerShape(999.dp)
private val AppButtonHeight = 48.dp
private val AppChipHeight = 42.dp

class MainActivity : ComponentActivity() {
    private val viewModel: ThoughtViewModel by viewModels {
        val app = application as ThoughtApplication
        ThoughtViewModel.Factory(app.repository, app.remoteDataSource)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ThoughtApp(viewModel) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThoughtApp(viewModel: ThoughtViewModel) {
    val space by viewModel.space.collectAsStateWithLifecycle()
    val destination by viewModel.destination.collectAsStateWithLifecycle()
    val thoughts by viewModel.thoughts.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val captureMessage by viewModel.captureMessage.collectAsStateWithLifecycle()
    val auth by viewModel.authUiState.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ThoughtTheme {
        if (auth.needsAuth) {
            AuthScreen(
                auth = auth,
                onSignIn = viewModel::signIn,
                onSignUp = viewModel::signUp,
                onContinuePrivately = viewModel::continuePrivately,
            )
            return@ThoughtTheme
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(modifier = Modifier.width(292.dp)) {
                    Text(
                        "Hold This Thought",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(24.dp),
                    )
                    listOf(
                        Destination.CAPTURE to "Capture",
                        Destination.THOUGHTS to "Thoughts",
                        Destination.NOTES to "Notes",
                        Destination.TODOS to "Todos",
                        Destination.SEARCH to "Search",
                    ).forEach { (item, label) ->
                        NavigationDrawerItem(
                            label = { Text(label) },
                            selected = destination == item,
                            onClick = {
                                viewModel.destination.value = item
                                scope.launch { drawerState.close() }
                            },
                            shape = AppPillShape,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    TextButton(
                        onClick = {
                            viewModel.space.value =
                                if (space == ThoughtSpace.WORK) ThoughtSpace.PERSONAL
                                else ThoughtSpace.WORK
                        },
                        shape = AppPillShape,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        Text("Switch to ${if (space == ThoughtSpace.WORK) "Personal" else "Work"}")
                    }
                    if (auth.isSignedIn) {
                        TextButton(
                            onClick = {
                                viewModel.signOut()
                                scope.launch { drawerState.close() }
                            },
                            shape = AppPillShape,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        ) {
                            val label = auth.email ?: "private mode"
                            Text("Sign out $label")
                        }
                    }
                }
            },
        ) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 20.dp),
                ) {
                    AppHeader(
                        destination = destination,
                        space = space,
                        onMenuClick = { scope.launch { drawerState.open() } },
                    )
                    when (destination) {
                        Destination.CAPTURE -> CaptureScreen(
                            draft = draft,
                            space = space,
                            captureMessage = captureMessage,
                            onDraftChange = { viewModel.draft.value = it },
                            onSpaceChange = { viewModel.space.value = it },
                            onSave = viewModel::save,
                        )
                        Destination.SEARCH -> ListScreen(
                            title = "Find a thought",
                            thoughts = thoughts,
                            query = query,
                            onQueryChange = { viewModel.searchQuery.value = it },
                        )
                        Destination.THOUGHTS -> ListScreen("Thoughts", thoughts)
                        Destination.NOTES -> ListScreen("Notes", thoughts)
                        Destination.TODOS -> ListScreen("Todos", thoughts)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppHeader(
    destination: Destination,
    space: ThoughtSpace,
    onMenuClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 8.dp),
    ) {
        FilledTonalButton(
            onClick = onMenuClick,
            shape = AppPillShape,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            modifier = Modifier.height(AppChipHeight),
        ) {
            Text("Menu")
        }
        Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
            Text(
                destination.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (space == ThoughtSpace.WORK) "Work space" else "Personal space",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

private val Destination.title: String
    get() = when (this) {
        Destination.CAPTURE -> "Capture"
        Destination.THOUGHTS -> "Thoughts"
        Destination.NOTES -> "Notes"
        Destination.TODOS -> "Todos"
        Destination.SEARCH -> "Search"
    }

@Composable
private fun AuthScreen(
    auth: AuthUiState,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String) -> Unit,
    onContinuePrivately: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val canSubmit = email.isNotBlank() && password.length >= 6 && !auth.isBusy

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                "Sign in to sync",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Use one account across installs and devices.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                shape = AppFieldShape,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                shape = AppFieldShape,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
            Button(
                enabled = !auth.isBusy,
                onClick = onContinuePrivately,
                shape = AppButtonShape,
                modifier = Modifier.fillMaxWidth().height(AppButtonHeight),
            ) {
                Text(if (auth.isBusy) "Working..." else "Continue privately")
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(
                    enabled = canSubmit,
                    onClick = { onSignIn(email, password) },
                    shape = AppButtonShape,
                    modifier = Modifier.weight(1f).height(AppButtonHeight),
                ) {
                    Text(if (auth.isBusy) "Working..." else "Sign in")
                }
                OutlinedButton(
                    enabled = canSubmit,
                    onClick = { onSignUp(email, password) },
                    shape = AppButtonShape,
                    modifier = Modifier.weight(1f).height(AppButtonHeight),
                ) {
                    Text("Create")
                }
            }
            auth.message?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
private fun CaptureScreen(
    draft: String,
    space: ThoughtSpace,
    captureMessage: String?,
    onDraftChange: (String) -> Unit,
    onSpaceChange: (ThoughtSpace) -> Unit,
    onSave: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().padding(bottom = 28.dp),
    ) {
        Text(
            "What’s on your mind?",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(16.dp))
        SpacePicker(space = space, onSpaceChange = onSpaceChange)
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            placeholder = { Text("Type a thought…") },
            minLines = 4,
            shape = AppFieldShape,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
        HoldToSpeakButton(onTranscript = onDraftChange)
        Spacer(Modifier.height(12.dp))
        Text(
            captureMessage ?: "Hold to speak",
            style = MaterialTheme.typography.bodyMedium,
            color = if (captureMessage == null) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.primary
            },
        )
        Spacer(Modifier.height(20.dp))
        Button(
            enabled = draft.isNotBlank(),
            onClick = onSave,
            shape = AppButtonShape,
            modifier = Modifier.fillMaxWidth().height(AppButtonHeight),
        ) {
            Text("Keep this thought")
        }
    }
}

@Composable
private fun SpacePicker(
    space: ThoughtSpace,
    onSpaceChange: (ThoughtSpace) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SpaceButton(
            label = "Work",
            selected = space == ThoughtSpace.WORK,
            onClick = { onSpaceChange(ThoughtSpace.WORK) },
        )
        SpaceButton(
            label = "Personal",
            selected = space == ThoughtSpace.PERSONAL,
            onClick = { onSpaceChange(ThoughtSpace.PERSONAL) },
        )
    }
}

@Composable
private fun SpaceButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    if (selected) {
        FilledTonalButton(
            onClick = onClick,
            shape = AppPillShape,
            modifier = Modifier.height(AppChipHeight),
        ) {
            Text(label)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            shape = AppPillShape,
            modifier = Modifier.height(AppChipHeight),
        ) {
            Text(label)
        }
    }
}

@Composable
private fun HoldToSpeakButton(onTranscript: (String) -> Unit) {
    val context = LocalContext.current
    var listening by remember { mutableStateOf(false) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }
    val recognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
    val intent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
    }

    DisposableEffect(recognizer) {
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { listening = true }
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() { listening = false }
            override fun onError(error: Int) { listening = false }
            override fun onResults(results: Bundle?) {
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.let(onTranscript)
                listening = false
            }
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.let(onTranscript)
            }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        onDispose { recognizer.destroy() }
    }

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        tonalElevation = if (listening) 10.dp else 4.dp,
        modifier = Modifier
            .size(if (listening) 108.dp else 96.dp)
            .pointerInput(hasPermission) {
                awaitEachGesture {
                    awaitFirstDown()
                    if (!hasPermission) {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        recognizer.startListening(intent)
                    }
                    do {
                        val event = awaitPointerEvent()
                    } while (event.changes.any { it.pressed })
                    if (hasPermission) recognizer.stopListening()
                }
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                if (listening) "Listening…" else "Speak",
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ListScreen(
    title: String,
    thoughts: List<Thought>,
    query: String? = null,
    onQueryChange: (String) -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 18.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Surface(
                shape = AppPillShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    thoughts.size.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        if (query != null) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search by anything you remember") },
                singleLine = true,
                shape = AppFieldShape,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
        }
        if (thoughts.isEmpty()) {
            EmptyState(title)
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(thoughts, key = { it.id }) { thought ->
                    ElevatedCard(
                        shape = AppCardShape,
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(thought.body, style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "${thought.kind.name.lowercase()} · ${thought.space.name.lowercase()}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(title: String) {
    Surface(
        shape = AppCardShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "Nothing here yet.",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Captured ${title.lowercase()} will appear here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}
