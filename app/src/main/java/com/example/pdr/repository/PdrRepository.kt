package com.example.pdr.repository

import androidx.compose.ui.geometry.Offset
import kotlin.math.sin
import kotlin.math.cos

/**
 * Repository for PDR (Pedestrian Dead Reckoning) path calculation logic.
 * Handles all business logic related to stride calculation and path tracking.
 * No dependencies on ViewModels or UI components.
 */
class PdrRepository {
    private var lastX = 0f
    private var lastY = 0f
    private val pixelsPerCm = 0.5f
    private val recentCadences = mutableListOf<Float>()

    // Callbacks for UI updates
    var onStepDetected: ((strideLengthCm: Float, cadence: Float, newPoint: Offset) -> Unit)? = null
    var onCadenceUpdated: ((averageCadence: Float) -> Unit)? = null

    /**
     * Processes a detected step and calculates the new point on the path.
     */
    fun processStep(
        strideLengthCm: Float,
        cadence: Float,
        heading: Float,
        cadenceAverageSize: Int
    ): Offset {
        // Add the new cadence to our list
        recentCadences.add(cadence)
        while (recentCadences.size > cadenceAverageSize) {
            recentCadences.removeAt(0)
        }
        val averageCadence = recentCadences.average().toFloat()

        // Convert stride to pixels
        val strideInPixels = strideLengthCm * pixelsPerCm

        // Calculate new point
        val newPoint = if (lastX == 0f && lastY == 0f && recentCadences.size == 1) {
            // First point
            Offset(lastX, lastY)
        } else {
            // Subsequent points
            val newX = lastX + strideInPixels * sin(heading)
            val newY = lastY - strideInPixels * cos(heading)
            lastX = newX
            lastY = newY
            Offset(newX, newY)
        }

        // Notify UI
        onStepDetected?.invoke(strideLengthCm, cadence, newPoint)
        onCadenceUpdated?.invoke(averageCadence)

        return newPoint
    }

    /**
     * Clears the path and resets position to origin.
     */
    fun clearPath() {
        lastX = 0f
        lastY = 0f
        recentCadences.clear()
        onCadenceUpdated?.invoke(0f)
    }

    /**
     * Sets a new origin point.
     */
    fun setNewOrigin(newOrigin: Offset) {
        lastX = newOrigin.x
        lastY = newOrigin.y
        recentCadences.clear()
        onCadenceUpdated?.invoke(0f)
    }
}
