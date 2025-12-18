package com.example.pdr.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.pdr.viewmodel.FloorPlanViewModel
import com.example.pdr.viewmodel.MotionViewModel
import com.example.pdr.viewmodel.StepViewModel
import kotlin.math.abs

/**
 * The main PDR screen, which displays the user's path, motion data, and a compass.
 */
@Composable
fun PdrScreen(
    stepViewModel: StepViewModel,
    motionViewModel: MotionViewModel,
    floorPlanViewModel: FloorPlanViewModel
) {
    // State for handling pan, zoom, and rotation gestures on the canvas.
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var rotation by remember { mutableFloatStateOf(0f) }
    // Get the list of points to draw from the ViewModel.
    val points = stepViewModel.points
    val walls = floorPlanViewModel.walls
    val floorPlanScale = floorPlanViewModel.floorPlanScale


    // Calculate the bounding box that contains all drawable content (walls and PDR points).
    val contentBounds = remember(walls, points, floorPlanScale) {
        if (walls.isEmpty() && points.isEmpty()) {
            // If there's nothing to draw, provide a default 1000x1000 area.
            return@remember Rect(-500f, -500f, 500f, 500f)
        }

        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY

        walls.forEach { wall ->
            minX = minOf(minX, wall.x1 * floorPlanScale, wall.x2 * floorPlanScale)
            minY = minOf(minY, wall.y1 * floorPlanScale, wall.y2 * floorPlanScale)
            maxX = maxOf(maxX, wall.x1 * floorPlanScale, wall.x2 * floorPlanScale)
            maxY = maxOf(maxY, wall.y1 * floorPlanScale, wall.y2 * floorPlanScale)
        }

        points.forEach { p ->
            minX = minOf(minX, p.x)
            minY = minOf(minY, p.y)
            maxX = maxOf(maxX, p.x)
            maxY = maxOf(maxY, p.y)
        }

        if (minX == Float.POSITIVE_INFINITY) {
            Rect(-500f, -500f, 500f, 500f)
        } else {
            Rect(minX, minY, maxX, maxY)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // The main canvas for drawing the map and PDR path.
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(floorPlanViewModel.isSettingOrigin) { // Key this to the state
                    if (floorPlanViewModel.isSettingOrigin) {
                        detectTapGestures {
                            val centerX = size.width / 2f
                            val centerY = size.height / 2f

                            // Reverse the transformations to find the tap point in world coordinates
                            // 1. Undo pan
                            val unpannedX = it.x - offsetX
                            val unpannedY = it.y - offsetY

                            // 2. Undo rotation
                            val angleRad = Math.toRadians(-rotation.toDouble()).toFloat()
                            val cos = kotlin.math.cos(angleRad)
                            val sin = kotlin.math.sin(angleRad)
                            val unrotatedX = unpannedX * cos - unpannedY * sin
                            val unrotatedY = unpannedX * sin + unpannedY * cos

                            // 3. Undo canvas scale
                            val unscaledX = unrotatedX / scale
                            val unscaledY = unrotatedY / scale

                            // 4. Undo canvas centering
                            val worldX = unscaledX - centerX
                            val worldY = unscaledY - centerY

                            stepViewModel.setNewOrigin(Offset(worldX, worldY))
                            floorPlanViewModel.isSettingOrigin = false
                        }
                    } else {
                        detectTransformGestures { centroid, pan, zoom, rotationChange ->
                            val effectiveZoom: Float
                            val effectiveRotationChange: Float

                            // Prioritize rotation only if the rotational change is significant compared to the zoom.
                            if (abs(rotationChange) > abs(zoom - 1f) * 50) { // Heuristic to distinguish rotation from zoom
                                effectiveRotationChange = rotationChange
                                effectiveZoom = 1f
                            } else {
                                effectiveRotationChange = 0f
                                effectiveZoom = zoom
                            }

                            val oldScale = scale
                            val newScale = (scale * effectiveZoom).coerceIn(0.1f, 10f)
                            val actualZoom = newScale / oldScale

                            val offsetFromCentroidX = offsetX - centroid.x
                            val offsetFromCentroidY = offsetY - centroid.y

                            val scaledOffsetFromCentroidX = offsetFromCentroidX * actualZoom
                            val scaledOffsetFromCentroidY = offsetFromCentroidY * actualZoom

                            val angleRad = Math.toRadians(effectiveRotationChange.toDouble()).toFloat()
                            val cos = kotlin.math.cos(angleRad)
                            val sin = kotlin.math.sin(angleRad)

                            val rotatedOffsetFromCentroidX = scaledOffsetFromCentroidX * cos - scaledOffsetFromCentroidY * sin
                            val rotatedOffsetFromCentroidY = scaledOffsetFromCentroidX * sin + scaledOffsetFromCentroidY * cos

                            offsetX = centroid.x + rotatedOffsetFromCentroidX + pan.x
                            offsetY = centroid.y + rotatedOffsetFromCentroidY + pan.y

                            scale = newScale
                            rotation += effectiveRotationChange
                        }
                    }
                }
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY,
                    rotationZ = rotation,
                    transformOrigin = TransformOrigin(0f, 0f)
                )
        ) {
            // Center the coordinate system so (0,0) is in the middle of the canvas.
            val centerX = size.width / 2
            val centerY = size.height / 2
            translate(left = centerX, top = centerY) {
                // Create an "infinite" background.
                val padding = 500f
                val backgroundTopLeft = Offset(contentBounds.left - padding, contentBounds.top - padding)
                val backgroundSize = Size(contentBounds.width + padding * 2, contentBounds.height + padding * 2)

                drawRect(
                    color = Color.LightGray,
                    topLeft = backgroundTopLeft,
                    size = backgroundSize
                )

                if (floorPlanViewModel.showFloorPlan) {
                    // Draw the floor plan.
                    for (wall in walls) {
                        drawLine(
                            color = Color.Black,
                            start = Offset(wall.x1 * floorPlanScale, wall.y1 * floorPlanScale),
                            end = Offset(wall.x2 * floorPlanScale, wall.y2 * floorPlanScale),
                            strokeWidth = 5f / scale // Keep stroke width consistent when zooming
                        )
                    }
                }
                // Always draw the PDR path on top.
                for (p in points) {
                    drawCircle(color = Color.Red, radius = 10f / scale, center = p)
                }
            }
        }
        // Box for the labels in the top-left corner.
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
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
        }

        // A separate, fixed canvas for the compass overlay.
        Canvas(
            modifier = Modifier
                .size(120.dp)
                .padding(16.dp)
                .align(Alignment.TopEnd)
        ) {
            val compassRadius = (size.minDimension / 2) * 0.9f
            val compassCenter = center

            // Draw the compass background circle.
            drawCircle(
                color = Color.DarkGray,
                radius = compassRadius,
                center = compassCenter,
                style = Stroke(width = 4.dp.toPx())
            )

            // Draw the red "North" line, which rotates based on the device's heading.
            val northEnd = Offset(
                compassCenter.x + compassRadius * kotlin.math.sin(-stepViewModel.heading),
                compassCenter.y - compassRadius * kotlin.math.cos(-stepViewModel.heading)
            )
            drawLine(color = Color.Red, start = compassCenter, end = northEnd, strokeWidth = 4.dp.toPx())

            // Draw the blue arrow representing the user's current forward direction (always points up).
            val headingEnd = Offset(
                compassCenter.x,
                compassCenter.y - compassRadius
            )
            drawLine(color = Color.Blue, start = compassCenter, end = headingEnd, strokeWidth = 5.dp.toPx())
        }

        // Buttons for canvas control
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
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
}
