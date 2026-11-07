package me.vanpra.tally.round

import kotlinx.serialization.Serializable

@Serializable
data class Round(
    val id: String,
    val courseName: String,
    /** Id of the mapped course this round was played on, if any (imported rounds may not have one). */
    val courseId: String? = null,
    val tee: String? = null,
    /** Local date the round was played, yyyy-MM-dd. */
    val date: String,
    val startedAtMillis: Long? = null,
    val finishedAtMillis: Long? = null,
    val source: RoundSource = RoundSource.TALLY,
    val holes: List<HoleScore> = emptyList(),
) {
    fun hole(number: Int): HoleScore? = holes.firstOrNull { it.number == number }
    /** Holes with a score: what totals and stats count. */
    val played: List<HoleScore> get() = holes.filter { it.strokes != null }
    /** Holes finished with: scored, or marked no score. What "thru" and moving through the round use. */
    val done: List<HoleScore> get() = holes.filter { it.isDone }
    val strokes: Int get() = played.sumOf { it.strokes ?: 0 }
    /** Score relative to par over the holes played so far. */
    val toPar: Int get() = played.sumOf { (it.strokes ?: 0) - (it.par ?: it.strokes ?: 0) }
    val isFinished: Boolean get() = finishedAtMillis != null || source != RoundSource.TALLY
    val isFullRound: Boolean get() = played.size >= 18
}

@Serializable
enum class RoundSource { TALLY, GOLFSHOT }

@Serializable
data class HoleScore(
    val number: Int,
    val par: Int? = null,
    val strokes: Int? = null,
    val putts: Int? = null,
    val teeShot: TeeShot? = null,
    val penalties: Int? = null,
    val bunkerShots: Int? = null,
    val teeClub: String? = null,
    /** Length of the first putt, in feet. */
    val firstPuttFeet: Int? = null,
    /** How the first putt missed, when it did. */
    val firstPuttMiss: PuttMiss? = null,
    /** How far the first putt finished from the hole, in feet, when it missed. */
    val leaveFeet: Int? = null,
    /** Picked up or not counted: no score, but the hole's done. Left out of totals and stats. */
    val pickedUp: Boolean = false,
    /** Where the third and later putts started, in feet, when you've set them. */
    val laterPuttFeet: List<Int> = emptyList(),
    /** Stored when known (imports); otherwise worked out from strokes and putts. */
    val girRecorded: Boolean? = null,
    /** Raw notes are the source of truth: structured fields can be re-read from them later. */
    val notes: List<HoleNote> = emptyList(),
    /** Where each field's value came from, so re-reading notes never overwrites what you set by hand. */
    val fieldSources: Map<String, FieldSource> = emptyMap(),
    /** Which reader last read the note. Notes not yet read in the cloud are re-read when back online. */
    val noteReadBy: ReadBy? = null,
    /** Changes a better reading of the note suggests, waiting for you to accept or keep. */
    val suggested: me.vanpra.tally.ai.NoteReading? = null,
    /** Where your ball was before each shot, as you marked it while playing. */
    val shots: List<ShotMark> = emptyList(),
) {
    val isDone: Boolean get() = strokes != null || pickedUp

    val gir: Boolean? get() = girRecorded ?: run {
        val s = strokes ?: return null
        val p = putts ?: return null
        val par = par ?: return null
        s - p <= par - 2
    }
}

@Serializable
enum class TeeShot { LEFT, FAIRWAY, RIGHT }

@Serializable
enum class PuttMiss { SHORT, LONG, LEFT, RIGHT, LOW, HIGH, LIPPED }

@Serializable
data class HoleNote(val text: String, val source: NoteSource, val createdAtMillis: Long)

@Serializable
enum class NoteSource { TYPED, VOICE }

@Serializable
enum class FieldSource {
    MANUAL, IMPORTED, AI,
    /** A starting value nobody set (e.g. 2 putts): a better reading of the note may change it. */
    DEFAULT,
}

/** Who read a hole's note into its fields. */
@Serializable
enum class ReadBy { INSTANT, ON_DEVICE, CLOUD }

fun scoreName(strokes: Int, par: Int): String = when (strokes - par) {
    in Int.MIN_VALUE..-3 -> "Albatross"
    -2 -> if (strokes == 1) "Ace" else "Eagle"
    -1 -> if (strokes == 1) "Ace" else "Birdie"
    0 -> "Par"
    1 -> "Bogey"
    2 -> "Double bogey"
    3 -> "Triple bogey"
    else -> "+${strokes - par}"
}

fun formatToPar(toPar: Int): String = when {
    toPar == 0 -> "E"
    toPar > 0 -> "+$toPar"
    else -> "−${-toPar}"
}
