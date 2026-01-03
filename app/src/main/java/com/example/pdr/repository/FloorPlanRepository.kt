package com.example.pdr.repository

import android.app.Application
import android.content.SharedPreferences
import com.example.pdr.model.Wall
import com.example.pdr.model.StairLine
import com.example.pdr.model.Stairwell
import com.example.pdr.model.Entrance
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader
import java.io.File
import kotlinx.coroutines.tasks.await

/**
 * Repository for floor plan data.
 * Responsible for loading wall and stairwell data from Firestore or JSON assets with caching.
 * No UI dependencies.
 */
class FloorPlanRepository(private val application: Application) {
    private val db = FirebaseFirestore.getInstance()
    private val sharedPreferences: SharedPreferences = application.getSharedPreferences(
        "floor_plan_cache",
        Application.MODE_PRIVATE
    )
    private val gson = Gson()

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
     * Loads stairs from Firestore for a specific building and floor.
     * Path: /buildings/{buildingName}/floors/{floorName} - stairs are stored as a field (JSON array)
     * Downloads and saves the stairs to a JSON file in cache directory.
     *
     * @param buildingName The name of the building.
     * @param floorName The name of the floor.
     * @return A list of Stairwell objects.
     */
    suspend fun loadStairwellsFromFirestore(buildingName: String, floorName: String): List<Stairwell> {
        return try {
            val fileName = "${buildingName}_${floorName}_stairs.json"
            
            android.util.Log.d("STAIRS_FILE", "🔄 Fetching stairs from Firestore: $buildingName/$floorName")
            val floorDoc = db.collection("buildings").document(buildingName)
                .collection("floors").document(floorName)
                .get().await()
            
            if (!floorDoc.exists()) {
                android.util.Log.e("STAIRS_FILE", "✗ Floor document not found: $buildingName/$floorName")
                return emptyList()
            }
            
            // Get the stairs array from the floor document
            @Suppress("UNCHECKED_CAST")
            val stairsList = floorDoc.get("stairs") as? List<Map<String, Any>> ?: emptyList()
            
            android.util.Log.d("STAIRS_FILE", "✓ Retrieved ${stairsList.size} stair lines from floor document")
            
            // Convert each stair map to StairLine object
            val stairLines = stairsList.mapNotNull { stairMap ->
                try {
                    val stairLine = gson.fromJson(gson.toJson(stairMap), StairLine::class.java)
                    stairLine
                } catch (e: Exception) {
                    android.util.Log.w("STAIRS_FILE", "⚠️ Failed to parse stair: ${e.message}")
                    null
                }
            }
            
            android.util.Log.d("STAIRS_FILE", "✓ Successfully converted ${stairLines.size} stair lines")
            
            // Build ordered polygons from line segments
            val groupedByPolygonId = stairLines.groupBy { it.stair_polygon_id }
            val stairwells = groupedByPolygonId.map { (polygonId, lines) ->
                val floorsConnected = lines.firstOrNull()?.floors_connected ?: emptyList()
                val orderedPoints = buildOrderedPolygon(lines)
                Stairwell(
                    polygonId = polygonId,
                    points = orderedPoints,
                    floorsConnected = floorsConnected
                )
            }
            
            // Save the stairs to a JSON file
            if (stairLines.isNotEmpty()) {
                saveStairsToFile(stairLines, fileName)
            }
            
            stairwells
        } catch (e: Exception) {
            android.util.Log.e("STAIRS_FILE", "✗ Error fetching from Firestore: ${e.message}")
            e.printStackTrace()
            // Fall back to cached file if Firestore fails
            loadStairwellsFromFile("${buildingName}_${floorName}_stairs.json")
        }
    }

    /**
     * Loads entrances from Firestore for a specific building and floor.
     * Path: /buildings/{buildingName}/floors/{floorName} - entrances are stored as a field (JSON array)
     * Downloads and saves the entrances to a JSON file in cache directory.
     *
     * @param buildingName The name of the building.
     * @param floorName The name of the floor.
     * @return A list of Entrance objects.
     */
    suspend fun loadEntrancesFromFirestore(buildingName: String, floorName: String): List<Entrance> {
        return try {
            val fileName = "${buildingName}_${floorName}_entrances.json"
            
            android.util.Log.d("ENTRANCES_FILE", "🔄 Fetching entrances from Firestore: $buildingName/$floorName")
            val floorDoc = db.collection("buildings").document(buildingName)
                .collection("floors").document(floorName)
                .get().await()
            
            if (!floorDoc.exists()) {
                android.util.Log.e("ENTRANCES_FILE", "✗ Floor document not found: $buildingName/$floorName")
                return emptyList()
            }
            
            // Get the entrances array from the floor document
            @Suppress("UNCHECKED_CAST")
            val entrancesList = floorDoc.get("entrances") as? List<Map<String, Any>> ?: emptyList()
            
            android.util.Log.d("ENTRANCES_FILE", "✓ Retrieved ${entrancesList.size} entrances from floor document")
            
            // Convert each entrance map to Entrance object
            val entrances = entrancesList.mapNotNull { entranceMap ->
                try {
                    val entrance = gson.fromJson(gson.toJson(entranceMap), Entrance::class.java)
                    entrance
                } catch (e: Exception) {
                    android.util.Log.w("ENTRANCES_FILE", "⚠️ Failed to parse entrance: ${e.message}")
                    null
                }
            }
            
            android.util.Log.d("ENTRANCES_FILE", "✓ Successfully converted ${entrances.size} entrances")
            
            // Save the entrances to a JSON file
            if (entrances.isNotEmpty()) {
                saveEntrancesToFile(entrances, fileName)
            }
            
            entrances
        } catch (e: Exception) {
            android.util.Log.e("ENTRANCES_FILE", "✗ Error fetching from Firestore: ${e.message}")
            e.printStackTrace()
            // Fall back to cached file if Firestore fails
            loadEntrancesFromFile("${buildingName}_${floorName}_entrances.json")
        }
    }

    /**
     * Fetches all available building names from Firestore.
     * Path: /buildings/{buildingName}/..
     *
     * @return A list of building names.
     */
    suspend fun fetchBuildingNames(): List<String> {
        return try {
            val snapshot = db.collection("buildings").get().await()
            snapshot.documents.mapNotNull { it.id }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Fetches all available floor names for a given building from Firestore.
     * Path: /buildings/{buildingName}/floors/{floorName}/..
     *
     * @param buildingName The name of the building.
     * @return A list of floor names.
     */
    suspend fun fetchFloorNames(buildingName: String): List<String> {
        return try {
            val snapshot = db.collection("buildings").document(buildingName)
                .collection("floors").get().await()
            snapshot.documents.mapNotNull { it.id }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Loads walls from Firestore for a specific building and floor.
     * Path: /buildings/{buildingName}/floors/{floorName} - walls are stored as a field (JSON array)
     * Downloads and saves the walls to a JSON file in cache directory.
     *
     * @param buildingName The name of the building.
     * @param floorName The name of the floor.
     * @return A list of Wall objects.
     */
    suspend fun loadWallsFromFirestore(buildingName: String, floorName: String): List<Wall> {
        return try {
            val fileName = "${buildingName}_${floorName}_walls.json"
            
            // Try to fetch from Firestore
            android.util.Log.d("WALLS_FILE", "🔄 Fetching walls from Firestore: $buildingName/$floorName")
            val floorDoc = db.collection("buildings").document(buildingName)
                .collection("floors").document(floorName)
                .get().await()
            
            if (!floorDoc.exists()) {
                android.util.Log.e("WALLS_FILE", "✗ Floor document not found: $buildingName/$floorName")
                return emptyList()
            }
            
            // Get the walls array from the floor document
            @Suppress("UNCHECKED_CAST")
            val wallsList = floorDoc.get("walls") as? List<Map<String, Any>> ?: emptyList()
            
            android.util.Log.d("WALLS_FILE", "✓ Retrieved ${wallsList.size} walls from floor document")
            
            // Convert each wall map to Wall object
            val walls = wallsList.mapNotNull { wallMap ->
                try {
                    val wall = gson.fromJson(gson.toJson(wallMap), Wall::class.java)
                    wall
                } catch (e: Exception) {
                    android.util.Log.w("WALLS_FILE", "⚠️ Failed to parse wall: ${e.message}")
                    null
                }
            }
            
            android.util.Log.d("WALLS_FILE", "✓ Successfully converted ${walls.size} walls")
            
            // Save the walls to a JSON file
            if (walls.isNotEmpty()) {
                saveWallsToFile(walls, fileName)
            }
            
            walls
        } catch (e: Exception) {
            android.util.Log.e("WALLS_FILE", "✗ Error fetching from Firestore: ${e.message}")
            e.printStackTrace()
            // Fall back to cached file if Firestore fails
            loadWallsFromFile("${buildingName}_${floorName}_walls.json")
        }
    }

    /**
     * Loads walls from the cache (SharedPreferences).
     *
     * @param buildingName The name of the building.
     * @param floorName The name of the floor.
     * @return A list of Wall objects from cache, or empty list if not cached.
     */
    private fun loadWallsFromCache(buildingName: String, floorName: String): List<Wall> {
        return try {
            val cacheKey = "walls_${buildingName}_${floorName}"
            val wallsJson = sharedPreferences.getString(cacheKey, null) ?: return emptyList()
            
            val wallListType = object : TypeToken<List<Wall>>() {}.type
            gson.fromJson(wallsJson, wallListType)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Saves walls to a JSON file in the app's cache directory.
     * File format: {buildingName}_{floorName}_walls.json
     *
     * @param walls The list of Wall objects to save.
     * @param fileName The name of the file to save to.
     */
    private fun saveWallsToFile(walls: List<Wall>, fileName: String) {
        try {
            val file = File(application.cacheDir, fileName)
            val wallsJson = gson.toJson(walls)
            file.writeText(wallsJson)
            android.util.Log.d("WALLS_FILE", "✓ Walls saved to: ${file.absolutePath}")
        } catch (e: Exception) {
            android.util.Log.e("WALLS_FILE", "✗ Error saving walls: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Loads walls from a JSON file in the app's cache directory.
     *
     * @param fileName The name of the file to load from.
     * @return A list of Wall objects from file, or empty list if file doesn't exist.
     */
    private fun loadWallsFromFile(fileName: String): List<Wall> {
        return try {
            val file = File(application.cacheDir, fileName)
            if (!file.exists()) {
                android.util.Log.d("WALLS_FILE", "⚠️ Cache file not found: $fileName")
                return emptyList()
            }
            
            val wallsJson = file.readText()
            val wallListType = object : TypeToken<List<Wall>>() {}.type
            val walls: List<Wall> = gson.fromJson(wallsJson, wallListType)
            android.util.Log.d("WALLS_FILE", "✓ Walls loaded from cache: ${walls.size} walls from ${file.absolutePath}")
            walls
        } catch (e: Exception) {
            android.util.Log.e("WALLS_FILE", "✗ Error loading from cache: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Clears the cache for a specific building and floor.
     *
     * @param buildingName The name of the building.
     * @param floorName The name of the floor.
     */
    fun clearCache(buildingName: String, floorName: String) {
        val cacheKey = "walls_${buildingName}_${floorName}"
        sharedPreferences.edit().remove(cacheKey).apply()
    }

    /**
     * Clears all cached data.
     */
    fun clearAllCache() {
        sharedPreferences.edit().clear().apply()
    }

    /**
     * Saves stairs to a JSON file in the app's cache directory.
     * File format: {buildingName}_{floorName}_stairs.json
     *
     * @param stairLines The list of StairLine objects to save.
     * @param fileName The name of the file to save to.
     */
    private fun saveStairsToFile(stairLines: List<StairLine>, fileName: String) {
        try {
            val file = File(application.cacheDir, fileName)
            val stairsJson = gson.toJson(stairLines)
            file.writeText(stairsJson)
            android.util.Log.d("STAIRS_FILE", "✓ Stairs saved to: ${file.absolutePath}")
        } catch (e: Exception) {
            android.util.Log.e("STAIRS_FILE", "✗ Error saving stairs: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Loads stairs from a JSON file in the app's cache directory.
     *
     * @param fileName The name of the file to load from.
     * @return A list of Stairwell objects from file, or empty list if file doesn't exist.
     */
    private fun loadStairwellsFromFile(fileName: String): List<Stairwell> {
        return try {
            val file = File(application.cacheDir, fileName)
            if (!file.exists()) {
                android.util.Log.d("STAIRS_FILE", "⚠️ Cache file not found: $fileName")
                return emptyList()
            }
            
            val stairsJson = file.readText()
            val stairLineListType = object : TypeToken<List<StairLine>>() {}.type
            val stairLines: List<StairLine> = gson.fromJson(stairsJson, stairLineListType)
            
            // Build ordered polygons from line segments
            val groupedByPolygonId = stairLines.groupBy { it.stair_polygon_id }
            val stairwells = groupedByPolygonId.map { (polygonId, lines) ->
                val floorsConnected = lines.firstOrNull()?.floors_connected ?: emptyList()
                val orderedPoints = buildOrderedPolygon(lines)
                Stairwell(
                    polygonId = polygonId,
                    points = orderedPoints,
                    floorsConnected = floorsConnected
                )
            }
            
            android.util.Log.d("STAIRS_FILE", "✓ Stairs loaded from cache: ${stairwells.size} stairwells from ${file.absolutePath}")
            stairwells
        } catch (e: Exception) {
            android.util.Log.e("STAIRS_FILE", "✗ Error loading stairs from file: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Saves entrances to a JSON file in the app's cache directory.
     * File format: {buildingName}_{floorName}_entrances.json
     *
     * @param entrances The list of Entrance objects to save.
     * @param fileName The name of the file to save to.
     */
    private fun saveEntrancesToFile(entrances: List<Entrance>, fileName: String) {
        try {
            val file = File(application.cacheDir, fileName)
            val entrancesJson = gson.toJson(entrances)
            file.writeText(entrancesJson)
            android.util.Log.d("ENTRANCES_FILE", "✓ Entrances saved to: ${file.absolutePath}")
        } catch (e: Exception) {
            android.util.Log.e("ENTRANCES_FILE", "✗ Error saving entrances: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Loads entrances from a JSON file in the app's cache directory.
     *
     * @param fileName The name of the file to load from.
     * @return A list of Entrance objects from file, or empty list if file doesn't exist.
     */
    private fun loadEntrancesFromFile(fileName: String): List<Entrance> {
        return try {
            val file = File(application.cacheDir, fileName)
            if (!file.exists()) {
                android.util.Log.d("ENTRANCES_FILE", "⚠️ Cache file not found: $fileName")
                return emptyList()
            }
            
            val entrancesJson = file.readText()
            val entranceListType = object : TypeToken<List<Entrance>>() {}.type
            val entrances: List<Entrance> = gson.fromJson(entrancesJson, entranceListType)
            android.util.Log.d("ENTRANCES_FILE", "✓ Entrances loaded from cache: ${entrances.size} entrances from ${file.absolutePath}")
            entrances
        } catch (e: Exception) {
            android.util.Log.e("ENTRANCES_FILE", "✗ Error loading entrances from file: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }
}
