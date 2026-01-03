package com.example.pdr.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.pdr.viewmodel.FloorPlanViewModel

/**
 * Building and Floor selector composable.
 * Allows users to select a building and floor to load the floor plan data from Firestore.
 */
@Composable
fun BuildingSelector(
    viewModel: FloorPlanViewModel,
    onDataLoaded: () -> Unit
) {
    LaunchedEffect(Unit) {
        // Fetch building names on first load
        viewModel.fetchBuildingNames()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Select Building & Floor",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // Error message display
        viewModel.errorMessage?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // Building dropdown
        if (viewModel.isLoadingBuildings) {
            CircularProgressIndicator(modifier = Modifier.padding(bottom = 16.dp))
        } else {
            BuildingDropdown(
                buildings = viewModel.buildings,
                selectedBuilding = viewModel.selectedBuilding,
                onBuildingSelected = { building ->
                    viewModel.selectedBuilding = building
                    viewModel.selectedFloor = null
                    viewModel.floors = emptyList()
                    viewModel.fetchFloorNames(building)
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Floor dropdown
        if (viewModel.isLoadingFloors) {
            CircularProgressIndicator(modifier = Modifier.padding(bottom = 16.dp))
        } else if (viewModel.selectedBuilding != null) {
            FloorDropdown(
                floors = viewModel.floors,
                selectedFloor = viewModel.selectedFloor,
                onFloorSelected = { floor ->
                    viewModel.selectedFloor = floor
                }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Load button
        Button(
            onClick = {
                val building = viewModel.selectedBuilding
                val floor = viewModel.selectedFloor
                
                if (building != null && floor != null) {
                    viewModel.loadWallsFromFirestore(building, floor)
                    viewModel.loadStairwellsFromFirestore(building, floor)
                    viewModel.loadEntrancesFromFirestore(building, floor)
                }
            },
            enabled = viewModel.selectedBuilding != null && 
                     viewModel.selectedFloor != null && 
                     !viewModel.isLoadingWalls,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            if (viewModel.isLoadingWalls) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Load Floor Plan")
            }
        }
    }

    // Navigate away when data is loaded
    if (viewModel.isDataLoaded) {
        onDataLoaded()
    }
}

@Composable
private fun BuildingDropdown(
    buildings: List<String>,
    selectedBuilding: String?,
    onBuildingSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        OutlinedButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = selectedBuilding ?: "Select Building",
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth()
        ) {
            buildings.forEach { building ->
                DropdownMenuItem(
                    text = { Text(building) },
                    onClick = {
                        onBuildingSelected(building)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun FloorDropdown(
    floors: List<String>,
    selectedFloor: String?,
    onFloorSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        OutlinedButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = selectedFloor ?: "Select Floor",
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth()
        ) {
            floors.forEach { floor ->
                DropdownMenuItem(
                    text = { Text(floor) },
                    onClick = {
                        onFloorSelected(floor)
                        expanded = false
                    }
                )
            }
        }
    }
}
