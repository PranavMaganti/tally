package me.vanpra.tally.round

import kotlinx.serialization.Serializable
import me.vanpra.tally.ai.Clubs
import me.vanpra.tally.course.Hole
import me.vanpra.tally.course.HoleDistances
import me.vanpra.tally.course.LatLng
import me.vanpra.tally.course.LocalProjection
import me.vanpra.tally.course.Polygon
import me.vanpra.tally.course.distanceMetres
import kotlin.math.roundToInt

/** Where your ball was when you marked it: the start of your next shot. */
@Serializable
data class ShotMark(
    val position: LatLng,
    val timeMillis: Long,
    /** The club hit *to* this spot, when known. */
    val club: String? = null,
    /** You picked the club yourself, so the note never changes it. */
    val clubByYou: Boolean = false,
    /** Found by Tally rather than marked by you. */
    val auto: Boolean = false,
)

/** One shot you've tracked: from where you hit it to where it finished. */
data class TrackedShot(
    val number: Int,
    val from: LatLng,
    val to: LatLng,
    val club: String?,
    /** Where it finished was found by Tally. */
    val auto: Boolean = false,
    /** Not marked: worked out from your score as the shot onto the green, so it ends at the green's middle. */
    val inferred: Boolean = false,
) {
    val metres: Double get() = distanceMetres(from, to)
}

/**
 * Turns the spots you marked on a hole into shots. The first shot starts on the tee: the mapped tee,
 * or your own mark if you made one standing on it. Each later mark is where a shot finished.
 */
object ShotTrack {
    /** A mark this close to the start of the hole is the tee, not a finished shot. */
    const val TEE_RADIUS_M = 25.0

    fun isTee(hole: Hole, p: LatLng): Boolean {
        if (distanceMetres(hole.line.first(), p) < TEE_RADIUS_M) return true
        val projection = LocalProjection(hole.line.first())
        return hole.tees.any { it.size >= 3 && projection.toPoint(p) in Polygon.of(it, projection) }
    }

    /** Where each shot started: the tee first, then every mark after it. */
    fun starts(hole: Hole, marks: List<ShotMark>): List<LatLng> {
        val onTee = marks.indexOfLast { isTee(hole, it.position) }
        val tee = if (onTee >= 0) marks[onTee].position else hole.line.first()
        return listOf(tee) + marks.drop(onTee + 1).map { it.position }
    }

    /**
     * Shots whose finish you've marked, then the shot onto the green if your card shows there was just one
     * more before the putts. Clubs come from the marks (the first falling back to [teeClub]); the shot onto
     * the green takes the note's last club when the note names one for every shot.
     */
    fun shots(hole: Hole, marks: List<ShotMark>, teeClub: String? = null, card: CardCounts? = null, note: String? = null): List<TrackedShot> {
        val onTee = marks.indexOfLast { isTee(hole, it.position) }
        val finished = marks.drop(onTee + 1)
        val starts = starts(hole, marks)
        val marked = finished.mapIndexed { i, m -> TrackedShot(i + 1, starts[i], m.position, m.club ?: teeClub.takeIf { i == 0 }, m.auto) }
        if (card?.unmarked(marked.size) != 1) return marked
        val green = HoleDistances(hole).greenMiddle ?: return marked
        val from = starts.last()
        // Already on the green where you last marked: nothing left to work out.
        if (distanceMetres(from, green) < ON_GREEN_M) return marked
        val clubs = note?.let { Clubs.mentioned(it).map { c -> c.first } }.orEmpty()
        val club = when {
            clubs.size == marked.size + 1 -> clubs.last()
            marked.isEmpty() -> teeClub
            else -> null
        }
        return marked + TrackedShot(marked.size + 1, from, green, club, inferred = true)
    }

    /** Shots this close to the middle of the green started on it. */
    private const val ON_GREEN_M = 12.0

    /**
     * The clubs a note names, matched to the shots in order: "driver right, 5 iron short, chipped on" →
     * Driver, 5i. Only when the note names a club for every tracked shot, so one shot without a club
     * ("chipped out") can't shift the rest onto the wrong shots; otherwise just the tee shot's club.
     * Clubs you picked yourself are kept.
     */
    fun withClubsFromNote(hole: Hole, marks: List<ShotMark>, note: String, teeClub: String?, card: CardCounts? = null): List<ShotMark> {
        val onTee = marks.indexOfLast { isTee(hole, it.position) }
        val count = marks.size - onTee - 1
        if (count <= 0) return marks
        val clubs = Clubs.mentioned(note).map { it.first }
        // The note's last club can belong to the shot onto the green that the card says wasn't marked.
        val unmarked = card?.unmarked(count)?.coerceAtLeast(0) ?: 0
        val named: List<String?> = when {
            clubs.size == count -> clubs
            unmarked > 0 && clubs.size == count + unmarked -> clubs.take(count)
            else -> listOf(teeClub ?: clubs.firstOrNull().takeIf { clubs.size == 1 && count == 1 })
        }
        return marks.mapIndexed { i, m ->
            val shot = i - onTee - 1
            when {
                shot < 0 || m.clubByYou -> m
                // The note names this shot's club, or it keeps the one it had.
                else -> m.copy(club = named.getOrNull(shot) ?: m.club)
            }
        }
    }
}

/** The counts on a hole's card that say how many shots there were before the putts. */
data class CardCounts(val strokes: Int?, val putts: Int?, val penalties: Int?) {
    /** Shots played (not putts or penalties) beyond the [marked] ones; null when the card doesn't say. */
    fun unmarked(marked: Int): Int? {
        val s = strokes ?: return null
        val p = putts ?: return null
        return s - p - (penalties ?: 0) - marked
    }

    companion object {
        fun of(h: HoleScore) = CardCounts(h.strokes, h.putts, h.penalties)
    }
}

/** How far you hit a club: the middle of your shots and the range most of them land in. */
data class ClubDistance(val club: String, val shots: Int, val typicalYards: Int, val lowYards: Int, val highYards: Int)

object ClubDistances {
    /** Clubs with at least [minShots] tracked shots, longest first. Mishits (under half the middle) are left out. */
    fun of(rounds: List<Round>, holes: (Round) -> Map<Int, Hole>, minShots: Int = 2): List<ClubDistance> {
        val byClub = mutableMapOf<String, MutableList<Double>>()
        rounds.forEach { r ->
            val mapped = holes(r)
            r.holes.forEach { h ->
                val hole = mapped[h.number] ?: return@forEach
                ShotTrack.shots(hole, h.shots, h.teeClub).filter { !it.inferred }.forEach { s ->
                    val club = s.club ?: return@forEach
                    byClub.getOrPut(club) { mutableListOf() } += s.metres * me.vanpra.tally.course.METRES_TO_YARDS
                }
            }
        }
        return byClub.mapNotNull { (club, all) ->
            val mid = median(all)
            val good = all.filter { it >= mid / 2 }.sorted()
            if (good.size < minShots) return@mapNotNull null
            ClubDistance(club, good.size, median(good).roundToInt(), quantile(good, 0.25).roundToInt(), quantile(good, 0.75).roundToInt())
        }.sortedByDescending { it.typicalYards }
    }

    private fun median(xs: List<Double>) = quantile(xs.sorted(), 0.5)
    private fun quantile(sorted: List<Double>, q: Double): Double {
        if (sorted.isEmpty()) return 0.0
        val pos = q * (sorted.size - 1)
        val lo = pos.toInt(); val hi = minOf(lo + 1, sorted.lastIndex)
        return sorted[lo] + (sorted[hi] - sorted[lo]) * (pos - lo)
    }
}
