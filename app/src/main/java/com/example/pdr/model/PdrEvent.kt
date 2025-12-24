package com.example.pdr.model

import androidx.compose.ui.geometry.Offset

/**
 * STATEFLOW CONCEPT: Event Data Classes
 *
 * In reactive programming, we emit immutable data objects that observers can react to.
 * This prevents listeners from accidentally modifying state and makes data flow clear.
 *
 * @param strideLengthCm The calculated stride length in centimeters
 * @param cadence Steps per second
 * @param newPoint The new coordinate after this step
 * @param timestamp When this step occurred (useful for debugging)
 */
data class StepEvent(
    val strideLengthCm: Float,
    val cadence: Float,
    val newPoint: Offset,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * STATEFLOW CONCEPT: State Data Classes
 *
 * Unlike events (which are single occurrences), states represent the current condition.
 * StateFlow always holds a "current value" - the latest state.
 * New subscribers immediately receive the current state.
 *
 * @param averageCadence The rolling average of cadence over recent steps
 * @param lastStrideLengthCm The most recent stride calculation
 */
data class CadenceState(
    val averageCadence: Float = 0f,
    val lastStrideLengthCm: Float = 0f
)
