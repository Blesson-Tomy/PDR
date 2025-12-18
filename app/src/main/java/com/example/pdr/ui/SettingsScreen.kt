package com.example.pdr.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.pdr.viewmodel.FloorPlanViewModel
import com.example.pdr.viewmodel.StepViewModel

/**
 * The settings screen, which allows the user to configure the PDR algorithm and clear the path.
 */
@Composable
fun SettingsScreen(stepViewModel: StepViewModel, floorPlanViewModel: FloorPlanViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {

        // Text field for the user to input their height.
        OutlinedTextField(
            value = stepViewModel.height,
            onValueChange = { stepViewModel.height = it },
            label = { Text("Height (cm)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )

        // --- UI Control --
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Show Floor Plan")
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = floorPlanViewModel.showFloorPlan,
                onCheckedChange = { floorPlanViewModel.showFloorPlan = it }
            )
        }

        OutlinedTextField(
            value = floorPlanViewModel.floorPlanScale,
            onValueChange = { floorPlanViewModel.floorPlanScale = it },
            label = { Text("Floor Plan Scale") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true
        )

        OutlinedTextField(
            value = floorPlanViewModel.floorPlanRotation,
            onValueChange = { floorPlanViewModel.floorPlanRotation = it },
            label = { Text("Floor Plan Rotation (°)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true
        )

        // --- Stride Calculation Parameters --
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

        // --- Step Detection Parameters --
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