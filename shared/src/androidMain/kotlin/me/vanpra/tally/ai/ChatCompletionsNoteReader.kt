package me.vanpra.tally.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
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
 * Reads notes with any OpenAI-style chat completions API (DeepSeek and others), for comparing providers.
 * Same prompt and parsing as every other reader. The key travels in a header and is never logged.
 */
class ChatCompletionsNoteReader(
    private val baseUrl: String,
    private val apiKey: String,
    val model: String,
    private val log: (String) -> Unit = {},
) : NoteReader {
    override val state: StateFlow<ReaderState> = MutableStateFlow(if (apiKey.isBlank()) ReaderState.Unavailable else ReaderState.Ready)
    override val by: ReadBy get() = ReadBy.CLOUD

    override suspend fun read(note: String, hole: HoleContext): NoteReading? {
        if (apiKey.isBlank()) return null
        if (note.isBlank()) return NoteReading.EMPTY
        return withContext(Dispatchers.IO) {
            try {
                NotePrompt.parse(call(NotePrompt.system, NotePrompt.user(note, hole)), hole, note)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("read failed: ${e.javaClass.simpleName}: ${e.message}")
                null
            }
        }
    }

    private fun call(system: String, user: String): String? {
        val body = buildJsonObject {
            put("model", model)
            putJsonArray("messages") {
                addJsonObject { put("role", "system"); put("content", system) }
                addJsonObject { put("role", "user"); put("content", user) }
            }
            put("temperature", 0)
            put("max_tokens", 512)
            putJsonObject("response_format") { put("type", "json_object") }
        }
        val conn = (URL("$baseUrl/chat/completions").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 5_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
        }
        try {
            conn.outputStream.use { it.write(body.toString().encodeToByteArray()) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) { log("HTTP $code: ${text.take(300)}"); return null }
            val message = Json.parseToJsonElement(text).jsonObject["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject
            return (message?.get("content") as? JsonPrimitive)?.content
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        const val DEEPSEEK = "https://api.deepseek.com"
    }
}
