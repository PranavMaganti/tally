package me.vanpra.tally.round

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StatsDataTest {
    /** 18 par 4s: hole n scores [scores][n], with the tee club and side given. */
    private fun round(id: String, date: String, course: String = "Wanstead Golf Club", holes: (Int) -> HoleScore) =
        Round(id, course, date = date, source = RoundSource.GOLFSHOT, holes = (1..18).map(holes))

    private suspend fun kotlinx.coroutines.test.TestScope.load(rounds: List<Round>, filter: StatsFilter = StatsFilter(0)): StatsView {
        val db = tallyDatabase(testDriver())
        val repo = RoundRepository(db, backgroundScope, Dispatchers.Unconfined)
        repo.importRounds(rounds)
        val picked = StatsData.pick(repo.state.value.history, filter)
        return StatsData.load(db, picked, oneCourse = filter.course != null)
    }

    @Test fun scoreMixGreensAndPutting() = runTest {
        // Every hole a par 4: odd holes par in regulation (2 putts), even holes bogey after missing the green (1 chip, 2 putts).
        val r = round("a", "2026-10-01") { n -> HoleScore(n, par = 4, strokes = if (n % 2 == 1) 4 else 5, putts = 2, teeClub = "1W", teeShot = TeeShot.FAIRWAY) }
        val s = load(listOf(r))
        assertEquals(9, s.mix.pars); assertEquals(9, s.mix.bogeys)
        assertEquals(50.0, s.girPct)
        assertEquals(0.0, s.scramblingPct)
        assertEquals(36.0, s.puttsPer18)
        assertEquals(81.0, s.scoringAverage)
        assertEquals(9.0, s.toParPer18)
    }

    @Test fun golfshotClubNamesAreFoldedIntoTallys() = runTest {
        val r = round("a", "2026-10-01") { n -> HoleScore(n, par = 4, strokes = 5, putts = 2, teeClub = if (n <= 12) "1W" else "Driver", teeShot = if (n <= 6) TeeShot.RIGHT else TeeShot.FAIRWAY) }
        val tee = load(listOf(r)).tee!!
        assertEquals(listOf("Driver"), tee.clubs.map { it.club })
        assertEquals(18, tee.clubs.single().shots)
        assertEquals(33.3, tee.rightPct, 0.1)
    }

    @Test fun firstPuttsByLength() = runTest {
        val r = round("a", "2026-10-01") { n -> HoleScore(n, par = 4, strokes = 5, putts = if (n <= 2) 1 else if (n <= 4) 3 else 2, firstPuttFeet = if (n <= 2) 4 else if (n <= 4) 30 else null) }
        val bands = load(listOf(r)).puttBands
        assertEquals(listOf("4–6 ft", "20+ ft"), bands.map { it.label })
        assertEquals(100.0, bands[0].holedPct); assertEquals(100.0, bands[1].threePuttPct)
    }

    @Test fun pickingACourseAndAWindow() = runTest {
        val rounds = (1..5).map { i -> round("w$i", "2026-10-0$i") { n -> HoleScore(n, par = 4, strokes = 4 + i % 2, putts = 2) } } +
            (1..3).map { i -> round("p$i", "2026-09-0$i", course = "Portlethen Golf Club") { n -> HoleScore(n, par = 4, strokes = 6, putts = 2) } }
        val wanstead = load(rounds, StatsFilter(window = 3, course = "Wanstead Golf Club"))
        assertEquals(3, wanstead.rounds)
        assertEquals(18, wanstead.holeByHole.size)
        val all = load(rounds, StatsFilter(window = 0))
        assertEquals(8, all.rounds)
        assertTrue(all.holeByHole.isEmpty())
        assertEquals(listOf("Wanstead Golf Club", "Portlethen Golf Club"), StatsData.courses(rounds).map { it.name })
    }

    @Test fun roundsWithPickupsCountButNotForScoringAverage() = runTest {
        val r = round("a", "2026-10-01") { n -> if (n == 5) HoleScore(n, par = 4, pickedUp = true) else HoleScore(n, par = 4, strokes = 5, putts = 2) }
        val s = load(listOf(r))
        assertEquals(1, s.rounds)
        assertEquals(17, s.holes)
        assertNull(s.scoringAverage)
        assertEquals(18.0, s.toParPer18)
    }
}
