package com.example.pdr.sensor

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.pdr.viewmodel.MotionViewModel
import com.example.pdr.viewmodel.StepViewModel
import kotlin.math.sqrt

/**
 * Detects steps using the device's accelerometer and calculates the stride length for each step.
 *
 * This class implements a simple state machine to identify step events based on the accelerometer's
 * magnitude data. It uses a frequency-based model to estimate stride length, which is more robust
 * than amplitude-based models against filtering and sensor orientation issues.
 *
 * @param sensorManager The system's sensor manager to access the accelerometer.
 * @param stepViewModel The ViewModel to which new steps (with their calculated stride length) are reported.
 * @param motionViewModel The ViewModel used for motion type classification (e.g., walking, running).
 */
class StepDetector(
    private val sensorManager: SensorManager,
    private val stepViewModel: StepViewModel,
    private val motionViewModel: MotionViewModel
) : SensorEventListener {

    // Timestamp of the last detected step in milliseconds. Used for debouncing and cadence calculation.
    private var lastStepTime = 0L
    // The current state of the step detection state machine (IDLE, RISING, FALLING).
    private var stepState = "IDLE"
    // Stores the maximum acceleration magnitude recorded during the current potential step.
    private var currentStepMaxAcc = 0f
    // Stores the minimum acceleration magnitude recorded during the current potential step.
    private var currentStepMinAcc = 0f
    // A sliding window of recent accelerometer magnitude readings to smooth out the data.
    private val magnitudeWindow = mutableListOf<Float>()

    /**
     * Registers the listener for the accelerometer to start detecting steps.
     */
    fun start() {
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            // SENSOR_DELAY_GAME is a good balance between responsiveness and battery usage for this use case.
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    /**
     * Unregisters the accelerometer listener to stop step detection and save battery.
     */
    fun stop() {
        sensorManager.unregisterListener(this)
    }

    /**
     * Called when new sensor data is available. This is the core of the step detection logic.
     */
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val now = System.currentTimeMillis()
            val accX = event.values[0]
            val accY = event.values[1]
            val accZ = event.values[2]

            // Pass raw sensor data to the MotionViewModel for activity classification.
            motionViewModel.onSensorDataReceived(accX, accY, accZ)

            // Calculate the magnitude of the acceleration vector.
            val magnitude = sqrt(accX * accX + accY * accY + accZ * accZ)
            // Add the new magnitude to our sliding window.
            magnitudeWindow.add(magnitude)
            // Ensure the window does not exceed its defined size.
            if (magnitudeWindow.size > stepViewModel.windowSize.toInt()) {
                magnitudeWindow.removeAt(0)
            }
            // Use the average of the window for a smoother, less noisy signal.
            val avgMagnitude = magnitudeWindow.average().toFloat()

            // This state machine identifies the pattern of a step: a rise then a fall in acceleration.
            when (stepState) {
                // The "IDLE" state is when the system is waiting for a potential step to begin.
                "IDLE" -> if (avgMagnitude > stepViewModel.threshold) {
                    // A significant rise in acceleration suggests a step has started.
                    stepState = "RISING"
                    // Initialize the max and min acceleration for this new potential step.
                    currentStepMaxAcc = avgMagnitude
                    currentStepMinAcc = avgMagnitude // Reset min at the start of a potential step
                }
                // The "RISING" state is when acceleration is increasing towards a peak.
                "RISING" -> if (avgMagnitude > currentStepMaxAcc) {
                    // We've found a new peak in acceleration for this step.
                    currentStepMaxAcc = avgMagnitude
                } else if (avgMagnitude < currentStepMaxAcc) {
                    // Acceleration is now decreasing, so we've passed the peak. Move to the FALLING state.
                    stepState = "FALLING"
                }
                // The "FALLING" state is when acceleration is decreasing towards a valley.
                "FALLING" -> if (avgMagnitude < currentStepMinAcc) {
                    // We've found a new valley in acceleration.
                    currentStepMinAcc = avgMagnitude
                } else if (avgMagnitude > stepViewModel.threshold) {
                    // Acceleration has risen again, indicating the step cycle is complete.
                    // Debounce to prevent multiple detections for a single physical step.
                    if (now - lastStepTime > stepViewModel.debounce.toLong()) {
                        // Calculate stride length and cadence.
                        val (strideLength, stepFrequency) = calculateStrideAndCadence(now)
                        // Report the new step, its stride length, and its cadence to the ViewModel.
                        stepViewModel.addStep(strideLength, stepFrequency)
                        lastStepTime = now
                    }
                    // Reset the state machine to wait for the next step.
                    stepState = "IDLE"
                    currentStepMaxAcc = 0f
                    currentStepMinAcc = 0f
                }
            }
        }
    }

    /**
     * Calculates the user's stride length and cadence.
     *
     * @param currentTime The timestamp of the current step, used for calculating cadence.
     * @return A Pair containing the calculated stride length in cm and the cadence in steps/sec.
     */
    private fun calculateStrideAndCadence(currentTime: Long): Pair<Float, Float> {
        // 1. Calculate Step Frequency (Steps per second) / Cadence
        val timeDiffSeconds = ((currentTime - lastStepTime) / 1000f).coerceAtLeast(0.2f)
        val stepFrequency = 1f / timeDiffSeconds

        // 2. Get User Height (in cm) from the ViewModel.
        val userHeightCm = stepViewModel.height.toFloatOrNull() ?: 175f

        // 3. Algorithm: A linear model relating stride length to step frequency and height.
        // The formula is: StrideLength = Height * (K * Frequency + C)
        // K and C are now sourced from the StepViewModel to be tunable from the UI.
        val K = stepViewModel.kValue
        val C = stepViewModel.cValue

        // The relationship is not purely linear; running has a different gait. We use a larger
        // coefficient if the frequency suggests the user is running (e.g., > 2.0 steps/sec).
        val dynamicK = if (stepFrequency > 2.0f) 0.5f else K

        // Calculate the final stride length in centimeters.
        val calculatedStrideCm = userHeightCm * (dynamicK * stepFrequency + C)

        // 4. Sanity Check / Clamping
        // Clamp the result to a realistic range based on the user's height to filter out anomalies.
        val minStride = userHeightCm * 0.2f // A very small step
        val maxStride = userHeightCm * 1.2f // A very large leap
        val finalStride = calculatedStrideCm.coerceIn(minStride, maxStride)

        return Pair(finalStride, stepFrequency)
    }

    /**
     * Unused for this implementation, but required by the SensorEventListener interface.
     */
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
