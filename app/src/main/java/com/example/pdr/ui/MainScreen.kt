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

@Composable
fun MainScreen(stepViewModel: StepViewModel, motionViewModel: MotionViewModel) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            BottomAppBar {
                NavigationBar {
                    // PDR Screen Destination
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
                    // Settings Screen Destination
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
        NavHost(
            navController = navController,
            startDestination = "pdr",
            modifier = Modifier.padding(innerPadding),
//            enterTransition = { EnterTransition.None },
//            exitTransition = { ExitTransition.None },
//            popEnterTransition = { EnterTransition.None },
//            popExitTransition = { ExitTransition.None }
        ) {
            composable("pdr") { PdrScreen(stepViewModel, motionViewModel) }
            composable("settings") { SettingsScreen(stepViewModel) }
        }
    }
}

@Composable
fun PdrScreen(stepViewModel: StepViewModel, motionViewModel: MotionViewModel) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val points = stepViewModel.points

    Column(modifier = Modifier.fillMaxSize()) {
        // Display the motion type and confidence
        val confidencePercentage = (motionViewModel.confidence * 100).toInt()
        Text(
            text = "Motion Type: ${motionViewModel.motionType} ($confidencePercentage%)",
            modifier = Modifier.padding(16.dp)
        )

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale *= zoom
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                }
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
        ) {
            val centerX = size.width / 2
            val centerY = size.height / 2
            translate(left = centerX, top = centerY) {
                drawRect(
                    color = Color.LightGray,
                    size = size,
                    topLeft = Offset(-centerX, -centerY)
                )

                // Draw points
                for (p in points) {
                    drawCircle(color = Color.Red, radius = 10f, center = p)
                }

                // Compass
                val compassRadius = 100f
                val compassCenter = Offset(size.width / 2 - 150f, -(size.height / 2 - 150f))
                drawCircle(
                    color = Color.Gray,
                    radius = compassRadius,
                    center = compassCenter,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
                )
                val northEnd = Offset(
                    compassCenter.x + compassRadius * kotlin.math.sin(-stepViewModel.heading),
                    compassCenter.y - compassRadius * kotlin.math.cos(-stepViewModel.heading)
                )
                drawLine(color = Color.Red, start = compassCenter, end = northEnd, strokeWidth = 4f)
                val headingEnd = Offset(
                    compassCenter.x + compassRadius * kotlin.math.sin(0f),
                    compassCenter.y - compassRadius * kotlin.math.cos(0f)
                )
                drawLine(color = Color.Blue, start = compassCenter, end = headingEnd, strokeWidth = 6f)
            }
        }
    }
}

@Composable
fun SettingsScreen(stepViewModel: StepViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(onClick = { stepViewModel.clearDots() }) {
            Text("Clear Canvas")
        }

        OutlinedTextField(
            value = stepViewModel.height,
            onValueChange = { stepViewModel.height = it },
            label = { Text("Height (cm)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )

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
    }
}
