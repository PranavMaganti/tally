package me.vanpra.tally.location

import me.vanpra.tally.course.LatLng
import me.vanpra.tally.course.distanceMetres
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

class PositionSmootherTest {
    private val spot = LatLng(51.5697687, 0.0330596) // Wanstead 1st tee

    /** Standing still with ±5 m GPS noise: the smoothed position should wander far less than the raw one. */
    @Test fun settlesWhenStandingStill() {
        val rnd = Random(7)
        val s = PositionSmoother()
        val raw = mutableListOf<Double>(); val smooth = mutableListOf<Double>()
        repeat(90) { i ->
            val noisy = LatLng(spot.lat + rnd.nextDouble(-1.0, 1.0) * 4.5e-5, spot.lon + rnd.nextDouble(-1.0, 1.0) * 7e-5)
            val out = s.add(LocationFix(noisy, 5.0, i * 1000L))
            if (i > 15) { raw += distanceMetres(noisy, spot); smooth += distanceMetres(out.position, spot) }
        }
        fun rms(a: List<Double>) = kotlin.math.sqrt(a.sumOf { it * it } / a.size)
        assertTrue(rms(smooth) < rms(raw) * 0.6, "smoothed ${rms(smooth)} m vs raw ${rms(raw)} m")
    }

    /** Walking 1.4 m/s for 30 s: the estimate keeps up (within a few metres) rather than lagging behind. */
    @Test fun followsWalking() {
        val s = PositionSmoother()
        var out = spot
        repeat(31) { i ->
            val p = LatLng(spot.lat + i * 1.4 / 111_320.0, spot.lon)
            out = s.add(LocationFix(p, 5.0, i * 1000L)).position
        }
        val truth = LatLng(spot.lat + 30 * 1.4 / 111_320.0, spot.lon)
        assertTrue(distanceMetres(out, truth) < 4.0, "lag ${distanceMetres(out, truth)} m")
    }

    /** Arriving at the next tee (a jump of 200 m) is taken straight away. */
    @Test fun jumpsResetIt() {
        val s = PositionSmoother()
        s.add(LocationFix(spot, 5.0, 0))
        val far = LatLng(spot.lat + 200 / 111_320.0, spot.lon)
        val out = s.add(LocationFix(far, 5.0, 60_000))
        assertTrue(distanceMetres(out.position, far) < 0.5)
    }

    /** Standing still and the motion sensor knows it: the position averages in place and barely moves. */
    @Test fun holdsStillWhenTheSensorSaysSo() {
        val rnd = Random(11)
        val s = PositionSmoother()
        val raw = mutableListOf<Double>(); val smooth = mutableListOf<Double>()
        repeat(90) { i ->
            val noisy = LatLng(spot.lat + rnd.nextDouble(-1.0, 1.0) * 4.5e-5, spot.lon + rnd.nextDouble(-1.0, 1.0) * 7e-5)
            val out = s.add(LocationFix(noisy, 5.0, i * 1000L, still = i > 5))
            if (i > 30) { raw += distanceMetres(noisy, spot); smooth += distanceMetres(out.position, spot) }
        }
        fun rms(a: List<Double>) = kotlin.math.sqrt(a.sumOf { it * it } / a.size)
        assertTrue(rms(smooth) < rms(raw) * 0.3, "still: smoothed ${rms(smooth)} m vs raw ${rms(raw)} m")
    }
}
