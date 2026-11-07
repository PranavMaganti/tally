package me.vanpra.tally.course

import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot

const val METRES_TO_YARDS = 1.0936133

@Serializable
data class LatLng(val lat: Double, val lon: Double)

/** A point in metres on a flat local plane: x grows east, y grows north. */
data class Point(val x: Double, val y: Double) {
    operator fun plus(o: Point) = Point(x + o.x, y + o.y)
    operator fun minus(o: Point) = Point(x - o.x, y - o.y)
    operator fun times(k: Double) = Point(x * k, y * k)
    infix fun dot(o: Point) = x * o.x + y * o.y
    /** z component of the 2D cross product; positive when [o] is anticlockwise (left) of this. */
    infix fun cross(o: Point) = x * o.y - y * o.x
    val length: Double get() = hypot(x, y)
    fun distanceTo(o: Point) = (this - o).length
}

/**
 * Equirectangular projection around [origin]. Over the few hundred metres of a golf hole
 * the error is millimetres, far below GPS noise, and it keeps all geometry as plain 2D maths.
 */
class LocalProjection(private val origin: LatLng) {
    private val metresPerDegLon = METRES_PER_DEG_LAT_EQUATOR * cos(origin.lat * PI / 180.0)

    fun toPoint(p: LatLng) = Point((p.lon - origin.lon) * metresPerDegLon, (p.lat - origin.lat) * METRES_PER_DEG_LAT)
    fun toLatLng(p: Point) = LatLng(origin.lat + p.y / METRES_PER_DEG_LAT, origin.lon + p.x / metresPerDegLon)

    private companion object {
        const val METRES_PER_DEG_LAT = 110_574.0
        const val METRES_PER_DEG_LAT_EQUATOR = 111_320.0
    }
}

/** Straight-line distance in metres between two coordinates (accurate at golf-hole scale). */
fun distanceMetres(a: LatLng, b: LatLng): Double = LocalProjection(a).toPoint(b).length

internal fun segmentDistance(p: Point, a: Point, b: Point): Double {
    val ab = b - a
    val lenSq = ab dot ab
    val t = if (lenSq == 0.0) 0.0 else (((p - a) dot ab) / lenSq).coerceIn(0.0, 1.0)
    return p.distanceTo(a + ab * t)
}

internal fun polylineDistance(p: Point, line: List<Point>): Double =
    line.zipWithNext().minOfOrNull { (a, b) -> segmentDistance(p, a, b) } ?: line.firstOrNull()?.distanceTo(p) ?: Double.MAX_VALUE

internal fun polylineLength(line: List<Point>): Double = line.zipWithNext().sumOf { (a, b) -> a.distanceTo(b) }

/** The point [metres] along [line], clamped to its ends. */
internal fun pointAlong(line: List<Point>, metres: Double): Point {
    var remaining = metres.coerceAtLeast(0.0)
    for ((a, b) in line.zipWithNext()) {
        val seg = a.distanceTo(b)
        if (remaining <= seg) return if (seg == 0.0) a else a + (b - a) * (remaining / seg)
        remaining -= seg
    }
    return line.last()
}

/** A simple closed polygon in local metres. The closing edge is implicit. */
internal class Polygon(val points: List<Point>) {
    private val edges: List<Pair<Point, Point>> = points.indices.map { i -> points[(i + points.size - 1) % points.size] to points[i] }

    val centroid: Point by lazy {
        var area = 0.0; var cx = 0.0; var cy = 0.0
        for ((a, b) in edges) {
            val f = a cross b
            area += f; cx += (a.x + b.x) * f; cy += (a.y + b.y) * f
        }
        if (abs(area) < 1e-9) Point(points.sumOf { it.x } / points.size, points.sumOf { it.y } / points.size)
        else Point(cx / (3 * area), cy / (3 * area))
    }

    operator fun contains(p: Point): Boolean {
        var inside = false
        for ((a, b) in edges) {
            if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x) inside = !inside
        }
        return inside
    }

    fun nearestDistance(p: Point): Double = if (p in this) 0.0 else edges.minOf { (a, b) -> segmentDistance(p, a, b) }
    fun farthestDistance(p: Point): Double = points.maxOf { it.distanceTo(p) }

    /** Distances along the ray from [origin] in [direction] (unit vector) where it crosses an edge. */
    fun rayCrossings(origin: Point, direction: Point): List<Double> = edges.mapNotNull { (a, b) ->
        val e = b - a
        val denom = direction cross e
        if (abs(denom) < 1e-12) return@mapNotNull null
        val w = a - origin
        val t = (w cross e) / denom
        val u = (w cross direction) / denom
        if (t >= 0 && u in 0.0..1.0) t else null
    }

    companion object {
        fun of(outline: List<LatLng>, projection: LocalProjection) = Polygon(outline.map(projection::toPoint))
    }
}
