package dev.holdthisthought.ui.capture

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.holdthisthought.ThoughtApplication
import dev.holdthisthought.data.LocalClassifier
import dev.holdthisthought.data.local.CaptureInput
import dev.holdthisthought.data.local.KindSource
import dev.holdthisthought.data.local.Thought
import dev.holdthisthought.data.local.ThoughtKind
import dev.holdthisthought.data.local.ThoughtSpace
import dev.holdthisthought.speech.SpeechCapture
import dev.holdthisthought.speech.SpeechState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CaptureViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as ThoughtApplication).container
    private val repository = container.repository

    private val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft.asStateFlow()

    /** null = let the app decide. */
    private val _kind = MutableStateFlow<ThoughtKind?>(null)
    val kind: StateFlow<ThoughtKind?> = _kind.asStateFlow()

    private val _space = MutableStateFlow(container.preferences.defaultSpace)
    val space: StateFlow<ThoughtSpace> = _space.asStateFlow()

    private val _saved = MutableSharedFlow<Thought>(extraBufferCapacity = 1)
    /** Emits each successfully kept thought, for the confirmation snackbar. */
    val saved: SharedFlow<Thought> = _saved.asSharedFlow()

    private var usedVoice = false

    private val speech = SpeechCapture(app) { segment ->
        usedVoice = true
        _draft.value = joinSegments(_draft.value, segment)
    }
    val speechState: StateFlow<SpeechState> = speech.state

    fun guessedKind(text: String): ThoughtKind = LocalClassifier.classify(text)

    fun onDraftChange(value: String) {
        _draft.value = value
    }

    fun onKindChange(kind: ThoughtKind?) {
        _kind.value = kind
    }

    fun onSpaceChange(space: ThoughtSpace) {
        _space.value = space
        container.preferences.defaultSpace = space
    }

    fun startListening() = speech.start()

    fun stopListening() = speech.stop()

    fun save() {
        speech.stop()
        val text = joinSegments(_draft.value, speech.state.value.partial)
        if (text.isBlank()) return
        val input = if (usedVoice) CaptureInput.VOICE else CaptureInput.TEXT
        val kind = _kind.value
        _draft.value = ""
        _kind.value = null
        usedVoice = false
        viewModelScope.launch {
            repository.capture(text, _space.value, kind, input)?.let { _saved.tryEmit(it) }
        }
    }

    fun undo(thought: Thought) {
        viewModelScope.launch {
            repository.delete(thought.id)
            if (_draft.value.isBlank()) {
                _draft.value = thought.body
                _kind.value = thought.kind.takeIf { thought.kindSource == KindSource.USER }
            }
        }
    }

    override fun onCleared() {
        speech.release()
    }

    private fun joinSegments(existing: String, segment: String): String {
        val a = existing.trimEnd()
        val b = segment.trim()
        return when {
            b.isEmpty() -> existing
            a.isEmpty() -> b.replaceFirstChar { it.uppercase() }
            else -> "$a $b"
        }
    }
}
