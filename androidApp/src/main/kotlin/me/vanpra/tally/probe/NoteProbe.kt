package me.vanpra.tally.probe

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.vanpra.tally.ai.HoleContext
import me.vanpra.tally.ai.NotePrompt
import me.vanpra.tally.ai.NoteReader
import me.vanpra.tally.ai.ReaderState

// TEMPORARY: runs sample notes through the reader and logs results. Remove before shipping.
object NoteProbe {
    private val notes = listOf(
        4 to "8, lost a ball right off the tee took a penalty drop 2 shots, hit a nice iron in to 10 feet but missed the putt low",
        4 to "par, fairway, 2 putts",
        3 to "7 iron to the back of the green, three putted for a 4",
        4 to "bogey, sliced it into the trees, punched out",
        5 to "6. drive left in the rough, two to get out of the fairway bunker, two putts",
        4 to "double. went in the water on the right with my drive",
        4 to "pulled the driver OB, reloaded, made 7",
        3 to "birdie! 9 iron to 4 feet",
        4 to "drive down the middle, wedge to 15 feet, two putts for par",
        5 to "duffed my chip, then chipped in",
        4 to "5",
        4 to "hit it left",
        4 to "greenside bunker, took 3 to get out, 3 putt, 9",
        3 to "plugged in the bunker, splashed out and one putt for par",
        4 to "triple, two balls in the lake",
        4 to "sorry, it was a 6 not a 5. drive right",
        4 to "6, hit first shot left into tee, chipped out, got a really good 9 iron onto the green and then 3 putt from 20 feet. First putt was a bad one, it ended up like 8 feet away",
        4 to "Six hit my drive right into the trees had to chip out then two putts",
        4 to "Got a 6 drove it right into the trees chipped out got nine iron onto the green but then I had really bad putt hit it to 8 ft from like 20 ft and then just two from there",
    )

    /**
     * Runs evals/note-evals.jsonl (pushed to the app's files/evals) through the on-device reader and writes
     * what it read to nano-results.jsonl beside it, for evals/run-nano-evals.sh to score on the Mac.
     */
    fun runEvals(reader: NoteReader, dir: java.io.File) = CoroutineScope(Dispatchers.Default).launch {
        val json = kotlinx.serialization.json.Json { encodeDefaults = false }
        reader.prepare()
        kotlinx.coroutines.withTimeoutOrNull(60_000) { reader.state.first { it !is ReaderState.Preparing } }
        reader.warmUp()
        val out = java.io.File(dir, "nano-results.jsonl").apply { delete() }
        java.io.File(dir, "note-evals.jsonl").readLines().filter { it.isNotBlank() }.forEach { line ->
            val o = json.parseToJsonElement(line) as kotlinx.serialization.json.JsonObject
            val par = (o["par"] as kotlinx.serialization.json.JsonPrimitive).content.toInt()
            val note = (o["note"] as kotlinx.serialization.json.JsonPrimitive).content
            val t0 = System.currentTimeMillis()
            val r = reader.read(note, HoleContext(1, par))
            val ms = System.currentTimeMillis() - t0
            val reading = r?.let { json.encodeToString(me.vanpra.tally.ai.NoteReading.serializer(), it) } ?: "null"
            out.appendText("{\"note\":${kotlinx.serialization.json.JsonPrimitive(note)},\"ms\":$ms,\"reading\":$reading}\n")
        }
        Log.i("TallyProbe", "evals done")
    }

    fun run(reader: NoteReader, only: List<String>? = null) = CoroutineScope(Dispatchers.Default).launch {
        val notes = only?.map { 4 to it } ?: notes
        Log.i("TallyProbe", "start")
        (reader as? me.vanpra.tally.ai.GeminiCloudNoteReader)?.let { c ->
            Log.i("TallyProbe", "configured=${c.configured} flash models=" + c.modelNames().filter { "flash" in it }.joinToString())
        }
        reader.prepare()
        val s = kotlinx.coroutines.withTimeoutOrNull(60_000) { reader.state.first { it !is ReaderState.Preparing } }
        Log.i("TallyProbe", "state=$s")
        reader.warmUp()
        notes.forEach { (par, note) ->
            val t0 = System.currentTimeMillis()
            val r = reader.read(note, HoleContext(1, par))
            val ms = System.currentTimeMillis() - t0
            Log.i("TallyProbe", "${ms}ms | par $par | $note\n    quick=${NotePrompt.quickRead(note, par)}\n    nano=$r")
        }
        Log.i("TallyProbe", "done")
    }
}
