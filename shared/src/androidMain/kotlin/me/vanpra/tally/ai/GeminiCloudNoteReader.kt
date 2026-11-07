package me.vanpra.tally.ai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import me.vanpra.tally.round.ReadBy
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.cancellation.CancellationException

/**
 * Reads notes with Gemini over the network: a stronger model than the one on the phone, used when
 * there's signal. Only the note text and the hole's par are sent. The key travels in a header, never
 * the URL, and is never logged.
 */
class GeminiCloudNoteReader(
    private val apiKey: String,
    val model: String = DEFAULT_MODEL,
    /** Where to log; plain JVM-friendly so the reader can be evaluated in host tests. Never given the key. */
    private val log: (String) -> Unit = { android.util.Log.d(TAG, it) },
    /**
     * Wait and retry once when rate-limited or busy. Off in the app, where the phone's own reading covers
     * a slow or refused cloud call; on for evals, which want every answer.
     */
    private val retryWhenBusy: Boolean = false,
) : NoteReader {
    /** Calls run here, detached, so a superseded read is abandoned at once instead of waited for. */
    private val calls = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override val state: StateFlow<ReaderState> = MutableStateFlow(if (apiKey.isBlank()) ReaderState.Unavailable else ReaderState.Ready)
    override val by: ReadBy get() = ReadBy.CLOUD
    val configured: Boolean get() = apiKey.isNotBlank()

    override suspend fun read(note: String, hole: HoleContext): NoteReading? {
        if (!configured) return null
        if (note.isBlank()) return NoteReading.EMPTY
        return run {
            try {
                val reply = generate(NotePrompt.cloudSystem, NotePrompt.user(note, hole))
                NotePrompt.parse(reply, hole, note).also { log("read \"${NotePrompt.user(note, hole)}\" -> ${reply?.replace('\n', ' ')} -> $it") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("read failed: ${e.javaClass.simpleName}: ${e.message}")
                null
            }
        }
    }

    /**
     * The network call is blocking and can't be interrupted, so it runs detached: if this read is cancelled
     * (you kept typing), we stop waiting immediately and the call finishes, unused, in the background.
     */
    private suspend fun generate(system: String, user: String): String? {
        suspend fun once(): Result<String?> {
            val job = calls.async { runCatching { call(system, user) } }
            return try { job.await() } catch (e: CancellationException) { job.cancel(); throw e }
        }
        val first = once()
        val busy = first.exceptionOrNull() as? Busy ?: return first.getOrThrow()
        if (!retryWhenBusy) { log("${busy.message}; using the phone's reading instead"); return null }
        log("${busy.message}; retrying in ${busy.waitMs} ms")
        delay(busy.waitMs)
        return once().getOrThrow()
    }

    /** Rate limited (429) or briefly overloaded (503): worth one retry. */
    private class Busy(val waitMs: Long, message: String) : Exception(message)

    private fun call(system: String, user: String): String? {
        val body = buildJsonObject {
            putJsonObject("systemInstruction") { putJsonArray("parts") { addJsonObject { put("text", system) } } }
            putJsonArray("contents") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("parts") { addJsonObject { put("text", user) } }
                }
            }
            putJsonObject("generationConfig") {
                put("temperature", 0)
                put("maxOutputTokens", 1024)
                put("responseMimeType", "application/json")
            }
        }
        val conn = (URL("$BASE/models/$model:generateContent").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 5_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-goog-api-key", apiKey)
        }
        try {
            conn.outputStream.use { it.write(body.toString().encodeToByteArray()) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code == 429) {
                val seconds = Regex("\"retryDelay\"\\s*:\\s*\"(\\d+)").find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 10
                throw Busy((seconds * 1000).coerceAtMost(30_000), "HTTP 429")
            }
            if (code == 503) throw Busy(1_500, "HTTP 503")
            if (code !in 200..299) {
                log("HTTP $code: ${text.take(300)}")
                return null
            }
            val root = json.parseToJsonElement(text).jsonObject
            val parts = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject?.get("content")?.jsonObject?.get("parts") as? JsonArray ?: return null
            // Skip any thought parts; the answer is the plain text.
            return parts.mapNotNull { p ->
                val o = p as? JsonObject ?: return@mapNotNull null
                if ((o["thought"] as? JsonPrimitive)?.booleanOrNull == true) null else (o["text"] as? JsonPrimitive)?.content
            }.joinToString("")
        } finally {
            conn.disconnect()
        }
    }

    /** For checking which models this key can use (names only). */
    suspend fun modelNames(): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL("$BASE/models?pageSize=200").openConnection() as HttpURLConnection).apply { setRequestProperty("x-goog-api-key", apiKey) }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            json.parseToJsonElement(text).jsonObject["models"]?.jsonArray?.mapNotNull { (it.jsonObject["name"] as? JsonPrimitive)?.content }.orEmpty()
        }.getOrDefault(emptyList())
    }

    companion object {
        const val DEFAULT_MODEL = "gemini-flash-lite-latest"
        private const val BASE = "https://generativelanguage.googleapis.com/v1beta"
        private const val TAG = "TallyCloud"
        private val json = Json { ignoreUnknownKeys = true }
    }
}
