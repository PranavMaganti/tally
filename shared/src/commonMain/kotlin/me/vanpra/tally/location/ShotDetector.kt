package me.vanpra.tally.location

import kotlinx.serialization.Serializable
import me.vanpra.tally.course.LatLng
import me.vanpra.tally.course.distanceMetres

/** A swing the phone felt: a sharp turn of your hips after standing still at address. [peak] is in rad/s. */
data class Swing(val timeMillis: Long, val peak: Float)

/** A shot Tally found: you swung here, then walked away. */
@Serializable
data class FoundShot(val position: LatLng, val timeMillis: Long, val swings: Int = 1, val peak: Float = 0f)

/**
 * Finds shots from GPS and swings: one or more swings at a spot, then walking away from it, is a shot
 * played from there. Practice swings at the same spot count once, timed at the last swing. Nothing is
 * reported until you leave, so a waggle and a re-grip don't make two shots.
 */
class ShotDetector(
    /** Further than this from where you swung and you've left the ball. */
    private val leaveMetres: Double = 15.0,
    private val maxAccuracyMetres: Double = 20.0,
) {
    private val recent = ArrayDeque<LocationFix>()
    private var pending: FoundShot? = null

    fun onFix(fix: LocationFix): FoundShot? {
        if (fix.accuracyMetres > maxAccuracyMetres) return null
        recent.addLast(fix)
        while (recent.isNotEmpty() && fix.timeMillis - recent.first().timeMillis > KEEP_MS) recent.removeFirst()
        val p = pending ?: return null
        if (distanceMetres(p.position, fix.position) <= leaveMetres) return null
        pending = null
        return p
    }

    fun onSwing(swing: Swing): FoundShot? {
        val at = positionAt(swing.timeMillis) ?: return null
        val p = pending
        if (p != null && distanceMetres(p.position, at) <= leaveMetres) {
            pending = p.copy(timeMillis = swing.timeMillis, swings = p.swings + 1, peak = maxOf(p.peak, swing.peak))
            return null
        }
        // A swing somewhere new before GPS showed you leaving the last spot: that one's done too.
        pending = FoundShot(at, swing.timeMillis, 1, swing.peak)
        return p
    }

    /** The round's over: a shot still waiting for you to walk away counts. */
    fun flush(): FoundShot? = pending.also { pending = null }

    /** Where you stood at [timeMillis]: the average of fixes around then, or the nearest one if it's recent. */
    private fun positionAt(timeMillis: Long): LatLng? {
        val around = recent.filter { kotlin.math.abs(it.timeMillis - timeMillis) <= AROUND_MS }
        if (around.isNotEmpty()) return LatLng(around.map { it.position.lat }.average(), around.map { it.position.lon }.average())
        return recent.minByOrNull { kotlin.math.abs(it.timeMillis - timeMillis) }
            ?.takeIf { kotlin.math.abs(it.timeMillis - timeMillis) <= NEAREST_MS }?.position
    }

    private companion object {
        const val KEEP_MS = 60_000L
        const val AROUND_MS = 4_000L
        const val NEAREST_MS = 15_000L
    }
}
