package com.example.pdr.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.compose.ui.geometry.Offset

class StepViewModel : ViewModel() {
    val points = mutableStateListOf<Offset>()

    var threshold by mutableFloatStateOf(12f)
    var windowSize by mutableFloatStateOf(6f)
    var debounce by mutableFloatStateOf(300f)

    // User height in cm
    var height by mutableStateOf("170")

    // Heading in radians (update from HeadingDetector)
    var heading by mutableFloatStateOf(0f)

    // Last point coordinates
    private var lastX by mutableFloatStateOf(0f)  // start at center
    private var lastY by mutableFloatStateOf(0f)

    fun addStep(strideLength: Float) {
        val newPoint: Offset

        if (points.isEmpty()) {
            // Start at center
            newPoint = Offset(lastX, lastY)
        } else {
            // Forward along heading
            val newX = lastX + strideLength * kotlin.math.sin(heading)
            val newY = lastY - strideLength * kotlin.math.cos(heading) // <--- subtract cos
            newPoint = Offset(newX, newY)
            lastX = newX
            lastY = newY
        }

        points.add(newPoint)
    }

    fun clearDots() {
        points.clear()
        lastX = 0f
        lastY = 0f
    }
}
