package me.vanpra.tally.ai

import me.vanpra.tally.round.FieldSource
import me.vanpra.tally.round.HoleEntryForm
import me.vanpra.tally.round.HoleScore
import me.vanpra.tally.round.ReadBy
import me.vanpra.tally.round.Round
import me.vanpra.tally.round.RoundSource

/**
 * Re-reading saved notes with a better reader. Nothing changes without your OK: a disagreement
 * becomes a suggestion on the hole. Only fields that came from the note (or were never set) can be
 * suggested; anything you entered yourself is left alone.
 */
object NoteReview {

    /** Holes whose note was read on the device or only instantly, and that have nothing waiting already. */
    fun needsReread(round: Round): List<HoleScore> =
        if (round.source != RoundSource.TALLY) emptyList()
        else round.holes.filter { h ->
            h.strokes != null && h.suggested == null && h.noteReadBy != ReadBy.CLOUD && h.notes.lastOrNull()?.text?.isNotBlank() == true
        }

    /**
     * What a better reading would change on [hole], or null when it agrees. Never suggests clearing a value,
     * only replacing or filling one.
     */
    fun suggestion(hole: HoleScore, better: NoteReading): NoteReading? {
        fun open(key: String) = when (hole.fieldSources[key] ?: hole.fieldSources["all"]) {
            FieldSource.AI, FieldSource.DEFAULT, null -> true
            else -> false
        }
        fun <T> pick(key: String, now: T?, better: T?): T? = better?.takeIf { open(key) && it != now }
        val s = NoteReading(
            strokes = pick(HoleEntryForm.STROKES, hole.strokes, better.strokes),
            putts = pick(HoleEntryForm.PUTTS, hole.putts, better.putts),
            teeShot = if ((hole.par ?: 4) >= 4) pick(HoleEntryForm.TEE_SHOT, hole.teeShot, better.teeShot) else null,
            teeClub = pick(HoleEntryForm.TEE_CLUB, hole.teeClub, better.teeClub),
            penalties = pick(HoleEntryForm.PENALTIES, hole.penalties, better.penalties),
            bunkerShots = pick(HoleEntryForm.BUNKER_SHOTS, hole.bunkerShots, better.bunkerShots),
            firstPuttFeet = pick(HoleEntryForm.FIRST_PUTT, hole.firstPuttFeet, better.firstPuttFeet),
            puttMiss = pick(HoleEntryForm.PUTT_MISS, hole.firstPuttMiss, better.puttMiss),
            leaveFeet = pick(HoleEntryForm.LEAVE, hole.leaveFeet, better.leaveFeet),
        )
        return s.takeIf { !it.isEmpty }
    }

    /** Applies [fields] of the hole's suggestion (all of them by default) and clears it. */
    fun accept(hole: HoleScore, fields: Set<String>? = null): HoleScore {
        val s = hole.suggested ?: return hole
        fun has(key: String) = fields == null || key in fields
        val sources = hole.fieldSources.toMutableMap()
        fun mark(key: String, present: Boolean) { if (present && has(key)) sources[key] = FieldSource.AI }
        mark(HoleEntryForm.STROKES, s.strokes != null); mark(HoleEntryForm.PUTTS, s.putts != null)
        mark(HoleEntryForm.TEE_SHOT, s.teeShot != null); mark(HoleEntryForm.PENALTIES, s.penalties != null)
        mark(HoleEntryForm.BUNKER_SHOTS, s.bunkerShots != null); mark(HoleEntryForm.FIRST_PUTT, s.firstPuttFeet != null)
        mark(HoleEntryForm.PUTT_MISS, s.puttMiss != null); mark(HoleEntryForm.LEAVE, s.leaveFeet != null)
        val strokes = if (has(HoleEntryForm.STROKES)) s.strokes ?: hole.strokes else hole.strokes
        val updated = hole.copy(
            strokes = strokes,
            putts = (if (has(HoleEntryForm.PUTTS)) s.putts else null) ?: hole.putts,
            teeShot = (if (has(HoleEntryForm.TEE_SHOT)) s.teeShot else null) ?: hole.teeShot,
            penalties = (if (has(HoleEntryForm.PENALTIES)) s.penalties else null) ?: hole.penalties,
            bunkerShots = (if (has(HoleEntryForm.BUNKER_SHOTS)) s.bunkerShots else null) ?: hole.bunkerShots,
            firstPuttFeet = (if (has(HoleEntryForm.FIRST_PUTT)) s.firstPuttFeet else null) ?: hole.firstPuttFeet,
            firstPuttMiss = (if (has(HoleEntryForm.PUTT_MISS)) s.puttMiss else null) ?: hole.firstPuttMiss,
            leaveFeet = (if (has(HoleEntryForm.LEAVE)) s.leaveFeet else null) ?: hole.leaveFeet,
            fieldSources = sources,
            noteReadBy = ReadBy.CLOUD,
            suggested = null,
        )
        // Keep the card possible: the tee shot plus putts, penalties and bunker shots can't exceed the score.
        val st = updated.strokes ?: return updated
        val over = 1 + (updated.putts ?: 0) + (updated.penalties ?: 0) + (updated.bunkerShots ?: 0) - st
        return if (over > 0) updated.copy(putts = ((updated.putts ?: 0) - over).coerceAtLeast(0)) else updated
    }

    /** Keeps the hole as it is; the note won't be suggested again. */
    fun keep(hole: HoleScore): HoleScore = hole.copy(suggested = null, noteReadBy = ReadBy.CLOUD)

    /** Each suggested change as (field, before, after) for showing. */
    fun changes(hole: HoleScore): List<Triple<String, Any?, Any>> {
        val s = hole.suggested ?: return emptyList()
        return listOfNotNull(
            s.strokes?.let { Triple(HoleEntryForm.STROKES, hole.strokes, it) },
            s.putts?.let { Triple(HoleEntryForm.PUTTS, hole.putts, it) },
            s.teeShot?.let { Triple(HoleEntryForm.TEE_SHOT, hole.teeShot, it) },
            s.penalties?.let { Triple(HoleEntryForm.PENALTIES, hole.penalties, it) },
            s.bunkerShots?.let { Triple(HoleEntryForm.BUNKER_SHOTS, hole.bunkerShots, it) },
            s.firstPuttFeet?.let { Triple(HoleEntryForm.FIRST_PUTT, hole.firstPuttFeet, it) },
            s.puttMiss?.let { Triple(HoleEntryForm.PUTT_MISS, hole.firstPuttMiss, it) },
            s.leaveFeet?.let { Triple(HoleEntryForm.LEAVE, hole.leaveFeet, it) },
        )
    }
}
