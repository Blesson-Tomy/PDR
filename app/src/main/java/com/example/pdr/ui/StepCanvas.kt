package com.example.pdr.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import com.example.pdr.viewmodel.StepViewModel

import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import com.example.pdr.viewmodel.MotionViewModel

@Composable
fun StepCanvas(stepViewModel: StepViewModel, motionViewModel: MotionViewModel) {

    var scale by remember { mutableFloatStateOf(1f) }
//    var rotation by remember { mutableStateOf(0f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val points = stepViewModel.points

    Column {
        Row {
            Button(onClick = { stepViewModel.addStepDot() }) {
                Text("Add Dot")
            }
            Spacer(modifier = Modifier.width(16.dp))
            Button(onClick = { stepViewModel.clearDots() }) {
                Text("Clear Canvas")
            }
        }

        Column(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp)
        ) {
            // Display the motion type and confidence from the MotionViewModel
            val confidencePercentage = (motionViewModel.confidence * 100).toInt()
            Text("Motion Type: ${motionViewModel.motionType} ($confidencePercentage%)")

            Text("Threshold: ${stepViewModel.threshold}")
            Slider(
                value = stepViewModel.threshold,
                onValueChange = { stepViewModel.threshold = it },
                valueRange = 5f..20f,
                steps = ((20f - 5f) / 0.2f - 1).toInt()
            )

            Text("Window Size: ${stepViewModel.windowSize.toInt()}")
            Slider(
                value = stepViewModel.windowSize,
                onValueChange = { stepViewModel.windowSize = it },
                valueRange = 1f..20f
            )

            Text("Debounce (ms): ${stepViewModel.debounce.toInt()}")
            Slider(
                value = stepViewModel.debounce,
                onValueChange = { stepViewModel.debounce = it },
                valueRange = 100f..600f,
                steps = ((600f - 100f) / 20f - 1).toInt()
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { //centroid, pan, zoom, rotate
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale *= zoom
//                        rotation += rotate
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                }
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
//                    rotationZ = rotation,
                    translationX = offsetX,
                    translationY = offsetY
                )
        ) {
            val centerX = size.width / 2
            val centerY = size.height / 2
            translate(left = centerX, top = centerY) {

                // Draw background
                drawRect(
                    color = Color.LightGray,
                    size = size,
                    topLeft = Offset(-centerX, -centerY)
                )

                // Draw points
                for (p in points) {
                    drawCircle(color = Color.Red, radius = 10f, center = p)
                }

                // ---- Compass code starts here ----
                val compassRadius = 100f
                val compassCenter = Offset(size.width/2 -150f, -(size.height/2 -150f))  // center of canvas

                // Draw compass circle
                drawCircle(
                    color = Color.Gray,
                    radius = compassRadius,
                    center = compassCenter,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
                )

                // Draw north line
                val northEnd = Offset(
                    compassCenter.x + compassRadius * kotlin.math.sin(-stepViewModel.heading),
                    compassCenter.y - compassRadius * kotlin.math.cos(-stepViewModel.heading)
                )

                drawLine(
                    color = Color.Red,
                    start = compassCenter,
                    end = northEnd,
                    strokeWidth = 4f
                )


                // Draw user heading arrow
                val arrowLength = compassRadius
                val headingEnd = Offset(
                    compassCenter.x + arrowLength * kotlin.math.sin(0f), // 0 rad = forward
                    compassCenter.y - arrowLength * kotlin.math.cos(0f)
                )

                drawLine(
                    color = Color.Blue,
                    start = compassCenter,
                    end = headingEnd,
                    strokeWidth = 6f
                )
                // ---- Compass code ends here ----
            }
        }


    }
}
