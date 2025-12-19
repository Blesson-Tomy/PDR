package com.example.pdr.model

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * Detects the device's heading (compass direction) using the rotation vector sensor.
 * Reports heading via callback instead of LiveData.
 * Pure model class with no UI dependencies.
 */
class HeadingDetector(private val sensorManager: SensorManager) : SensorEventListener {

    // Callback for heading updates (in radians)
    var onHeadingChanged: ((heading: Float) -> Unit)? = null

    /**
     * Starts listening for rotation vector sensor events.
     */
    fun start() {
        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    /**
     * Stops listening for rotation vector sensor events.
     */
    fun stop() {
        sensorManager.unregisterListener(this)
    }

    /**
     * Called when rotation vector data is available. Calculates and reports the heading.
     */
    override fun onSensorChanged(event: SensorEvent) {
        val rotationMatrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

        val orientationAngles = FloatArray(3)
        SensorManager.getOrientation(rotationMatrix, orientationAngles)

        // Report heading (azimuth in radians) via callback
        onHeadingChanged?.invoke(orientationAngles[0])
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
