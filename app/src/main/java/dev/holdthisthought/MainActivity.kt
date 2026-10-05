package dev.holdthisthought

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.LocalLibrary
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import dev.holdthisthought.ui.capture.CaptureScreen
import dev.holdthisthought.ui.library.LibraryScreen
import dev.holdthisthought.ui.settings.SettingsScreen
import dev.holdthisthought.ui.theme.HoldThisThoughtTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            HoldThisThoughtTheme { AppRoot() }
        }
    }
}

private enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    CAPTURE("Capture", Icons.Outlined.EditNote, Icons.Rounded.EditNote),
    LIBRARY("Library", Icons.Outlined.LocalLibrary, Icons.Rounded.LocalLibrary),
    SETTINGS("Settings", Icons.Outlined.Settings, Icons.Rounded.Settings),
}

@Composable
private fun AppRoot() {
    var tab by rememberSaveable { mutableStateOf(Tab.CAPTURE) }
    val snackbar = remember { SnackbarHostState() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(if (tab == item) item.selectedIcon else item.icon, null) },
                        label = { Text(item.label) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Crossfade(targetState = tab, label = "tab", modifier = Modifier.padding(padding).consumeWindowInsets(padding)) { current ->
            when (current) {
                Tab.CAPTURE -> CaptureScreen(snackbar)
                Tab.LIBRARY -> LibraryScreen(snackbar)
                Tab.SETTINGS -> SettingsScreen(snackbar)
            }
        }
    }
}
