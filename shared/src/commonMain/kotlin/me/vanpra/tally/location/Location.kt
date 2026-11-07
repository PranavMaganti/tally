package me.vanpra.tally.location

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import me.vanpra.tally.course.LatLng

/** One GPS reading. [still] is true when the phone's motion sensor says you're standing still. */
data class LocationFix(val position: LatLng, val accuracyMetres: Double, val timeMillis: Long, val still: Boolean = false)

/**
 * Position updates. Implementations must only run the GPS while the flow is collected,
 * so collecting it with a lifecycle-aware collector switches GPS off whenever the screen isn't visible.
 */
fun interface LocationSource {
    fun fixes(): Flow<LocationFix>
}

object NoLocation : LocationSource {
    override fun fixes(): Flow<LocationFix> = emptyFlow()
}
