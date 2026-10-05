package dev.holdthisthought.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.holdthisthought.ThoughtApplication
import dev.holdthisthought.export.ThoughtExporter
import dev.holdthisthought.sync.SyncEngine
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AccountState { NOT_CONFIGURED, LOADING, UNREACHABLE, SIGNED_OUT, ANONYMOUS, SIGNED_IN }

data class SyncUi(
    val pending: Int = 0,
    val lastSyncedAt: Long? = null,
    val lastError: String? = null,
)

enum class ExportFormat(val mimeType: String, val extension: String) {
    MARKDOWN("text/markdown", "md"),
    JSON("application/json", "json"),
}

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as ThoughtApplication).container
    private val remote = container.remote

    val account: StateFlow<Pair<AccountState, String?>> = remote.sessionStatus
        .map { status ->
            when {
                !remote.isConfigured -> AccountState.NOT_CONFIGURED to null
                status is SessionStatus.Initializing -> AccountState.LOADING to null
                // A saved session exists but the server couldn't be reached to refresh it.
                status is SessionStatus.RefreshFailure -> AccountState.UNREACHABLE to null
                status is SessionStatus.Authenticated -> {
                    val user = status.session.user
                    if (user?.isAnonymous == true) AccountState.ANONYMOUS to null
                    else AccountState.SIGNED_IN to user?.email
                }
                else -> AccountState.SIGNED_OUT to null
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountState.LOADING to null)

    val sync: StateFlow<SyncUi> = combine(
        container.repository.observePendingCount(),
        container.preferences.lastSyncedAt,
        container.preferences.lastSyncError,
    ) { pending, last, error -> SyncUi(pending, last, error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncUi())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun consumeMessage() {
        _message.value = null
    }

    fun signIn(email: String, password: String) = run("Signed in. Syncing your library…") {
        remote.signIn(email, password)
        syncNowInternal()
    }

    fun createAccount(email: String, password: String) = run(null) {
        val signedIn = remote.signUp(email, password)
        _message.value = if (signedIn) {
            syncNowInternal()
            "Account created. Your thoughts are backing up."
        } else {
            "Check your inbox to confirm your email, then sign in."
        }
    }

    /** Keeps the current anonymous user id (and all its cloud rows) but adds an email login. */
    fun secureAccount(email: String, password: String) = run(
        "Account secured. Confirm your email if asked, then you can sign in anywhere.",
    ) {
        remote.upgradeAnonymous(email, password)
    }

    fun signOut() = run("Signed out. Your thoughts stay on this device.") {
        remote.signOut()
    }

    fun syncNow() = run(null) {
        _message.value = when (val outcome = syncNowInternal()) {
            SyncEngine.Outcome.Synced -> "Up to date."
            SyncEngine.Outcome.Skipped -> "Sign in to back up."
            is SyncEngine.Outcome.Failed -> "Sync failed: ${outcome.error.message ?: "unknown error"}"
        }
    }

    fun export(uri: Uri, format: ExportFormat) = run(null) {
        val thoughts = container.repository.allVisible()
        val text = when (format) {
            ExportFormat.MARKDOWN -> ThoughtExporter.toMarkdown(thoughts)
            ExportFormat.JSON -> ThoughtExporter.toJson(thoughts)
        }
        withContext(Dispatchers.IO) {
            val resolver = getApplication<Application>().contentResolver
            requireNotNull(resolver.openOutputStream(uri)) { "Couldn't open file." }
                .bufferedWriter().use { it.write(text) }
        }
        _message.value = "Exported ${thoughts.size} entries."
    }

    private suspend fun syncNowInternal() = container.syncEngine.sync()

    private fun run(successMessage: String?, block: suspend () -> Unit) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            runCatching { block() }
                .onSuccess { successMessage?.let { _message.value = it } }
                .onFailure { _message.value = it.message ?: "Something went wrong." }
            _busy.value = false
        }
    }
}
