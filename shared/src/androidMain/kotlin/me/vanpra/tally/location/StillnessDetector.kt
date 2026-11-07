package me.vanpra.tally.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * Whether you're standing still, from the accelerometer: walking shakes the phone by a couple of m/s²
 * every step; standing over a putt, even holding the phone, it barely moves. Needs no permission and
 * runs only while GPS does (the hole screen is visible), ten readings a second, batched to save power.
 */
internal class StillnessDetector(context: Context) : SensorEventListener {
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val accelerometer = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    /** Read from the GPS thread; written from the sensor thread. */
    @Volatile var still = false
        private set

    private val window = ArrayDeque<Pair<Long, Float>>()
    private var calmSince = -1L

    fun start() {
        accelerometer?.let { sensors?.registerListener(this, it, 100_000, 1_000_000) }
    }

    fun stop() {
        sensors?.unregisterListener(this)
        still = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        val (x, y, z) = Triple(event.values[0], event.values[1], event.values[2])
        val t = event.timestamp / 1_000_000 // ms
        window.addLast(t to sqrt(x * x + y * y + z * z))
        while (window.isNotEmpty() && t - window.first().first > WINDOW_MS) window.removeFirst()
        if (window.size < 5) return
        val mean = window.sumOf { it.second.toDouble() } / window.size
        val spread = sqrt(window.sumOf { (it.second - mean) * (it.second - mean) } / window.size)
        when {
            // Hysteresis: settle only after a couple of calm seconds; any real movement ends it at once.
            spread > MOVING -> { still = false; calmSince = -1 }
            spread < CALM -> {
                if (calmSince < 0) calmSince = t
                if (t - calmSince >= SETTLE_MS) still = true
            }
            else -> calmSince = -1
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private companion object {
        const val WINDOW_MS = 1_500L
        const val SETTLE_MS = 2_000L
        const val CALM = 0.35   // m/s²: holding the phone, looking at it
        const val MOVING = 0.9  // m/s²: walking
    }
}
