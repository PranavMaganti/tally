package me.vanpra.tally.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.vanpra.tally.round.PuttMiss
import me.vanpra.tally.round.ReadBy
import me.vanpra.tally.round.TeeShot

/** What we know about the hole the note is about. */
data class HoleContext(val number: Int, val par: Int)

/** The card fields a note mentions. Null means the note doesn't say, never "zero". */
@kotlinx.serialization.Serializable
data class NoteReading(
    val strokes: Int? = null,
    val putts: Int? = null,
    val teeShot: TeeShot? = null,
    val penalties: Int? = null,
    val bunkerShots: Int? = null,
    val firstPuttFeet: Int? = null,
    val puttMiss: PuttMiss? = null,
    val leaveFeet: Int? = null,
    /** The club off the tee, in short form ("Driver", "3W", "5i"). */
    val teeClub: String? = null,
    /** The note says the hole wasn't finished or isn't to be counted ("picked up", "no score"). */
    val pickedUp: Boolean? = null,
) {
    val isEmpty: Boolean get() = this == EMPTY

    /** Fills gaps in this reading with [other]; this reading wins where both have a value. */
    infix fun over(other: NoteReading) = NoteReading(
        strokes = strokes ?: other.strokes,
        putts = putts ?: other.putts,
        teeShot = teeShot ?: other.teeShot,
        penalties = penalties ?: other.penalties,
        bunkerShots = bunkerShots ?: other.bunkerShots,
        firstPuttFeet = firstPuttFeet ?: other.firstPuttFeet,
        puttMiss = puttMiss ?: other.puttMiss,
        leaveFeet = leaveFeet ?: other.leaveFeet,
        teeClub = teeClub ?: other.teeClub,
        pickedUp = pickedUp ?: other.pickedUp,
    )

    companion object { val EMPTY = NoteReading() }
}

/** The fields a note can fill. The names are the keys for saved field sources and note underlines. */
object NoteField {
    const val STROKES = "strokes"
    const val PUTTS = "putts"
    const val TEE_SHOT = "teeShot"
    const val PENALTIES = "penalties"
    const val BUNKER_SHOTS = "bunkerShots"
    const val FIRST_PUTT = "firstPuttFeet"
    const val PUTT_MISS = "firstPuttMiss"
    const val LEAVE = "leaveFeet"
    const val TEE_CLUB = "teeClub"
}

sealed interface ReaderState {
    /** No on-device model on this phone (or it failed); notes are still saved, just not read. */
    data object Unavailable : ReaderState
    /** The model is being downloaded or loaded. [progress] is 0..1 when known. */
    data class Preparing(val progress: Float? = null) : ReaderState
    data object Ready : ReaderState
}

/** Reads a free-text hole note into card fields, on the device. */
interface NoteReader {
    val state: StateFlow<ReaderState>
    /** Cheap: checks whether a model exists and starts a download if needed. Safe to call often. */
    fun prepare() {}
    /** Loads the model into memory ahead of use, so the first read is quick. Call when a note might be written. */
    fun warmUp() {}
    /** Null when the note couldn't be read (no model, error); [NoteReading.EMPTY] when it says nothing usable. */
    suspend fun read(note: String, hole: HoleContext): NoteReading?

    /** Who this reader is, for marking saved holes. */
    val by: ReadBy get() = ReadBy.ON_DEVICE

    /**
     * Readings of [note] as they arrive, best last: a combined reader can show a quick on-device reading
     * and then replace it with a better one.
     */
    fun reads(note: String, hole: HoleContext): Flow<NoteRead> = flow { read(note, hole)?.let { emit(NoteRead(it, by)) } }

    /** A quick reading while you're still typing or talking: on the phone, so it's instant and free. */
    suspend fun draftRead(note: String, hole: HoleContext): NoteRead? = read(note, hole)?.let { NoteRead(it, by) }

    /** The best reading once you've finished, from a stronger reader if there is one; null when there isn't. */
    suspend fun finalRead(note: String, hole: HoleContext): NoteRead? = null
}

/** A reading and who produced it. */
data class NoteRead(val reading: NoteReading, val by: ReadBy)

object NoNoteReader : NoteReader {
    override val state: StateFlow<ReaderState> = MutableStateFlow(ReaderState.Unavailable).asStateFlow()
    override suspend fun read(note: String, hole: HoleContext): NoteReading? = null
}
