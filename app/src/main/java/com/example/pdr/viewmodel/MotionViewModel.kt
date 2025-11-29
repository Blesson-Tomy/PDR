package com.example.pdr.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pdr.model.MotionClassifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MotionViewModel(application: Application) : AndroidViewModel(application) {

    private val motionClassifier = MotionClassifier(application)

    // Buffer to hold windows of sensor data.
    private val sensorDataBuffer = mutableListOf<FloatArray>()

    // Expose the latest motion type and confidence to the UI.
    var motionType by mutableStateOf("Idle")
        private set
    var confidence by mutableFloatStateOf(0f)
        private set

    // Confidence threshold for showing a classification vs. "Idle"
    private val confidenceThreshold = 0.75f

    /**
     * Receives sensor data, buffers it, and triggers prediction.
     */
    fun onSensorDataReceived(accX: Float, accY: Float, accZ: Float, gyroX: Float, gyroY: Float, gyroZ: Float) {
        sensorDataBuffer.add(floatArrayOf(accX, accY, accZ, gyroX, gyroY, gyroZ))

        if (sensorDataBuffer.size == motionClassifier.meta.windowSize) {
            val dataToPredict = sensorDataBuffer.toTypedArray()

            viewModelScope.launch(Dispatchers.IO) {
                val prediction = motionClassifier.predict(dataToPredict)
                
                // A more robust way to find the index and value of the max confidence
                val predictedIndex = prediction.indices.maxByOrNull { prediction[it] } ?: -1
                val maxConfidence = if (predictedIndex != -1) prediction[predictedIndex] else 0f

                if (maxConfidence >= confidenceThreshold && predictedIndex != -1) {
                    // If confidence is high enough, show the predicted class
                    motionType = motionClassifier.meta.classNames[predictedIndex]
                    confidence = maxConfidence
                } else {
                    // Otherwise, show Idle
                    motionType = "Idle"
                    confidence = maxConfidence // Show the confidence of the top (but failing) class
                }
            }

            // Slide the window
            for (i in 0 until motionClassifier.meta.stepSize) {
                if (sensorDataBuffer.isNotEmpty()) {
                    sensorDataBuffer.removeAt(0)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        motionClassifier.close()
    }
}
