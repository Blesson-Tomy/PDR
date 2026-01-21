package com.example.pdr.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pdr.model.Wall
import com.example.pdr.model.Stairwell
import com.example.pdr.model.Entrance
import com.example.pdr.repository.FloorPlanRepository
import com.example.pdr.location.LocationService
import kotlinx.coroutines.launch

/**
 * Manages UI state for the floor plan.
 * Handles building/floor selection and async data loading.
 */
class FloorPlanViewModel : ViewModel() {

    var floorPlanRepository: FloorPlanRepository? = null

    // Building and floor selection
    var buildings by mutableStateOf<List<String>>(emptyList())
    var floors by mutableStateOf<List<String>>(emptyList())
    var selectedBuilding by mutableStateOf<String?>(null)
    var selectedFloor by mutableStateOf<String?>(null)
    
    // Loading states
    var isLoadingBuildings by mutableStateOf(false)
    var isLoadingFloors by mutableStateOf(false)
    var isLoadingWalls by mutableStateOf(false)
    var isLocatingBuilding by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var isDataLoaded by mutableStateOf(false)

    // Floor plan data (loaded by repository)
    val walls = mutableStateListOf<Wall>()
    val stairwells = mutableStateListOf<Stairwell>()
    val entrances = mutableStateListOf<Entrance>()

    // UI state for floor plan display
    var showFloorPlan by mutableStateOf(true)
    var showPointNumbers by mutableStateOf(false)
    var showEntrances by mutableStateOf(true)
    var isSettingOrigin by mutableStateOf(false)
    var floorPlanScale by mutableStateOf("0.62")
    var floorPlanRotation by mutableStateOf("0.00")

    /**
     * Fetches all available building names from Firestore.
     */
    fun fetchBuildingNames() {
        isLoadingBuildings = true
        errorMessage = null
        
        viewModelScope.launch {
            try {
                val buildingNames = floorPlanRepository?.fetchBuildingNames() ?: emptyList()
                buildings = buildingNames
                isLoadingBuildings = false
            } catch (e: Exception) {
                errorMessage = "Failed to fetch buildings: ${e.message}"
                isLoadingBuildings = false
            }
        }
    }

    /**
     * Fetches floor names for the selected building.
     */
    fun fetchFloorNames(buildingName: String) {
        isLoadingFloors = true
        errorMessage = null
        
        viewModelScope.launch {
            try {
                val floorNames = floorPlanRepository?.fetchFloorNames(buildingName) ?: emptyList()
                floors = floorNames
                isLoadingFloors = false
            } catch (e: Exception) {
                errorMessage = "Failed to fetch floors: ${e.message}"
                isLoadingFloors = false
            }
        }
    }

    /**
     * Loads walls data from Firestore for a specific building and floor.
     */
    fun loadWallsFromFirestore(buildingName: String, floorName: String) {
        isLoadingWalls = true
        errorMessage = null
        
        viewModelScope.launch {
            try {
                val loadedWalls = floorPlanRepository?.loadWallsFromFirestore(buildingName, floorName) ?: emptyList()
                
                loadWalls(loadedWalls)
                isLoadingWalls = false
                isDataLoaded = true
            } catch (e: Exception) {
                errorMessage = "Failed to load walls: ${e.message}"
                isLoadingWalls = false
            }
        }
    }

    /**
     * Loads stairs data from Firestore for a specific building and floor.
     */
    fun loadStairwellsFromFirestore(buildingName: String, floorName: String) {
        viewModelScope.launch {
            try {
                val loadedStairwells = floorPlanRepository?.loadStairwellsFromFirestore(buildingName, floorName) ?: emptyList()
                loadStairwells(loadedStairwells)
            } catch (e: Exception) {
                errorMessage = "Failed to load stairs: ${e.message}"
            }
        }
    }

    /**
     * Loads entrances data from Firestore for a specific building and floor.
     */
    fun loadEntrancesFromFirestore(buildingName: String, floorName: String) {
        viewModelScope.launch {
            try {
                val loadedEntrances = floorPlanRepository?.loadEntrancesFromFirestore(buildingName, floorName) ?: emptyList()
                loadEntrances(loadedEntrances)
            } catch (e: Exception) {
                errorMessage = "Failed to load entrances: ${e.message}"
            }
        }
    }

    /**
     * Loads walls data from repository.
     */
    fun loadWalls(wallsList: List<Wall>) {
        walls.clear()
        walls.addAll(wallsList)
    }

    /**
     * Loads stairwell polygons from repository.
     */
    fun loadStairwells(stairwellsList: List<Stairwell>) {
        stairwells.clear()
        stairwells.addAll(stairwellsList)
    }

    /**
     * Loads entrance points from repository.
     */
    fun loadEntrances(entrancesList: List<Entrance>) {
        entrances.clear()
        entrances.addAll(entrancesList)
    }

    /**
     * Toggles the state for setting a new origin point on the map.
     */
    fun toggleIsSettingOrigin() {
        isSettingOrigin = !isSettingOrigin
    }

    /**
     * Attempts to automatically select a building based on the device's current location.
     */
    fun autoSelectBuilding(locationService: LocationService) {
        if (isLocatingBuilding) return
        
        isLocatingBuilding = true
        errorMessage = null
        
        viewModelScope.launch {
            try {
                // 1. Fetch buildings with location data
                val buildingsWithLoc = floorPlanRepository?.fetchBuildingsWithLocation() ?: emptyList()
                
                if (buildingsWithLoc.isEmpty()) {
                    // Fallback to just names if no location data found, but we can't geolocate
                    fetchBuildingNames()
                    isLocatingBuilding = false
                    return@launch
                }
                
                // Update the names list regardless
                buildings = buildingsWithLoc.map { it.id } // Using ID as name for now
                
                // 2. Get current location
                val currentLoc = locationService.getCurrentLocation()
                
                if (currentLoc != null) {
                    // 3. Find closest
                    val closest = locationService.findNearestBuilding(currentLoc, buildingsWithLoc)
                    
                    if (closest != null) {
                        selectedBuilding = closest.id
                        // Automatically fetch floors for this building
                        fetchFloorNames(closest.id)
                    } else {
                        errorMessage = "No buildings found nearby"
                    }
                } else {
                    errorMessage = "Could not determine location"
                }
            } catch (e: Exception) {
                errorMessage = "Location error: ${e.message}"
            } finally {
                isLocatingBuilding = false
            }
        }
    }
}
