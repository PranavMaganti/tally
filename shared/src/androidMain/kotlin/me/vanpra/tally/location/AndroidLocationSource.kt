package me.vanpra.tally.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import me.vanpra.tally.course.LatLng

/**
 * Raw GPS through [LocationManager]: no Play Services dependency, and the chip is only on while
 * [fixes] is being collected. One update per second at most, and only after moving 1 m.
 */
class AndroidLocationSource(context: Context) : LocationSource {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(LocationManager::class.java)
    private val permitted = MutableStateFlow(hasPermission())

    /** Call after the permission dialog closes so collection starts without restarting the screen. */
    fun onPermissionResult() {
        permitted.value = hasPermission()
    }

    private fun hasPermission() =
        appContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun fixes(): Flow<LocationFix> = permitted.flatMapLatest { if (it) gpsUpdates() else emptyFlow() }

    @SuppressLint("MissingPermission") // only called once permission is granted
    private fun gpsUpdates(): Flow<LocationFix> = callbackFlow {
        val motion = StillnessDetector(appContext)
        motion.start()
        val listener = LocationListener { trySend(it.toFix(motion.still)) }
        manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?.takeIf { SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos < 30_000_000_000L }
            ?.let { trySend(it.toFix(false)) }
        // Every fix, once a second: the smoother needs the small movements too to tell jitter from walking.
        manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, listener, Looper.getMainLooper())
        awaitClose {
            manager.removeUpdates(listener)
            motion.stop()
        }
    }

    private fun Location.toFix(still: Boolean) = LocationFix(LatLng(latitude, longitude), accuracy.toDouble(), time, still)
}
