package dev.holdthisthought.ui.onboarding

import dev.holdthisthought.ui.theme.Motion
import android.Manifest
import android.app.Application
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.holdthisthought.ThoughtApplication
import dev.holdthisthought.data.AppPreferences
import dev.holdthisthought.health.HealthConnectSource
import dev.holdthisthought.ui.components.Blip
import dev.holdthisthought.ui.components.BlipMood
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as ThoughtApplication).container
    val healthSource = container.health.source
    val stepGoal: StateFlow<Int> = container.preferences.stepGoal
    val canSignIn: Boolean get() = container.remote.isConfigured

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun setStepGoal(steps: Int) = container.preferences.setStepGoal(steps)

    init {
        viewModelScope.launch(Dispatchers.IO) { container.health.refreshStatus() }
    }

    /** Health Connect step only if it's available and not connected yet. */
    fun needsHealthStep(): Boolean {
        val status = container.health.status.value
        return status.availability == HealthConnectSource.Availability.AVAILABLE && !status.connected
    }

    fun signedIn(): Boolean = container.remote.ownerAccessToken() != null

    fun onHealthResult() = viewModelScope.launch(Dispatchers.IO) { container.health.sync() }

    fun signIn(email: String, password: String, onDone: () -> Unit) = viewModelScope.launch {
        _busy.value = true
        _error.value = null
        try {
            container.remote.signIn(email.trim(), password)
            container.syncScheduler.requestSync()
            onDone()
        } catch (e: Exception) {
            val text = e.message.orEmpty()
            _error.value = when {
                text.contains("invalid_credentials", true) || text.contains("Invalid login", true) ->
                    "That email and password don't match."
                text.contains("Unable to resolve host", true) || text.contains("timeout", true) ->
                    "No connection. You can do this later in You."
                else -> "Couldn't sign in. You can try again later in You."
            }
        } finally {
            _busy.value = false
        }
    }

    fun finish() = container.preferences.finishOnboarding()
}

/** First launch: three short steps, each skippable. Fitness first. */
@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel = viewModel()) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.finish() }
    fun finish() {
        if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS) else viewModel.finish()
    }
    val health = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        viewModel.onHealthResult()
        if (viewModel.signedIn()) finish() else step = 2
    }

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .imePadding(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            StepDots(step)
            Spacer(Modifier.height(24.dp))
            Blip(mood = if (step == 0) BlipMood.EXCITED else BlipMood.CHEERFUL, level = 1, size = 190.dp, bounceKey = step, onReact = {})
            Spacer(Modifier.height(12.dp))
            AnimatedContent(step, transitionSpec = { fadeIn(Motion.standard(Motion.Duration.medium)) togetherWith fadeOut(Motion.standard(Motion.Duration.short)) }, label = "step") { current ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    when (current) {
                        0 -> GoalStep(viewModel) {
                            when {
                                viewModel.needsHealthStep() -> step = 1
                                viewModel.signedIn() -> finish()
                                else -> step = 2
                            }
                        }
                        1 -> HealthStep(
                            onConnect = { health.launch(viewModel.healthSource.requested + viewModel.healthSource.backgroundPermission) },
                            onSkip = { if (viewModel.signedIn()) finish() else step = 2 },
                        )
                        else -> BackupStep(viewModel, onDone = ::finish)
                    }
                }
            }
        }
    }
}

@Composable
private fun StepDots(step: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(3) { i ->
            Box(
                Modifier
                    .size(width = if (i == step) 22.dp else 8.dp, height = 8.dp)
                    .clip(CircleShape)
                    .background(if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest),
            )
        }
    }
}

@Composable
private fun Title(text: String, body: String) {
    Text(text, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    Text(
        body,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun GoalStep(viewModel: OnboardingViewModel, onNext: () -> Unit) {
    val goal by viewModel.stepGoal.collectAsStateWithLifecycle()
    Title("Hi, I'm Blip 👋", "Let's get you fit, one small win a day. How many steps a day feels right to start?")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AppPreferences.STEP_GOALS.forEach { option ->
            FilterChip(
                selected = goal == option,
                onClick = { viewModel.setStepGoal(option) },
                label = { Text("%,d".format(option)) },
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    Text("You can change this any time.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(28.dp))
    Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Let's go") }
}

@Composable
private fun HealthStep(onConnect: () -> Unit, onSkip: () -> Unit) {
    Title(
        "Let me count your steps",
        "Connect Health Connect and your steps, walks and sleep fill in by themselves, from Google Fit and your other apps. Nothing to type.",
    )
    Button(onClick = onConnect, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Connect") }
    TextButton(onClick = onSkip) { Text("Not now") }
}

@Composable
private fun BackupStep(viewModel: OnboardingViewModel, onDone: () -> Unit) {
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    Title("Keep your progress safe", "Sign in once and everything backs up by itself. You won't need to do this again.")
    if (viewModel.canSignIn) {
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
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { viewModel.signIn(email, password, onDone) },
            enabled = !busy && email.contains('@') && password.length >= 6,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(if (busy) "Signing in…" else "Sign in") }
    }
    TextButton(onClick = onDone) { Text("Not now") }
}
