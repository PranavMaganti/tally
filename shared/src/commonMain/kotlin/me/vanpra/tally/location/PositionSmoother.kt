package me.vanpra.tally.location

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.vanpra.tally.course.LatLng
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Steadies GPS. Tracks where you are and how fast you're moving, trusting each fix in proportion to its
 * accuracy: standing still the position settles instead of wandering a few metres a second, and walking
 * is followed without lagging behind. A fix far from the estimate (you've moved to the next tee) resets it.
 *
 * A constant-velocity Kalman filter per axis, in metres on a local plane.
 */
class PositionSmoother(
    /** How sharply you might speed up or slow down, m/s². Lower steadies more. */
    private val acceleration: Double = 0.2,
    /** Beyond this many metres from the estimate, a fix is taken as-is. */
    private val resetMetres: Double = 40.0,
) {
    private class Axis {
        var p = 0.0; var v = 0.0
        var pp = 0.0; var pv = 0.0; var vv = 0.0
        fun reset(pos: Double, r: Double) { p = pos; v = 0.0; pp = r; pv = 0.0; vv = 1.0 }
        /** Not moving: no velocity, and no doubt about it. */
        fun stop() { v = 0.0; pv = 0.0; vv = 0.0 }
        fun predict(dt: Double, q: Double) {
            p += v * dt
            val dt2 = dt * dt
            pp += 2 * dt * pv + dt2 * vv + q * dt2 * dt2 / 4
            pv += dt * vv + q * dt2 * dt / 2
            vv += q * dt2
        }
        fun update(z: Double, r: Double) {
            val s = pp + r
            val kp = pp / s; val kv = pv / s
            val y = z - p
            p += kp * y; v += kv * y
            vv -= kv * pv; pv -= kv * pp; pp -= kp * pp
        }
    }

    private val x = Axis()
    private val y = Axis()
    private var originLat = 0.0
    private var originLon = 0.0
    private var mPerLon = 0.0
    private var lastMillis = -1L

    private val mPerLat = 111_320.0

    private companion object {
        const val MIN_ACCURACY_M = 4.0
        /** Only enough to let a slow drift through over a minute or so. */
        const val STILL_ACCELERATION = 0.01
    }

    fun add(fix: LocationFix): LocationFix {
        // Phones often claim ±1–2 m while the reading still wobbles 3–4 m: never trust a fix more than ±4 m.
        val r = fix.accuracyMetres.coerceAtLeast(MIN_ACCURACY_M).let { it * it }
        if (lastMillis >= 0) {
            val zx = (fix.position.lon - originLon) * mPerLon
            val zy = (fix.position.lat - originLat) * mPerLat
            val dt = ((fix.timeMillis - lastMillis) / 1000.0).coerceIn(0.0, 30.0)
            // Standing still (the motion sensor says so): you aren't going anywhere, so every reading is just
            // another look at the same spot. Average them in place instead of following the wobble.
            val q = if (fix.still) { x.stop(); y.stop(); STILL_ACCELERATION * STILL_ACCELERATION } else acceleration * acceleration
            x.predict(dt, q)
            y.predict(dt, q)
            val off = sqrt((zx - x.p) * (zx - x.p) + (zy - y.p) * (zy - y.p))
            if (off <= resetMetres) {
                x.update(zx, r); y.update(zy, r)
                lastMillis = fix.timeMillis
                val pos = LatLng(originLat + y.p / mPerLat, originLon + x.p / mPerLon)
                return LocationFix(pos, sqrt((x.pp + y.pp) / 2), fix.timeMillis)
            }
        }
        originLat = fix.position.lat; originLon = fix.position.lon
        mPerLon = mPerLat * cos(originLat * PI / 180)
        x.reset(0.0, r); y.reset(0.0, r)
        lastMillis = fix.timeMillis
        return fix
    }
}

/** The same fixes, steadied. */
fun Flow<LocationFix>.smoothed(): Flow<LocationFix> = flow {
    val smoother = PositionSmoother()
    collect { emit(smoother.add(it)) }
}
