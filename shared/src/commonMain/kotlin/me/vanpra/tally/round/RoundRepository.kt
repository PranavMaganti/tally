package me.vanpra.tally.round

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import me.vanpra.tally.course.Course
import me.vanpra.tally.db.TallyDatabase
import me.vanpra.tally.platform.TextStore
import me.vanpra.tally.platform.currentTimeMillis
import me.vanpra.tally.platform.todayIso

@Serializable
data class RoundsState(
    val rounds: List<Round> = emptyList(),
    val activeRoundId: String? = null,
) {
    val activeRound: Round? get() = activeRoundId?.let { id -> rounds.firstOrNull { it.id == id } }
    /** Newest first. */
    val history: List<Round> get() = rounds.filter { it.id != activeRoundId }.sortedWith(compareByDescending<Round> { it.date }.thenByDescending { it.startedAtMillis ?: 0 })
}

/**
 * All rounds: kept in memory for the screens, saved to the database as each change is made. Writes go
 * through one queue, in order, off the main thread, and only touch what changed (a hole, not every round).
 * With no database (previews) everything still works, it just isn't kept.
 */
class RoundRepository(
    /** The database behind it, for queries like stats. Null in previews. */
    val db: TallyDatabase?,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
    /** Where rounds were kept before the database; imported once if the database is empty. */
    legacy: TextStore? = null,
) {
    private val tables = db?.let(::RoundTables)
    private val _state = MutableStateFlow(load(legacy))
    val state: StateFlow<RoundsState> = _state.asStateFlow()

    private val writes = Channel<(RoundTables) -> Unit>(Channel.UNLIMITED)

    init {
        if (tables != null) scope.launch(ioDispatcher) { for (w in writes) w(tables) }
    }

    private fun load(legacy: TextStore?): RoundsState {
        val t = tables ?: return RoundsState()
        if (legacy != null) JsonRoundsImport.into(t, db!!, legacy)
        return t.load()
    }

    private fun persist(write: (RoundTables) -> Unit) {
        if (tables != null) writes.trySend(write)
    }

    fun startRound(course: Course): Round {
        val now = currentTimeMillis()
        val round = Round(
            id = "tally-$now",
            courseName = course.name,
            courseId = course.id,
            date = todayIso(),
            startedAtMillis = now,
            holes = course.holes.map { HoleScore(number = it.number, par = it.par) },
        )
        _state.value = _state.value.let { it.copy(rounds = it.rounds + round, activeRoundId = round.id) }
        persist { it.write(round); it.setActive(round.id) }
        return round
    }

    fun saveHole(roundId: String, hole: HoleScore) {
        _state.value = _state.value.let { s ->
            s.copy(rounds = s.rounds.map { r ->
                if (r.id != roundId) r else r.copy(holes = (r.holes.filter { it.number != hole.number } + hole).sortedBy { it.number })
            })
        }
        persist { it.writeHole(roundId, hole) }
    }

    /** Changes one hole from its latest saved state, so a background update can't undo a newer edit. */
    fun updateHole(roundId: String, number: Int, transform: (HoleScore) -> HoleScore) {
        val hole = _state.value.rounds.firstOrNull { it.id == roundId }?.hole(number) ?: return
        saveHole(roundId, transform(hole))
    }

    fun finishRound(roundId: String) {
        val round = _state.value.rounds.firstOrNull { it.id == roundId } ?: return
        val at = currentTimeMillis()
        // A round with nothing entered isn't worth keeping.
        val keep = round.done.isNotEmpty()
        val wasActive = _state.value.activeRoundId == roundId
        _state.value = _state.value.let { s ->
            s.copy(
                rounds = if (keep) s.rounds.map { if (it.id == roundId) it.copy(finishedAtMillis = at) else it } else s.rounds.filter { it.id != roundId },
                activeRoundId = s.activeRoundId.takeIf { it != roundId },
            )
        }
        persist {
            if (keep) it.finish(roundId, at) else it.delete(roundId)
            if (wasActive) it.setActive(null)
        }
    }

    fun deleteRound(roundId: String) {
        val wasActive = _state.value.activeRoundId == roundId
        _state.value = _state.value.let { s -> s.copy(rounds = s.rounds.filter { it.id != roundId }, activeRoundId = s.activeRoundId.takeIf { it != roundId }) }
        persist {
            it.delete(roundId)
            if (wasActive) it.setActive(null)
        }
    }

    /** Adds rounds not already present (matched by id). Returns how many were new. */
    fun importRounds(rounds: List<Round>): Int {
        val existing = _state.value.rounds.map { it.id }.toSet()
        val fresh = rounds.filter { it.id !in existing }
        if (fresh.isNotEmpty()) {
            _state.value = _state.value.let { it.copy(rounds = it.rounds + fresh) }
            persist { t -> fresh.forEach(t::write) }
        }
        return fresh.size
    }
}
