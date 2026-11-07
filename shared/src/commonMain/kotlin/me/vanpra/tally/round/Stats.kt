package me.vanpra.tally.round

import kotlin.math.abs
import kotlin.math.roundToInt

/** Headline numbers over a set of rounds. Percentages are 0–100. Null means not enough data. */
data class GameStats(
    val rounds: Int,
    val fullRounds: Int,
    val scoringAverage: Double?,
    val averageToPar: Double?,
    val puttsPerRound: Double?,
    val fairwayPct: Double?,
    val missLeftPct: Double?,
    val missRightPct: Double?,
    val teeShots: Int,
    val girPct: Double?,
    val penaltiesPerRound: Double?,
    val threePuttsPerRound: Double?,
    val par3: Double?,
    val par4: Double?,
    val par5: Double?,
    /** Oldest → newest scores of full rounds, for a trend line. */
    val recentScores: List<Int>,
    val insight: String?,
)

object Stats {
    fun of(rounds: List<Round>): GameStats {
        val holes = rounds.flatMap { it.played }
        val full = rounds.filter { it.isFullRound }.sortedBy { it.date }
        val drives = holes.filter { (it.par ?: 0) >= 4 && it.teeShot != null }
        fun pct(n: Int, d: Int) = if (d == 0) null else n * 100.0 / d
        fun avgToPar(par: Int) = holes.filter { it.par == par && it.strokes != null }
            .takeIf { it.size >= 5 }?.map { it.strokes!! - par }?.average()

        val withPutts = full.filter { r -> r.played.all { it.putts != null } }
        val fairway = pct(drives.count { it.teeShot == TeeShot.FAIRWAY }, drives.size)
        val left = pct(drives.count { it.teeShot == TeeShot.LEFT }, drives.size)
        val right = pct(drives.count { it.teeShot == TeeShot.RIGHT }, drives.size)
        val girKnown = holes.mapNotNull { it.gir }
        val perRound = { total: Int -> if (full.isEmpty()) null else total.toDouble() / full.size }
        val fullHoles = full.flatMap { it.played }

        val s = GameStats(
            rounds = rounds.size,
            fullRounds = full.size,
            scoringAverage = full.takeIf { it.isNotEmpty() }?.map { it.strokes }?.average(),
            averageToPar = full.takeIf { it.isNotEmpty() }?.map { it.toPar }?.average(),
            puttsPerRound = withPutts.takeIf { it.isNotEmpty() }?.map { r -> r.played.sumOf { it.putts ?: 0 } }?.average(),
            fairwayPct = fairway,
            missLeftPct = left,
            missRightPct = right,
            teeShots = drives.size,
            girPct = pct(girKnown.count { it }, girKnown.size),
            penaltiesPerRound = perRound(fullHoles.sumOf { it.penalties ?: 0 }),
            threePuttsPerRound = perRound(fullHoles.count { (it.putts ?: 0) >= 3 }),
            par3 = avgToPar(3),
            par4 = avgToPar(4),
            par5 = avgToPar(5),
            recentScores = full.takeLast(12).map { it.strokes },
            insight = null,
        )
        return s.copy(insight = insight(s))
    }

    /** One plain sentence about where shots go. Picks the most striking pattern in the data. */
    private fun insight(s: GameStats): String? {
        if (s.fullRounds < 3) return null
        val l = s.missLeftPct ?: 0.0
        val r = s.missRightPct ?: 0.0
        val pens = s.penaltiesPerRound ?: 0.0
        val worstPar = listOfNotNull(s.par3?.let { 3 to it }, s.par4?.let { 4 to it }, s.par5?.let { 5 to it }).maxByOrNull { it.second }
        return when {
            r > 1.8 * l && r >= 20 -> "Your tee shots miss right ${(r / l.coerceAtLeast(1.0)).let { if (it >= 10) "far more often" else "${it.oneDecimal()}× as often" }} as left. ${r.roundToInt()}% of drives finish right of the fairway."
            l > 1.8 * r && l >= 20 -> "Your tee shots miss left ${(l / r.coerceAtLeast(1.0)).oneDecimal()}× as often as right. ${l.roundToInt()}% of drives finish left of the fairway."
            pens >= 1.5 -> "Penalties cost you ${pens.oneDecimal()} shots a round. Keeping the ball in play is the quickest saving."
            (s.threePuttsPerRound ?: 0.0) >= 2.0 -> "You three-putt ${s.threePuttsPerRound!!.oneDecimal()} times a round. Lag putting is the quickest saving."
            worstPar != null -> "Par ${worstPar.first}s are your toughest holes, averaging ${formatAvg(worstPar.second)} each."
            else -> null
        }
    }
}

fun Double.oneDecimal(): String {
    val v = (this * 10).roundToInt()
    return "${v / 10}.${abs(v % 10)}"
}

/** +0.4, −0.2, E */
fun formatAvg(toPar: Double): String {
    val v = (toPar * 10).roundToInt()
    return when {
        v == 0 -> "E"
        v > 0 -> "+${(v / 10.0).let { (it * 10).roundToInt() / 10.0 }}"
        else -> "−${(-v / 10.0)}"
    }
}

/** Average score on a given hole of a course across past rounds. */
fun holeAverage(rounds: List<Round>, courseName: String, hole: Int): Pair<Double, Int>? {
    val scores = rounds.filter { sameCourse(it.courseName, courseName) }.mapNotNull { r -> r.hole(hole)?.strokes }
    return if (scores.size < 2) null else scores.average() to scores.size
}

fun sameCourse(a: String, b: String): Boolean {
    fun key(s: String) = s.lowercase().replace("golf club", "").replace("golf & country club", "").replace(Regex("[^a-z]"), "")
    return key(a) == key(b) || key(a).startsWith(key(b)) || key(b).startsWith(key(a))
}

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** "2026-10-03" → "3 Oct 2026" (year dropped when it's [currentYear]). */
fun displayDate(iso: String, currentYear: String? = null): String {
    val p = iso.split("-")
    if (p.size != 3) return iso
    val month = MONTHS.getOrNull((p[1].toIntOrNull() ?: 0) - 1) ?: return iso
    val day = p[2].toIntOrNull() ?: return iso
    return if (p[0] == currentYear) "$day $month" else "$day $month ${p[0]}"
}
