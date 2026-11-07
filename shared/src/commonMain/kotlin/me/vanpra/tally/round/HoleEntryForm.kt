package me.vanpra.tally.round

import me.vanpra.tally.ai.NoteField
import me.vanpra.tally.ai.NoteReading

/** Where a field's current value came from. */
enum class Origin {
    /** Nothing set yet: the starting value (or what was saved before). */
    BASE,
    /** Filled in from the note. */
    NOTE,
    /** Tapped by you. */
    YOU,
}

/** A form field: its value, where that came from, and what to fall back to if the note stops mentioning it. */
data class Field<T>(val value: T, val origin: Origin = Origin.BASE, val base: T = value, val baseOrigin: Origin = origin) {
    fun tapped(v: T) = copy(value = v, origin = Origin.YOU)

    /**
     * The latest thing said or tapped wins. The note only touches a field when what it says about that field
     * changes, so a field you tapped stays put while you keep writing about other things. Rewrite the
     * note ("actually a 6") and the note wins again. If the note stops mentioning it, the field goes back.
     */
    fun noted(now: T?, before: T?): Field<T> = when {
        now == before -> this
        // If the note overrides something you tapped, your tap becomes the fallback.
        now != null && origin == Origin.YOU -> copy(value = now, origin = Origin.NOTE, base = value, baseOrigin = Origin.YOU)
        now != null -> copy(value = now, origin = Origin.NOTE)
        origin == Origin.NOTE -> copy(value = base, origin = baseOrigin)
        else -> this
    }
}

/** The state of the hole entry sheet. Pure, so the merge rules can be tested. */
data class HoleEntryForm(
    val number: Int,
    val par: Int?,
    val strokes: Field<Int?>,
    val putts: Field<Int>,
    val teeShot: Field<TeeShot?>,
    val teeClub: Field<String?> = Field(null),
    /** Picked up / not counted: done without a score. */
    val noScore: Field<Boolean> = Field(false),
    val penalties: Field<Int>,
    val bunkerShots: Field<Int>,
    val firstPutt: Field<Int?> = Field(null),
    val puttMiss: Field<PuttMiss?> = Field(null),
    val leave: Field<Int?> = Field(null),
    /** Third and later putts' starting distances: only ever set by hand on the putting line. */
    val laterPutts: Field<List<Int>> = Field(emptyList()),
    /** The reading last applied, so the next one can tell what changed. */
    val reading: NoteReading = NoteReading.EMPTY,
) {
    val parOrDefault: Int get() = par ?: 4
    val canSave: Boolean get() = strokes.value != null || noScore.value
    val filledFromNote: List<String> get() = buildList {
        if (strokes.origin == Origin.NOTE) add(STROKES)
        if (putts.origin == Origin.NOTE) add(PUTTS)
        if (teeShot.origin == Origin.NOTE) add(TEE_SHOT)
        if (teeClub.origin == Origin.NOTE) add(TEE_CLUB)
        if (penalties.origin == Origin.NOTE) add(PENALTIES)
        if (bunkerShots.origin == Origin.NOTE) add(BUNKER_SHOTS)
        if (firstPutt.origin == Origin.NOTE) add(FIRST_PUTT)
        if (puttMiss.origin == Origin.NOTE) add(PUTT_MISS)
        if (leave.origin == Origin.NOTE) add(LEAVE)
    }

    fun origin(key: String): Origin = when (key) {
        STROKES -> strokes.origin; PUTTS -> putts.origin; TEE_SHOT -> teeShot.origin; TEE_CLUB -> teeClub.origin; PENALTIES -> penalties.origin
        BUNKER_SHOTS -> bunkerShots.origin; FIRST_PUTT -> firstPutt.origin; PUTT_MISS -> puttMiss.origin; else -> leave.origin
    }

    /** Just the values that came from the note, for finding their words in it. */
    fun fromNote(): NoteReading {
        fun <T> Field<T>.n(): T? = value.takeIf { origin == Origin.NOTE }
        return NoteReading(strokes.n(), putts.n(), teeShot.n(), penalties.n(), bunkerShots.n(), firstPutt.n(), puttMiss.n(), leave.n(), teeClub.n())
    }

    /** Putting details only apply with putts to have them: a one-putt was holed, so it had no miss. */
    val showsFirstPutt: Boolean get() = putts.value >= 1
    val showsMiss: Boolean get() = putts.value >= 2

    fun read(new: NoteReading): HoleEntryForm {
        val tee = if (parOrDefault >= 4) new.teeShot else null
        val next = copy(
            strokes = strokes.noted(new.strokes, this.reading.strokes),
            putts = putts.noted(new.putts, this.reading.putts),
            teeShot = teeShot.noted(tee, this.reading.teeShot),
            teeClub = teeClub.noted(new.teeClub, this.reading.teeClub),
            noScore = noScore.noted(new.pickedUp, this.reading.pickedUp),
            penalties = penalties.noted(new.penalties, this.reading.penalties),
            bunkerShots = bunkerShots.noted(new.bunkerShots, this.reading.bunkerShots),
            firstPutt = firstPutt.noted(new.firstPuttFeet, this.reading.firstPuttFeet),
            puttMiss = puttMiss.noted(new.puttMiss, this.reading.puttMiss),
            leave = leave.noted(new.leaveFeet, this.reading.leaveFeet),
            reading = new.copy(teeShot = tee),
        )
        return next.fit(keep = null)
    }

    /** What the note said when the sheet opened, so editing an old note doesn't undo fields you set by hand. */
    fun withBaseline(r: NoteReading) = copy(reading = r.copy(teeShot = if (parOrDefault >= 4) r.teeShot else null))

    fun tapStrokes(s: Int) = copy(strokes = strokes.tapped(s), noScore = noScore.tapped(false)).fit(keep = null)
    /** No score for this hole: clears the score; everything else you recorded is kept. */
    fun tapNoScore(on: Boolean) = if (on) copy(noScore = noScore.tapped(true), strokes = strokes.tapped(null)) else copy(noScore = noScore.tapped(false))
    fun tapPutts(p: Int) = copy(putts = putts.tapped(p)).fit(keep = PUTTS)
    fun tapTeeShot(t: TeeShot?) = copy(teeShot = teeShot.tapped(t))
    fun tapTeeClub(c: String?) = copy(teeClub = teeClub.tapped(c))
    fun tapPenalties(p: Int) = copy(penalties = penalties.tapped(p)).fit(keep = PENALTIES)
    fun tapBunkerShots(b: Int) = copy(bunkerShots = bunkerShots.tapped(b)).fit(keep = BUNKER_SHOTS)
    fun tapFirstPutt(feet: Int?) = copy(firstPutt = firstPutt.tapped(feet))
    fun tapPuttMiss(m: PuttMiss?) = copy(puttMiss = puttMiss.tapped(m))
    fun tapLeave(feet: Int?) = copy(leave = leave.tapped(feet))

    /** Where each putt started, in feet (null where not known), one entry per putt. */
    val puttFeet: List<Int?> get() = List(putts.value) { i ->
        when (i) { 0 -> firstPutt.value; 1 -> leave.value; else -> laterPutts.value.getOrNull(i - 2) }
    }

    /** Sets where putt [index] (0-based) started; null clears it. */
    fun tapPuttFeet(index: Int, feet: Int?): HoleEntryForm = when (index) {
        0 -> tapFirstPutt(feet)
        1 -> tapLeave(feet)
        else -> {
            val list = laterPutts.value.toMutableList()
            val i = index - 2
            if (feet == null) { if (i < list.size) list.subList(i, list.size).clear() }
            else { while (list.size < i) list += feet; if (i < list.size) list[i] = feet else list += feet }
            copy(laterPutts = laterPutts.tapped(list))
        }
    }

    fun clearPuttFeet() = copy(firstPutt = firstPutt.tapped(null), leave = leave.tapped(null), laterPutts = laterPutts.tapped(emptyList()))

    private fun count(key: String): Field<Int> = when (key) { PUTTS -> putts; PENALTIES -> penalties; else -> bunkerShots }
    private fun withCount(key: String, f: Field<Int>) = when (key) { PUTTS -> copy(putts = f); PENALTIES -> copy(penalties = f); else -> copy(bunkerShots = f) }

    /**
     * The most [key] can be: every hole starts with a tee shot, which is neither a putt nor a bunker shot,
     * so putts + penalties + bunker shots + 1 can't exceed the score. Null when there's no score yet.
     */
    fun maxFor(key: String): Int? = strokes.value?.let { s -> (s - 1 - COUNTS.filter { it != key }.sumOf { count(it).value }).coerceAtLeast(0) }

    /**
     * Makes the counts fit the score. What you just changed ([keep]) is never touched; defaults give way
     * first, then what the note said, and what you tapped last.
     */
    private fun fit(keep: String?): HoleEntryForm {
        // Start from what each count wants to be: anything trimmed earlier to make room comes back
        // once there's room again (typing "two putts" briefly reads "two" as the score).
        var form = this
        COUNTS.forEach { key ->
            val f = form.count(key)
            val wanted = when (f.origin) {
                Origin.BASE -> f.base
                Origin.NOTE -> reading.count(key) ?: f.value
                Origin.YOU -> f.value
            }
            if (wanted != f.value) form = form.withCount(key, f.copy(value = wanted))
        }
        val s = strokes.value ?: return form
        var over = 1 + COUNTS.sumOf { form.count(it).value } - s
        if (over <= 0) return form
        val order = COUNTS.filter { it != keep }.sortedBy { count(it).origin.giveWay } + listOfNotNull(keep)
        for (key in order) {
            val f = form.count(key)
            val cut = minOf(f.value, over)
            if (cut > 0) { form = form.withCount(key, f.copy(value = f.value - cut)); over -= cut }
            if (over <= 0) break
        }
        return form
    }

    private fun NoteReading.count(key: String): Int? = when (key) { PUTTS -> putts; PENALTIES -> penalties; else -> bunkerShots }

    private val Origin.giveWay: Int get() = when (this) { Origin.BASE -> 0; Origin.NOTE -> 1; Origin.YOU -> 2 }

    fun toHoleScore(existing: HoleScore?, notes: List<HoleNote>): HoleScore? {
        if (!canSave) return null
        // No score: the hole's done, but there's no stroke count to hold the other counts to.
        val s = strokes.value.takeIf { !noScore.value }
        fun src(f: Field<*>, key: String) = when (f.origin) {
            Origin.NOTE -> FieldSource.AI
            Origin.YOU -> FieldSource.MANUAL
            Origin.BASE -> existing?.fieldSources?.let { it[key] ?: it["all"] } ?: FieldSource.DEFAULT
        }
        return HoleScore(
            number = number,
            par = par,
            strokes = s,
            pickedUp = noScore.value,
            putts = if (s != null) putts.value.coerceAtMost(s) else putts.value,
            teeShot = if (parOrDefault >= 4) teeShot.value else null,
            penalties = penalties.value,
            bunkerShots = bunkerShots.value,
            teeClub = teeClub.value,
            firstPuttFeet = firstPutt.value.takeIf { showsFirstPutt },
            firstPuttMiss = puttMiss.value.takeIf { showsMiss },
            leaveFeet = leave.value.takeIf { showsMiss },
            laterPuttFeet = laterPutts.value.take((putts.value - 2).coerceAtLeast(0)),
            notes = notes,
            fieldSources = mapOf(
                STROKES to src(strokes, STROKES),
                PUTTS to src(putts, PUTTS),
                TEE_SHOT to src(teeShot, TEE_SHOT),
                TEE_CLUB to src(teeClub, TEE_CLUB),
                PENALTIES to src(penalties, PENALTIES),
                BUNKER_SHOTS to src(bunkerShots, BUNKER_SHOTS),
                FIRST_PUTT to src(firstPutt, FIRST_PUTT),
                PUTT_MISS to src(puttMiss, PUTT_MISS),
                LEAVE to src(leave, LEAVE),
            ),
        )
    }

    companion object {
        const val STROKES = NoteField.STROKES
        const val PUTTS = NoteField.PUTTS
        const val TEE_SHOT = NoteField.TEE_SHOT
        const val PENALTIES = NoteField.PENALTIES
        const val BUNKER_SHOTS = NoteField.BUNKER_SHOTS
        const val FIRST_PUTT = NoteField.FIRST_PUTT
        const val PUTT_MISS = NoteField.PUTT_MISS
        const val LEAVE = NoteField.LEAVE
        const val TEE_CLUB = NoteField.TEE_CLUB
        /** In the order they give way when they don't fit: putts default to 2, so they go first. */
        private val COUNTS = listOf(PUTTS, BUNKER_SHOTS, PENALTIES)

        fun start(number: Int, par: Int?, existing: HoleScore?): HoleEntryForm {
            val sources = existing?.fieldSources.orEmpty()
            fun origin(key: String) = when (sources[key] ?: sources["all"]) {
                FieldSource.AI -> Origin.NOTE
                null, FieldSource.DEFAULT -> Origin.BASE
                else -> Origin.YOU
            }
            fun <T> field(value: T, key: String): Field<T> =
                if (existing == null) Field(value) else Field(value, origin(key), value, origin(key))
            return HoleEntryForm(
                number = number,
                par = par,
                strokes = field(existing?.strokes, STROKES),
                putts = field(existing?.putts ?: 2, PUTTS),
                teeShot = field(existing?.teeShot, TEE_SHOT),
                teeClub = field(existing?.teeClub, TEE_CLUB),
                noScore = Field(existing?.pickedUp == true),
                penalties = field(existing?.penalties ?: 0, PENALTIES),
                bunkerShots = field(existing?.bunkerShots ?: 0, BUNKER_SHOTS),
                firstPutt = field(existing?.firstPuttFeet, FIRST_PUTT),
                puttMiss = field(existing?.firstPuttMiss, PUTT_MISS),
                leave = field(existing?.leaveFeet, LEAVE),
                laterPutts = Field(existing?.laterPuttFeet.orEmpty()),
            )
        }
    }
}
