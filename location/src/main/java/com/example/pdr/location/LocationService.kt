package com.example.pdr.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.example.pdr.location.BuildingLocation
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await
import kotlin.math.*

/**
 * Service to handle location-related operations.
 * Requires ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION permissions.
 */
class LocationService(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    /**
     * Gets the current location of the device.
     * 
     * @return The current Location, or null if location is unavailable or permission denied.
     */
    @SuppressLint("MissingPermission") // Caller handles permission check
    suspend fun getCurrentLocation(): Location? {
        return try {
            val updateCancellationToken = CancellationTokenSource()
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                updateCancellationToken.token
            ).await()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Finds the nearest building from a list based on the user's current location.
     * 
     * @param currentLocation The user's current location.
     * @param buildings List of buildings with their coordinates.
     * @param maxDistanceMeters Maximum distance to consider a building valid (Geofence).
     * @return The closest BuildingLocation within range, or null if none found.
     */
    fun findNearestBuilding(
        currentLocation: Location,
        buildings: List<BuildingLocation>,
        maxDistanceMeters: Float = 200f
    ): BuildingLocation? {
        var closestBuilding: BuildingLocation? = null
        var minDistance = Float.MAX_VALUE

        for (building in buildings) {
            val results = FloatArray(1)
            Location.distanceBetween(
                currentLocation.latitude,
                currentLocation.longitude,
                building.latitude,
                building.longitude,
                results
            )
            val distance = results[0]

            if (distance <= maxDistanceMeters && distance < minDistance) {
                minDistance = distance
                closestBuilding = building
            }
        }

        return closestBuilding
    }
}
