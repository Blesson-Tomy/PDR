package com.example.pdr.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.pdr.viewmodel.FloorPlanViewModel
import com.example.pdr.viewmodel.MotionViewModel
import com.example.pdr.viewmodel.StepViewModel
import kotlin.math.sqrt

/**
 * Stats panel displayed in the top-left corner showing motion and stride info.
 */
@Composable
fun StatsPanel(
    stepViewModel: StepViewModel,
    motionViewModel: MotionViewModel,
    distanceBetweenPointsCm: Float,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(16.dp)
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val confidencePercentage = (motionViewModel.confidence * 100).toInt()
        Text(
            text = "Motion Type: ${motionViewModel.motionType} ($confidencePercentage%)",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "Last Stride: ${"%.1f".format(stepViewModel.lastStrideLengthCm)} cm",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "Average Cadence: ${"%.2f".format(stepViewModel.averageCadence)} steps/sec",
            style = MaterialTheme.typography.bodyMedium
        )
        if (distanceBetweenPointsCm > 0f) {
            Text(
                text = "Distance (26→52): ${"%.2f".format(distanceBetweenPointsCm)} cm",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * Compass overlay in the top-right corner showing heading and user direction.
 */
@Composable
fun CompassOverlay(
    stepViewModel: StepViewModel,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .size(120.dp)
            .padding(16.dp)
    ) {
        val compassRadius = (size.minDimension / 2) * 0.9f
        val compassCenter = center

        // Draw the compass background circle
        drawCircle(
            color = Color.DarkGray,
            radius = compassRadius,
            center = compassCenter,
            style = Stroke(width = 4.dp.toPx())
        )

        // Draw the red "North" line
        val northEnd = Offset(
            compassCenter.x + compassRadius * kotlin.math.sin(-stepViewModel.heading),
            compassCenter.y - compassRadius * kotlin.math.cos(-stepViewModel.heading)
        )
        drawLine(color = Color.Red, start = compassCenter, end = northEnd, strokeWidth = 4.dp.toPx())

        // Draw the blue arrow (user's forward direction)
        val headingEnd = Offset(
            compassCenter.x,
            compassCenter.y - compassRadius
        )
        drawLine(color = Color.Blue, start = compassCenter, end = headingEnd, strokeWidth = 5.dp.toPx())
    }
}

/**
 * Control buttons at the bottom-center of the screen.
 */
@Composable
fun CanvasControls(
    floorPlanViewModel: FloorPlanViewModel,
    stepViewModel: StepViewModel,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Button(onClick = { floorPlanViewModel.toggleIsSettingOrigin() }) {
            Text(if (floorPlanViewModel.isSettingOrigin) "Tap to set origin" else "Set Origin")
        }
        Button(onClick = { stepViewModel.clearDots() }) {
            Text("Clear Canvas")
        }
    }
}

/**
 * Calculates the distance between wall endpoints 26 and 52.
 * Used for calibration purposes (1 unit = 2 cm).
 */
fun calculateReferenceDistance(uniqueEndpoints: List<Triple<Float, Float, String>>): Float {
    return if (uniqueEndpoints.size >= 52) {
        val point26 = uniqueEndpoints[25] // 0-indexed
        val point52 = uniqueEndpoints[51] // 0-indexed
        val dx = point52.first - point26.first
        val dy = point52.second - point26.second
        val distanceInUnits = sqrt(dx * dx + dy * dy)
        distanceInUnits * 2 // Convert to cm
    } else {
        0f
    }
}
