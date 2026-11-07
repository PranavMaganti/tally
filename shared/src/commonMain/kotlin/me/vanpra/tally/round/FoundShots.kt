package me.vanpra.tally.round

import me.vanpra.tally.course.Course
import me.vanpra.tally.course.Hole
import me.vanpra.tally.course.LatLng
import me.vanpra.tally.course.LocalProjection
import me.vanpra.tally.course.Polygon
import me.vanpra.tally.course.distanceMetres
import me.vanpra.tally.course.polylineDistance
import me.vanpra.tally.location.FoundShot

/** Puts shots found automatically (from swings and GPS stops, e.g. with a watch later) onto the holes of a round. */
object FoundShots {
    /** Further than this from a hole's line (or tee) and a found shot isn't on that hole. */
    const val ON_HOLE_M = 45.0
    /** A found shot this close to one you marked is the same shot. */
    const val SAME_SPOT_M = 12.0
    /** On or this close to a green: putts, which the putting details cover, not shots to track. */
    private const val GREEN_EDGE_M = 1.5

    /** New marks for each hole. Shots on a green, before the round, off every hole, or already marked are left out. */
    fun place(course: Course, round: Round, found: List<FoundShot>): Map<Int, List<ShotMark>> {
        val added = mutableMapOf<Int, MutableList<ShotMark>>()
        // The hole you were last on, so a spot between two holes goes to the one you're playing.
        var lastHole = round.holes.filter { it.shots.isNotEmpty() }.maxByOrNull { h -> h.shots.maxOf { it.timeMillis } }?.number
        found.sortedBy { it.timeMillis }.forEach { shot ->
            if (round.startedAtMillis != null && shot.timeMillis < round.startedAtMillis) return@forEach
            if (course.holes.any { onGreen(it, shot.position) }) return@forEach
            val hole = holeFor(course, shot.position, lastHole) ?: return@forEach
            val existing = round.hole(hole.number)?.shots.orEmpty() + added[hole.number].orEmpty()
            if (existing.any { distanceMetres(it.position, shot.position) < SAME_SPOT_M }) return@forEach
            added.getOrPut(hole.number) { mutableListOf() } += ShotMark(shot.position, shot.timeMillis, auto = true)
            lastHole = hole.number
        }
        return added
    }

    /** Holes you could be on; of those, the first one from the hole you were last on, else the nearest. */
    internal fun holeFor(course: Course, p: LatLng, lastHole: Int?): Hole? {
        val near = course.holes.mapNotNull { h -> distanceToHole(h, p).takeIf { it < ON_HOLE_M }?.let { h to it } }
        if (near.isEmpty()) return null
        lastHole?.let { last -> near.filter { it.first.number >= last }.minByOrNull { it.first.number }?.let { return it.first } }
        return near.minBy { it.second }.first
    }

    private fun distanceToHole(h: Hole, p: LatLng): Double {
        val projection = LocalProjection(h.line.first())
        val point = projection.toPoint(p)
        val line = polylineDistance(point, h.line.map(projection::toPoint))
        val tee = if (ShotTrack.isTee(h, p)) 0.0 else Double.MAX_VALUE
        return minOf(line, tee)
    }

    private fun onGreen(h: Hole, p: LatLng): Boolean {
        val green = h.green?.takeIf { it.size >= 3 } ?: return false
        val projection = LocalProjection(green.first())
        val poly = Polygon.of(green, projection)
        val point = projection.toPoint(p)
        return poly.nearestDistance(point) < GREEN_EDGE_M
    }
}

