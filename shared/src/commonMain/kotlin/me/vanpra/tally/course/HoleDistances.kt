package me.vanpra.tally.course

/** All values in metres. Convert with [METRES_TO_YARDS] at the UI edge. */
data class GreenDistances(val front: Double, val middle: Double, val back: Double)

enum class Side { LEFT, CENTRE, RIGHT }

data class HazardDistance(
    val hazard: Hazard,
    val side: Side,
    /** Distance to the nearest edge: how far to reach it. */
    val reach: Double,
    /** Distance to the farthest edge: how far to carry it. */
    val carry: Double,
    /** How far the hazard's middle is along your line to the green. Used for ordering. */
    val along: Double,
)

/**
 * Distance calculations for one hole. Everything is projected onto a flat plane around the tee,
 * which is exact to well under GPS noise across a single hole.
 */
class HoleDistances(val hole: Hole) {
    private val projection = LocalProjection(hole.line.first())
    private val line = hole.line.map(projection::toPoint)
    private val green = hole.green?.let { Polygon.of(it, projection) }
    private val hazards = hole.hazards.map { it to Polygon.of(it.outline, projection) }

    /** Length of the mapped hole line. OSM lines often start at a forward tee, so this can be shorter than the card. */
    val lengthMetres: Double = polylineLength(line)

    val greenMiddle: LatLng? = green?.let { projection.toLatLng(it.centroid) }

    /**
     * Front, middle and back measured along the player's own line to the middle of the green,
     * so they change as the player moves sideways, unlike fixed front/back points.
     */
    fun green(player: LatLng): GreenDistances? {
        val g = green ?: return null
        val p = projection.toPoint(player)
        val toMiddle = g.centroid - p
        val middle = toMiddle.length
        if (middle < 0.01) return GreenDistances(0.0, 0.0, g.farthestDistance(p))
        val dir = toMiddle * (1 / middle)
        val crossings = g.rayCrossings(p, dir)
        return when {
            p in g -> GreenDistances(0.0, middle, crossings.maxOrNull() ?: g.farthestDistance(p))
            crossings.size >= 2 -> GreenDistances(crossings.min(), middle, crossings.max())
            else -> GreenDistances(g.nearestDistance(p), middle, g.farthestDistance(p))
        }
    }

    /**
     * Hazards between the player and the back of the green, within [corridorMetres] either side
     * of the player's line to the middle of the green, nearest first.
     */
    fun hazardsAhead(player: LatLng, corridorMetres: Double = 30.0): List<HazardDistance> {
        val g = green ?: return emptyList()
        val p = projection.toPoint(player)
        val toMiddle = g.centroid - p
        val length = toMiddle.length
        if (length < 1.0) return emptyList()
        val dir = toMiddle * (1 / length)
        return hazards.mapNotNull { (hazard, poly) ->
            val rel = poly.centroid - p
            val along = rel dot dir
            val offset = dir cross rel // positive = left of the line
            if (along < 15.0 || along > length + 20.0 || kotlin.math.abs(offset) > corridorMetres) return@mapNotNull null
            HazardDistance(
                hazard = hazard,
                side = when {
                    kotlin.math.abs(offset) < 6.0 -> Side.CENTRE
                    offset > 0 -> Side.LEFT
                    else -> Side.RIGHT
                },
                reach = poly.nearestDistance(p),
                carry = poly.farthestDistance(p),
                along = along,
            )
        }.sortedBy { it.along }
    }

    /** The point [metres] along the hole line from the tee. Handy for simulating a position. */
    fun pointAlongHole(metres: Double): LatLng = projection.toLatLng(pointAlong(line, metres))
}
