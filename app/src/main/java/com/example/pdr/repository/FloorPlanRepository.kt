package com.example.pdr.repository

import android.app.Application
import com.example.pdr.model.Wall
import com.example.pdr.model.StairLine
import com.example.pdr.model.Stairwell
import com.example.pdr.model.Entrance
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader

/**
 * Repository for floor plan data.
 * Responsible for loading wall and stairwell data from JSON assets.
 * No UI dependencies.
 */
class FloorPlanRepository(private val application: Application) {

    /**
     * Loads the floor plan walls from the JSON file in the assets folder.
     *
     * @return A list of Wall objects.
     */
    fun loadFloorPlan(fileName: String = "first_floor_walls.json"): List<Wall> {
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

    /**
     * Loads the stairwell polygons from the JSON file in the assets folder.
     * Groups stair lines by polygon ID and creates Stairwell objects.
     *
     * @param fileName The name of the stair JSON file (default: "first_floor_stairs.json")
     * @return A list of Stairwell objects where each represents a complete stairwell polygon.
     */
    fun loadStairwells(fileName: String = "first_floor_stairs.json"): List<Stairwell> {
        return try {
            val inputStream = application.assets.open(fileName)
            val reader = InputStreamReader(inputStream)

            // Parse all stair lines
            val stairLineListType = object : TypeToken<List<StairLine>>() {}.type
            val stairLines: List<StairLine> = Gson().fromJson(reader, stairLineListType)

            // Group stair lines by polygon ID
            val groupedByPolygonId = stairLines.groupBy { it.stair_polygon_id }

            // Convert each group into a Stairwell polygon
            groupedByPolygonId.map { (polygonId, lines) ->
                val floorsConnected = lines.firstOrNull()?.floors_connected ?: emptyList()
                
                // Build ordered polygon points from line segments
                val orderedPoints = buildOrderedPolygon(lines)

                Stairwell(
                    polygonId = polygonId,
                    points = orderedPoints,
                    floorsConnected = floorsConnected
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Builds an ordered list of points from line segments that form a polygon.
     * Traces the edges to create a proper polygon path for filling.
     */
    private fun buildOrderedPolygon(lines: List<StairLine>): List<Pair<Float, Float>> {
        if (lines.isEmpty()) return emptyList()

        // Create a map of edges: each point maps to all points it connects to
        val edges = mutableMapOf<Pair<Float, Float>, MutableList<Pair<Float, Float>>>()
        
        for (line in lines) {
            val p1 = Pair(line.x1, line.y1)
            val p2 = Pair(line.x2, line.y2)
            
            // Add both directions to handle edges in any order
            edges.computeIfAbsent(p1) { mutableListOf() }.add(p2)
            edges.computeIfAbsent(p2) { mutableListOf() }.add(p1)
        }

        // Trace the polygon by following connected edges
        val orderedPoints = mutableListOf<Pair<Float, Float>>()
        val visited = mutableSetOf<Pair<Pair<Float, Float>, Pair<Float, Float>>>()
        
        // Start from any point
        if (edges.isEmpty()) return emptyList()
        
        var currentPoint = edges.keys.first()
        val startPoint = currentPoint
        
        do {
            orderedPoints.add(currentPoint)
            val neighbors = edges[currentPoint] ?: break
            
            // Find the next unvisited neighbor
            var nextPoint: Pair<Float, Float>? = null
            for (neighbor in neighbors) {
                val edge = Pair(currentPoint, neighbor)
                val reverseEdge = Pair(neighbor, currentPoint)
                
                // Pick next point if we haven't used this edge
                if (!visited.contains(edge) && !visited.contains(reverseEdge)) {
                    nextPoint = neighbor
                    visited.add(edge)
                    break
                }
            }
            
            if (nextPoint == null) break
            currentPoint = nextPoint
            
        } while (currentPoint != startPoint && orderedPoints.size < edges.size * 2)
        
        return orderedPoints
    }

    /**
     * Loads the entrance points from the JSON file in the assets folder.
     * Entrances can be regular room openings or stairwell entry/exits.
     * Stairwell entry/exits are marked with "stairs": true.
     *
     * @param fileName The name of the entrance JSON file (default: "first_floor_entrances.json")
     * @return A list of Entrance objects.
     */
    fun loadEntrances(fileName: String = "first_floor_entrances.json"): List<Entrance> {
        return try {
            val inputStream = application.assets.open(fileName)
            val reader = InputStreamReader(inputStream)

            // Parse the JSON object which contains an "entrances" array
            val jsonObject = Gson().fromJson(reader, JsonObject::class.java)
            val entrancesArray = jsonObject.getAsJsonArray("entrances")

            val entranceListType = object : TypeToken<List<Entrance>>() {}.type
            Gson().fromJson(entrancesArray, entranceListType)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
