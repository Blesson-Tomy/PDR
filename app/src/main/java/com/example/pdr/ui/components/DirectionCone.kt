package com.example.pdr.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import com.example.pdr.viewmodel.FloorPlanViewModel
import kotlin.math.cos
import kotlin.math.sin

/**
 * Direction cone overlay that shows where the user is heading.
 * This is a separate composable to avoid redrawing the entire floor plan canvas
 * every time the heading changes. Only this small overlay redraws.
 * 
 * Applies the same transformations as the floor plan canvas to stay aligned.
 * 
 * MVVM: Reads canvas transformation state from FloorPlanViewModel (single source of truth).
 */
@Composable
fun DirectionConeOverlay(
    lastPoint: Offset?,
    heading: Float,
    floorPlanViewModel: FloorPlanViewModel,
    modifier: Modifier = Modifier
) {
    if (lastPoint == null) return

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer(
                scaleX = floorPlanViewModel.canvasScale,
                scaleY = floorPlanViewModel.canvasScale,
                translationX = floorPlanViewModel.canvasOffsetX,
                translationY = floorPlanViewModel.canvasOffsetY,
                rotationZ = floorPlanViewModel.canvasRotation,
                transformOrigin = TransformOrigin(0f, 0f)
            )
    ) {
        // Apply the same center translation as FloorPlanCanvas
        val centerX = size.width / 2
        val centerY = size.height / 2
        translate(left = centerX, top = centerY) {
        // Cone dimensions
        val coneLength = 60f / floorPlanViewModel.canvasScale
        val coneWidth = 30f
        val vertexRadius = 8f / floorPlanViewModel.canvasScale

        val vertexX = lastPoint.x
        val vertexY = lastPoint.y

        // heading is already in radians from HeadingDetector (azimuth -π to π)
        // graphicsLayer rotation will rotate the cone along with the canvas
        val angleRad = heading

        // Tip of the cone (forward direction)
        val tipX = vertexX + coneLength * sin(angleRad)
        val tipY = vertexY - coneLength * cos(angleRad)

        // Left edge of the cone base
        val leftAngleRad = angleRad + Math.toRadians(coneWidth.toDouble()).toFloat()
        val tipX1 = vertexX + (coneLength * 0.6f) * sin(leftAngleRad)
        val tipY1 = vertexY - (coneLength * 0.6f) * cos(leftAngleRad)

        // Right edge of the cone base
        val rightAngleRad = angleRad - Math.toRadians(coneWidth.toDouble()).toFloat()
        val tipX2 = vertexX + (coneLength * 0.6f) * sin(rightAngleRad)
        val tipY2 = vertexY - (coneLength * 0.6f) * cos(rightAngleRad)

        // Draw the cone triangle
        val conePath = Path().apply {
            moveTo(tipX, tipY)
            lineTo(tipX1, tipY1)
            lineTo(tipX2, tipY2)
            close()
        }

        // Fill with Google Maps blue
        drawPath(path = conePath, color = Color(0xFF4285F4), style = Fill)
        drawPath(path = conePath, color = Color(0x664285F4), style = Fill)

        // Vertex circle (solid blue dot at user position)
        drawCircle(
            color = Color(0xFF4285F4),
            radius = vertexRadius,
            center = Offset(vertexX, vertexY)
        )

        // Outline
        drawPath(
            path = conePath,
            color = Color(0xFF1E88E5),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f / floorPlanViewModel.canvasScale)
        )
        }
    }
}
