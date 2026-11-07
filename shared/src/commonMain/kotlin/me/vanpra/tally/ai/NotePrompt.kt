package me.vanpra.tally.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import me.vanpra.tally.round.PuttMiss
import me.vanpra.tally.round.TeeShot

/**
 * The prompt and the parsing around it, shared by every on-device model.
 *
 * Small models are good at reading golf talk but shaky at arithmetic and prone to filling every
 * field, so: named scores ("bogey") come back as words and are turned into numbers here, every
 * value is range-checked, and anything the note doesn't mention must come back null.
 */
object NotePrompt {

    val system: String = """
        Fill in a golfer's scorecard from their note about one hole. Reply with JSON only. Leave out anything the note doesn't say or make certain.

        score: strokes on the hole ("made 7", or "6," at the start).
        shots: only when there's no score: if the note describes every shot from the tee until the ball is holed, list them in order, a few words each (each putt separately). Otherwise leave it out.
        result: the score's name when it isn't a number: albatross, eagle, birdie, par, bogey, double, triple.
        putts: count every putt. "two putts" = 2, "holed it" = 1, chipped in or holed out = 0. Follow the story: "putted from 20 ft to 8 ft, then two from there" is a first putt of 20, leaving 8, then 2 more = 3.
        tee: where the drive finished on a par 4 or 5: left, fairway or right. Pushed or sliced = right, pulled or hooked = left.
        tee_club: the club hit off the tee, as written ("driver", "3 wood", "5 iron").
        penalties: penalty strokes. A ball lost, out of bounds or in water is 1, however it's described (reloaded, re-teed, dropped), unless the note gives a number.
        bunker: shots played from bunkers ("two to get out" = 2).
        first_putt_ft: length of the first putt in feet.
        miss: how the first putt missed: short, long, left, right, low, high or lipped.
        leave_ft: feet left after a missed first putt.

        If the score is given and the note accounts for every other shot, the rest are putts.
        Dictated notes have speech mistakes: "T-shirt" means tee shot, "pot" or "part" means putt.

        Par 4. Note: 8, lost a ball right off the tee, penalty drop 2 shots, iron to 10 feet but missed the putt low
        {"score":8,"putts":2,"tee":"right","penalties":2,"first_putt_ft":10,"miss":"low"}
        Par 4. Note: drive went out of bounds right, re-teed and made 6
        {"score":6,"tee":"right","penalties":1}
        Par 4. Note: par. drive and a wedge to 10 feet
        {"result":"par","putts":2,"first_putt_ft":10}
        Par 5. Note: 3 wood, 5 iron, wedge to 12 feet, two putts
        {"shots":["3 wood","5 iron","wedge to 12 feet","putt","putt"],"putts":2,"tee_club":"3 wood","first_putt_ft":12}
        Par 5. Note: 7, three putt from 40 feet, left the first one 6 feet short
        {"score":7,"putts":3,"first_putt_ft":40,"miss":"short","leave_ft":6}
        Par 4. Note: bogey, hooked it into the trees, chipped in from the rough
        {"result":"bogey","tee":"left","putts":0}
        Par 3. Note: bogey. greenside bunker, out first go, two putts
        {"result":"bogey","bunker":1,"putts":2}
        Par 4. Note: really windy today
        {}
    """.trimIndent()

    /** Cloud models get the same prompt, so what works for one carries to the other. */
    val cloudSystem: String get() = system

    fun user(note: String, hole: HoleContext): String = "Par ${hole.par}. Note: ${note.trim().replace('\n', ' ')}"

    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    /** Turns the model's reply into a reading, discarding anything malformed or out of range. */
    fun parse(reply: String?, hole: HoleContext, note: String? = null): NoteReading? {
        val text = reply ?: return null
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val obj = runCatching { json.parseToJsonElement(text.substring(start, end + 1)) as? JsonObject }.getOrNull() ?: return null

        // The number has to be in the note as a score, not as "8 ft" or "9 iron" (seen: "to 8 ft" read as score 8).
        // A number has to be in the note as a score; a score worked out from the shots comes from "shots" below.
        val numbered = obj.int("score")?.takeIf {
            it in 1..MAX_STROKES && (note == null || NoteEvidence.mentionsScore(note, it))
        }
        // A named result has to be named in the note (seen: Nano inventing a 4 for "duffed my chip, then chipped in").
        val named = obj.word("result")?.let { RESULTS[it.replace(" bogey", "").replace("-", " ").trim()] }?.let { hole.par + it }
            ?.takeIf { it in 1..MAX_STROKES && (note == null || RESULT_NAMED.containsMatchIn(note)) }
        // "birdie! 9 iron to 4 feet" once came back as score 4: when a named result disagrees, the name wins.
        val score = if (named != null && note != null && (STATED_RESULT.containsMatchIn(note) || LEADING_RESULT.containsMatchIn(note))) named else numbered ?: named
        // Small models sometimes fill a field from the wrong words ("two to get out" as penalties), so a
        // count is only kept when the note talks about that kind of thing at all.
        fun mentions(r: Regex) = note == null || r.containsMatchIn(note)
        // Putts can also follow from the score when the note accounts for the other shots ("birdie, 9 iron to 4 feet").
        val putts = obj.int("putts")?.takeIf {
            it in 0..6 && (score == null || it < score) && (mentions(PUTTING) || (score != null && mentions(DISTANCE))) &&
                // No putts only when holed from off the green; a missed putt means at least two.
                (it != 0 || note == null || HOLED_FROM_OFF.containsMatchIn(note)) &&
                (it != 1 || note == null || !MISSED_PUTT.containsMatchIn(note))
        }
        val tee = if (hole.par < 4) null else when (obj.word("tee")) {
            "left" -> TeeShot.LEFT
            "fairway", "middle", "centre", "center" -> TeeShot.FAIRWAY
            "right" -> TeeShot.RIGHT
            else -> null
        }
        val penalties = obj.int("penalties")?.takeIf { it in 0..6 && (score == null || it < score) && (it == 0 || mentions(PENALTY)) }
        val bunker = obj.int("bunker")?.takeIf { it in 0..6 && (score == null || it < score) && (it == 0 || mentions(SAND)) }
        // Putting details only make sense with putts to have them: one putt was holed, so it had no miss.
        val first = obj.int("first_putt_ft")?.takeIf { it in 1..150 && putts != 0 && mentions(DISTANCE) }
        val miss = obj.word("miss")?.let { w -> PuttMiss.entries.firstOrNull { it.name.equals(w, ignoreCase = true) } ?: if (w.startsWith("lip")) PuttMiss.LIPPED else null }
            ?.takeIf { putts == null || putts >= 2 }
        val leave = obj.int("leave_ft")?.takeIf { it in 1..60 && (putts == null || putts >= 2) && mentions(DISTANCE) }

        // No score given but every shot listed: the score is the count, plus penalty strokes.
        // Every listed shot has to be in the note, or the count is a guess (seen: an approach shot made up
        // for "3 wood left into the trees, two putts").
        val shots = (obj["shots"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }
        val counted = shots?.takeIf { it.isNotEmpty() && (note == null || it.all { s -> grounded(s, note) }) }
            ?.size?.let { it + (penalties ?: 0) }
            ?.takeIf { it in 1..MAX_STROKES && (putts == null || it > putts) }
        // The tee club has to be a club the note names.
        val club = Clubs.normalize(obj.word("tee_club"))?.takeIf { c -> note == null || Clubs.mentioned(note).any { it.first == c } }
        return NoteReading(score ?: counted, putts, tee, penalties, bunker, first, miss?.takeIf { note == null || NoteEvidence.saysMiss(note, it) }, leave, club)
    }

    /**
     * The one thing worth reading without a model: a note that starts with the score ("8, lost a ball…").
     * Instant, so the score lights up as you type it. "7 iron off the tee" is not a score.
     */
    fun quickRead(note: String, par: Int? = null): NoteReading = quickScore(note, par).copy(
        putts = if (HOLED_FROM_OFF.containsMatchIn(note)) 0 else null,
        teeShot = if (par == null || par >= 4) shapeOfDrive(note) else null,
        teeClub = clubInFirstPhrase(note),
        pickedUp = true.takeIf { PICKED_UP.containsMatchIn(note) },
    )

    /** "picked up", "didn't finish", "no score", "NR": the hole's done but not scored. */
    private val PICKED_UP = Regex("""\b(?:picked (?:it )?up|pick(?:ed)? up|didn'?t finish|did not finish|gave up|no score|not counting|don'?t count|nr)\b""", RegexOption.IGNORE_CASE)

    /** "5 iron right, …", "pulled the driver OB, …", "7 iron to the back of the green": the first phrase's club is the tee club. */
    private fun clubInFirstPhrase(note: String): String? {
        val first = Regex("""^[^,.;!?]*""").find(note.trim())?.value?.split(Regex("""\b(?:then|and then|but)\b""", RegexOption.IGNORE_CASE))?.firstOrNull().orEmpty()
        val club = Clubs.mentioned(first).firstOrNull() ?: return null
        // Only if no other shot comes first ("Gotta 6 drove it right … nine iron onto the green" isn't a 9 iron tee shot).
        val otherShot = OTHER_SHOT.find(first)?.range?.first ?: Int.MAX_VALUE
        return club.first.takeIf { club.second.first < otherShot }
    }

    private val OTHER_SHOT = Regex("""\b(?:drove|drive|chip\w*|pitch\w*|punch\w*|putt\w*|second|approach|onto|on to)\b""", RegexOption.IGNORE_CASE)

    /**
     * "sliced it into the trees" is right and "hooked it" is left, which small models sometimes flip.
     * Only when the note uses one kind of shape and doesn't name a side itself.
     */
    private fun shapeOfDrive(note: String): me.vanpra.tally.round.TeeShot? {
        if (SIDE_NAMED.containsMatchIn(note)) return null
        val right = WENT_RIGHT.containsMatchIn(note)
        val left = WENT_LEFT.containsMatchIn(note)
        return when {
            right && !left -> me.vanpra.tally.round.TeeShot.RIGHT
            left && !right -> me.vanpra.tally.round.TeeShot.LEFT
            else -> null
        }
    }

    private val SIDE_NAMED = Regex("""\b(?:left|right|fairway|middle)\b""", RegexOption.IGNORE_CASE)
    private val WENT_RIGHT = Regex("""\b(?:sliced?|slicing|pushed|blocked)\b""", RegexOption.IGNORE_CASE)
    private val WENT_LEFT = Regex("""\b(?:hooked?|hooking|pulled|snap(?:ped)? hooked?)\b""", RegexOption.IGNORE_CASE)
    private val RESULT_NAMED = Regex("""\b(?:albatross|eagle|birdie|par|bogey|bogie|double|triple|quad\w*)\b(?!\s*\d)""", RegexOption.IGNORE_CASE)
    private val MISSED_PUTT = Regex("""\bmissed (?:the |my |a |that |first |short )?(?:\w+ )?putt\b|\bthe putt (?:missed|lipped|horseshoed)\b""", RegexOption.IGNORE_CASE)

    private val FILLER = setOf("the", "a", "an", "to", "of", "it", "shot", "ball", "onto", "on", "in", "into", "from", "and", "then", "feet", "ft", "foot", "yards", "yard", "yd", "my", "first", "second", "third", "over", "out", "short", "long", "left", "right")

    /** A listed shot shares a real word with the note ("chipped out" matches "chipped out to about 20 feet"; stems compared). */
    private fun grounded(shot: String, note: String): Boolean {
        fun stems(s: String) = Regex("""[a-z0-9]+""").findAll(s.lowercase()).map { it.value }.filter { it !in FILLER }.map { it.take(4) }.toSet()
        val words = stems(shot)
        return words.isEmpty() || words.any { it in stems(note) } || (words.any { it.startsWith("putt") } && PUTTING.containsMatchIn(note))
    }

    /** "chipped in", "holed out from the bunker": no putts. Not "chipped in to 10 feet". */
    private val HOLED_FROM_OFF = Regex("""\b(?:chip(?:ped)? (?:it )?in|holed (?:it )?out|hole[ds]? out|pitched in|holed (?:my|the) (?:chip|pitch|bunker shot|approach))\b(?!\s+to\b)""", RegexOption.IGNORE_CASE)

    private fun quickScore(note: String, par: Int?): NoteReading {
        val m = LEADING_SCORE.find(note) ?: STATED_SCORE.findAll(note).lastOrNull()
        val number = m?.groupValues?.get(1)?.let(::numberOf)
        if (m != null && number != null && number in 1..MAX_STROKES) return NoteReading(strokes = number)
        if (par == null) return NoteReading.EMPTY
        val r = LEADING_RESULT.find(note) ?: STATED_RESULT.findAll(note).lastOrNull() ?: return NoteReading.EMPTY
        val diff = RESULTS[r.groupValues[1].lowercase().replace(" bogey", "").replace("-", " ").trim()] ?: return NoteReading.EMPTY
        val strokes = (par + diff).takeIf { it in 1..MAX_STROKES } ?: return NoteReading.EMPTY
        return NoteReading(strokes = strokes)
    }

    // Speech often comes back as words: "six, hit it left".
    internal val WORDS = listOf("one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve")
    internal val NUM_PATTERN = """(\d{1,2}|${WORDS.joinToString("|")})"""
    private val NUM = NUM_PATTERN
    internal fun numberOf(s: String): Int? = s.toIntOrNull() ?: WORDS.indexOf(s.lowercase()).takeIf { it >= 0 }?.plus(1)

    internal val PUTTING = Regex("""putt|(?:one|two|three|four|\d)[- ]?puts?\b|(?:one|two|three|four|\d)[- ]?(?:pot|pots|part|parts)\b|bad (?:pot|part)\b|holed|hole[ds]? out|chip(?:ped)? in|drain|sank|sink|tap(?:ped)? in|lip|knocked it in|rolled it in|jar""", RegexOption.IGNORE_CASE)
    internal val PENALTY = Regex("""penalt|drop|lost|\bob\b|o\.b|out of bounds|water|lake|pond|hazard|ditch|stream|river|creek|unplayable|reload|re-tee|stroke and distance|provisional|in the drink|wet""", RegexOption.IGNORE_CASE)
    private val DISTANCE = Regex("""\d\s*(?:ft|')|feet|foot|footer|yards?|yds?|metres?|meters?|inch""", RegexOption.IGNORE_CASE)
    internal val SAND = Regex("""bunker|sand|trap|beach|splash""", RegexOption.IGNORE_CASE)

    internal const val NOT_A_DISTANCE = """(?!\s*(?:-|\s)?(?:ft|feet|foot|yd|yds|yards?|m\b|metres?|meters?|iron|i\b|wood|w\b|hybrid|h\b|rescue|putts?|putted|pot|pots|potted|part|parts|footer|%|th|st|nd|rd|/|\.\d|\d))"""
    private const val NAMES = """(albatross|double eagle|eagle|birdie|par|bogey|bogie|double(?: bogey)?|triple(?: bogey)?|quad(?:ruple)?(?: bogey)?)"""

    /** "made 7", "for a 4", "scored 6", "carded a 5". Not "took 3 to get out". */
    internal val STATED_SCORE = Regex("""\b(?:(?:made|for|scored|carded|card)\s+(?:an?\s+)?|(?:got|had|took|shot)\s+an?\s+|gotta\s+)$NUM\b$NOT_A_DISTANCE""", RegexOption.IGNORE_CASE)
    internal val LEADING_RESULT = Regex("""^\s*$NAMES\b(?!\s*(?:putt|chance|look|try|attempt|\d))""", RegexOption.IGNORE_CASE)
    /** "for par", "made bogey", "for a double". "par 5" or "a birdie putt" are not results. */
    internal val STATED_RESULT = Regex("""\b(?:made|for|carded|scored)\s+(?:an?\s+)?$NAMES\b(?!\s*(?:putt|chance|look|try|attempt|\d))""", RegexOption.IGNORE_CASE)

    internal val LEADING_SCORE = Regex(
        """^\s*$NUM$NOT_A_DISTANCE(?=\s|[,.;:!)-]|$)""",
        RegexOption.IGNORE_CASE,
    )

    internal val RESULTS = mapOf(
        "albatross" to -3, "double eagle" to -3, "eagle" to -2, "birdie" to -1, "par" to 0,
        "bogey" to 1, "bogie" to 1, "double" to 2, "triple" to 3, "quad" to 4, "quadruple" to 4,
    )

    private const val MAX_STROKES = 15

    private fun JsonObject.int(key: String): Int? = when (val v: JsonElement? = this[key]) {
        null, JsonNull -> null
        is JsonPrimitive -> v.intOrNull ?: v.content.trim().toIntOrNull()
        else -> null
    }

    private fun JsonObject.word(key: String): String? = when (val v = this[key]) {
        null, JsonNull -> null
        is JsonPrimitive -> v.content.trim().lowercase().takeIf { it.isNotEmpty() && it != "null" }
        else -> null
    }
}
