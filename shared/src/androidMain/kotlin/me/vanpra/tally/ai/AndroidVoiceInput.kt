package me.vanpra.tally.ai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

/**
 * Android's speech recogniser, on-device (Android 12+) so it works with no signal.
 *
 * The recogniser ends after a short silence and ignores longer timeouts, which is no good when you
 * stop to think mid-sentence. So a session keeps going until you tap stop: each time the recogniser
 * finishes a stretch, it's started again and the pieces are joined. Only a long silence ends it.
 */
class AndroidVoiceInput(
    private val context: Context,
    /** Asks for the microphone permission; true if granted. */
    private val requestPermission: suspend () -> Boolean,
) : VoiceInput {
    private val onDevice = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    override val isAvailable: Boolean = onDevice || SpeechRecognizer.isRecognitionAvailable(context)
    private var finishRequested: (() -> Unit)? = null

    override fun listen(): Flow<VoiceEvent> = callbackFlow {
        val granted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED || requestPermission()
        if (!granted) {
            send(VoiceEvent.Failed(VoiceFailure.NO_PERMISSION))
            close()
            awaitClose {}
            return@callbackFlow
        }
        val recognizer = if (onDevice && Build.VERSION.SDK_INT >= 31) SpeechRecognizer.createOnDeviceSpeechRecognizer(context) else SpeechRecognizer.createSpeechRecognizer(context)
        val pieces = mutableListOf<String>()   // finished stretches of speech
        var current = ""                        // the stretch being spoken now
        var finishing = false
        var lastHeard = SystemClock.elapsedRealtime()
        fun text() = GolfSpeech.tidy((pieces + current).filter { it.isNotBlank() }.joinToString(" "))
        fun end() {
            val all = text()
            if (all.isNotBlank()) trySend(VoiceEvent.Final(all)) else trySend(VoiceEvent.Failed(VoiceFailure.NOTHING_HEARD))
            close()
        }
        fun again() = recognizer.startListening(intent())

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { trySend(VoiceEvent.Listening) }
            override fun onBeginningOfSpeech() { lastHeard = SystemClock.elapsedRealtime() }
            override fun onRmsChanged(rmsdB: Float) {
                val level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                if (level > 0.35f) lastHeard = SystemClock.elapsedRealtime()
                trySend(VoiceEvent.Level(level))
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults.best()?.takeIf { it.isNotBlank() }?.let {
                    current = it
                    lastHeard = SystemClock.elapsedRealtime()
                    trySend(VoiceEvent.Partial(text()))
                }
            }
            override fun onResults(results: Bundle?) {
                results.best()?.takeIf { it.isNotBlank() }?.let { current = it }
                if (current.isNotBlank()) pieces += current
                current = ""
                if (finishing || quiet()) end() else { trySend(VoiceEvent.Partial(text())); again() }
            }
            override fun onError(error: Int) {
                if (current.isNotBlank()) { pieces += current; current = "" }
                val pause = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                when {
                    finishing -> end()
                    pause && !quiet() -> again() // a pause to think: keep listening
                    pause || pieces.isNotEmpty() -> end()
                    else -> { trySend(VoiceEvent.Failed(error.toFailure())); close() }
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
            private fun quiet() = SystemClock.elapsedRealtime() - lastHeard > LONG_SILENCE_MS
        })
        finishRequested = { finishing = true; recognizer.stopListening() }
        again()
        awaitClose {
            finishRequested = null
            recognizer.destroy()
        }
    }.flowOn(Dispatchers.Main)

    override fun finish() { finishRequested?.invoke() }

    private fun intent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
        // Nudge the recogniser towards golf words ("tee shot", not "T-shirt").
        if (Build.VERSION.SDK_INT >= 33) putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, ArrayList(GolfSpeech.vocabulary))
    }

    private fun Bundle?.best(): String? = this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    private fun Int.toFailure() = when (this) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> VoiceFailure.NOTHING_HEARD
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoiceFailure.NO_PERMISSION
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> VoiceFailure.BUSY
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE, SpeechRecognizer.ERROR_CLIENT -> VoiceFailure.UNAVAILABLE
        else -> VoiceFailure.OTHER
    }

    private companion object { const val LONG_SILENCE_MS = 10_000L }
}
