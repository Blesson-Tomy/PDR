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
import kotlin.math.sqrt

/**
 * Manages the state and logic related to motion activity classification.
 *
 * This ViewModel receives raw accelerometer data, preprocesses it into windows, and uses a
 * [MotionClassifier] to predict the user's current activity (e.g., Idle, Walking, Running).
 * The results are then exposed as state to be observed by the UI.
 *
 * @param application The application context, required by AndroidViewModel.
 */
class MotionViewModel(application: Application) : AndroidViewModel(application) {

    // The TFLite model wrapper that handles loading the model and running inference.
    private val motionClassifier = MotionClassifier(application)

    // A buffer to hold incoming sensor data. Once it's full (reaches the model's window size),
    // a prediction is triggered.
    private val sensorDataBuffer = mutableListOf<FloatArray>()

    // Exposes the latest classified motion type (e.g., "Idle") to the UI.
    // It is private to ensure it's only modified within this ViewModel.
    var motionType by mutableStateOf("Idle")
        private set
    // Exposes the confidence score (0.0 to 1.0) of the latest classification to the UI.
    var confidence by mutableFloatStateOf(0f)
        private set

    /**
     * The main entry point for new sensor data.
     * It receives accelerometer readings, calculates the magnitude, adds the data to a buffer,
     * and triggers a prediction when the buffer is full.
     */
    fun onSensorDataReceived(accX: Float, accY: Float, accZ: Float) {
        // The model is trained on four features: the raw X, Y, Z, and the vector magnitude.
        val accMag = sqrt(accX * accX + accY * accY + accZ * accZ)

        // Add the new 4-feature data point to the end of the buffer.
        sensorDataBuffer.add(floatArrayOf(accX, accY, accZ, accMag))

        // Once the buffer reaches the exact size the model expects, trigger prediction.
        if (sensorDataBuffer.size == motionClassifier.meta.windowSize) {
            val dataToPredict = sensorDataBuffer.toTypedArray()

            // Launch a coroutine on a background thread (Dispatchers.IO) to run the inference,
            // preventing any blockage of the main UI thread.
            viewModelScope.launch(Dispatchers.IO) {
                val prediction = motionClassifier.predict(dataToPredict)

                // Find the index of the class with the highest confidence score.
                val predictedIndex = prediction.indices.maxByOrNull { prediction[it] } ?: -1
                val maxConfidence = if (predictedIndex != -1) prediction[predictedIndex] else 0f

                // Always show the top prediction from the model, regardless of confidence.
                if (predictedIndex != -1) {
                    motionType = motionClassifier.meta.classNames[predictedIndex]
                    confidence = maxConfidence
                }
            }

            // Implement a sliding window: remove the oldest data points from the start of the buffer
            // to make room for new ones. The stepSize determines how much the window slides.
            for (i in 0 until motionClassifier.meta.stepSize) {
                if (sensorDataBuffer.isNotEmpty()) {
                    sensorDataBuffer.removeAt(0)
                }
            }
        }
    }

    /**
     * Called when the ViewModel is about to be destroyed. This is the correct place to release
     * resources, such as closing the TFLite interpreter to prevent memory leaks.
     */
    override fun onCleared() {
        super.onCleared()
        motionClassifier.close()
    }
}
