package me.henneke.wearauthn.service

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/** Reports when the watch is taken off the wrist after having been worn. */
class OffWristMonitor(context: Context, private val onTakenOff: () -> Unit) : SensorEventListener {

    private val sensorManager = context.getSystemService(SensorManager::class.java)

    // The wake-up variant also reports while the watch sleeps.
    private val sensor =
        sensorManager?.getDefaultSensor(Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT, true)

    private var wrist = WristState()

    fun start() {
        wrist = WristState()
        sensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (wrist.report(onWrist = event.values[0] != 0f))
            onTakenOff()
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
}

/**
 * Tracks whether the watch is worn. The sensor's first report is the current state, so a watch
 * that starts out off the wrist, for example on its charger, has not been taken off.
 */
internal class WristState {
    private var worn = false

    /** Returns true if this report means the watch was just taken off. */
    fun report(onWrist: Boolean): Boolean {
        val takenOff = worn && !onWrist
        worn = onWrist
        return takenOff
    }
}
