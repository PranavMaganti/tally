package me.vanpra.tally.round

import me.vanpra.tally.course.Hole
import me.vanpra.tally.course.LatLng
import me.vanpra.tally.course.METRES_TO_YARDS
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals

class ShotTrackTest {
    // A straight hole running north: 0.001° of latitude is about 111 m.
    private val tee = LatLng(51.0, 0.0)
    private val hole = Hole(1, 4, listOf(tee, LatLng(51.0036, 0.0)), green = listOf(at(390.0).copy(lon = -0.0001), at(390.0).copy(lon = 0.0001), at(410.0).copy(lon = 0.0001), at(410.0).copy(lon = -0.0001)))
    private fun at(metresNorth: Double) = LatLng(51.0 + metresNorth / 111_195.0, 0.0)
    private fun mark(m: Double, club: String? = null, byYou: Boolean = false) = ShotMark(at(m), 0, club, byYou)
    private fun yards(s: TrackedShot) = (s.metres * METRES_TO_YARDS).roundToInt()
    private fun near(expected: Int, actual: Int) = kotlin.test.assertTrue(kotlin.math.abs(expected - actual) <= 3, "expected about $expected, was $actual")

    @Test fun firstShotStartsOnTheMappedTee() {
        val shots = ShotTrack.shots(hole, listOf(mark(220.0), mark(370.0)))
        assertEquals(2, shots.size)
        near(241, yards(shots[0]))
        near(164, yards(shots[1]))
    }

    @Test fun aMarkOnTheTeeStartsTheHoleFromThere() {
        val shots = ShotTrack.shots(hole, listOf(mark(10.0), mark(220.0)))
        assertEquals(1, shots.size)
        near(230, yards(shots[0]))
    }

    @Test fun teeClubNamesTheFirstShot() {
        assertEquals("Driver", ShotTrack.shots(hole, listOf(mark(220.0)), teeClub = "Driver").single().club)
    }

    @Test fun clubsFromTheNoteGoToTheShotsInOrder() {
        val marked = ShotTrack.withClubsFromNote(hole, listOf(mark(220.0), mark(370.0)), "Driver right, 5 iron short, chipped on and 2 putts", null)
        assertEquals(listOf("Driver", "5i"), marked.map { it.club })
    }

    @Test fun aShotWithoutAClubKeepsTheOthersFromShifting() {
        // Three shots marked but only two clubs named: only the tee club is trusted.
        val marked = ShotTrack.withClubsFromNote(hole, listOf(mark(220.0), mark(250.0), mark(370.0)), "Driver into trees, chipped out, 7 iron on", "Driver")
        assertEquals(listOf("Driver", null, null), marked.map { it.club })
    }

    @Test fun clubsYouPickedAreKept() {
        val marked = ShotTrack.withClubsFromNote(hole, listOf(mark(220.0), mark(370.0, "6i", byYou = true)), "Driver, 5 iron", null)
        assertEquals(listOf("Driver", "6i"), marked.map { it.club })
    }

    @Test fun clubDistancesUseTheMiddleAndDropMishits() {
        val holes = (1..4).map { i -> HoleScore(i, par = 4, teeClub = "Driver", shots = listOf(mark(listOf(210.0, 220.0, 230.0, 60.0)[i - 1]))) }
        val round = Round("r", "Test", date = "2026-11-07", holes = holes)
        val d = ClubDistances.of(listOf(round), { (1..4).associateWith { hole } }).single()
        assertEquals("Driver", d.club)
        assertEquals(3, d.shots)
        near(241, d.typicalYards)
    }

    @Test fun theShotOntoTheGreenIsWorkedOutFromTheCard() {
        // A 4 with 2 putts and the drive marked: one shot left before the putts, which must have reached the green.
        val two = ShotTrack.shots(hole, listOf(mark(220.0)), "Driver", CardCounts(4, 2, 0), "Driver down the middle, 9 iron onto the green, two putts")
        assertEquals(2, two.size)
        val approach = two.last()
        assertEquals(true, approach.inferred)
        assertEquals("9i", approach.club)
        near(197, yards(approach))
    }

    @Test fun nothingIsWorkedOutWhenMoreThanOneShotIsMissing() {
        // A 6 with 2 putts and one mark: two unmarked shots, so where the first finished isn't known.
        assertEquals(1, ShotTrack.shots(hole, listOf(mark(220.0)), card = CardCounts(6, 2, 0)).size)
    }

    @Test fun penaltiesArentShots() {
        // 6 = 2 putts + 1 penalty + 3 shots; two marked, so one more onto the green.
        assertEquals(3, ShotTrack.shots(hole, listOf(mark(150.0), mark(220.0)), card = CardCounts(6, 2, 1)).size)
    }

    @Test fun theNotesLastClubGoesToTheWorkedOutShotNotTheMarkedOnes() {
        val marked = ShotTrack.withClubsFromNote(hole, listOf(mark(220.0)), "Driver, 9 iron on, 2 putts", null, CardCounts(4, 2, 0))
        assertEquals(listOf("Driver"), marked.map { it.club })
    }

    @Test fun aClubAlreadyOnAShotIsKeptWhenTheNoteDoesntSay() {
        val marked = ShotTrack.withClubsFromNote(hole, listOf(mark(220.0, "Driver")), "5, chipped to 4 feet", null)
        assertEquals(listOf("Driver"), marked.map { it.club })
    }
}
