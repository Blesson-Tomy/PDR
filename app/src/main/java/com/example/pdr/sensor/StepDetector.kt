package com.example.pdr.sensor

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.pdr.viewmodel.MotionViewModel
import com.example.pdr.viewmodel.StepViewModel
import kotlin.math.sqrt

class StepDetector(
    private val sensorManager: SensorManager,
    private val stepViewModel: StepViewModel,
    private val motionViewModel: MotionViewModel // Added MotionViewModel
) : SensorEventListener {

    private var lastStepTime = 0L
    private var stepState = "IDLE"
    private var candidatePeak = 0f
    private val magnitudeWindow = mutableListOf<Float>()

    // Latest accelerometer values
    private var lastAccX = 0f
    private var lastAccY = 0f
    private var lastAccZ = 0f

    // Latest gyroscope values
    private var lastGyroX = 0f
    private var lastGyroY = 0f
    private var lastGyroZ = 0f

    fun start() {
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val now = System.currentTimeMillis()

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                lastAccX = event.values[0]
                lastAccY = event.values[1]
                lastAccZ = event.values[2]

                // Step detection logic remains the same
                val magnitude = sqrt(lastAccX * lastAccX + lastAccY * lastAccY + lastAccZ * lastAccZ)
                magnitudeWindow.add(magnitude)
                if (magnitudeWindow.size > stepViewModel.windowSize.toInt()) magnitudeWindow.removeAt(0)
                val avgMagnitude = magnitudeWindow.average().toFloat()

                when (stepState) {
                    "IDLE" -> if (avgMagnitude > stepViewModel.threshold) {
                        stepState = "RISING"
                        candidatePeak = avgMagnitude
                    }
                    "RISING" -> if (avgMagnitude > candidatePeak) {
                        candidatePeak = avgMagnitude
                    } else if (avgMagnitude < candidatePeak) {
                        stepState = "FALLING"
                    }
                    "FALLING" -> if (avgMagnitude < stepViewModel.threshold) {
                        if (now - lastStepTime > stepViewModel.debounce.toLong()) {
                            stepViewModel.addStepDot()
                            lastStepTime = now
                        }
                        stepState = "IDLE"
                        candidatePeak = 0f
                    }
                }
            }
            Sensor.TYPE_GYROSCOPE -> {
                lastGyroX = event.values[0]
                lastGyroY = event.values[1]
                lastGyroZ = event.values[2]
            }
        }

        // Pass combined sensor data to the MotionViewModel
        motionViewModel.onSensorDataReceived(
            accX = lastAccX,
            accY = lastAccY,
            accZ = lastAccZ,
            gyroX = lastGyroX,
            gyroY = lastGyroY,
            gyroZ = lastGyroZ
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
