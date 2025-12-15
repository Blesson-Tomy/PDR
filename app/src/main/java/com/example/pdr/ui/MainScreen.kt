package com.example.pdr.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
 */
@Composable
fun MainScreen(stepViewModel: StepViewModel, motionViewModel: MotionViewModel) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            BottomAppBar {
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
            composable("pdr") { PdrScreen(stepViewModel, motionViewModel) }
            composable("settings") { SettingsScreen(stepViewModel) }
        }
    }
}

/**
 * The main PDR screen, which displays the user's path, motion data, and a compass.
 */
@Composable
fun PdrScreen(stepViewModel: StepViewModel, motionViewModel: MotionViewModel) {
    // State for handling pan, zoom, and rotation gestures on the canvas.
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    // Get the list of points to draw from the ViewModel.
    val points = stepViewModel.points

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Display the motion type and its confidence score from the MotionViewModel.
            val confidencePercentage = (motionViewModel.confidence * 100).toInt()
            Text("Motion Type: ${motionViewModel.motionType} ($confidencePercentage%)")

            // Display the last calculated stride length from the StepViewModel.
            Text("Last Stride: ${"%.1f".format(stepViewModel.lastStrideLengthCm)} cm")

            // Display the average cadence over the last few steps.
            Text("Average Cadence: ${"%.2f".format(stepViewModel.averageCadence)} steps/sec")
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                // Add gesture detection for pan and zoom.
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale *= zoom
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                }
                // Apply the gesture transformations to the canvas.
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
        ) {
            // Center the coordinate system so (0,0) is in the middle of the canvas.
            val centerX = size.width / 2
            val centerY = size.height / 2
            translate(left = centerX, top = centerY) {
                // Draw a light gray background.
                drawRect(
                    color = Color.LightGray,
                    size = size,
                    topLeft = Offset(-centerX, -centerY)
                )

                // Draw a red circle for each point in the user's path.
                for (p in points) {
                    drawCircle(color = Color.Red, radius = 10f, center = p)
                }

                // --- Compass Drawing --- //
                val compassRadius = 100f
                val compassCenter = Offset(size.width / 2 - 150f, -(size.height / 2 - 150f)) // Top-right corner

                // Draw the compass background circle.
                drawCircle(
                    color = Color.Gray,
                    radius = compassRadius,
                    center = compassCenter,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
                )

                // Draw the red "North" line, which rotates based on the device's heading.
                val northEnd = Offset(
                    compassCenter.x + compassRadius * kotlin.math.sin(-stepViewModel.heading),
                    compassCenter.y - compassRadius * kotlin.math.cos(-stepViewModel.heading)
                )
                drawLine(color = Color.Red, start = compassCenter, end = northEnd, strokeWidth = 4f)
                
                // Draw the blue arrow representing the user's current forward direction (always points up).
                val headingEnd = Offset(
                    compassCenter.x + compassRadius * kotlin.math.sin(0f),
                    compassCenter.y - compassRadius * kotlin.math.cos(0f)
                )
                drawLine(color = Color.Blue, start = compassCenter, end = headingEnd, strokeWidth = 6f)
            }
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
