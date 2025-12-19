package com.example.pdr.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

/**
 * Manages ONLY UI state for motion classification.
 * All ML inference logic has been moved to MotionRepository.
 * This ViewModel is purely a state holder for the UI layer.
 */
class MotionViewModel : ViewModel() {

    // Exposes the latest classified motion type (e.g., "Idle") to the UI.
    var motionType by mutableStateOf("Idle")
        private set

    // Exposes the confidence score (0.0 to 1.0) of the latest classification to the UI.
    var confidence by mutableFloatStateOf(0f)
        private set

    /**
     * Called by MotionRepository when motion is detected.
     * Updates UI state only - no inference logic here.
     */
    fun onMotionDetected(motionType: String, confidence: Float) {
        this.motionType = motionType
        this.confidence = confidence
    }
}

