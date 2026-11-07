package me.vanpra.tally.round

import app.cash.sqldelight.adapter.primitive.IntColumnAdapter
import app.cash.sqldelight.db.SqlDriver
import kotlinx.serialization.json.Json
import me.vanpra.tally.ai.Clubs
import me.vanpra.tally.ai.NoteReading
import me.vanpra.tally.course.LatLng
import me.vanpra.tally.db.Field_source
import me.vanpra.tally.db.Hole
import me.vanpra.tally.db.Note
import me.vanpra.tally.db.Shot
import me.vanpra.tally.db.TallyDatabase
import me.vanpra.tally.platform.TextStore
import me.vanpra.tally.db.Round as RoundRow

fun tallyDatabase(driver: SqlDriver): TallyDatabase = TallyDatabase(
    driver,
    field_sourceAdapter = Field_source.Adapter(IntColumnAdapter),
    holeAdapter = Hole.Adapter(IntColumnAdapter, IntColumnAdapter, IntColumnAdapter, IntColumnAdapter, IntColumnAdapter, IntColumnAdapter, IntColumnAdapter, IntColumnAdapter),
    noteAdapter = Note.Adapter(IntColumnAdapter, IntColumnAdapter),
    shotAdapter = Shot.Adapter(IntColumnAdapter, IntColumnAdapter),
)

/** Rounds in SQLite: one row per round, hole, note, marked shot and field source. */
internal class RoundTables(private val db: TallyDatabase) {
    private val q = db.tallyQueries
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): RoundsState {
        val notes = q.allNotes().executeAsList().groupBy { it.round_id to it.hole_number }
        val shots = q.allShots().executeAsList().groupBy { it.round_id to it.hole_number }
        val sources = q.allFieldSources().executeAsList().groupBy { it.round_id to it.hole_number }
        val holes = q.allHoles().executeAsList().groupBy { it.round_id }.mapValues { (_, rows) ->
            rows.map { h ->
                val key = h.round_id to h.number
                h.toHoleScore(notes[key].orEmpty(), shots[key].orEmpty(), sources[key].orEmpty())
            }
        }
        val rounds = q.allRounds().executeAsList().map { r ->
            Round(
                id = r.id,
                courseName = r.course_name,
                courseId = r.course_id,
                tee = r.tee,
                date = r.date,
                startedAtMillis = r.started_at,
                finishedAtMillis = r.finished_at,
                source = enumOr(r.source, RoundSource.TALLY),
                holes = holes[r.id].orEmpty(),
            )
        }
        return RoundsState(rounds, activeRoundId = q.setting(ACTIVE).executeAsOneOrNull())
    }

    val isEmpty: Boolean get() = q.roundCount().executeAsOne() == 0L

    /** A whole round, its holes and everything on them. */
    fun write(round: Round) = db.transaction {
        q.deleteRound(round.id)
        q.upsertRound(round.toRow())
        round.holes.forEach { writeHoleRows(round.id, it) }
    }

    fun writeHole(roundId: String, hole: HoleScore) = db.transaction { writeHoleRows(roundId, hole) }

    fun finish(roundId: String, at: Long) { q.finishRound(at, roundId) }

    fun delete(roundId: String) { q.deleteRound(roundId) }

    fun setActive(roundId: String?) {
        if (roundId == null) q.removeSetting(ACTIVE) else q.putSetting(ACTIVE, roundId)
    }

    private fun writeHoleRows(roundId: String, h: HoleScore) {
        q.deleteHoleChildren(roundId, h.number)
        q.upsertHole(
            Hole(
                round_id = roundId,
                number = h.number,
                par = h.par,
                strokes = h.strokes,
                putts = h.putts,
                tee_shot = h.teeShot?.name,
                penalties = h.penalties,
                bunker_shots = h.bunkerShots,
                // One name per club, whoever wrote it ("1W" from Golfshot is Driver).
                tee_club = h.teeClub?.let { Clubs.normalize(it) ?: it },
                first_putt_feet = h.firstPuttFeet,
                first_putt_miss = h.firstPuttMiss?.name,
                leave_feet = h.leaveFeet,
                picked_up = h.pickedUp,
                later_putt_feet = h.laterPuttFeet.takeIf { it.isNotEmpty() }?.joinToString(","),
                gir_recorded = h.girRecorded,
                note_read_by = h.noteReadBy?.name,
                suggested = h.suggested?.let { json.encodeToString(NoteReading.serializer(), it) },
            ),
        )
        h.notes.forEachIndexed { i, n -> q.insertNote(Note(roundId, h.number, i, n.text, n.source.name, n.createdAtMillis)) }
        h.shots.forEachIndexed { i, s -> q.insertShot(Shot(roundId, h.number, i, s.position.lat, s.position.lon, s.timeMillis, s.club, s.clubByYou, s.auto)) }
        h.fieldSources.forEach { (field, source) -> q.insertFieldSource(Field_source(roundId, h.number, field, source.name)) }
    }

    private fun Hole.toHoleScore(notes: List<Note>, shots: List<Shot>, sources: List<Field_source>) = HoleScore(
        number = number,
        par = par,
        strokes = strokes,
        putts = putts,
        teeShot = tee_shot?.let { enumOrNull<TeeShot>(it) },
        penalties = penalties,
        bunkerShots = bunker_shots,
        teeClub = tee_club,
        firstPuttFeet = first_putt_feet,
        firstPuttMiss = first_putt_miss?.let { enumOrNull<PuttMiss>(it) },
        leaveFeet = leave_feet,
        pickedUp = picked_up,
        laterPuttFeet = later_putt_feet?.split(",")?.mapNotNull { it.trim().toIntOrNull() }.orEmpty(),
        girRecorded = gir_recorded,
        notes = notes.map { HoleNote(it.text, enumOr(it.source, NoteSource.TYPED), it.created_at) },
        fieldSources = sources.mapNotNull { s -> enumOrNull<FieldSource>(s.source)?.let { s.field_ to it } }.toMap(),
        noteReadBy = note_read_by?.let { enumOrNull<ReadBy>(it) },
        suggested = suggested?.let { runCatching { json.decodeFromString(NoteReading.serializer(), it) }.getOrNull() },
        shots = shots.map { ShotMark(LatLng(it.lat, it.lon), it.time_millis, it.club, it.club_by_you, it.auto) },
    )

    private fun Round.toRow() = RoundRow(id, courseName, courseId, tee, date, startedAtMillis, finishedAtMillis, source.name)

    private companion object {
        const val ACTIVE = "active_round"
    }
}

private inline fun <reified E : Enum<E>> enumOrNull(name: String): E? = enumValues<E>().firstOrNull { it.name == name }
private inline fun <reified E : Enum<E>> enumOr(name: String, fallback: E): E = enumOrNull<E>(name) ?: fallback

/**
 * Rounds used to live in rounds.json. On the first launch with the database it's copied in once, in one
 * transaction, and the file is left where it was as a backup.
 */
internal object JsonRoundsImport {
    const val FILE = "rounds.json"

    fun into(tables: RoundTables, db: TallyDatabase, store: TextStore) {
        if (!tables.isEmpty) return
        val text = store.read(FILE) ?: return
        val old = runCatching { Json { ignoreUnknownKeys = true }.decodeFromString(RoundsState.serializer(), text) }.getOrNull() ?: return
        db.transaction {
            old.rounds.forEach(tables::write)
            tables.setActive(old.activeRoundId)
        }
    }
}
