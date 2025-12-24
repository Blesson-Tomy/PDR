package com.example.pdr.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pdr.repository.MotionRepository
import kotlinx.coroutines.launch

/**
 * Manages ONLY UI state for motion classification.
 * All ML inference logic has been moved to MotionRepository.
 * 
 * STATEFLOW PATTERN:
 * - Repository emits MotionEvent objects
 * - ViewModel collects and extracts data for UI display
 * - This separates concerns: repository handles ML, ViewModel handles presentation
 */
class MotionViewModel : ViewModel() {
    
    var motionRepository: MotionRepository? = null
        set(value) {
            field = value
            observeRepositoryFlows()
        }

    // Exposes the latest classified motion type (e.g., "Idle") to the UI.
    var motionType by mutableStateOf("Idle")
        private set

    // Exposes the confidence score (0.0 to 1.0) of the latest classification to the UI.
    var confidence by mutableFloatStateOf(0f)
        private set

    /**
     * STATEFLOW COLLECTION:
     * Collects MotionEvent objects and extracts the pieces the UI needs.
     * 
     * WHY THIS IS BETTER THAN CALLBACKS:
     * - No manual callback wiring needed
     * - viewModelScope ensures cleanup on destroy
     * - Easy to add multiple observers (add more .collect { } blocks)
     * - Type-safe (can't accidentally call wrong method)
     */
    private fun observeRepositoryFlows() {
        motionRepository?.let { repo ->
            viewModelScope.launch {
                repo.motionEvents.collect { event ->
                    event?.let {
                        motionType = it.motionType.name  // Convert enum to string for display
                        confidence = it.confidence
                    }
                }
            }
        }
    }
}