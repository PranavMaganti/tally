package me.vanpra.tally.round

import me.vanpra.tally.ai.Clubs
import me.vanpra.tally.db.TallyDatabase

/** Which rounds the Stats screen looks at: the latest [window] (0 for all) on one course or every course. */
data class StatsFilter(val window: Int = 20, val course: String? = null)

/** A course you've played enough to look at on its own. */
data class PlayedCourse(val name: String, val rounds: Int)

/** Everything on the Stats screen. Rates are 0–100; "per 18" numbers are scaled from the holes played. */
data class StatsView(
    val rounds: Int,
    val holes: Int,
    /** Average score over complete 18-hole rounds; null when there aren't any. */
    val scoringAverage: Double?,
    val toParPer18: Double,
    val bestRound: Int?,
    /** Scores of complete rounds, oldest first, for the trend line. */
    val recent: List<Int>,
    val mix: ScoreMixView,
    val tee: TeeView?,
    val girPct: Double?,
    val scramblingPct: Double?,
    val puttsPerGir: Double?,
    val puttsPer18: Double?,
    val onePuttPct: Double?,
    val threePuttsPer18: Double?,
    val puttBands: List<PuttBand>,
    val penaltiesPer18: Double,
    val byPar: Map<Int, Double>,
    /** Hole by hole, when one course is picked. */
    val holeByHole: List<HoleAverage>,
    val insight: String?,
)

data class ScoreMixView(val underPar: Int, val pars: Int, val bogeys: Int, val doubles: Int, val worse: Int) {
    val holes: Int get() = underPar + pars + bogeys + doubles + worse
}

data class TeeView(val shots: Int, val fairwayPct: Double, val leftPct: Double, val rightPct: Double, val clubs: List<TeeClub>)
data class TeeClub(val club: String, val shots: Int, val fairwayPct: Double, val leftPct: Double, val rightPct: Double, val toPar: Double)

/** First putts from one distance band: how often they dropped and how often it took three. */
data class PuttBand(val label: String, val holes: Int, val holedPct: Double, val threePuttPct: Double)

data class HoleAverage(val number: Int, val par: Int?, val average: Double, val played: Int, val best: Int?) {
    val toPar: Double? get() = par?.let { average - it }
}

object StatsData {
    /** Fewer rounds than this on a course and it isn't offered as a filter. */
    private const val COURSE_MIN_ROUNDS = 3
    /** A round counts once this many holes are done; scoring average needs a full 18. */
    private const val MIN_HOLES = 9
    /** Clubs off the tee with fewer shots than this aren't listed. */
    private const val TEE_CLUB_MIN = 5
    private val BANDS = listOf("0–3 ft", "4–6 ft", "7–10 ft", "11–20 ft", "20+ ft")

    fun courses(history: List<Round>): List<PlayedCourse> =
        history.groupBy { r -> history.first { sameCourse(it.courseName, r.courseName) }.courseName }
            .map { (name, rs) -> PlayedCourse(name, rs.size) }
            .filter { it.rounds >= COURSE_MIN_ROUNDS }
            .sortedByDescending { it.rounds }

    /** The rounds a filter picks, newest first. */
    fun pick(history: List<Round>, filter: StatsFilter): List<Round> {
        val counted = history.filter { it.done.size >= MIN_HOLES && (filter.course == null || sameCourse(it.courseName, filter.course)) }
        return if (filter.window == 0) counted else counted.take(filter.window)
    }

    fun load(db: TallyDatabase, rounds: List<Round>, oneCourse: Boolean): StatsView {
        val q = db.statsQueries
        val ids = rounds.map { it.id }
        val full = rounds.filter { it.played.size >= 18 }
        val mix = q.scoreMix(ids).executeAsOne().let { ScoreMixView((it.under_par ?: 0).toInt(), (it.pars ?: 0).toInt(), (it.bogeys ?: 0).toInt(), (it.doubles ?: 0).toInt(), (it.worse ?: 0).toInt()) }
        val holes = mix.holes
        fun per18(n: Number) = if (holes == 0) 0.0 else n.toDouble() * 18 / holes
        fun pct(n: Number?, d: Number) = if (d.toDouble() == 0.0) null else (n?.toDouble() ?: 0.0) * 100 / d.toDouble()

        // Clubs off the tee, with Golfshot's names folded into Tally's ("1W" is Driver).
        val teeRows = q.teeByClub(ids).executeAsList()
            .groupBy { Clubs.normalize(it.tee_club) ?: it.tee_club }
            .map { (club, rows) ->
                val shots = rows.sumOf { it.shots }
                TeeClub(
                    club, shots.toInt(),
                    rows.sumOf { it.fairway } * 100.0 / shots, rows.sumOf { it.left_ } * 100.0 / shots, rows.sumOf { it.right_ } * 100.0 / shots,
                    rows.sumOf { it.to_par * it.shots } / shots,
                )
            }.sortedByDescending { it.shots }
        val teeShots = teeRows.sumOf { it.shots }
        val tee = teeShots.takeIf { it > 0 }?.let { n ->
            TeeView(
                n,
                teeRows.sumOf { it.fairwayPct * it.shots } / n, teeRows.sumOf { it.leftPct * it.shots } / n, teeRows.sumOf { it.rightPct * it.shots } / n,
                teeRows.filter { it.shots >= TEE_CLUB_MIN },
            )
        }

        val greens = q.greens(ids).executeAsOne()
        val hit = greens.hit ?: 0
        val missed = greens.holes - hit
        val putting = q.putting(ids).executeAsOne()
        val bands = q.puttsByDistance(ids).executeAsList().map {
            PuttBand(BANDS[it.band.toInt().coerceIn(0, BANDS.lastIndex)], it.holes.toInt(), it.holed * 100.0 / it.holes, it.three_putts * 100.0 / it.holes)
        }
        val trouble = q.trouble(ids).executeAsOne()
        val byPar = q.byPar(ids).executeAsList().mapNotNull { r -> r.par?.let { p -> r.to_par?.let { p to it } } }.toMap()
        val holeByHole = if (!oneCourse) emptyList() else q.holeByHole(ids).executeAsList().mapNotNull { h ->
            h.average?.let { HoleAverage(h.number, h.par, it, h.played.toInt(), h.best) }
        }

        val view = StatsView(
            rounds = rounds.size,
            holes = holes,
            scoringAverage = full.takeIf { it.isNotEmpty() }?.map { it.strokes }?.average(),
            toParPer18 = per18(rounds.sumOf { r -> r.played.sumOf { (it.strokes ?: 0) - (it.par ?: it.strokes ?: 0) } }),
            bestRound = full.minOfOrNull { it.strokes },
            recent = full.take(12).map { it.strokes }.reversed(),
            mix = mix,
            tee = tee,
            girPct = pct(hit, greens.holes),
            scramblingPct = pct(greens.saved, missed),
            puttsPerGir = if (hit == 0L) null else (greens.putts_on_hit ?: 0.0) / hit,
            puttsPer18 = if (putting.holes == 0L) null else (putting.putts ?: 0.0) * 18 / putting.holes,
            onePuttPct = pct(putting.one_putts, putting.holes),
            threePuttsPer18 = if (putting.holes == 0L) null else (putting.three_putts ?: 0).toDouble() * 18 / putting.holes,
            puttBands = bands,
            penaltiesPer18 = per18(trouble.penalties ?: 0.0),
            byPar = byPar,
            holeByHole = holeByHole,
            insight = null,
        )
        return view.copy(insight = insight(view))
    }

    /** One plain sentence: the most striking thing in the numbers. */
    internal fun insight(s: StatsView): String? {
        if (s.holes < 54) return null
        val driver = s.tee?.clubs?.firstOrNull { it.club == "Driver" }
        val other = s.tee?.clubs?.filter { it.club != "Driver" }?.maxByOrNull { it.shots }
        return when {
            driver != null && other != null && driver.shots >= 20 && other.shots >= 10 && other.toPar + 0.25 < driver.toPar ->
                "You score ${(driver.toPar - other.toPar).oneDecimal()} shots better on holes where you tee off with ${other.club} instead of Driver."
            s.tee != null && s.tee.rightPct > 1.8 * s.tee.leftPct && s.tee.rightPct >= 20 ->
                "Your tee shots miss right ${(s.tee.rightPct / s.tee.leftPct.coerceAtLeast(1.0)).oneDecimal()}× as often as left."
            s.tee != null && s.tee.leftPct > 1.8 * s.tee.rightPct && s.tee.leftPct >= 20 ->
                "Your tee shots miss left ${(s.tee.leftPct / s.tee.rightPct.coerceAtLeast(1.0)).oneDecimal()}× as often as right."
            s.penaltiesPer18 >= 1.5 -> "Penalties cost you ${s.penaltiesPer18.oneDecimal()} shots a round. Keeping the ball in play is the quickest saving."
            (s.threePuttsPer18 ?: 0.0) >= 2.0 -> "You three-putt ${s.threePuttsPer18!!.oneDecimal()} times a round. Lag putting is the quickest saving."
            s.scramblingPct != null && s.scramblingPct < 20 -> "You save par only ${s.scramblingPct.toInt()}% of the time after missing a green. Chipping is where the shots are."
            else -> null
        }
    }
}
