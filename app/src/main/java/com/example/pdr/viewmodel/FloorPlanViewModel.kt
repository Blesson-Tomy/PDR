package com.example.pdr.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.example.pdr.model.Wall
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader

/**
 * Loads and holds the floor plan data from a JSON asset file.
 *
 * @param application The application context, needed to access the assets.
 */
class FloorPlanViewModel(application: Application) : AndroidViewModel(application) {

    // A list of all the walls that make up the floor plan.
    val walls: List<Wall>

    // If true, the canvas will show the static floor plan. Otherwise, it shows the live PDR path.
    var showFloorPlan by mutableStateOf(true)
    // When true, the next tap on the canvas will set a new origin point.
    var isSettingOrigin by mutableStateOf(false)
    // A scaling factor to apply to the floor plan coordinates.
    var floorPlanScale by mutableStateOf("0.62")

    init {
        // Load the floor plan from the JSON file in the assets folder at initialization.
        walls = loadFloorPlanFromAssets("first_floor.json")
    }

    /**
     * Reads a JSON file from the assets folder and parses it into a list of Wall objects.
     *
     * @param fileName The name of the JSON file in the assets folder.
     * @return A list of Wall objects.
     */
    private fun loadFloorPlanFromAssets(fileName: String): List<Wall> {
        return try {
            // Open the asset and use a stream reader to handle the file.
            val inputStream = getApplication<Application>().assets.open(fileName)
            val reader = InputStreamReader(inputStream)

            // Use Gson to parse the JSON array directly into a list of Wall objects.
            val wallListType = object : TypeToken<List<Wall>>() {}.type
            Gson().fromJson(reader, wallListType)
        } catch (e: Exception) {
            // If the file can't be read or parsed, print the error and return an empty list.
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Toggles the state for setting a new origin point on the map.
     */
    fun toggleIsSettingOrigin() {
        isSettingOrigin = !isSettingOrigin
    }
}
