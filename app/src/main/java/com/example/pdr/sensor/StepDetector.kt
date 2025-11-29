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
    private val motionViewModel: MotionViewModel
) : SensorEventListener {

    private var lastStepTime = 0L
    private var stepState = "IDLE"
    private var candidatePeak = 0f
    private val magnitudeWindow = mutableListOf<Float>()

    fun start() {
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val now = System.currentTimeMillis()
            val accX = event.values[0]
            val accY = event.values[1]
            val accZ = event.values[2]

            // Pass accelerometer data to the MotionViewModel for classification
            motionViewModel.onSensorDataReceived(accX, accY, accZ)

            // --- Original Step Detection Logic ---
            val magnitude = sqrt(accX * accX + accY * accY + accZ * accZ)
            magnitudeWindow.add(magnitude)
            if (magnitudeWindow.size > stepViewModel.windowSize.toInt()) {
                magnitudeWindow.removeAt(0)
            }
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
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
