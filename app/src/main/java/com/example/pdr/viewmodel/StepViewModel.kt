package com.example.pdr.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.compose.ui.geometry.Offset

/**
 * Manages the state and logic for the Pedestrian Dead Reckoning (PDR) system.
 *
 * This ViewModel holds the list of points that make up the user's path, as well as the
 * configuration settings for the step detection algorithm (threshold, window size, etc.).
 * It is responsible for adding new points to the path and clearing it.
 */
class StepViewModel : ViewModel() {
    // A list of all the points (steps) on the PDR path. This is observed by the Canvas to draw the path.
    val points = mutableStateListOf<Offset>()

    // The minimum acceleration magnitude required to trigger the start of a potential step.
    var threshold by mutableFloatStateOf(12f)
    // The number of accelerometer readings to average over for smoothing the data.
    var windowSize by mutableFloatStateOf(6f)
    // The number of recent steps to include in the average cadence calculation.
    var cadenceAverageSize by mutableFloatStateOf(5f)
    // The minimum time (in milliseconds) that must pass between two detected steps to prevent double counting.
    var debounce by mutableFloatStateOf(300f)

    // The user's height in centimeters, provided via the settings screen.
    var height by mutableStateOf("170")

    // --- Stride Calculation Parameters ---
    // Coefficient for the impact of frequency on stride length.
    var kValue by mutableFloatStateOf(0.37f)
    // Intercept constant for the base stride-to-height ratio.
    var cValue by mutableFloatStateOf(0.15f)

    // --- UI Control ---
    // If true, the canvas will show the static floor plan. Otherwi se, it shows the live PDR path.
    var showFloorPlan by mutableStateOf(true)
    // When true, the next tap on the canvas will set a new origin point.
    var isSettingOrigin by mutableStateOf(false)

    // The current heading of the device in radians, updated by the HeadingDetector.
    var heading by mutableFloatStateOf(0f)

    // The most recently calculated stride length in centimeters. Exposed to the UI for display.
    var lastStrideLengthCm by mutableFloatStateOf(0f)

    // The calculated average cadence (steps per second) over the last few steps.
    var averageCadence by mutableFloatStateOf(0f)

    // A list to store the cadence of the most recent steps.
    private val recentCadences = mutableStateListOf<Float>()

    // The coordinates of the last point added to the canvas. Used to calculate the next point's position.
    private var lastX by mutableFloatStateOf(0f)  // start at center
    private var lastY by mutableFloatStateOf(0f)

    // A scaling factor to convert the real-world stride length (in cm) to on-screen pixels for drawing.
    private val pixelsPerCm = 0.5f

    /**
     * Adds a new step to the PDR path and updates the average cadence.
     *
     * @param strideLengthCm The length of the step in real-world centimeters.
     * @param cadence The cadence of the step in steps per second.
     */
    fun addStep(strideLengthCm: Float, cadence: Float) {
        lastStrideLengthCm = strideLengthCm // Store the real-world value for display.

        // Add the new cadence to our list.
        recentCadences.add(cadence)
        // Ensure the list doesn't grow larger than the desired average size.
        while (recentCadences.size > cadenceAverageSize.toInt()) {
            recentCadences.removeAt(0)
        }
        // Update the average cadence.
        averageCadence = recentCadences.average().toFloat()

        // Convert the real-world stride length into a value that can be drawn on the canvas.
        val strideInPixels = strideLengthCm * pixelsPerCm

        val newPoint: Offset
        if (points.isEmpty()) {
            // If this is the first point, start it at the origin (0,0).
            newPoint = Offset(lastX, lastY)
        } else {
            // Calculate the new point's position based on the last point, the current heading, and the stride length.
            val newX = lastX + strideInPixels * kotlin.math.sin(heading)
            val newY = lastY - strideInPixels * kotlin.math.cos(heading) // Y is inverted in canvas coordinates.
            newPoint = Offset(newX, newY)
            // Update the last position for the next calculation.
            lastX = newX
            lastY = newY
        }

        points.add(newPoint)
    }

    /**
     * Clears all points from the PDR path and resets the position to the origin.
     */
    fun clearDots() {
        points.clear()
        lastX = 0f
        lastY = 0f
        recentCadences.clear()
        averageCadence = 0f
    }

    /**
     * Toggles the state for setting a new origin point on the map.
     */
    fun toggleIsSettingOrigin() {
        isSettingOrigin = !isSettingOrigin
    }

    /**
     * Resets the PDR path to a new user-defined starting point.
     *
     * @param newOrigin The canvas coordinates for the new starting point.
     */
    fun setNewOrigin(newOrigin: Offset) {
        points.clear()
        lastX = newOrigin.x
        lastY = newOrigin.y
        points.add(newOrigin) // Add the new origin as the first point
        isSettingOrigin = false // Exit origin setting mode
    }
}
