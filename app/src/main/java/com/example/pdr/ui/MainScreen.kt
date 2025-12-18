package com.example.pdr.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
            composable("settings") { SettingsScreen(stepViewModel, floorPlanViewModel) }
        }
    }
}


