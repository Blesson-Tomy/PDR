package com.example.pdr.repository

import android.app.Application
import com.example.pdr.model.MotionClassifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Repository for motion classification using ML model.
 * Handles sensor data buffering and inference.
 * Reports results via callbacks to ViewModels.
 */
class MotionRepository(application: Application) {

    private val motionClassifier = MotionClassifier(application)
    private val sensorDataBuffer = mutableListOf<FloatArray>()
    private val scope = CoroutineScope(Dispatchers.Default)

    // Callbacks for UI updates
    var onMotionDetected: ((motionType: String, confidence: Float) -> Unit)? = null

    /**
     * Processes incoming accelerometer data for motion classification.
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
                    val motionType = motionClassifier.meta.classNames[predictedIndex]
                    
                    // Update UI on main thread
                    scope.launch(Dispatchers.Main) {
                        onMotionDetected?.invoke(motionType, maxConfidence)
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

