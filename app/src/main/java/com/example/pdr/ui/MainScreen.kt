package com.example.pdr.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.pdr.viewmodel.FloorPlanViewModel
import com.example.pdr.viewmodel.MotionViewModel
import com.example.pdr.viewmodel.StepViewModel

/**
 * The main screen of the application, which sets up the navigation between the PDR and Settings screens.
 *
 * This composable uses a [Scaffold] to provide a standard layout structure, including a
 * [BottomAppBar] for navigation. A [NavHost] is used to swap between the different screens.
 *
 * @param stepViewModel The ViewModel for the PDR system.
 * @param motionViewModel The ViewModel for motion classification.
 * @param floorPlanViewModel The ViewModel for the floor plan.
 */
@Composable
fun MainScreen(
    stepViewModel: StepViewModel,
    motionViewModel: MotionViewModel,
    floorPlanViewModel: FloorPlanViewModel
) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                // Navigation item for the PDR (main) screen.
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Place, contentDescription = "PDR") },
                    label = { Text("PDR") },
                    selected = navController.currentDestination?.route == "pdr",
                    onClick = {
                        navController.navigate("pdr") {
                            popUpTo(navController.graph.startDestinationId)
                            launchSingleTop = true
                        }
                    }
                )
                // Navigation item for the Settings screen.
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    selected = navController.currentDestination?.route == "settings",
                    onClick = {
                        navController.navigate("settings") {
                            popUpTo(navController.graph.startDestinationId)
                            launchSingleTop = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        // The NavHost is the container for the different screens (destinations).
        NavHost(
            navController = navController,
            startDestination = "pdr",
            modifier = Modifier.padding(innerPadding),
            // Disable animations for a faster, more responsive feel between tabs.
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {
            composable("pdr") { PdrScreen(stepViewModel, motionViewModel, floorPlanViewModel) }
            composable("settings") { SettingsScreen(stepViewModel) }
        }
    }
}

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
    // Get the list of points to draw from the ViewModel.
    val points = stepViewModel.points
    val walls = floorPlanViewModel.walls

    // Calculate the bounding box that contains all drawable content (walls and PDR points).
    val contentBounds = remember(walls, points) {
        if (walls.isEmpty() && points.isEmpty()) {
            // If there's nothing to draw, provide a default 1000x1000 area.
            return@remember Rect(-500f, -500f, 500f, 500f)
        }

        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY

        walls.forEach { wall ->
            minX = minOf(minX, wall.x1, wall.x2)
            minY = minOf(minY, wall.y1, wall.y2)
            maxX = maxOf(maxX, wall.x1, wall.x2)
            maxY = maxOf(maxY, wall.y1, wall.y2)
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
                .pointerInput(Unit) {
                    if (stepViewModel.isSettingOrigin) {
                        detectTapGestures {
                            // Convert tap location to canvas coordinates
                            val canvasX = (it.x - offsetX) / scale
                            val canvasY = (it.y - offsetY) / scale
                            stepViewModel.setNewOrigin(Offset(canvasX, canvasY))
                        }
                    } else {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(0.1f, 10f)
                            val zoomFactor = newScale / scale // Calculate the actual zoom factor after coercion

                            // Update offset to center zoom around the gesture centroid
                            offsetX = (offsetX - centroid.x) * zoomFactor + centroid.x + pan.x
                            offsetY = (offsetY - centroid.y) * zoomFactor + centroid.y + pan.y
                            
                            scale = newScale
                        }
                    }
                }
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY,
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

                if (stepViewModel.showFloorPlan) {
                    // Draw the floor plan.
                    for (wall in walls) {
                        drawLine(
                            color = Color.Black,
                            start = Offset(wall.x1, wall.y1),
                            end = Offset(wall.x2, wall.y2),
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
        
        // Button to set a new origin
        Button(
            onClick = { stepViewModel.toggleIsSettingOrigin() },
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        ) {
            Text(if (stepViewModel.isSettingOrigin) "Tap to set origin" else "Set Origin")
        }
    }
}

/**
 * The settings screen, which allows the user to configure the PDR algorithm and clear the path.
 */
@Composable
fun SettingsScreen(stepViewModel: StepViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Button to clear the PDR path.
        Button(onClick = { stepViewModel.clearDots() }) {
            Text("Clear Canvas")
        }

        // Text field for the user to input their height.
        OutlinedTextField(
            value = stepViewModel.height,
            onValueChange = { stepViewModel.height = it },
            label = { Text("Height (cm)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )

        // --- UI Control ---
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Show Floor Plan")
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = stepViewModel.showFloorPlan,
                onCheckedChange = { stepViewModel.showFloorPlan = it }
            )
        }

        // --- Stride Calculation Parameters ---
        Text("K (Frequency Factor): ${"%.2f".format(stepViewModel.kValue)}")
        Text("Controls how much stride length increases with speed.", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = stepViewModel.kValue,
            onValueChange = { stepViewModel.kValue = it },
            valueRange = 0.1f..1.0f
        )

        Text("C (Base Stride Factor): ${"%.2f".format(stepViewModel.cValue)}")
        Text("Determines base stride length as a percent of height.", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = stepViewModel.cValue,
            onValueChange = { stepViewModel.cValue = it },
            valueRange = 0.05f..0.5f
        )

        // --- Step Detection Parameters ---
        Text("Threshold: ${"%.1f".format(stepViewModel.threshold)}")
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

        Text("Cadence Average Size: ${stepViewModel.cadenceAverageSize.toInt()}")
        Slider(
            value = stepViewModel.cadenceAverageSize,
            onValueChange = { stepViewModel.cadenceAverageSize = it },
            valueRange = 1f..20f
        )
    }
}
