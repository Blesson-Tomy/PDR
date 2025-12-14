package com.example.pdr.sensor

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.pdr.viewmodel.MotionViewModel
import com.example.pdr.viewmodel.StepViewModel
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Detects steps using the device's accelerometer and calculates the stride length for each step.
 *
 * This class implements a simple state machine to identify step events based on the accelerometer's
 * magnitude data. It uses the Weinberg model to estimate stride length, incorporating factors like
 * acceleration peaks, user height, and cadence (time between steps).
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

    // A calibration constant for the Weinberg stride length model. This value can be tuned
    // for better accuracy or made a user-configurable setting.
    private val K = 0.4f

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
                        // Calculate stride length based on the recorded min/max acceleration.
                        val strideLength = calculateStrideLength(now)
                        // Report the new step and its stride length to the ViewModel.
                        stepViewModel.addStep(strideLength)
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
     * Calculates the user's stride length in centimeters using a formula that combines
     * acceleration, user height, and cadence.
     *
     * @param currentTime The timestamp of the current step, used for calculating cadence.
     * @return The calculated stride length in centimeters.
     */
    private fun calculateStrideLength(currentTime: Long): Float {
        // The difference between peak (max) and valley (min) acceleration during the step.
        val accDiff = currentStepMaxAcc - currentStepMinAcc
        // Safely get the user's height from the ViewModel, with a default fallback.
        val userHeight = stepViewModel.height.toFloatOrNull() ?: 170f
        // Time elapsed since the last step, in seconds. This represents the cadence.
        val timeDiff = (currentTime - lastStepTime) / 1000f // in seconds

        // **Weinberg Model**: A common algorithm for stride length estimation.
        // It states that stride length is proportional to the 4th root of the acceleration difference.
        // The constant K is a calibration factor.
        val weinbergStride = K * accDiff.pow(0.25f)

        // **Adjustment Factors**: We refine the Weinberg model with other data.
        // Normalize by the user's height relative to an average height (170cm). Taller people generally have longer strides.
        val heightFactor = userHeight / 170f
        // Increase stride length slightly for a faster cadence (shorter time between steps).
        val cadenceFactor = if (timeDiff > 0.5f) 1.0f else 1.0f + (0.5f - timeDiff) // walk faster, stride longer

        // Combine the base stride with the adjustment factors and convert to centimeters.
        val finalStrideCm = (weinbergStride * heightFactor * cadenceFactor) * 100f // Convert to cm

        // Finally, clamp the result to a realistic range (e.g., 30cm to 150cm) to filter out noise or errors.
        return finalStrideCm.coerceIn(30f, 150f)
    }

    /**
     * Unused for this implementation, but required by the SensorEventListener interface.
     */
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
