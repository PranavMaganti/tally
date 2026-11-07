package me.vanpra.tally.ai

import me.vanpra.tally.round.PuttMiss
import me.vanpra.tally.round.TeeShot

/** Where in the note a field was read from, for underlining it. */
data class NoteSpan(val field: String, val start: Int, val end: Int)

/**
 * Finds the words in a note behind each value, so they can be underlined in that field's colour.
 * Done here rather than by the model: it's instant, works on every phone, and can't cite words that
 * aren't there. It also backs up the model: a putt "miss" only counts if a phrase about the putt says so.
 */
object NoteEvidence {

    fun spans(note: String, values: NoteReading, par: Int): List<NoteSpan> = buildList {
        values.strokes?.let { v -> strokes(note, v, par)?.let { add(NoteSpan(NoteField.STROKES, it.first, it.last + 1)) } }
        values.putts?.let { v -> putts(note, v)?.let { add(NoteSpan(NoteField.PUTTS, it.first, it.last + 1)) } }
        values.firstPuttFeet?.let { v -> feet(note, v).firstOrNull()?.let { add(NoteSpan(NoteField.FIRST_PUTT, it.first, it.last + 1)) } }
        values.puttMiss?.let { m -> miss(note, m)?.let { add(NoteSpan(NoteField.PUTT_MISS, it.first, it.last + 1)) } }
        values.leaveFeet?.let { v ->
            val first = values.firstPuttFeet?.let { feet(note, it).firstOrNull() }
            feet(note, v).lastOrNull { it != first }?.let { add(NoteSpan(NoteField.LEAVE, it.first, it.last + 1)) }
        }
        values.teeShot?.let { t -> tee(note, t)?.let { add(NoteSpan(NoteField.TEE_SHOT, it.first, it.last + 1)) } }
        values.teeClub?.let { c -> Clubs.mentioned(note).firstOrNull { it.first == c }?.second?.let { add(NoteSpan(NoteField.TEE_CLUB, it.first, it.last + 1)) } }
        values.penalties?.takeIf { it > 0 }?.let { PENALTY_PHRASE.findAll(note).forEach { m -> add(NoteSpan(NoteField.PENALTIES, m.range.first, m.range.last + 1)) } }
        values.bunkerShots?.takeIf { it > 0 }?.let { clauses(note).firstOrNull { c -> NotePrompt.SAND.containsMatchIn(note.substring(c.first, c.last + 1)) }?.let { add(NoteSpan(NoteField.BUNKER_SHOTS, it.first, it.last + 1)) } }
    }.sortedBy { it.start }

    /** True when [v] appears in the note as a score: a bare number (not "8 ft", "9 iron") or a named result. */
    fun mentionsScore(note: String, v: Int): Boolean = NUMBER.findAll(note).any { NotePrompt.numberOf(it.groupValues[1]) == v }

    /**
     * True when [v] appears in the note only as a distance ("to 8 ft"): a counted score that happens to match
     * a distance is far more likely the model grabbing the wrong number (seen from Nano) than a real count.
     */
    fun onlyAsDistance(note: String, v: Int): Boolean {
        val w = NotePrompt.WORDS.getOrNull(v - 1) ?: "$v"
        val any = Regex("""\b(?:$v|$w)\b""", RegexOption.IGNORE_CASE).findAll(note).toList()
        val asDistance = Regex("""\b(?:$v|$w)\s*(?:-|\s)?(?:ft|feet|foot|footer|'|yd|yds|yards?|m\b|metres?|meters?)""", RegexOption.IGNORE_CASE).findAll(note).count()
        return any.isNotEmpty() && asDistance == any.size
    }

    /** True when a phrase about the putt says it missed that way ("missed it low", "raced it past"). */
    fun saysMiss(note: String, m: PuttMiss): Boolean = miss(note, m) != null

    private fun strokes(note: String, v: Int, par: Int): IntRange? {
        (NotePrompt.LEADING_SCORE.findAll(note) + NotePrompt.STATED_SCORE.findAll(note))
            .firstOrNull { NotePrompt.numberOf(it.groupValues[1]) == v }?.let { return it.range.trimmed(note) }
        (NotePrompt.LEADING_RESULT.findAll(note) + NotePrompt.STATED_RESULT.findAll(note))
            .firstOrNull { resultValue(it.groupValues[1], par) == v }?.let { return it.range.trimmed(note) }
        NUMBER.findAll(note).firstOrNull { NotePrompt.numberOf(it.groupValues[1]) == v }?.let { return it.groups[1]!!.range }
        return RESULT_WORD.findAll(note).firstOrNull { resultValue(it.value, par) == v }?.range
    }

    private fun putts(note: String, v: Int): IntRange? {
        val all = PUTT_PHRASE.findAll(note).toList()
        return (all.firstOrNull { m -> NUMBER.find(m.value)?.let { NotePrompt.numberOf(it.groupValues[1]) } == v } ?: all.firstOrNull())?.range
    }

    private fun feet(note: String, v: Int): List<IntRange> =
        Regex("""\b(?:$v|${NotePrompt.WORDS.getOrNull(v - 1) ?: "$v"})\s*(?:-|\s)?(?:feet|foot|ft|footer|')(?:\s+(?:away|past|by|short|long))?""", RegexOption.IGNORE_CASE)
            .findAll(note).map { it.range }.toList()

    private fun miss(note: String, m: PuttMiss): IntRange? {
        val words = MISS_WORDS.getValue(m)
        // From the first phrase about putting onwards: "3 putt from 20 feet, left it 8 feet short".
        val all = clauses(note)
        val from = all.indexOfFirst { PUTT_TALK.containsMatchIn(note.substring(it.first, it.last + 1)) }
        if (from < 0) return null
        return all.drop(from).firstNotNullOfOrNull { c ->
            words.find(note.substring(c.first, c.last + 1))?.range?.let { (it.first + c.first)..(it.last + c.first) }
        }
    }

    private fun tee(note: String, t: TeeShot): IntRange? {
        val direction = TEE_DIRECTION.getValue(t)
        val all = clauses(note)
        return all.firstNotNullOfOrNull { c ->
            val text = note.substring(c.first, c.last + 1)
            val d = direction.findAll(text).toList()
            if (d.isEmpty() || (!TEE_TALK.containsMatchIn(text) && c != all.first() && t != TeeShot.FAIRWAY)) return@firstNotNullOfOrNull null
            if (PUTT_TALK.containsMatchIn(text) && !TEE_TALK.containsMatchIn(text)) return@firstNotNullOfOrNull null
            val marks = (TEE_TALK.findAll(text) + d.asSequence()).map { it.range }.toList()
            (marks.minOf { it.first } + c.first)..(marks.maxOf { it.last } + c.first)
        }
    }

    /** The note split into phrases at punctuation and at "then"/"but", trimmed. */
    private fun clauses(note: String): List<IntRange> = Regex("""[^,.;!?]+""").findAll(note).flatMap { part ->
        var start = part.range.first
        val pieces = mutableListOf<IntRange>()
        Regex("""\b(?:and then|then|but)\b""", RegexOption.IGNORE_CASE).findAll(part.value).forEach { sep ->
            pieces += start until part.range.first + sep.range.first
            start = part.range.first + sep.range.last + 1
        }
        pieces += start..part.range.last
        pieces.asSequence()
    }.map { it.trimmed(note) }.filter { !it.isEmpty() }.toList()

    private fun IntRange.trimmed(note: String): IntRange {
        var a = first; var b = last
        while (a <= b && note[a].isWhitespace()) a++
        while (b >= a && note[b].isWhitespace()) b--
        return a..b
    }

    private fun resultValue(word: String, par: Int): Int? =
        NotePrompt.RESULTS[word.lowercase().replace(" bogey", "").replace("-", " ").trim()]?.let { par + it }

    private val i = RegexOption.IGNORE_CASE
    private val NUMBER = Regex("""\b${NotePrompt.NUM_PATTERN}\b${NotePrompt.NOT_A_DISTANCE}""", i)
    private val RESULT_WORD = Regex("""\b(?:albatross|eagle|birdie|par|bogey|double(?: bogey)?|triple(?: bogey)?|quad(?:ruple)?)\b(?!\s*\d)""", i)
    private val PUTT_PHRASE = Regex("""\b(?:(?:one|two|three|four|[1-4])[- ]?putt(?:s|ed|ing)?|putt(?:s|ed)?|chip(?:ped)? (?:it )?in|holed (?:it|out|the putt)|tap(?:ped)? (?:it )?in|drained it|sank it)\b""", i)
    private val PUTT_TALK = Regex("""putt|missed|lip|holed|first one|second one|green""", i)
    private val TEE_TALK = Regex("""\b(?:drive|drove|driver|tee shot|off the tee|first shot|tee|teed|sliced|hooked|pushed|pulled|blocked|3 wood|three wood)\b""", i)
    private val TEE_DIRECTION = mapOf(
        TeeShot.LEFT to Regex("""\b(?:left|hooked|pulled)\b""", i),
        TeeShot.RIGHT to Regex("""\b(?:right|sliced|pushed|blocked)\b""", i),
        TeeShot.FAIRWAY to Regex("""\b(?:fairway|middle|centre|center|short grass)\b""", i),
    )
    private val PENALTY_PHRASE = Regex(
        """\b(?:lost (?:a |my |the )?ball|out of bounds|ob|o\.b\.?|penalty(?: drop)?(?: (?:\d|one|two|three) (?:shots?|strokes?))?|(?:(?:\w+ balls? )?in(?:to)? the )?(?:water|lake|pond|ditch|stream|river|creek|drink)|unplayable|reload(?:ed)?|re-?teed?|stroke and distance)\b""", i,
    )
    private val MISS_WORDS = mapOf(
        PuttMiss.SHORT to Regex("""\b(?:short|came up|didn't get there|never got there)\b""", i),
        PuttMiss.LONG to Regex("""\b(?:long|past|raced|ran it|by)\b""", i),
        PuttMiss.LEFT to Regex("""\b(?:left|pulled)\b""", i),
        PuttMiss.RIGHT to Regex("""\b(?:right|pushed)\b""", i),
        PuttMiss.LOW to Regex("""\blow\b""", i),
        PuttMiss.HIGH to Regex("""\bhigh\b""", i),
        PuttMiss.LIPPED to Regex("""\blip(?:ped|s)?(?: out)?\b""", i),
    )
}
