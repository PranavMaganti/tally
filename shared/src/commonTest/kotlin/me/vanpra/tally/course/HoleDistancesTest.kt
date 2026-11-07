package me.vanpra.tally.course

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HoleDistancesTest {
    // A 20 m square green whose middle is 200 m north of the tee, with one bunker short-right of it.
    private val projection = LocalProjection(LatLng(51.57, 0.03))
    private fun ll(x: Double, y: Double) = projection.toLatLng(Point(x, y))
    private fun square(cx: Double, cy: Double, half: Double) =
        listOf(ll(cx - half, cy - half), ll(cx + half, cy - half), ll(cx + half, cy + half), ll(cx - half, cy + half))

    private val hole = Hole(
        number = 1,
        par = 4,
        line = listOf(ll(0.0, 0.0), ll(0.0, 200.0)),
        green = square(0.0, 200.0, 10.0),
        hazards = listOf(Hazard(HazardType.BUNKER, square(15.0, 170.0, 4.0))),
    )
    private val distances = HoleDistances(hole)

    @Test
    fun frontMiddleBackFromStraightBehind() {
        val d = assertNotNull(distances.green(ll(0.0, 100.0)))
        assertEquals(90.0, d.front, 0.05)
        assertEquals(100.0, d.middle, 0.05)
        assertEquals(110.0, d.back, 0.05)
    }

    @Test
    fun frontAndBackFollowTheAngledLine() {
        // From 100 m out and 50 m to the side, the line crosses the square diagonally, so the green plays deeper.
        val d = assertNotNull(distances.green(ll(-50.0, 100.0)))
        assertTrue(d.back - d.front > 20.0, "depth along an angled line should exceed the straight 20 m, was ${d.back - d.front}")
    }

    @Test
    fun onTheGreenFrontIsZero() {
        val d = assertNotNull(distances.green(ll(2.0, 195.0)))
        assertEquals(0.0, d.front, 0.0)
    }

    @Test
    fun bunkerShortRightIsReported() {
        val h = distances.hazardsAhead(ll(0.0, 0.0)).single()
        assertEquals(Side.RIGHT, h.side)
        assertTrue(h.reach < h.carry)
        assertEquals(166.0, h.reach, 1.5)
    }

    @Test
    fun hazardsBehindThePlayerAreIgnored() {
        assertTrue(distances.hazardsAhead(ll(0.0, 180.0)).isEmpty())
    }

    @Test
    fun holeLength() {
        assertEquals(200.0, distances.lengthMetres, 0.05)
    }
}
