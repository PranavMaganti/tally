package me.vanpra.tally.round

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import me.vanpra.tally.ai.NoteReading
import me.vanpra.tally.course.LatLng
import me.vanpra.tally.platform.InMemoryTextStore
import kotlin.test.Test
import kotlin.test.assertEquals

class RoundDatabaseTest {
    private val hole = HoleScore(
        number = 7, par = 5, strokes = 6, putts = 3, teeShot = TeeShot.RIGHT, penalties = 1, bunkerShots = 1, teeClub = "Driver",
        firstPuttFeet = 30, firstPuttMiss = PuttMiss.LONG, leaveFeet = 5, laterPuttFeet = listOf(4),
        notes = listOf(HoleNote("6, driver right", NoteSource.TYPED, 1_000), HoleNote("6, driver right, 3 putts", NoteSource.VOICE, 2_000)),
        fieldSources = mapOf("strokes" to FieldSource.AI, "putts" to FieldSource.MANUAL),
        noteReadBy = ReadBy.ON_DEVICE,
        suggested = NoteReading(putts = 2),
        shots = listOf(ShotMark(LatLng(51.5, 0.03), 500, "Driver", clubByYou = true), ShotMark(LatLng(51.501, 0.031), 600, auto = true)),
    )
    private val round = Round("r1", "Wanstead Golf Club", courseId = "osm", tee = "White", date = "2026-11-07", startedAtMillis = 10, finishedAtMillis = 20, holes = listOf(hole, HoleScore(8, par = 4, pickedUp = true)))

    @Test fun everyFieldSurvivesTheDatabase() = runTest {
        val db = tallyDatabase(testDriver())
        RoundRepository(db, backgroundScope, Dispatchers.Unconfined).importRounds(listOf(round))
        assertEquals(round, RoundRepository(db, backgroundScope, Dispatchers.Unconfined).state.value.rounds.single())
    }

    @Test fun roundsJsonIsImportedOnceAndKept() = runTest {
        val store = InMemoryTextStore()
        val json = Json.encodeToString(RoundsState.serializer(), RoundsState(listOf(round, round.copy(id = "r2", finishedAtMillis = null)), activeRoundId = "r2"))
        store.write("rounds.json", json)
        val db = tallyDatabase(testDriver())
        val state = RoundRepository(db, backgroundScope, Dispatchers.Unconfined, legacy = store).state.value
        assertEquals(2, state.rounds.size)
        assertEquals("r2", state.activeRoundId)
        assertEquals(round, state.rounds.first { it.id == "r1" })
        // The file stays as a backup, and isn't imported again over newer data.
        assertEquals(json, store.read("rounds.json"))
        RoundRepository(db, backgroundScope, Dispatchers.Unconfined).deleteRound("r1")
        assertEquals(listOf("r2"), RoundRepository(db, backgroundScope, Dispatchers.Unconfined, legacy = store).state.value.rounds.map { it.id })
    }

    @Test fun savingAHoleOnlyChangesThatHole() = runTest {
        val db = tallyDatabase(testDriver())
        val repo = RoundRepository(db, backgroundScope, Dispatchers.Unconfined)
        repo.importRounds(listOf(round))
        repo.updateHole("r1", 7) { it.copy(shots = it.shots.dropLast(1), notes = it.notes.take(1)) }
        val reloaded = RoundRepository(db, backgroundScope, Dispatchers.Unconfined).state.value.rounds.single()
        assertEquals(1, reloaded.hole(7)!!.shots.size)
        assertEquals(1, reloaded.hole(7)!!.notes.size)
        assertEquals(true, reloaded.hole(8)!!.pickedUp)
    }
}
