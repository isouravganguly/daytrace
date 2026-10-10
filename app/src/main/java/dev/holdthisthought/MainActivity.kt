package dev.holdthisthought

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.holdthisthought.notify.Notifier
import dev.holdthisthought.ui.onboarding.OnboardingScreen
import dev.holdthisthought.ui.components.CelebrationOverlay
import dev.holdthisthought.ui.components.LevelUpDialog
import dev.holdthisthought.ui.library.ThoughtsScreen
import dev.holdthisthought.ui.settings.YouScreen
import dev.holdthisthought.ui.theme.HoldThisThoughtTheme
import dev.holdthisthought.ui.today.TodayScreen
import dev.holdthisthought.ui.today.TodayViewModel

class MainActivity : ComponentActivity() {
    /** Set when a notification asks to open something specific (e.g. the verdict sheet). */
    private val openRequest = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        openRequest.value = intent?.getStringExtra(Notifier.EXTRA_OPEN)
        val preferences = (application as ThoughtApplication).container.preferences
        setContent {
            HoldThisThoughtTheme {
                val onboarded by preferences.onboardingDone.collectAsStateWithLifecycle()
                if (onboarded) AppRoot(openRequest.value) { openRequest.value = null }
                else OnboardingScreen()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openRequest.value = intent.getStringExtra(Notifier.EXTRA_OPEN)
    }
}

/** One theme everywhere; each tab only gets its own dull, earthy accent. */
private enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    TODAY("Today", Icons.Outlined.WbSunny, Icons.Rounded.WbSunny),
    THOUGHTS("Thoughts", Icons.Outlined.EditNote, Icons.Rounded.EditNote),
    YOU("You", Icons.Outlined.Person, Icons.Rounded.Person),
}

@Composable
private fun AppRoot(openRequest: String?, onOpenHandled: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    val snackbar = remember { SnackbarHostState() }
    val today: TodayViewModel = viewModel()

    LaunchedEffect(openRequest) {
        if (openRequest == Notifier.OPEN_VERDICT) {
            tab = Tab.TODAY
            today.requestVerdict()
        }
        if (openRequest == Notifier.OPEN_CAPTURE) tab = Tab.THOUGHTS
        if (openRequest != null) onOpenHandled()
    }
    val celebration by today.celebration.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(celebration?.id) {
        if (celebration?.big == true) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Tablets and unfolded phones get a side rail; phones a bottom bar.
        val wide = maxWidth >= 600.dp
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (!wide) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        Tab.entries.forEach { item ->
                            NavigationBarItem(
                                selected = tab == item,
                                onClick = { tab = item },
                                icon = { Icon(if (tab == item) item.selectedIcon else item.icon, null) },
                                label = { Text(item.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                if (wide) {
                    NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        Tab.entries.forEach { item ->
                            NavigationRailItem(
                                selected = tab == item,
                                onClick = { tab = item },
                                icon = { Icon(if (tab == item) item.selectedIcon else item.icon, null) },
                                label = { Text(item.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                }
                Crossfade(targetState = tab, label = "tab", modifier = Modifier.weight(1f)) { current ->
                    when (current) {
                        Tab.TODAY -> TodayScreen(
                            snackbar,
                            onOpenCapture = { tab = Tab.THOUGHTS },
                            onOpenYou = { tab = Tab.YOU },
                        )
                        Tab.THOUGHTS -> ThoughtsScreen(snackbar)
                        Tab.YOU -> YouScreen(snackbar)
                    }
                }
            }
        }
        CelebrationOverlay(celebration, Modifier.statusBarsPadding())
        var dismissedLevelUp by rememberSaveable { mutableStateOf<Long?>(null) }
        celebration?.takeIf { it.levelUp != null && it.id != dismissedLevelUp }?.let { c ->
            LevelUpDialog(c.levelUp!!) { dismissedLevelUp = c.id }
        }
    }
}
