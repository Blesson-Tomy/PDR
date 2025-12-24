package com.example.pdr.repository

import androidx.compose.ui.geometry.Offset
import com.example.pdr.model.StepEvent
import com.example.pdr.model.CadenceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sin
import kotlin.math.cos

/**
 * Repository for PDR (Pedestrian Dead Reckoning) path calculation logic.
 * Handles all business logic related to stride calculation and path tracking.
 * Uses StateFlow for reactive data emission.
 *
 * STATEFLOW CONCEPTS:
 * - MutableStateFlow: The internal, mutable version (only repositories modify this)
 * - StateFlow: The public, read-only version (UI and other observers use this)
 * - asStateFlow(): Converts mutable to immutable (protects from external modification)
 */
class PdrRepository {
    private var lastX = 0f
    private var lastY = 0f
    private val pixelsPerCm = 0.5f
    private val recentCadences = mutableListOf<Float>()

    // ===== STATEFLOW PATTERN =====
    // Each StateFlow emits a specific type of data:
    
    /**
     * Emits individual step events.
     * Observers use this to react to each step (e.g., update UI, check for turns).
     * 
     * MutableStateFlow vs StateFlow:
     * - MutableStateFlow<StepEvent?> = We can call .value = newEvent
     * - StateFlow<StepEvent?> = Others can only read it
     */
    private val _stepEvents = MutableStateFlow<StepEvent?>(null)
    val stepEvents: StateFlow<StepEvent?> = _stepEvents.asStateFlow()

    /**
     * Emits cadence and stride state.
     * The UI displays these values, so they need their own state flow.
     */
    private val _cadenceState = MutableStateFlow(CadenceState())
    val cadenceState: StateFlow<CadenceState> = _cadenceState.asStateFlow()

    /**
     * Processes a detected step and calculates the new point on the path.
     * 
     * STATEFLOW IN ACTION:
     * This method emits new values to StateFlows, triggering all observers.
     * Anyone collecting from stepEvents or cadenceState will be notified.
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

        // Emit to StateFlows
        // .value setter notifies all collectors
        _stepEvents.value = StepEvent(
            strideLengthCm = strideLengthCm,
            cadence = cadence,
            newPoint = newPoint
        )

        _cadenceState.value = CadenceState(
            averageCadence = averageCadence,
            lastStrideLengthCm = strideLengthCm
        )

        return newPoint
    }

    /**
     * Clears the path and resets position to origin.
     * Also emits updated state to listeners.
     */
    fun clearPath() {
        lastX = 0f
        lastY = 0f
        recentCadences.clear()
        
        // Emit zero state
        _cadenceState.value = CadenceState(averageCadence = 0f, lastStrideLengthCm = 0f)
    }

    /**
     * Sets a new origin point.
     * Also emits updated state to listeners.
     */
    fun setNewOrigin(newOrigin: Offset) {
        lastX = newOrigin.x
        lastY = newOrigin.y
        recentCadences.clear()
        
        // Emit zero state
        _cadenceState.value = CadenceState(averageCadence = 0f, lastStrideLengthCm = 0f)
    }
}
