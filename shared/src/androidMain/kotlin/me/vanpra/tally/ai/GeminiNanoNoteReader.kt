package me.vanpra.tally.ai

import android.util.Log
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.ModelPreference
import com.google.mlkit.genai.prompt.ModelReleaseStage
import com.google.mlkit.genai.prompt.SystemInstruction
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import com.google.mlkit.genai.prompt.generationConfig
import com.google.mlkit.genai.prompt.modelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Gemini Nano through ML Kit's Prompt API. Runs entirely on the phone (AICore), so it works with no signal.
 * The model lives in AICore and is shared with the system; we only ask for it to be downloaded if it isn't there.
 */
class GeminiNanoNoteReader : NoteReader {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow<ReaderState>(ReaderState.Preparing())
    override val state: StateFlow<ReaderState> = _state.asStateFlow()

    private val model: GenerativeModel by lazy {
        Generation.getClient(
            generationConfig {
                modelConfig = modelConfig {
                    releaseStage = ModelReleaseStage.STABLE
                    preference = ModelPreference.FULL
                }
            },
        )
    }
    private var systemPromptSupported = true
    private var prepareJob: Job? = null
    private var warm = false
    private val lock = Mutex()

    override fun prepare() {
        if (prepareJob?.isActive == true || _state.value == ReaderState.Ready) return
        prepareJob = scope.launch {
            try {
                val status = model.checkStatus()
                Log.i(TAG, "status=$status")
                when (status) {
                    FeatureStatus.AVAILABLE -> ready()
                    FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING -> download()
                    else -> _state.value = ReaderState.Unavailable
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Gemini Nano unavailable", e)
                _state.value = ReaderState.Unavailable
            }
        }
    }

    private suspend fun download() {
        var total = 0L
        model.download().collect { s ->
            when (s) {
                is DownloadStatus.DownloadStarted -> { total = s.bytesToDownload; _state.value = ReaderState.Preparing(0f) }
                is DownloadStatus.DownloadProgress -> _state.value = ReaderState.Preparing(if (total > 0) (s.totalBytesDownloaded.toFloat() / total).coerceIn(0f, 1f) else null)
                is DownloadStatus.DownloadCompleted -> ready()
                is DownloadStatus.DownloadFailed -> { Log.w(TAG, "Gemini Nano download failed", s.e); _state.value = ReaderState.Unavailable }
            }
        }
    }

    private suspend fun ready() {
        systemPromptSupported = runCatching { model.isSystemPromptAvailable() }.getOrDefault(false)
        _state.value = ReaderState.Ready
    }

    override fun warmUp() {
        if (warm) return
        scope.launch {
            prepareJob?.join()
            if (_state.value != ReaderState.Ready) return@launch
            runCatching { model.warmup() }.onSuccess { warm = true }.onFailure { Log.w(TAG, "warm-up failed", it) }
        }
    }

    override suspend fun read(note: String, hole: HoleContext): NoteReading? {
        if (note.isBlank()) return NoteReading.EMPTY
        prepareJob?.join()
        if (_state.value != ReaderState.Ready) return null
        return withContext(Dispatchers.Default) {
            // One request at a time: AICore queues anyway, and the latest note is the one that matters.
            lock.withLock {
                try {
                    val user = NotePrompt.user(note, hole)
                    val request = if (systemPromptSupported) {
                        generateContentRequest(SystemInstruction(NotePrompt.system), TextPart(user)) { tune() }
                    } else {
                        generateContentRequest(TextPart(NotePrompt.system + "\n\n" + user)) { tune() }
                    }
                    val reply = model.generateContent(request).candidates.firstOrNull()?.text
                    NotePrompt.parse(reply, hole, note).also { Log.d(TAG, "read \"$user\" -> $reply -> $it") }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "read failed", e)
                    null
                }
            }
        }
    }

    private fun com.google.mlkit.genai.prompt.GenerateContentRequest.Builder.tune() {
        temperature = 0f
        topK = 1
        candidateCount = 1
        maxOutputTokens = 320
    }

    private companion object { const val TAG = "TallyNano" }
}
