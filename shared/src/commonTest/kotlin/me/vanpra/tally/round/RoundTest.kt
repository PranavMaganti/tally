package me.vanpra.tally.round

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import me.vanpra.tally.course.Course
import me.vanpra.tally.course.CourseSource
import me.vanpra.tally.course.Hole
import me.vanpra.tally.course.LatLng
import me.vanpra.tally.platform.ImportFile
import me.vanpra.tally.platform.InMemoryTextStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoundTest {
    private val roundsCsv = """
        round_id,course,course_id,tee,course_rating,slope,course_handicap,date,location,game_mode,pace_of_play,players,holes_played,score,par,putts,penalties,fairways_hit,fairways_left,fairways_right,gir
        abc,Wanstead Golf Club - Wanstead,x,Yellow,68.0,120,10,"Oct 03, 2026","Wanstead, LND",Stroke Play,3:51,1,2,9,8,4,1,1,0,0,1
    """.trimIndent()
    private val holesCsv = """
        round_id,date,course,hole,distance,stroke_index,par,score,net_score,tee_club,fairway,gir,putts,sand_shots,penalties
        abc,"Oct 03, 2026",Wanstead Golf Club - Wanstead,1,344,5,4,4,3,1W,hit,True,2,,
        abc,"Oct 03, 2026",Wanstead Golf Club - Wanstead,2,423,1,4,5,4,3W,right,False,2,1,1
        abc,"Oct 03, 2026",Wanstead Golf Club - Wanstead,3,381,9,4,,,,,,,,
    """.trimIndent()

    @Test
    fun importsGolfshotCsv() {
        val result = GolfshotImport.parse(listOf(ImportFile("holes.csv", holesCsv), ImportFile("rounds.csv", roundsCsv)))
        val round = assertIs<GolfshotImport.Result.Success>(result).rounds.single()
        assertEquals("golfshot-abc", round.id)
        assertEquals("Wanstead Golf Club", round.courseName)
        assertEquals("Yellow", round.tee)
        assertEquals("2026-10-03", round.date)
        assertEquals(3, round.holes.size)
        assertEquals(2, round.played.size)
        assertEquals(9, round.strokes)
        assertEquals(1, round.toPar)
        val h2 = round.hole(2)!!
        assertEquals(TeeShot.RIGHT, h2.teeShot)
        assertEquals(1, h2.bunkerShots)
        assertEquals(1, h2.penalties)
        assertEquals(false, h2.gir)
        assertEquals(0, round.hole(1)!!.penalties)
        assertNull(round.hole(3)!!.penalties)
    }

    @Test
    fun missingFileIsReported() {
        assertIs<GolfshotImport.Result.Problem>(GolfshotImport.parse(listOf(ImportFile("rounds.csv", roundsCsv))))
    }

    @Test
    fun csvHandlesQuotesAndCommas() {
        val rows = GolfshotImport.parseCsv("a,b\n\"x, y\",\"say \"\"hi\"\"\"\n")
        assertEquals(listOf("x, y", "say \"hi\""), rows[1])
    }

    @Test
    fun girIsWorkedOutWhenNotRecorded() {
        assertEquals(true, HoleScore(1, par = 4, strokes = 4, putts = 2).gir)
        assertEquals(false, HoleScore(1, par = 4, strokes = 5, putts = 2).gir)
        assertNull(HoleScore(1, par = 4, strokes = 5).gir)
    }

    @Test
    fun repositoryPersistsAndReloads() = runTest {
        val db = tallyDatabase(testDriver())
        val repo = RoundRepository(db, backgroundScope, Dispatchers.Unconfined)
        val course = Course("c", "Wanstead Golf Club", listOf(Hole(1, 4, listOf(LatLng(0.0, 0.0), LatLng(0.0, 0.001)), null)), CourseSource.OPEN_STREET_MAP)
        val round = repo.startRound(course)
        repo.saveHole(round.id, HoleScore(1, par = 4, strokes = 5, putts = 2, teeShot = TeeShot.LEFT))
        testScheduler.advanceUntilIdle()
        val reloaded = RoundRepository(db, backgroundScope, Dispatchers.Unconfined).state.value
        assertEquals(round.id, reloaded.activeRoundId)
        assertEquals(5, reloaded.activeRound!!.hole(1)!!.strokes)

        repo.finishRound(round.id)
        testScheduler.advanceUntilIdle()
        val after = RoundRepository(db, backgroundScope, Dispatchers.Unconfined).state.value
        assertNull(after.activeRoundId)
        assertNotNull(after.history.single().finishedAtMillis)
    }

    @Test
    fun emptyRoundIsDiscardedOnFinish() = runTest {
        val repo = RoundRepository(tallyDatabase(testDriver()), backgroundScope, Dispatchers.Unconfined)
        val course = Course("c", "X", listOf(Hole(1, 4, listOf(LatLng(0.0, 0.0), LatLng(0.0, 0.001)), null)), CourseSource.OPEN_STREET_MAP)
        val round = repo.startRound(course)
        repo.finishRound(round.id)
        assertTrue(repo.state.value.rounds.isEmpty())
    }

    @Test
    fun statsFromRounds() {
        val holes = (1..18).map { HoleScore(it, par = 4, strokes = 5, putts = 2, teeShot = if (it % 3 == 0) TeeShot.RIGHT else TeeShot.FAIRWAY, penalties = 0) }
        val rounds = (1..3).map { Round("r$it", "Wanstead Golf Club", date = "2026-10-0$it", source = RoundSource.GOLFSHOT, holes = holes) }
        val s = Stats.of(rounds)
        assertEquals(90.0, s.scoringAverage)
        assertEquals(18.0, s.averageToPar)
        assertEquals(36.0, s.puttsPerRound)
        assertEquals(66.67, s.fairwayPct!!, 0.01)
        assertEquals(0.0, s.missLeftPct)
        assertNotNull(s.insight)
    }

    @Test
    fun dates() {
        assertEquals("2026-10-03", GolfshotImport.isoDate("Oct 03, 2026"))
        assertEquals("3 Oct", displayDate("2026-10-03", currentYear = "2026"))
        assertEquals("28 Apr 2025", displayDate("2025-04-28", currentYear = "2026"))
        assertTrue(sameCourse("Wanstead Golf Club", "Wanstead Golf Club - Wanstead".substringBefore(" - ")))
    }
}
