package com.example.pdr.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.pdr.model.Wall
import com.example.pdr.model.Stairwell
import com.example.pdr.model.Entrance

/**
 * Manages UI state for the floor plan.
 * Data loading is handled by FloorPlanRepository.
 */
class FloorPlanViewModel : ViewModel() {

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
}

