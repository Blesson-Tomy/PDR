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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import android.graphics.Paint
import com.example.pdr.viewmodel.FloorPlanViewModel
import com.example.pdr.viewmodel.MotionViewModel
import com.example.pdr.viewmodel.StepViewModel
import kotlin.math.abs
import kotlin.math.sqrt

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
    val floorPlanScale = floorPlanViewModel.floorPlanScale.toFloatOrNull() ?: 1f
    val floorPlanRotationDegrees = floorPlanViewModel.floorPlanRotation.toFloatOrNull() ?: 0f

    // Helper function to rotate a point around the origin by the given angle in degrees
    val rotatePoint = { x: Float, y: Float, angleDegrees: Float ->
        val angleRad = Math.toRadians(angleDegrees.toDouble()).toFloat()
        val cos = kotlin.math.cos(angleRad)
        val sin = kotlin.math.sin(angleRad)
        val rotatedX = x * cos - y * sin
        val rotatedY = x * sin + y * cos
        Pair(rotatedX, rotatedY)
    }

    // Extract and label unique wall endpoints
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

    // Calculate distance between points 26 and 52 in cm (1 unit = 2 cm)
    val distanceBetweenPointsCm = remember(uniqueEndpoints) {
        if (uniqueEndpoints.size >= 52) {
            val point26 = uniqueEndpoints[25] // 0-indexed
            val point52 = uniqueEndpoints[51] // 0-indexed
            val dx = point52.first - point26.first
            val dy = point52.second - point26.second
            val distanceInUnits = sqrt(dx * dx + dy * dy)
            distanceInUnits * 2 // Convert to cm (1 unit = 2 cm)
        } else {
            0f
        }
    }


    // Calculate the bounding box that contains all drawable content (walls and PDR points) after rotation and scaling.
    val contentBounds = remember(walls, points, floorPlanScale, floorPlanRotationDegrees) {
        if (walls.isEmpty() && points.isEmpty()) {
            // If there's nothing to draw, provide a default 1000x1000 area.
            return@remember Rect(-500f, -500f, 500f, 500f)
        }

        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY

        // Calculate bounds for rotated and scaled walls
        walls.forEach { wall ->
            val x1 = wall.x1 * floorPlanScale
            val y1 = wall.y1 * floorPlanScale
            val x2 = wall.x2 * floorPlanScale
            val y2 = wall.y2 * floorPlanScale
            
            // Apply rotation
            val rotated1 = rotatePoint(x1, y1, floorPlanRotationDegrees)
            val rotated2 = rotatePoint(x2, y2, floorPlanRotationDegrees)
            
            minX = minOf(minX, rotated1.first, rotated2.first)
            minY = minOf(minY, rotated1.second, rotated2.second)
            maxX = maxOf(maxX, rotated1.first, rotated2.first)
            maxY = maxOf(maxY, rotated1.second, rotated2.second)
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
                            if (abs(rotationChange) > abs(zoom - 1f) * 60) { // Heuristic to distinguish rotation from zoom
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
                        val x1 = wall.x1 * floorPlanScale
                        val y1 = wall.y1 * floorPlanScale
                        val x2 = wall.x2 * floorPlanScale
                        val y2 = wall.y2 * floorPlanScale
                        // Apply rotation
                        val angleRad = Math.toRadians(floorPlanRotationDegrees.toDouble()).toFloat()
                        val cos = kotlin.math.cos(angleRad)
                        val sin = kotlin.math.sin(angleRad)
                        val rotatedX1 = x1 * cos - y1 * sin
                        val rotatedY1 = x1 * sin + y1 * cos
                        val rotatedX2 = x2 * cos - y2 * sin
                        val rotatedY2 = x2 * sin + y2 * cos
                        drawLine(
                            color = Color.Black,
                            start = Offset(rotatedX1, rotatedY1),
                            end = Offset(rotatedX2, rotatedY2),
                            strokeWidth = 5f / scale // Keep stroke width consistent when zooming
                        )
                    }
                    
                    // Draw labels for unique wall endpoints
                    // Labels stay with their positions (pan/zoom) but text doesn't rotate
                    drawIntoCanvas { canvas ->
                        val paint = Paint().apply {
                            color = android.graphics.Color.BLUE
                            textSize = 40f / scale
                            textAlign = Paint.Align.LEFT
                            isAntiAlias = true
                        }
                        
                        for ((x, y, label) in uniqueEndpoints) {
                            // Save canvas state before transformation
                            canvas.nativeCanvas.save()
                            
                            // Translate to label position
                            canvas.nativeCanvas.translate(x, y + 15f / scale)
                            
                            // Counter-rotate the text by the global canvas rotation
                            // This keeps text upright while position rotates with map
                            canvas.nativeCanvas.rotate(-rotation)
                            
                            // Draw text at origin (now rotated back to upright)
                            canvas.nativeCanvas.drawText(label, 0f, 0f, paint)
                            
                            // Restore canvas state
                            canvas.nativeCanvas.restore()
                        }
                    }
                    
                    // Draw circles at each endpoint
                    for ((x, y, _) in uniqueEndpoints) {
                        drawCircle(
                            color = Color.Blue,
                            radius = 6f / scale,
                            center = Offset(x, y)
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
            if (distanceBetweenPointsCm > 0f) {
                Text(
                    text = "Distance (26→52): ${"%.2f".format(distanceBetweenPointsCm)} cm",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
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
