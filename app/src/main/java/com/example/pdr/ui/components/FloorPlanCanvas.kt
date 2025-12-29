package com.example.pdr.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import android.graphics.Paint
import com.example.pdr.model.Entrance
import com.example.pdr.model.Stairwell
import com.example.pdr.model.Wall
import com.example.pdr.viewmodel.FloorPlanViewModel
import com.example.pdr.viewmodel.StepViewModel
import kotlin.math.abs

/**
 * The main floor plan canvas with pan/zoom/rotate gestures and all floor plan drawing logic.
 * Handles walls, stairwells, entrances, and PDR path rendering.
 */
@Composable
fun FloorPlanCanvas(
    stepViewModel: StepViewModel,
    floorPlanViewModel: FloorPlanViewModel,
    onOriginSet: (Offset) -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var rotation by remember { mutableFloatStateOf(0f) }

    val points = stepViewModel.points
    val walls = floorPlanViewModel.walls
    val stairwells = floorPlanViewModel.stairwells
    val entrances = floorPlanViewModel.entrances
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

    // Calculate the bounding box that contains all drawable content
    val contentBounds = remember(walls, points, floorPlanScale, floorPlanRotationDegrees) {
        if (walls.isEmpty() && points.isEmpty()) {
            return@remember Rect(-500f, -500f, 500f, 500f)
        }

        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY

        walls.forEach { wall ->
            val x1 = wall.x1 * floorPlanScale
            val y1 = wall.y1 * floorPlanScale
            val x2 = wall.x2 * floorPlanScale
            val y2 = wall.y2 * floorPlanScale
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

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(floorPlanViewModel.isSettingOrigin) {
                if (floorPlanViewModel.isSettingOrigin) {
                    detectTapGestures {
                        val centerX = size.width / 2f
                        val centerY = size.height / 2f
                        val unpannedX = it.x - offsetX
                        val unpannedY = it.y - offsetY
                        val angleRad = Math.toRadians(-rotation.toDouble()).toFloat()
                        val cos = kotlin.math.cos(angleRad)
                        val sin = kotlin.math.sin(angleRad)
                        val unrotatedX = unpannedX * cos - unpannedY * sin
                        val unrotatedY = unpannedX * sin + unpannedY * cos
                        val unscaledX = unrotatedX / scale
                        val unscaledY = unrotatedY / scale
                        val worldX = unscaledX - centerX
                        val worldY = unscaledY - centerY
                        onOriginSet(Offset(worldX, worldY))
                        floorPlanViewModel.isSettingOrigin = false
                    }
                } else {
                    detectTransformGestures { centroid, pan, zoom, rotationChange ->
                        val effectiveZoom: Float
                        val effectiveRotationChange: Float
                        if (abs(rotationChange) > abs(zoom - 1f) * 60) {
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
        val centerX = size.width / 2
        val centerY = size.height / 2
        translate(left = centerX, top = centerY) {
            val padding = 500f
            val backgroundTopLeft = Offset(contentBounds.left - padding, contentBounds.top - padding)
            val backgroundSize = Size(contentBounds.width + padding * 2, contentBounds.height + padding * 2)

            drawRect(
                color = Color.LightGray,
                topLeft = backgroundTopLeft,
                size = backgroundSize
            )

            if (floorPlanViewModel.showFloorPlan) {
                drawStairwells(stairwells, floorPlanScale, floorPlanRotationDegrees)
                drawWalls(walls, floorPlanScale, floorPlanRotationDegrees, scale)
                drawWallEndpoints(uniqueEndpoints, floorPlanViewModel.showPointNumbers, scale, rotation)
            }

            if (floorPlanViewModel.showEntrances) {
                drawEntrances(entrances, floorPlanScale, floorPlanRotationDegrees, scale, rotation)
            }

            drawPdrPath(points)
        }
    }
}

/**
 * Draws all stairwell polygons filled with light blue.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStairwells(
    stairwells: List<Stairwell>,
    floorPlanScale: Float,
    floorPlanRotationDegrees: Float
) {
    for (stairwell in stairwells) {
        if (stairwell.points.size >= 2) {
            val transformedPoints = stairwell.points.map { (x, y) ->
                val scaledX = x * floorPlanScale
                val scaledY = y * floorPlanScale
                val angleRad = Math.toRadians(floorPlanRotationDegrees.toDouble()).toFloat()
                val cos = kotlin.math.cos(angleRad)
                val sin = kotlin.math.sin(angleRad)
                val rotatedX = scaledX * cos - scaledY * sin
                val rotatedY = scaledX * sin + scaledY * cos
                Offset(rotatedX, rotatedY)
            }

            if (transformedPoints.isNotEmpty()) {
                drawPath(
                    path = Path().apply {
                        moveTo(transformedPoints[0].x, transformedPoints[0].y)
                        for (i in 1 until transformedPoints.size) {
                            lineTo(transformedPoints[i].x, transformedPoints[i].y)
                        }
                        close()
                    },
                    color = Color(0xADD8F3FF),
                    style = Fill
                )
            }
        }
    }
}

/**
 * Draws all walls as black lines.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWalls(
    walls: List<Wall>,
    floorPlanScale: Float,
    floorPlanRotationDegrees: Float,
    scale: Float
) {
    for (wall in walls) {
        val x1 = wall.x1 * floorPlanScale
        val y1 = wall.y1 * floorPlanScale
        val x2 = wall.x2 * floorPlanScale
        val y2 = wall.y2 * floorPlanScale
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
            strokeWidth = 5f / scale
        )
    }
}

/**
 * Draws wall endpoint circles and labels.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWallEndpoints(
    uniqueEndpoints: List<Triple<Float, Float, String>>,
    showPointNumbers: Boolean,
    scale: Float,
    rotation: Float
) {
    if (showPointNumbers) {
        drawIntoCanvas { canvas ->
            val paint = Paint().apply {
                color = android.graphics.Color.BLUE
                textSize = 40f / scale
                textAlign = Paint.Align.LEFT
                isAntiAlias = true
            }
            for ((x, y, label) in uniqueEndpoints) {
                canvas.nativeCanvas.save()
                canvas.nativeCanvas.translate(x, y + 15f / scale)
                canvas.nativeCanvas.rotate(-rotation)
                canvas.nativeCanvas.drawText(label, 0f, 0f, paint)
                canvas.nativeCanvas.restore()
            }
        }
    }

    for ((x, y, _) in uniqueEndpoints) {
        drawCircle(
            color = Color.Blue,
            radius = 6f / scale,
            center = Offset(x, y)
        )
    }
}

/**
 * Draws entrance points (yellow for normal, green for stairwells) with labels.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEntrances(
    entrances: List<Entrance>,
    floorPlanScale: Float,
    floorPlanRotationDegrees: Float,
    scale: Float,
    rotation: Float
) {
    // Draw entrance circles
    for (entrance in entrances) {
        val x = entrance.x * floorPlanScale
        val y = entrance.y * floorPlanScale
        val angleRad = Math.toRadians(floorPlanRotationDegrees.toDouble()).toFloat()
        val cos = kotlin.math.cos(angleRad)
        val sin = kotlin.math.sin(angleRad)
        val rotatedX = x * cos - y * sin
        val rotatedY = x * sin + y * cos
        val entranceColor = if (entrance.stairs) Color(0xFF00FF00) else Color(0xFFFFFF00)
        drawCircle(
            color = entranceColor,
            radius = 8f / scale,
            center = Offset(rotatedX, rotatedY)
        )
    }

    // Draw entrance labels
    drawIntoCanvas { canvas ->
        val paint = Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 32f / scale
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            isFakeBoldText = true
        }
        for (entrance in entrances) {
            val x = entrance.x * floorPlanScale
            val y = entrance.y * floorPlanScale
            val angleRad = Math.toRadians(floorPlanRotationDegrees.toDouble()).toFloat()
            val cos = kotlin.math.cos(angleRad)
            val sin = kotlin.math.sin(angleRad)
            val rotatedX = x * cos - y * sin
            val rotatedY = x * sin + y * cos
            canvas.nativeCanvas.save()
            canvas.nativeCanvas.translate(rotatedX, rotatedY - 8f / scale)
            canvas.nativeCanvas.rotate(-rotation)
            canvas.nativeCanvas.drawText(entrance.id.toString(), 0f, 0f, paint)
            canvas.nativeCanvas.restore()
        }
    }
}

/**
 * Draws the PDR path as red circles.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPdrPath(
    points: List<Offset>
) {
    for (p in points) {
        drawCircle(color = Color.Red, radius = 10f, center = p)
    }
}
