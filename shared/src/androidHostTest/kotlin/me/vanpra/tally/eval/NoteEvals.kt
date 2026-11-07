package me.vanpra.tally.eval

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.vanpra.tally.ai.GeminiCloudNoteReader
import me.vanpra.tally.ai.HoleContext
import me.vanpra.tally.ai.NotePrompt
import me.vanpra.tally.ai.NoteReading
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.test.Test

/**
 * Not a test: an on-demand eval of note reading against evals/note-evals.jsonl, run with
 * `evals/run-note-evals.sh`. Scores the same pipeline the app uses (instant read + cloud model + guards)
 * and writes a report to evals/results. Does nothing in normal test runs.
 */
class NoteEvals {
    private class Case(val par: Int, val note: String, val expect: Map<String, String?>, val why: String?)
    private class Outcome(val case: Case, val got: Map<String, String?>, val ms: Long, val failed: Boolean, val log: List<String> = emptyList()) {
        val wrong = FIELDS.filter { f -> norm(f, case.expect[f]) != norm(f, got[f]) }
        val invented = wrong.filter { f -> norm(f, case.expect[f]) == null }
    }

    @Test fun run() {
        if (System.getProperty("tally.eval") != "true") return
        val key = System.getProperty("tally.geminiKey").orEmpty()
        val deepseekKey = System.getProperty("tally.deepseekKey").orEmpty()
        val cases = File(System.getProperty("tally.evalFile")).readLines().filter { it.isNotBlank() }.map(::parseCase)
        val models = System.getProperty("tally.evalModels").split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val out = File(System.getProperty("tally.evalOut")).apply { mkdirs() }
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm"))

        val runs = buildList {
            add("rules only (offline)" to cases.map { c -> Outcome(c, fields(NotePrompt.quickRead(c.note, c.par)), 0, false) })
            System.getProperty("tally.evalNano")?.takeIf { it.isNotBlank() && File(it).exists() }?.let { path ->
                val byNote = File(path).readLines().filter { it.isNotBlank() }.associate { l ->
                    val o = Json.parseToJsonElement(l).jsonObject
                    val reading = o["reading"]?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.let { Json.decodeFromJsonElement(NoteReading.serializer(), it) }
                    o["note"]!!.jsonPrimitive.content to (reading to o["ms"]!!.jsonPrimitive.content.toLong())
                }
                add("gemini nano (on device)" to cases.map { c ->
                    val (r, ms) = byNote[c.note] ?: (null to 0L)
                    Outcome(c, fields(NotePrompt.quickRead(c.note, c.par) over (r ?: NoteReading.EMPTY)), ms, r == null)
                })
            }
            for (model in models) {
                val lines = mutableListOf<String>()
                // "deepseek:<model>" goes to DeepSeek; anything else is a Gemini model.
                val reader: me.vanpra.tally.ai.NoteReader = if (model.startsWith("deepseek:")) {
                    check(deepseekKey.isNotBlank()) { "DEEPSEEK_API_KEY isn't set in local.properties" }
                    me.vanpra.tally.ai.ChatCompletionsNoteReader(me.vanpra.tally.ai.ChatCompletionsNoteReader.DEEPSEEK, deepseekKey, model.removePrefix("deepseek:"), log = { lines += it })
                } else {
                    check(key.isNotBlank()) { "GEMINI_API_KEY isn't set in local.properties" }
                    GeminiCloudNoteReader(key, model, log = { lines += it }, retryWhenBusy = true)
                }
                val pace = System.getProperty("tally.evalPaceMs")?.toLongOrNull() ?: 0
                val results = cases.map { c ->
                    lines.clear()
                    val t0 = System.nanoTime()
                    val r = runBlocking { reader.read(c.note, HoleContext(1, c.par)) }
                    val ms = (System.nanoTime() - t0) / 1_000_000
                    Thread.sleep(pace)
                    Outcome(c, fields(NotePrompt.quickRead(c.note, c.par) over (r ?: NoteReading.EMPTY)), ms, r == null, lines.filterNot { it.startsWith("read \"") })
                }
                add(model to results)
            }
        }

        val summary = StringBuilder("# Note reading eval · $stamp\n\n${cases.size} notes, ${FIELDS.size} fields each.\n\n")
        summary.append("| Pipeline | Notes fully right | Fields right | Invented fields | Failed calls | Median ms | Slowest ms |\n|---|---|---|---|---|---|---|\n")
        runs.forEach { (name, rs) ->
            val fieldsRight = rs.sumOf { FIELDS.size - it.wrong.size }
            val times = rs.filter { it.ms > 0 }.map { it.ms }.sorted()
            summary.append("| $name | ${rs.count { it.wrong.isEmpty() }}/${rs.size} | $fieldsRight/${rs.size * FIELDS.size} | ${rs.sumOf { it.invented.size }} | ${rs.count { it.failed }} | ${times.getOrNull(times.size / 2) ?: "–"} | ${times.lastOrNull() ?: "–"} |\n")
        }
        summary.append("\n## Accuracy by field\n\n| Pipeline | ${FIELDS.joinToString(" | ")} |\n|---|${FIELDS.joinToString("") { "---|" }}\n")
        runs.forEach { (name, rs) -> summary.append("| $name | ${FIELDS.joinToString(" | ") { f -> "${rs.count { f !in it.wrong }}/${rs.size}" }} |\n") }
        runs.drop(1).forEach { (name, rs) ->
            summary.append("\n## Misses · $name\n\n")
            rs.filter { it.wrong.isNotEmpty() || it.failed }.forEach { o ->
                summary.append("- *Par ${o.case.par}:* “${o.case.note}”${o.case.why?.let { " ($it)" } ?: ""}${if (o.failed) " **call failed**" else ""}\n")
                o.log.forEach { summary.append("  - log: ${it.take(200)}\n") }
                o.wrong.forEach { f -> summary.append("  - $f: expected ${norm(f, o.case.expect[f]) ?: "nothing"}, got ${norm(f, o.got[f]) ?: "nothing"}${if (f in o.invented) " (invented)" else ""}\n") }
            }
        }
        val report = summary.toString()
        println("\n" + report)
        File(out, "$stamp-${runs.drop(1).joinToString("+") { it.first.substringBefore(" ") }}.md").writeText(report)
    }

    private fun parseCase(line: String): Case {
        val o = Json.parseToJsonElement(line).jsonObject
        val e = o["expect"]!!.jsonObject
        return Case(o["par"]!!.jsonPrimitive.int, o["note"]!!.jsonPrimitive.content, FIELDS.associateWith { (e[it] as? JsonPrimitive)?.content }, (o["why"] as? JsonPrimitive)?.content)
    }

    private fun fields(r: NoteReading): Map<String, String?> = mapOf(
        "strokes" to r.strokes?.toString(), "putts" to r.putts?.toString(), "teeShot" to r.teeShot?.name,
        "penalties" to r.penalties?.toString(), "bunkerShots" to r.bunkerShots?.toString(),
        "firstPuttFeet" to r.firstPuttFeet?.toString(), "puttMiss" to r.puttMiss?.name, "leaveFeet" to r.leaveFeet?.toString(),
        "teeClub" to r.teeClub,
    )

    private companion object {
        val FIELDS = listOf("strokes", "putts", "teeShot", "teeClub", "penalties", "bunkerShots", "firstPuttFeet", "puttMiss", "leaveFeet")
        /** "No penalties" and "0 penalties" mean the same on the card. */
        fun norm(field: String, v: String?): String? = if (v == "0" && (field == "penalties" || field == "bunkerShots")) null else v
    }
}
