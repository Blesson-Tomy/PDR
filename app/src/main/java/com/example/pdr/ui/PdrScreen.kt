package com.example.pdr.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.pdr.ui.components.CanvasControls
import com.example.pdr.ui.components.CompassOverlay
import com.example.pdr.ui.components.FloorPlanCanvas
import com.example.pdr.ui.components.StatsPanel
import com.example.pdr.ui.components.calculateReferenceDistance
import com.example.pdr.viewmodel.FloorPlanViewModel
import com.example.pdr.viewmodel.MotionViewModel
import com.example.pdr.viewmodel.StepViewModel

/**
 * The main PDR screen that orchestrates all floor plan and navigation UI components.
 * 
 * Structure:
 * - FloorPlanCanvas: Main interactive canvas with walls, stairwells, entrances, and PDR path
 * - StatsPanel: Motion and stride statistics (top-left)
 * - CompassOverlay: Heading and direction indicator (top-right)
 * - CanvasControls: Origin setting and canvas control buttons (bottom-center)
 */
@Composable
fun PdrScreen(
    stepViewModel: StepViewModel,
    motionViewModel: MotionViewModel,
    floorPlanViewModel: FloorPlanViewModel
) {
    val walls = floorPlanViewModel.walls
    val floorPlanScale = floorPlanViewModel.floorPlanScale.toFloatOrNull() ?: 1f
    val floorPlanRotationDegrees = floorPlanViewModel.floorPlanRotation.toFloatOrNull() ?: 0f

    // Helper function to rotate a point
    val rotatePoint = { x: Float, y: Float, angleDegrees: Float ->
        val angleRad = Math.toRadians(angleDegrees.toDouble()).toFloat()
        val cos = kotlin.math.cos(angleRad)
        val sin = kotlin.math.sin(angleRad)
        val rotatedX = x * cos - y * sin
        val rotatedY = x * sin + y * cos
        Pair(rotatedX, rotatedY)
    }

    // Extract and label unique wall endpoints (needed for reference distance calculation)
    val uniqueEndpoints = remember(walls, floorPlanScale, floorPlanRotationDegrees) {
        val endpoints = mutableSetOf<Pair<Float, Float>>()
        walls.forEach { wall ->
            val x1 = wall.x1 * floorPlanScale
            val y1 = wall.y1 * floorPlanScale
            val x2 = wall.x2 * floorPlanScale
            val y2 = wall.y2 * floorPlanScale
            val rotated1 = rotatePoint(x1, y1, floorPlanRotationDegrees)
            val rotated2 = rotatePoint(x2, y2, floorPlanRotationDegrees)
            endpoints.add(rotated1)
            endpoints.add(rotated2)
        }
        endpoints.toList().sortedWith(compareBy({ it.first }, { it.second })).mapIndexed { index, point ->
            val label = (index + 1).toString()
            Triple(point.first, point.second, label)
        }
    }

    // Calculate reference distance for calibration
    val distanceBetweenPointsCm = remember(uniqueEndpoints) {
        calculateReferenceDistance(uniqueEndpoints)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Main interactive floor plan canvas
        FloorPlanCanvas(
            stepViewModel = stepViewModel,
            floorPlanViewModel = floorPlanViewModel,
            onOriginSet = { offset -> stepViewModel.setNewOrigin(offset) }
        )

        // Stats panel (top-left)
        StatsPanel(
            stepViewModel = stepViewModel,
            motionViewModel = motionViewModel,
            distanceBetweenPointsCm = distanceBetweenPointsCm,
            modifier = Modifier.align(Alignment.TopStart)
        )

        // Compass overlay (top-right)
        CompassOverlay(
            stepViewModel = stepViewModel,
            modifier = Modifier.align(Alignment.TopEnd)
        )

        // Control buttons (bottom-center)
        CanvasControls(
            floorPlanViewModel = floorPlanViewModel,
            stepViewModel = stepViewModel,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
