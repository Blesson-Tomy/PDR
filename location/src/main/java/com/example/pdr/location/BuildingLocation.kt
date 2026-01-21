package com.example.pdr.location

/**
 * Data class representing a building's geographic location.
 */
data class BuildingLocation(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radius: Float = 50f // Default radius for geofencing
)
