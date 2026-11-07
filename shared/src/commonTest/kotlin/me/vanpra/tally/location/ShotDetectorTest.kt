package me.vanpra.tally.location

import me.vanpra.tally.course.Course
import me.vanpra.tally.course.CourseSource
import me.vanpra.tally.course.Hole
import me.vanpra.tally.course.LatLng
import me.vanpra.tally.round.FoundShots
import me.vanpra.tally.round.HoleScore
import me.vanpra.tally.round.Round
import me.vanpra.tally.round.ShotMark
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShotDetectorTest {
    private fun at(metresNorth: Double, metresEast: Double = 0.0) = LatLng(51.0 + metresNorth / 111_195.0, metresEast / 69_970.0)
    private fun fix(t: Long, north: Double, east: Double = 0.0) = LocationFix(at(north, east), 4.0, t)

    /** Stand at [north] from [from] to [to] seconds, one fix a second. */
    private fun ShotDetector.stand(north: Double, from: Int, to: Int) = (from..to).mapNotNull { onFix(fix(it * 1000L, north)) }
    /** Walk north from [a] to [b] metres, a metre a second, starting at [start] seconds. */
    private fun ShotDetector.walk(a: Double, b: Double, start: Int) = (0..(b - a).toInt()).mapNotNull { onFix(fix((start + it) * 1000L, a + it)) }

    @Test fun aSwingThenWalkingAwayIsAShot() {
        val d = ShotDetector()
        d.stand(0.0, 0, 20)
        assertNull(d.onSwing(Swing(15_000, 6f)))
        val found = d.walk(0.0, 40.0, 21)
        assertEquals(1, found.size)
        assertTrue(kotlin.math.abs(found.single().position.lat - 51.0) < 0.00002)
        assertEquals(15_000, found.single().timeMillis)
    }

    @Test fun practiceSwingsAtTheSameSpotCountOnce() {
        val d = ShotDetector()
        d.stand(0.0, 0, 30)
        d.onSwing(Swing(10_000, 4f)); d.onSwing(Swing(14_000, 4.5f)); d.onSwing(Swing(25_000, 7f))
        val found = d.walk(0.0, 40.0, 31)
        assertEquals(1, found.size)
        assertEquals(3, found.single().swings)
        assertEquals(25_000, found.single().timeMillis)
        assertEquals(7f, found.single().peak)
    }

    @Test fun standingStillWithoutSwingingIsNotAShot() {
        val d = ShotDetector()
        d.stand(0.0, 0, 60)
        assertTrue(d.walk(0.0, 40.0, 61).isEmpty())
    }

    @Test fun twoShotsInARow() {
        val d = ShotDetector()
        d.stand(0.0, 0, 10); d.onSwing(Swing(8_000, 6f))
        val first = d.walk(0.0, 200.0, 11)
        d.stand(200.0, 212, 230); d.onSwing(Swing(225_000, 6f))
        val second = d.walk(200.0, 300.0, 231)
        assertEquals(1, first.size); assertEquals(1, second.size)
    }

    @Test fun aSwingWithNoRecentGpsIsIgnored() {
        val d = ShotDetector()
        d.stand(0.0, 0, 5)
        assertNull(d.onSwing(Swing(60_000, 6f)))
        assertNull(d.flush())
    }

    // Placing found shots on a round.
    private val hole1 = Hole(1, 4, listOf(at(0.0), at(350.0)), green = listOf(at(340.0, -10.0), at(340.0, 10.0), at(360.0, 10.0), at(360.0, -10.0)))
    private val hole2 = Hole(2, 4, listOf(at(370.0, 30.0), at(370.0, 400.0)), null)
    private val course = Course("c", "Test", listOf(hole1, hole2), CourseSource.OPEN_STREET_MAP)
    private val round = Round("r", "Test", date = "2026-11-07", startedAtMillis = 0, holes = listOf(HoleScore(1, 4), HoleScore(2, 4)))

    @Test fun foundShotsGoOnTheHoleTheyreOn() {
        val placed = FoundShots.place(course, round, listOf(FoundShot(at(220.0, 5.0), 100), FoundShot(at(372.0, 200.0), 200)))
        assertEquals(1, placed[1]?.size); assertEquals(1, placed[2]?.size)
        assertTrue(placed.values.flatten().all { it.auto })
    }

    @Test fun shotsOnTheGreenAndOffTheCourseAreLeftOut() {
        val placed = FoundShots.place(course, round, listOf(FoundShot(at(350.0), 100), FoundShot(at(200.0, 300.0), 200)))
        assertTrue(placed.isEmpty())
    }

    @Test fun aSpotYouAlreadyMarkedIsntAddedAgain() {
        val marked = round.copy(holes = listOf(HoleScore(1, 4, shots = listOf(ShotMark(at(220.0), 90))), HoleScore(2, 4)))
        assertTrue(FoundShots.place(course, marked, listOf(FoundShot(at(225.0), 100))).isEmpty())
    }
}
