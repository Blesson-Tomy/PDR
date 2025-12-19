package com.example.pdr.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.compose.ui.geometry.Offset

/**
 * Manages ONLY UI state for the PDR system.
 * All business logic has been moved to repositories.
 * This ViewModel is purely a state holder for the UI layer.
 */
class StepViewModel : ViewModel() {
    // ===== UI STATE =====
    val points = mutableStateListOf<Offset>()

    // Step detection parameters (for UI configuration)
    var threshold by mutableFloatStateOf(12f)
    var windowSize by mutableFloatStateOf(6f)
    var cadenceAverageSize by mutableFloatStateOf(5f)
    var debounce by mutableFloatStateOf(300f)

    // User settings
    var height by mutableStateOf("170")

    // Stride calculation parameters
    var kValue by mutableFloatStateOf(0.37f)
    var cValue by mutableFloatStateOf(0.15f)

    // Sensor readings displayed to UI
    var heading by mutableFloatStateOf(0f)
    var lastStrideLengthCm by mutableFloatStateOf(0f)
    var averageCadence by mutableFloatStateOf(0f)

    /**
     * Called by PdrRepository when a step is detected.
     * Updates UI state only - no business logic here.
     */
    fun onStepDetected(strideLengthCm: Float, cadence: Float, newPoint: Offset) {
        lastStrideLengthCm = strideLengthCm
        points.add(newPoint)
    }

    /**
     * Called by PdrRepository when cadence is updated.
     */
    fun onCadenceUpdated(averageCadence: Float) {
        this.averageCadence = averageCadence
    }

    /**
     * Clears the PDR path - delegates to UI state management.
     */
    fun clearDots() {
        points.clear()
        lastStrideLengthCm = 0f
        averageCadence = 0f
    }

    /**
     * Sets a new origin point - updates UI state.
     */
    fun setNewOrigin(newOrigin: Offset) {
        points.clear()
        points.add(newOrigin)
    }
}

