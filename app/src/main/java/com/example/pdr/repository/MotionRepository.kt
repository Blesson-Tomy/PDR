package com.example.pdr.repository

import android.app.Application
import com.example.pdr.model.MotionClassifier
import com.example.pdr.model.MotionEvent
import com.example.pdr.model.MotionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Repository for motion classification using ML model.
 * Handles sensor data buffering and inference.
 * Emits results via StateFlow.
 *
 * STATEFLOW CONCEPTS:
 * - Using StateFlow for asynchronous events (ML inference happens on background thread)
 * - .launch(Dispatchers.Main) ensures StateFlow updates happen on main thread
 * - This is safe because Compose only reads on main thread anyway
 */
class MotionRepository(application: Application) {

    private val motionClassifier = MotionClassifier(application)
    private val sensorDataBuffer = mutableListOf<FloatArray>()
    private val scope = CoroutineScope(Dispatchers.Default)

    // StateFlow for motion events
    private val _motionEvents = MutableStateFlow<MotionEvent?>(null)
    val motionEvents: StateFlow<MotionEvent?> = _motionEvents.asStateFlow()

    /**
     * Processes incoming accelerometer data for motion classification.
     * 
     * THREAD SAFETY & STATEFLOW:
     * - Background thread: collects sensor data and runs ML inference
     * - Main thread: emits to StateFlow (Compose only reads on main thread)
     * - This is the correct pattern for background work + UI updates
     */
    fun onSensorDataReceived(accX: Float, accY: Float, accZ: Float) {
        val accMag = sqrt(accX * accX + accY * accY + accZ * accZ)
        val accelerometerData = floatArrayOf(accX, accY, accZ, accMag)

        sensorDataBuffer.add(accelerometerData)

        // Check if buffer is full - trigger inference
        if (sensorDataBuffer.size >= motionClassifier.meta.windowSize) {
            val bufferArray = sensorDataBuffer.toTypedArray()
            
            // Run inference on background thread
            scope.launch {
                val prediction = motionClassifier.predict(bufferArray)
                
                // Find the class with highest confidence
                val predictedIndex = prediction.indices.maxByOrNull { prediction[it] } ?: -1
                val maxConfidence = if (predictedIndex != -1) prediction[predictedIndex] else 0f
                
                if (predictedIndex != -1) {
                    val motionTypeString = motionClassifier.meta.classNames[predictedIndex]
                    // Convert class name from JSON to MotionType enum
                    // JSON classes: "walking", "upstairs", "downstairs", "idle"
                    val motionType = when (motionTypeString.lowercase()) {
                        "walking" -> MotionType.WALKING
                        "upstairs" -> MotionType.STAIR_ASCENT
                        "downstairs" -> MotionType.STAIR_DESCENT
                        "idle" -> MotionType.STATIONARY
                        else -> MotionType.UNKNOWN
                    }
                    
                    // Update UI on main thread
                    scope.launch(Dispatchers.Main) {
                        val event = MotionEvent(
                            motionType = motionType,
                            confidence = maxConfidence
                        )
                        _motionEvents.value = event
                    }
                }
            }

            // Implement sliding window: remove oldest data points
            for (i in 0 until motionClassifier.meta.stepSize) {
                if (sensorDataBuffer.isNotEmpty()) {
                    sensorDataBuffer.removeAt(0)
                }
            }
        }
    }

    /**
     * Cleans up resources.
     */
    fun cleanup() {
        motionClassifier.close()
    }
}

