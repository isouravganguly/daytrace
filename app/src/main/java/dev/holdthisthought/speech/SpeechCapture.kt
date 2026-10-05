package dev.holdthisthought.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SpeechState(
    val isListening: Boolean = false,
    /** Live, not-yet-final words for the current phrase. */
    val partial: String = "",
    /** Microphone loudness, 0..1, for the waveform. */
    val level: Float = 0f,
    val error: String? = null,
)

/**
 * Continuous dictation on top of Android's [SpeechRecognizer].
 *
 * The platform recognizer ends a session after a short pause. While the user is still
 * "recording", this restarts it automatically, so long thoughts are captured as a series
 * of final segments delivered through [onSegment]. Must be used from the main thread.
 */
class SpeechCapture(
    context: Context,
    private val onSegment: (String) -> Unit,
) {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var active = false

    private val _state = MutableStateFlow(SpeechState())
    val state: StateFlow<SpeechState> = _state.asStateFlow()

    val isAvailable: Boolean get() = SpeechRecognizer.isRecognitionAvailable(appContext)

    private val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4_000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3_000L)
    }

    fun start() {
        if (active) return
        if (!isAvailable) {
            _state.value = SpeechState(error = "Speech recognition isn't available on this device.")
            return
        }
        active = true
        _state.value = SpeechState(isListening = true)
        listen()
    }

    /** Stops listening; the last phrase is still delivered once the recognizer finalises it. */
    fun stop() {
        if (!active) return
        active = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.stopListening()
        _state.update { it.copy(isListening = false, level = 0f) }
    }

    fun release() {
        active = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
    }

    private fun listen() {
        val current = recognizer ?: SpeechRecognizer.createSpeechRecognizer(appContext).also {
            it.setRecognitionListener(listener)
            recognizer = it
        }
        current.startListening(intent)
    }

    private fun restartIfActive() {
        if (active) handler.postDelayed({ if (active) listen() }, RESTART_DELAY_MS)
    }

    private fun commit(text: String) {
        val cleaned = text.trim()
        if (cleaned.isNotEmpty()) onSegment(cleaned)
        _state.update { it.copy(partial = "") }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onRmsChanged(rmsdB: Float) {
            if (active) _state.update { it.copy(level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)) }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty()
            if (text.isNotBlank()) _state.update { it.copy(partial = text) }
        }

        override fun onResults(results: Bundle?) {
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            commit(text ?: _state.value.partial)
            restartIfActive()
        }

        override fun onError(error: Int) {
            // Keep whatever was heard before the error.
            commit(_state.value.partial)
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                SpeechRecognizer.ERROR_CLIENT -> restartIfActive()
                else -> {
                    active = false
                    _state.value = SpeechState(error = errorMessage(error))
                }
            }
        }
    }

    private fun errorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is needed."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "Voice needs a connection on this device. Typing still works offline."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "The microphone is busy. Try again."
        SpeechRecognizer.ERROR_AUDIO -> "Couldn't access the microphone."
        else -> "Voice capture stopped. Try again."
    }

    private companion object {
        const val RESTART_DELAY_MS = 120L
    }
}
