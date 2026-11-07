package me.vanpra.tally.round

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.vanpra.tally.platform.TextStore

/** The clubs you carry and how far you say you hit each one (total yards, carry plus roll). */
@Serializable
data class Bag(
    val clubs: List<String> = BagClubs.STARTER,
    val yards: Map<String, Int> = emptyMap(),
) {
    /** How far a club goes for you: what Tally has measured once there's enough, else your number, else a typical one. */
    fun distanceFor(club: String, tracked: ClubDistance?): ClubYardage = when {
        tracked != null && tracked.shots >= BagClubs.TRUST_TRACKED_AFTER -> ClubYardage(tracked.typicalYards, YardageSource.TRACKED)
        yards[club] != null -> ClubYardage(yards.getValue(club), YardageSource.YOURS)
        else -> ClubYardage(BagClubs.TYPICAL[club] ?: 0, YardageSource.TYPICAL)
    }
}

enum class YardageSource { TRACKED, YOURS, TYPICAL }
data class ClubYardage(val yards: Int, val source: YardageSource)

object BagClubs {
    /** Every club Tally knows, longest first. Putter isn't listed: it never needs a distance. */
    val ALL = listOf("Driver", "3W", "5W", "7W", "Hybrid", "2i", "3i", "4i", "5i", "6i", "7i", "8i", "9i", "PW", "GW", "SW", "LW")

    /** A usual set to start from: 13 clubs plus the putter. */
    val STARTER = listOf("Driver", "3W", "5W", "Hybrid", "5i", "6i", "7i", "8i", "9i", "PW", "GW", "SW", "LW")

    /** Rough totals for a mid-handicap golfer, used until you give your own or Tally has measured yours. */
    val TYPICAL = mapOf(
        "Driver" to 230, "3W" to 210, "5W" to 195, "7W" to 185, "Hybrid" to 185,
        "2i" to 200, "3i" to 190, "4i" to 180, "5i" to 170, "6i" to 160, "7i" to 150, "8i" to 140, "9i" to 130,
        "PW" to 120, "GW" to 105, "SW" to 90, "LW" to 70,
    )

    /** Tracked shots with a club before Tally prefers its measurement over your number. */
    const val TRUST_TRACKED_AFTER = 5

    fun order(clubs: Collection<String>) = clubs.sortedBy { ALL.indexOf(it).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE }
}

/** Your bag, saved on the phone. */
class BagStore(private val store: TextStore) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _bag = MutableStateFlow(store.read(FILE)?.let { runCatching { json.decodeFromString<Bag>(it) }.getOrNull() } ?: Bag())
    val bag: StateFlow<Bag> = _bag.asStateFlow()

    fun toggle(club: String) = change { b ->
        b.copy(clubs = BagClubs.order(if (club in b.clubs) b.clubs - club else b.clubs + club))
    }

    /** Your distance for a club; null clears it back to typical. */
    fun setYards(club: String, yards: Int?) = change { b ->
        b.copy(yards = if (yards == null || yards <= 0) b.yards - club else b.yards + (club to yards.coerceAtMost(400)))
    }

    private fun change(block: (Bag) -> Bag) {
        _bag.value = block(_bag.value)
        store.write(FILE, json.encodeToString(Bag.serializer(), _bag.value))
    }

    private companion object { const val FILE = "bag.json" }
}
