package com.example.pdr.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.geometry.Offset
import com.example.pdr.repository.PdrRepository
import kotlinx.coroutines.launch

/**
 * STATEFLOW BEST PRACTICE:
 * ViewModels are perfect places to collect from StateFlows and transform the data into
 * Compose-friendly state. This separates:
 * - Business logic (repositories emit raw events)
 * - Presentation logic (ViewModel transforms for UI)
 * - UI rendering (Compose reads ViewModel state)
 */
class StepViewModel : ViewModel() {
    // Reference to PdrRepository for business logic operations
    var pdrRepository: PdrRepository? = null
        set(value) {
            field = value
            // When repository is injected, start collecting from its flows
            observeRepositoryFlows()
        }

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
     * STATEFLOW COLLECTION:
     * This method sets up collection from repository's StateFlows.
     * viewModelScope automatically cancels when ViewModel is destroyed.
     * 
     * HOW IT WORKS:
     * 1. Repository emits StepEvent to stepEvents StateFlow
     * 2. ViewModel collects from stepEvents
     * 3. ViewModel updates local Compose state (points, lastStrideLengthCm)
     * 4. Compose recomposes because state changed
     * 
     * This is much cleaner than callback hell!
     */
    private fun observeRepositoryFlows() {
        pdrRepository?.let { repo ->
            // Observe step events
            viewModelScope.launch {
                repo.stepEvents.collect { event ->
                    event?.let {
                        lastStrideLengthCm = it.strideLengthCm
                        points.add(it.newPoint)
                    }
                }
            }

            // Observe cadence state
            viewModelScope.launch {
                repo.cadenceState.collect { cadenceState ->
                    averageCadence = cadenceState.averageCadence
                }
            }
        }
    }

    /**
     * Clears the PDR path - updates both repository and UI state.
     */
    fun clearDots() {
        pdrRepository?.clearPath()
        points.clear()
        lastStrideLengthCm = 0f
        averageCadence = 0f
    }

    /**
     * Sets a new origin point - updates both repository and UI state.
     */
    fun setNewOrigin(newOrigin: Offset) {
        pdrRepository?.setNewOrigin(newOrigin)
        points.clear()
        points.add(newOrigin)
    }
}