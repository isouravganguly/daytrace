package dev.holdthisthought.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.holdthisthought.data.SupabaseRemoteDataSource
import dev.holdthisthought.data.Thought
import dev.holdthisthought.data.ThoughtKind
import dev.holdthisthought.data.ThoughtRepository
import dev.holdthisthought.data.ThoughtSpace
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Destination { CAPTURE, THOUGHTS, NOTES, TODOS, SEARCH }

data class AuthUiState(
    val isConfigured: Boolean,
    val isBusy: Boolean = false,
    val isSignedIn: Boolean = false,
    val isAnonymous: Boolean = false,
    val allowPrivateMode: Boolean = false,
    val email: String? = null,
    val message: String? = null,
) {
    val needsAuth: Boolean get() = isConfigured && (!isSignedIn || (isAnonymous && !allowPrivateMode))
}

@OptIn(ExperimentalCoroutinesApi::class)
class ThoughtViewModel(
    private val repository: ThoughtRepository,
    private val remoteDataSource: SupabaseRemoteDataSource,
) : ViewModel() {
    val draft = MutableStateFlow("")
    val space = MutableStateFlow(ThoughtSpace.WORK)
    val destination = MutableStateFlow(Destination.CAPTURE)
    val searchQuery = MutableStateFlow("")
    val captureMessage = MutableStateFlow<String?>(null)
    private val authMessage = MutableStateFlow<String?>(null)
    private val authBusy = MutableStateFlow(false)
    private val allowPrivateMode = MutableStateFlow(false)

    val authUiState: StateFlow<AuthUiState> =
        combine(
            remoteDataSource.sessionStatus,
            authMessage,
            authBusy,
            allowPrivateMode,
        ) { status, message, busy, privateMode ->
            val user = (status as? SessionStatus.Authenticated)?.session?.user
            AuthUiState(
                isConfigured = remoteDataSource.isConfigured,
                isBusy = busy || status == SessionStatus.Initializing,
                isSignedIn = user != null,
                isAnonymous = user?.isAnonymous == true,
                allowPrivateMode = privateMode,
                email = user?.email,
                message = message,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            AuthUiState(isConfigured = remoteDataSource.isConfigured),
        )

    val thoughts: StateFlow<List<Thought>> =
        combine(destination, searchQuery) { destination, query ->
            query to when (destination) {
                Destination.THOUGHTS -> ThoughtKind.THOUGHT
                Destination.NOTES -> ThoughtKind.NOTE
                Destination.TODOS -> ThoughtKind.TODO
                else -> null
            }
        }.flatMapLatest { (query, kind) ->
            repository.observe(if (destination.value == Destination.SEARCH) query else "", kind)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save() {
        val value = draft.value
        if (value.isBlank()) return
        viewModelScope.launch {
            repository.capture(value, space.value)
            draft.value = ""
            captureMessage.value = "Kept."
            delay(1_400)
            captureMessage.value = null
        }
    }

    fun signIn(email: String, password: String) {
        runAuthAction {
            remoteDataSource.signIn(email, password)
            allowPrivateMode.value = false
            authMessage.value = "Signed in. Syncing your thoughts now."
            repository.requestSync()
        }
    }

    fun signUp(email: String, password: String) {
        runAuthAction {
            val signedIn = remoteDataSource.signUp(email, password)
            if (signedIn) allowPrivateMode.value = false
            authMessage.value =
                if (signedIn) "Account created. Syncing your thoughts now."
                else "Account created. Check your email, or turn off Confirm email for MVP testing."
            if (signedIn) repository.requestSync()
        }
    }

    fun continuePrivately() {
        runAuthAction {
            remoteDataSource.continuePrivately()
            allowPrivateMode.value = true
            authMessage.value = "Private mode is on. Syncing this device's thoughts."
            repository.requestSync()
        }
    }

    fun signOut() {
        runAuthAction {
            remoteDataSource.signOut()
            allowPrivateMode.value = false
            authMessage.value = "Signed out. New thoughts will stay local until you sign in."
        }
    }

    private fun runAuthAction(action: suspend () -> Unit) {
        viewModelScope.launch {
            authBusy.value = true
            authMessage.value = null
            runCatching { action() }
                .onFailure { authMessage.value = it.message ?: "Authentication failed." }
            authBusy.value = false
        }
    }

    class Factory(
        private val repository: ThoughtRepository,
        private val remoteDataSource: SupabaseRemoteDataSource,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ThoughtViewModel(repository, remoteDataSource) as T
    }
}
