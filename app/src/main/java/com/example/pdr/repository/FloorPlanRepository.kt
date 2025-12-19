package com.example.pdr.repository

import android.app.Application
import com.example.pdr.model.Wall
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader

/**
 * Repository for floor plan data.
 * Responsible for loading wall data from JSON assets.
 * No UI dependencies.
 */
class FloorPlanRepository(private val application: Application) {

    /**
     * Loads the floor plan from the JSON file in the assets folder.
     *
     * @return A list of Wall objects.
     */
    fun loadFloorPlan(fileName: String = "first_floor.json"): List<Wall> {
        return try {
            // Open the asset and use a stream reader to handle the file.
            val inputStream = application.assets.open(fileName)
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
}
